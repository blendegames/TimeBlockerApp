package com.focustask.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focustask.Fmt
import com.focustask.MainActivity
import com.focustask.R
import com.focustask.core.BarKind
import com.focustask.core.FocusState
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.data.AppData
import com.focustask.data.toEpochMillisLocal

/** The ongoing notification: one task, a countdown, Done and Open. */
object Notifier {
    const val ID = 1

    fun update(ctx: Context, data: AppData, state: FocusState) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!Alerts.notificationsAllowed(ctx) ||
            (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        ) {
            nm.cancel(ID)
            return
        }
        val now = state.now
        val bar = state.bar
        val open = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(ctx, Alerts.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2F5D50.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)

        val target = (bar.phaseEndsAt ?: bar.endsAt)
        if (target != null && bar.kind != BarKind.IDLE) {
            b.setShowWhen(true).setWhen(target.toEpochMillisLocal())
                .setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            b.setShowWhen(false)
        }

        when (state.mode) {
            Mode.FOCUS -> {
                val c = state.carousel!!
                val cur = c.current
                b.setSubText(
                    if (cur.reason == Reason.OVERDUE) "Overdue" else state.active?.block?.name ?: cur.list.title
                )
                b.setContentTitle(cur.task.title)
                val due = cur.deadline?.let {
                    if (it < now) Fmt.late(it, now) else "Due " + Fmt.deadline(it, now)
                }
                val cycle = if (bar.cycleCount > 0) "Cycle ${bar.cycleNumber} of ${bar.cycleCount}" else null
                b.setContentText(listOfNotNull(due, cycle).joinToString(" · "))
                b.setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        listOfNotNull(due, Fmt.stripText(bar), cycle).joinToString("\n")
                    )
                )
                if (bar.blockLeftMin != null && state.active != null) {
                    b.setProgress(1000, (bar.sun * 1000).toInt(), false)
                }
                val done = PendingIntent.getBroadcast(
                    ctx, 11,
                    Intent(ctx, NotificationActionReceiver::class.java)
                        .setAction(NotificationActionReceiver.ACTION_DONE)
                        .putExtra(NotificationActionReceiver.EXTRA_TASK_ID, cur.task.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                b.addAction(0, "Done", done)
                b.addAction(0, "Open", open)
            }
            Mode.BREAK -> {
                b.setContentTitle("Long break")
                b.setContentText(
                    state.longBreak?.until?.let { "Back at ${Fmt.time(it)}" + (bar.nextName?.let { n -> " · $n" } ?: "") }
                        ?: "Take a real break"
                )
                b.setStyle(NotificationCompat.BigTextStyle().bigText(Fmt.stripText(bar)))
                b.setColor(0xFF3B4F9A.toInt())
            }
            Mode.ALL_DONE -> {
                b.setContentTitle("All done")
                b.setContentText(
                    state.next?.let { "${it.block.name} starts ${Fmt.time(it.start)}" } ?: "Nothing scheduled"
                )
                if (bar.kind == BarKind.WAIT_NEXT) b.setStyle(NotificationCompat.BigTextStyle().bigText(Fmt.stripText(bar)))
                b.addAction(0, "Open", open)
            }
        }
        runCatching { nm.notify(ID, b.build()) }
    }
}
