package com.focustask

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focustask.alerts.Alerts
import com.focustask.alerts.SoundPlayer
import com.focustask.core.FocusEngine
import com.focustask.core.FocusState
import com.focustask.data.AlertMode
import com.focustask.data.AppData
import com.focustask.data.Repository
import com.focustask.data.SoundSlot
import kotlinx.coroutines.Dispatchers
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
import java.io.File
import java.time.LocalDateTime

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
            FocusEngine.build(now, d::blocksFor, d.lists, d.tasks, d.settings.cycle, d.settings.userBreak),
            now,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(3000), null)

    fun run(block: suspend Repository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    fun settings(block: suspend com.focustask.data.SettingsStore.() -> Unit) {
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
