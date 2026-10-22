package com.focustask.alerts

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import com.focustask.FocusApp
import com.focustask.core.AlertKind
import com.focustask.core.AlertPlanner
import com.focustask.core.BreakPlanner
import com.focustask.core.Mode
import com.focustask.core.Schedule
import com.focustask.core.Vibrations
import com.focustask.data.AlertMode
import com.focustask.data.AppData
import com.focustask.data.SoundSlot
import com.focustask.data.fromStored
import com.focustask.data.toStored
import com.focustask.widget.FocusLargeWidgetReceiver
import com.focustask.widget.FocusWidgetReceiver
import com.focustask.widget.FocusWidgets
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class Output { SOUND, VIBRATE, NONE }

/** Plans alarms, plays the alerts and keeps the notification and widgets fresh. */
object Alerts {
    const val CHANNEL = "focus_timer"
    const val ACTION_TICK = "com.focustask.TICK"
    const val ACTION_TEST = "com.focustask.TEST_ALERT"
    const val TEST_DELAY_SEC = 10

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Your current task and the focus timer"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun notificationsAllowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** What actually happens when an alert is due: the app follows the phone's settings. */
    fun outputFor(ctx: Context, mode: AlertMode): Output {
        if (!notificationsAllowed(ctx)) return Output.NONE
        if (mode == AlertMode.SILENT) return Output.NONE
        val ringer = ctx.getSystemService(AudioManager::class.java).ringerMode
        if (mode == AlertMode.VIBRATE || ringer != AudioManager.RINGER_MODE_NORMAL) return Output.VIBRATE
        return Output.SOUND
    }

    private fun zone() = ZoneId.systemDefault()
    private fun LocalDateTime.millis(): Long = atZone(zone()).toInstant().toEpochMilli()

    /** Recomputes notification, widgets and the next alarm. */
    suspend fun refresh(ctx: Context) {
        val repo = FocusApp.instance.repo
        // Repeating tasks whose day has come are brought back first; the change re-triggers this refresh.
        if (repo.resetRepeating(LocalDateTime.now()) > 0) return
        val data = repo.awaitData()
        val now = LocalDateTime.now()
        val state = data.focusAt(now)
        Notifier.update(ctx, state, data)
        FocusWidgets.updateAll(ctx)
        schedule(ctx, data, state.mode, now)
    }

    private fun widgetsInstalled(ctx: Context): Boolean {
        val m = AppWidgetManager.getInstance(ctx)
        return listOf(
            FocusWidgetReceiver::class.java,
            FocusLargeWidgetReceiver::class.java,
        ).any { m.getAppWidgetIds(ComponentName(ctx, it)).isNotEmpty() }
    }

