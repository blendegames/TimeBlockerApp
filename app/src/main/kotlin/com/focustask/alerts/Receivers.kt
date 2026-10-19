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
            try { Alerts.onTick(app) } finally { pending.finish() }
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

/** The "Done" button of the notification and widgets. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        val app = context.applicationContext
        FocusApp.instance.scope.launch {
            try {
                FocusApp.instance.repo.complete(id)
                delay(700) // let the database flow publish the change
                Alerts.refresh(app)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val ACTION_DONE = "com.focustask.DONE"
    }
}
