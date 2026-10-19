package com.focustask

import android.app.Application
import com.focustask.alerts.Alerts
import com.focustask.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class FocusApp : Application() {
    /** Background work never crashes the app: failures are logged and the next change retries. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> runCatching { android.util.Log.w("FocusTask", "background failure", e) } }
    )
    lateinit var repo: Repository
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this, scope)
        Alerts.createChannel(this)
        scope.launch {
            repo.seedIfNeeded()
            repo.purgeOldChanges()
        }
        // Whenever tasks, blocks or settings change, re-plan alarms, notification and widgets.
        scope.launch {
            repo.data.filterNotNull().debounce(400).collect { runCatching { Alerts.refresh(this@FocusApp) } }
        }
    }

    companion object {
        lateinit var instance: FocusApp
            private set
    }
}