    private fun schedule(ctx: Context, data: AppData, mode: Mode, now: LocalDateTime) {
        val s = data.settings
        val instances = Schedule.instances(now, data::blocksFor)
        val candidates = ArrayList<LocalDateTime>()
        nextAlertAlarm(now, instances, s.cycle, s.userBreak)?.let { candidates += it }
        instances.forEach { i ->
            if (i.start > now) candidates += i.start
            if (i.end > now) candidates += i.end
        }
        BreakPlanner.windows(instances, s.cycle).forEach { w ->
            if (w.from > now) candidates += w.from
            if (w.until > now) candidates += w.until
        }
        s.userBreak?.until?.takeIf { it > now }?.let { candidates += it }
        data.tasks.filter { !it.done }.mapNotNull { it.deadline }.filter { it > now }.minOrNull()?.let { candidates += it }
        // a completed repeating task comes back at its reset time
        data.tasks.filter { it.done && it.parentId == null }
            .mapNotNull { t -> t.repeat?.let { r -> t.completedAt?.let(r::comesBackAfter) } }
            .filter { it > now }.minOrNull()?.let { candidates += it }
        // Keep the timer text and progress fresh while something is going on.
        val busy = instances.any { it.contains(now) || (it.start > now && it.start < now.plusHours(2)) } || mode == Mode.BREAK
        if (busy) {
            val step = if (widgetsInstalled(ctx)) 1L else 5L
            candidates += now.withSecond(0).withNano(0).plusMinutes(step)
        }
        candidates += now.plusMinutes(30)
        val at = candidates.filter { it > now }.minOrNull() ?: now.plusMinutes(30)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = tickIntent(ctx)
        val millis = at.millis().coerceAtLeast(System.currentTimeMillis() + 1000)
        try {
            if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    /** When the alarm for the next alert goes off: early enough for its vibration to build up to its moment. */
    internal fun nextAlertAlarm(
        now: LocalDateTime, instances: List<com.focustask.core.BlockInstance>, cycle: com.focustask.core.CycleSettings, userBreak: com.focustask.core.UserBreak?,
    ): LocalDateTime? = AlertPlanner.upcoming(now, instances, cycle, userBreak)
        .map { it.at.minusNanos(Vibrations.leadMs(it.kind) * 1_000_000) }.filter { it > now }.minOrNull()

    private fun tickIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, AlertReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Called by the alarm: refresh everything, then play whatever alert is due. */
    suspend fun onTick(ctx: Context) {
        val repo = FocusApp.instance.repo
        val data = repo.awaitData()
        val now = LocalDateTime.now()
        val s = data.settings

        val last = s.lastAlertAt.takeIf { it > 0 }?.fromStored() ?: now.minusMinutes(2)
        val windowStart = maxOf(last, now.minusSeconds(90))
        val instances = Schedule.instances(now, data::blocksFor)
        val due = AlertPlanner.upcoming(windowStart, instances, s.cycle, s.userBreak)
            .filter { it.at.minusNanos(Vibrations.leadMs(it.kind) * 1_000_000) <= now.plusSeconds(2) }
        refresh(ctx)
        if (due.isEmpty()) return
        repo.settingsStore.setLastAlertAt(maxOf(now, due.maxOf { it.at }).toStored())

        val pick = listOf(AlertKind.BLOCK_ENDING, AlertKind.BLOCK_STARTING, AlertKind.CYCLE_CHIME, AlertKind.PHASE_CUE)
            .firstNotNullOfOrNull { k -> due.firstOrNull { it.kind == k } } ?: return
        playAlert(ctx, data, pick.kind, pick.at)
    }

    /** A whole alert, the way it comes from the alarm, [TEST_DELAY_SEC] seconds from now: to check that alerts come through. */
    fun scheduleTest(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, AlertReceiver::class.java).setAction(ACTION_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + TEST_DELAY_SEC * 1000L
        try {
            if (canScheduleExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** The test alert: a block starting, so it has the growing vibration and then the block sound. */
    suspend fun onTest(ctx: Context) {
        val data = FocusApp.instance.repo.awaitData()
        val kind = AlertKind.BLOCK_STARTING
        playAlert(ctx, data, kind, LocalDateTime.now().plusNanos(Vibrations.leadMs(kind) * 1_000_000))
    }

    /**
     * Plays the alert for [kind], whose moment is [at]: the vibration starts now (it builds up to [at] for a block
     * starting or ending) and the sound plays on [at].
     */
    internal suspend fun playAlert(ctx: Context, data: AppData, kind: AlertKind, at: LocalDateTime = LocalDateTime.now()) {
        val s = data.settings
        val slot = SoundSlot.forKind(kind)
        val cfg = s.sounds.getValue(slot)
        if (!cfg.enabled) return
        when (outputFor(ctx, s.alertMode)) {
            Output.NONE -> Unit
            Output.VIBRATE -> vibrate(ctx, kind)
            Output.SOUND -> {
                // Sound mode plays the sound and vibrates: the vibration first, the sound right on the moment.
                vibrate(ctx, kind)
                val wait = java.time.Duration.between(LocalDateTime.now(), at).toMillis()
                if (wait > 0) delay(wait.coerceAtMost(Vibrations.leadMs(kind)))
                val done = CompletableDeferred<Unit>()
                val pb = SoundPlayer.play(ctx, cfg.source, s.maxSoundSec, s.fade) { done.complete(Unit) }
                withTimeoutOrNull((s.maxSoundSec + 3) * 1000L) { done.await() }
            }
        }
    }

    /** How many times the phone was asked to buzz (read by tests). */
    @Volatile internal var vibrations = 0

    fun vibrate(ctx: Context, kind: AlertKind) {
        vibrations++
        val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return
        val feel = Vibrations.feelFor(kind)
        val effect = if (v.hasAmplitudeControl()) {
            Vibrations.waveform(feel).let { w -> VibrationEffect.createWaveform(w.timings, w.amplitudes, -1) }
        } else {
            VibrationEffect.createWaveform(Vibrations.onOff(feel), -1)
        }
        // As an alarm: Android drops other vibrations from an app that is not on screen, which is when alerts happen.
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    }
}
