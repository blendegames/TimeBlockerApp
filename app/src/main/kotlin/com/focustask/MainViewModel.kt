package com.focustask

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focustask.alerts.Alerts
import com.focustask.alerts.SoundPlayer
import com.focustask.core.FocusState
import com.focustask.core.Task
import com.focustask.data.AlertMode
import com.focustask.data.AppData
import com.focustask.data.DataFormatException
import com.focustask.data.DataMirror
import com.focustask.data.LoadedCopy
import com.focustask.data.MirrorStatus
import com.focustask.data.Repository
import com.focustask.data.SettingsStore
import com.focustask.data.SoundSlot
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(val data: AppData, val focus: FocusState, val now: LocalDateTime)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repository = (app as FocusApp).repo

    private val ticker = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }

    val ui: StateFlow<UiState?> = combine(repo.data.filterNotNull(), ticker) { d, now ->
        UiState(
            d,
            d.focusAt(now),
            now,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)

    private val mirror get() = (getApplication<Application>() as FocusApp).mirror
    val copyStatus: StateFlow<MirrorStatus> get() = mirror.status

    /** Keep the JSON copy in the folder [uri] as well, so it survives uninstalling the app. */
    fun keepCopyIn(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch { mirror.useFolder(uri) }
    }

    fun stopKeepingCopy() = mirror.stopUsingFolder()

    /** Deletes all the data (see [DataMirror.deleteEverything]). */
    fun deleteAllData(done: () -> Unit) {
        viewModelScope.launch { mirror.deleteEverything(); done() }
    }

    /** Reads and checks the copy in [uri]; [done] gets it, or a message for the person. */
    fun checkCopyIn(uri: Uri, done: (Result<LoadedCopy>) -> Unit) {
        viewModelScope.launch {
            done(runCatching { mirror.loadFromTree(uri) }.recoverCatching { e ->
                throw if (e is DataFormatException) e
                else DataFormatException("That folder could not be read.")
            })
        }
    }

    fun restoreCopy(copy: LoadedCopy, done: () -> Unit) {
        viewModelScope.launch { mirror.restore(copy); done() }
    }

    fun run(block: suspend Repository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    /**
     * Ticks in the Lists tab wait a second before they count: the box shows the new state at once, and a second
     * tap within that second takes it back without anything having changed. Then the task moves (to "Completed",
     * or back up from there).
     */
    val pendingTicks = mutableStateMapOf<Long, Job>()

    /** Whether [t] looks done right now: its saved state, flipped while a tick on it is waiting. */
    fun shownDone(t: Task): Boolean = t.done != (t.id in pendingTicks)

    fun tickSoon(t: Task) {
        pendingTicks.remove(t.id)?.let { it.cancel(); return }
        val makeDone = !t.done
        pendingTicks[t.id] = viewModelScope.launch {
            delay(TICK_DELAY_MS)
            if (makeDone) repo.complete(t.id) else repo.reopen(t.id)
            // keep showing the new state until the saved data has caught up, so the box doesn't flicker back
            val end = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < end && repo.data.value?.tasks?.firstOrNull { it.id == t.id }?.done != makeDone) delay(30)
            pendingTicks.remove(t.id)
        }
    }

    companion object {
        const val TICK_DELAY_MS = 1000L
    }

    fun settings(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { repo.settingsStore.block() }
    }

    /** The round button next to the timer: Sound <-> Vibrate. */
    fun toggleAlertMode(current: AlertMode) {
        settings { setMode(if (current == AlertMode.SOUND) AlertMode.VIBRATE else AlertMode.SOUND) }
    }

    fun refreshAlerts() {
        viewModelScope.launch(Dispatchers.Default) { Alerts.refresh(getApplication()) }
    }

    fun preview(source: String, maxSec: Int, fade: Boolean) {
        SoundPlayer.play(getApplication(), source, maxSec, fade)
    }

    /** Copies the chosen audio file into the app so it keeps working if the original moves. */
    fun importSound(slot: SoundSlot, uri: Uri, previousSource: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var name = "sound"
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
                    }
                    val ext = name.substringAfterLast('.', "audio").take(5).filter { it.isLetterOrDigit() }.ifEmpty { "audio" }
                    val dir = File(ctx.filesDir, "sounds").apply { mkdirs() }
                    val out = File(dir, "${slot.name.lowercase()}_${System.currentTimeMillis()}.$ext")
                    ctx.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
                    val source = "file:${out.absolutePath}"
                    if (SoundPlayer.durationMs(ctx, source) == null) {
                        out.delete()
                        error("not audio")
                    }
                    if (previousSource.startsWith("file:")) File(previousSource.removePrefix("file:")).delete()
                    source to name
                }
            }
            result.onSuccess { (source, name) -> repo.settingsStore.setSound(slot, source, name) }
                .onFailure { Toast.makeText(ctx, "That file can't be played. Try an MP3, OGG, WAV, M4A or FLAC file.", Toast.LENGTH_LONG).show() }
        }
    }

    fun chooseBuiltIn(slot: SoundSlot, id: String, label: String, previousSource: String) {
        if (previousSource.startsWith("file:")) File(previousSource.removePrefix("file:")).delete()
        settings { setSound(slot, "builtin:$id", label) }
    }
}
