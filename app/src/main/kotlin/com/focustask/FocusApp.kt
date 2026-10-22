package com.focustask

import android.app.Application
import android.util.Log
import com.focustask.alerts.Alerts
import com.focustask.data.DataMirror
import com.focustask.data.Repository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

open class FocusApp : Application() {
    /** The real app starts empty. Tests override this to put example lists in. */
    protected open suspend fun seedExampleData(repo: Repository) {}

    /** Background work never crashes the app: failures are logged and the next change retries. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> runCatching { Log.w("FocusTask", "background failure", e) } }
    )
    lateinit var repo: Repository
        private set
    /** Keeps the JSON copy of everything up to date, and brings it back when needed. */
    lateinit var mirror: DataMirror
        private set

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this, scope)
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0"
        mirror = DataMirror(this, repo, scope, version)
        Alerts.createChannel(this)
        scope.launch {
            // If the database was lost but the app's own JSON copy is still there, bring the data back instead of starting empty.
            runCatching { mirror.restoreIfEmpty() }
            seedExampleData(repo)
            repo.purgeOldChanges()
            mirror.start()
            // Repeating tasks come back at midnight: check now, then every minute while the app is alive
            // (the alarm in Alerts covers the time when it is not).
            while (true) {
                runCatching { repo.resetRepeating() }
                delay(60_000)
            }
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
