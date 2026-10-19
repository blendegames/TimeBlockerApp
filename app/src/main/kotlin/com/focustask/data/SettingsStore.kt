package com.focustask.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focustask.core.AlertKind
import com.focustask.core.CycleSettings
import com.focustask.core.UserBreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AlertMode { SOUND, VIBRATE, SILENT }

enum class SoundSlot(val kind: AlertKind) {
    CHIME(AlertKind.CYCLE_CHIME),
    CUE(AlertKind.PHASE_CUE),
    ENDING(AlertKind.BLOCK_ENDING),
}

/**
 * [source] is "builtin:<id>" or "file:<absolute path>" (a copy inside the app's storage).
 * [label] is what the user sees.
 */
data class SoundConfig(val source: String, val label: String, val enabled: Boolean = true)

data class Settings(
    val cycle: CycleSettings = CycleSettings(),
    val alertMode: AlertMode = AlertMode.SOUND,
    val sounds: Map<SoundSlot, SoundConfig> = defaultSounds(),
    val maxSoundSec: Int = 10,
    val fade: Boolean = true,
    val userBreak: UserBreak? = null,
    val lockWarningDismissed: Boolean = false,
    val lastAlertAt: Long = 0L,
    val seeded: Boolean = false,
)

fun defaultSounds(): Map<SoundSlot, SoundConfig> = mapOf(
    SoundSlot.CHIME to SoundConfig("builtin:bell", "Soft bell"),
    SoundSlot.CUE to SoundConfig("builtin:pop", "Soft pop"),
    SoundSlot.ENDING to SoundConfig("builtin:rise", "Rising tone"),
)

private val Context.dataStore by preferencesDataStore("focustask_settings")

class SettingsStore(private val context: Context) {

    private object K {
        val work = intPreferencesKey("work_min")
        val short = intPreferencesKey("short_min")
        val long = intPreferencesKey("long_min")
        val countdown = intPreferencesKey("countdown_min")
        val mode = stringPreferencesKey("alert_mode")
        val maxSound = intPreferencesKey("max_sound_sec")
        val fade = booleanPreferencesKey("fade")
        val ubFrom = longPreferencesKey("user_break_from")
        val ubUntil = longPreferencesKey("user_break_until")
        val lockDismissed = booleanPreferencesKey("lock_warning_dismissed")
        val lastAlert = longPreferencesKey("last_alert_at")
        val seeded = booleanPreferencesKey("seeded")
        fun src(s: SoundSlot) = stringPreferencesKey("snd_${s.name}_src")
        fun label(s: SoundSlot) = stringPreferencesKey("snd_${s.name}_label")
        fun on(s: SoundSlot) = booleanPreferencesKey("snd_${s.name}_on")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): Settings {
        val d = CycleSettings()
        val defaults = defaultSounds()
        val ubF = this[K.ubFrom]
        val ubU = this[K.ubUntil]
        return Settings(
            cycle = CycleSettings(
                workMin = this[K.work] ?: d.workMin,
                shortBreakMin = this[K.short] ?: d.shortBreakMin,
                longBreakMin = this[K.long] ?: d.longBreakMin,
                countdownMin = this[K.countdown] ?: d.countdownMin,
            ),
            alertMode = this[K.mode]?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() } ?: AlertMode.SOUND,
            sounds = SoundSlot.values().associateWith { s ->
                val def = defaults.getValue(s)
                SoundConfig(
                    source = this[K.src(s)] ?: def.source,
                    label = this[K.label(s)] ?: def.label,
                    enabled = this[K.on(s)] ?: true,
                )
            },
            maxSoundSec = this[K.maxSound] ?: 10,
            fade = this[K.fade] ?: true,
            userBreak = if (ubF != null && ubU != null) UserBreak(ubF.fromStored(), ubU.fromStored()) else null,
            lockWarningDismissed = this[K.lockDismissed] ?: false,
            lastAlertAt = this[K.lastAlert] ?: 0L,
            seeded = this[K.seeded] ?: false,
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setCycle(work: Int? = null, short: Int? = null, long: Int? = null, countdown: Int? = null) = edit {
        work?.let { v -> it[K.work] = v.coerceIn(1, 180) }
        short?.let { v -> it[K.short] = v.coerceIn(0, 60) }
        long?.let { v -> it[K.long] = v.coerceIn(0, 120) }
        countdown?.let { v -> it[K.countdown] = v.coerceIn(0, 60) }
    }

    suspend fun resetCycle() = edit {
        it[K.work] = 25; it[K.short] = 5; it[K.long] = 15
    }

    suspend fun setMode(m: AlertMode) = edit { it[K.mode] = m.name }
    suspend fun setMaxSound(sec: Int) = edit { it[K.maxSound] = sec.coerceIn(1, 60) }
    suspend fun setFade(on: Boolean) = edit { it[K.fade] = on }
    suspend fun setSound(slot: SoundSlot, source: String, label: String) = edit {
        it[K.src(slot)] = source; it[K.label(slot)] = label
    }
    suspend fun setSoundEnabled(slot: SoundSlot, on: Boolean) = edit { it[K.on(slot)] = on }
    suspend fun setUserBreak(b: UserBreak?) = edit {
        if (b == null) { it.remove(K.ubFrom); it.remove(K.ubUntil) }
        else { it[K.ubFrom] = b.from.toStored(); it[K.ubUntil] = b.until.toStored() }
    }
    suspend fun setLockWarningDismissed(v: Boolean) = edit { it[K.lockDismissed] = v }
    suspend fun setLastAlertAt(v: Long) = edit { it[K.lastAlert] = v }
    suspend fun setSeeded() = edit { it[K.seeded] = true }
}
