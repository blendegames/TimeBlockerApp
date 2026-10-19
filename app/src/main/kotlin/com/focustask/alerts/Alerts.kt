package com.focustask.alerts

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import com.focustask.FocusApp
import com.focustask.core.AlertKind
import com.focustask.core.AlertPlanner
import com.focustask.core.FocusEngine
import com.focustask.core.Mode
import com.focustask.core.Schedule
import com.focustask.data.AlertMode
import com.focustask.data.AppData
import com.focustask.data.SoundSlot
import com.focustask.data.fromStored
import com.focustask.data.toStored
import com.focustask.widget.FocusWidgets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

enum class Output { SOUND, VIBRATE, NONE }

/** Plans alarms, plays the alerts and keeps the notification and widgets fresh. */
object Alerts {
    const val CHANNEL = "focus_timer"
    const val ACTION_TICK = "com.focustask.TICK"

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
        val data = FocusApp.instance.repo.awaitData()
        val now = LocalDateTime.now()
        val state = FocusEngine.build(now, data::blocksFor, data.lists, data.tasks, data.settings.cycle, data.settings.userBreak)
        Notifier.update(ctx, data, state)
        FocusWidgets.updateAll(ctx)
        schedule(ctx, data, state.mode, now)
    }

    private fun widgetsInstalled(ctx: Context): Boolean {
        val m = AppWidgetManager.getInstance(ctx)
        return listOf(
            com.focustask.widget.FocusWidgetReceiver::class.java,
            com.focustask.widget.FocusLargeWidgetReceiver::class.java,
        ).any { m.getAppWidgetIds(ComponentName(ctx, it)).isNotEmpty() }
    }

    private fun schedule(ctx: Context, data: AppData, mode: Mode, now: LocalDateTime) {
        val s = data.settings
        val instances = Schedule.instances(now, data::blocksFor)
        val candidates = ArrayList<LocalDateTime>()
        AlertPlanner.upcoming(now, instances, s.cycle, s.userBreak).firstOrNull()?.let { candidates += it.at }
        instances.forEach { i ->
            if (i.start > now) candidates += i.start
            if (i.end > now) candidates += i.end
            val b = i.end.plusMinutes(s.cycle.longBreakMin.toLong())
            if (b > now) candidates += b
        }
        s.userBreak?.until?.takeIf { it > now }?.let { candidates += it }
        data.tasks.filter { !it.done }.mapNotNull { it.deadline }.filter { it > now }.minOrNull()?.let { candidates += it }
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
            .filter { it.at <= now.plusSeconds(2) }
        refresh(ctx)
        if (due.isEmpty()) return
        repo.settingsStore.setLastAlertAt(maxOf(now, due.maxOf { it.at }).toStored())

        val pick = listOf(AlertKind.BLOCK_ENDING, AlertKind.CYCLE_CHIME, AlertKind.PHASE_CUE)
            .firstOrNull { k -> due.any { it.kind == k } } ?: return
        playAlert(ctx, data, pick)
    }

    private suspend fun playAlert(ctx: Context, data: AppData, kind: AlertKind) {
        val s = data.settings
        val slot = SoundSlot.values().first { it.kind == kind }
        val cfg = s.sounds.getValue(slot)
        if (!cfg.enabled) return
        when (outputFor(ctx, s.alertMode)) {
            Output.NONE -> Unit
            Output.VIBRATE -> vibrate(ctx, kind)
            Output.SOUND -> {
                val done = CompletableDeferred<Unit>()
                val pb = SoundPlayer.play(ctx, cfg.source, s.maxSoundSec, s.fade) { done.complete(Unit) }
                if (pb == null) vibrate(ctx, kind) // missing file: at least buzz
                withTimeoutOrNull((s.maxSoundSec + 3) * 1000L) { done.await() }
            }
        }
    }

    fun vibrate(ctx: Context, kind: AlertKind) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return
        val pattern = when (kind) {
            AlertKind.CYCLE_CHIME -> longArrayOf(0, 180)
            AlertKind.PHASE_CUE -> longArrayOf(0, 90, 90, 90)
            AlertKind.BLOCK_ENDING -> longArrayOf(0, 120, 90, 120, 90, 120)
        }
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
