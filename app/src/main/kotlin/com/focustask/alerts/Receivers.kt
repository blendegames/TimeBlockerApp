package com.focustask.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focustask.FocusApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        FocusApp.instance.scope.launch {
            try {
                if (intent.action == Alerts.ACTION_TEST) Alerts.onTest(app) else Alerts.onTick(app)
            } finally { pending.finish() }
        }
    }
}

/** Boot, time changes, app updates: just re-plan everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        FocusApp.instance.scope.launch {
            try { Alerts.refresh(app) } finally { pending.finish() }
        }
    }
}

/** The buttons of the notification and widgets: Done, Skip and Break until the next block. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (intent.action != ACTION_BREAK && id < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        FocusApp.instance.scope.launch {
            try {
                val repo = FocusApp.instance.repo
                when (intent.action) {
                    ACTION_SKIP -> repo.skipTask(id)
                    ACTION_BREAK -> {
                        // the same break as the button on Today: until the next block starts
                        val now = java.time.LocalDateTime.now()
                        repo.awaitData().focusAt(now).next?.let { repo.startUserBreak(now, it.start) }
                    }
                    else -> repo.complete(id)
                }
                delay(700) // let the database flow publish the change
                Alerts.refresh(app)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val ACTION_DONE = "com.focustask.DONE"
        const val ACTION_SKIP = "com.focustask.SKIP"
        const val ACTION_BREAK = "com.focustask.BREAK"
    }
}
