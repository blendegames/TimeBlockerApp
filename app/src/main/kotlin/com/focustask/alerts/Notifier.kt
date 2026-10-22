package com.focustask.alerts

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focustask.Fmt
import com.focustask.Words
import com.focustask.MainActivity
import com.focustask.R
import com.focustask.core.BarKind
import com.focustask.core.FocusState
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.data.toEpochMillisLocal
import com.focustask.ui.components.barTexts
import com.focustask.core.CycleSettings
import com.focustask.data.AppData
import java.time.Duration
import java.time.LocalDateTime

/**
 * The ongoing notification: the task at hand, the same countdown as the timer bar, Done and Skip.
 * On Android 16 and later it is a Live Update: a chip with the countdown in the status bar, and a progress bar of the
 * block split into its work and break parts.
 */
object Notifier {
    const val ID = 1
    private const val WORK_COLOR = 0xFF4F9D86.toInt()
    private const val BREAK_COLOR = 0xFF7A8FD9.toInt()

    fun update(ctx: Context, state: FocusState, data: AppData) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!Alerts.notificationsAllowed(ctx) ||
            (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) {
            nm.cancel(ID)
            return
        }
        runCatching { nm.notify(ID, build(ctx, state, data)) }
    }

    fun build(ctx: Context, state: FocusState, data: AppData): Notification {
        val cycle = data.settings.cycle
        val now = state.now
        val bar = state.bar
        val texts = barTexts(state)
        val open = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, Alerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            // the app's green; with "Match my wallpaper" on, Android's own theme color instead
            .apply { if (!data.settings.wallpaperColors || Build.VERSION.SDK_INT < 31) setColor(0xFF2F5D50.toInt()) }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)

        // the countdown: the same number as the big one of the timer bar (and the same mm:ss), in the status bar chip too
        val target = (bar.phaseEndsAt ?: bar.endsAt)
        if (target != null && bar.kind != BarKind.IDLE) {
            b.setShowWhen(true).setWhen(target.toEpochMillisLocal())
                .setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }
        // the progress bar of the Live Update; null when there is nothing to track
        var live: LiveProgress? = null

        when (state.mode) {
            Mode.FOCUS -> {
                val c = state.carousel!!
                val cur = c.current
                b.setSubText(
                    if (cur.reason == Reason.OVERDUE) "Overdue" else state.active?.block?.name ?: cur.list.title
                )
                b.setContentTitle(cur.task.title)
                // the list's color, as on its card on Today
                val listColor = data.colorOfListAlways(cur.task.listId).toInt()
                b.setColor(listColor)
                val due = cur.deadline?.let { Fmt.dueLong(it, now) }
                // the same words as the timer bar: "Focus · Cycle 2 of 4 · Break at 14:25"
                val timer = if (state.active != null && bar.cycleCount > 0) {
                    listOf(texts.labelSmall.lowercase().replaceFirstChar { it.uppercase() }, texts.title, texts.sub).joinToString(" · ")
                } else null
                b.setContentText(listOfNotNull(timer, due).joinToString(" · "))
                b.setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(timer, due).joinToString("\n")))
                val act = state.active
                if (act != null && bar.blockLeftMin != null) {
                    b.setProgress(1000, (bar.sun * 1000).toInt(), false)
                    live = LiveProgress.ofBlock(act.start, act.end, now, cycle, workColor = listColor)
                }
                b.addAction(0, "Done", action(ctx, 11, NotificationActionReceiver.ACTION_DONE, cur.task.id))
                if (c.next != null) b.addAction(0, "Skip", action(ctx, 12, NotificationActionReceiver.ACTION_SKIP, cur.task.id))
            }
            Mode.BREAK -> {
                val lb = state.longBreak
                b.setContentTitle(Words.LONG_BREAK)
                b.setContentText(lb?.until?.let { Words.backAt(it, bar.nextName) } ?: "")
                if (!data.settings.wallpaperColors || Build.VERSION.SDK_INT < 31) b.setColor(0xFF3B4F9A.toInt())
                if (lb != null) {
                    val total = Duration.between(lb.from, lb.until).seconds.coerceAtLeast(1)
                    b.setProgress(1000, (Duration.between(lb.from, now).seconds * 1000 / total).toInt().coerceIn(0, 1000), false)
                    live = LiveProgress(
                        listOf(total.toInt() to BREAK_COLOR),
                        Duration.between(lb.from, now).seconds.toInt().coerceIn(0, total.toInt()),
                    )
                }
            }
            Mode.ALL_DONE -> {
                val next = state.next
                // the same words as Today: all done in a block, or waiting between blocks
                b.setContentTitle(if (state.active != null) Words.ALL_DONE else Words.WAITING)
                b.setContentText(next?.let { Words.starts(it.block.name, it.start, now) } ?: Words.NOTHING_PLANNED)
                // the same button as on Today; a break of more than an hour is only started from the app, which asks first
                if (next != null && state.active != null && Duration.between(now, next.start).toMinutes() in 1..60) {
                    b.addAction(0, "${Words.LONG_BREAK} until ${Fmt.time(next.start)}", action(ctx, 13, NotificationActionReceiver.ACTION_BREAK, 0))
                }
            }
        }
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 36 && live != null) return liveUpdate(ctx, n, live)
        return n
    }

    private fun action(ctx: Context, code: Int, action: String, taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, code,
        Intent(ctx, NotificationActionReceiver::class.java).setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The parts of the progress bar (length in seconds and color) and how far along it is. */
    data class LiveProgress(val parts: List<Pair<Int, Int>>, val at: Int) {
        companion object {
            /** A running block: a part for each work time and each short break, as the cycles go. */
            fun ofBlock(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime, cycle: CycleSettings, workColor: Int = WORK_COLOR): LiveProgress {
                val total = Duration.between(start, end).seconds.toInt().coerceAtLeast(1)
                val work = cycle.workMin * 60
                val brk = cycle.shortBreakMin * 60
                val parts = ArrayList<Pair<Int, Int>>()
                var t = 0
                while (t < total) {
                    val w = minOf(work, total - t); parts += w to workColor; t += w
                    if (t >= total || brk == 0) continue
                    val r = minOf(brk, total - t); parts += r to BREAK_COLOR; t += r
                }
                return LiveProgress(parts, Duration.between(start, now).seconds.toInt().coerceIn(0, total))
            }
        }
    }

    @RequiresApi(36)
    private fun liveUpdate(ctx: Context, n: Notification, live: LiveProgress): Notification {
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressSegments(live.parts.map { (len, color) -> Notification.ProgressStyle.Segment(len.coerceAtLeast(1)).setColor(color) })
            .setProgress(live.at)
        val nb = Notification.Builder.recoverBuilder(ctx, n).setStyle(style)
        // ask for it to be shown as a Live Update (chip in the status bar, at the top of the shade and on the lock screen)
        nb.extras.putBoolean("android.requestPromotedOngoing", true)
        return nb.build()
    }
}
