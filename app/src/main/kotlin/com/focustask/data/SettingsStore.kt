package com.focustask.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focustask.core.AlertKind
import com.focustask.core.CycleSettings
import com.focustask.core.IdleView
import com.focustask.core.UserBreak
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AlertMode { SOUND, VIBRATE, SILENT }

enum class SoundSlot {
    /** Back to work. */
    CHIME,
    /** Time for a break. */
    CUE,
    /** A block starts or ends soon. */
    ENDING;

    companion object {
        fun forKind(kind: AlertKind): SoundSlot = when (kind) {
            AlertKind.CYCLE_CHIME -> CHIME
            AlertKind.PHASE_CUE -> CUE
            AlertKind.BLOCK_STARTING, AlertKind.BLOCK_ENDING -> ENDING
        }
    }
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
    /** The screen stays on while the app is open in the foreground. */
    val keepScreenOn: Boolean = false,
    /** Android 12+: the backgrounds and accents take their colors from the wallpaper. */
    val wallpaperColors: Boolean = false,
    /** While the screen is kept on: fade it down when nobody touches it. */
    val dimWhenIdle: Boolean = true,
    /** Seconds after the timer view fills the screen (after the last touch when that view is off) before dimming starts. */
    val dimAfterSec: Int = IdleView.DEFAULT_DIM_SEC,
    /** When nobody touches the app: fade to a black screen with the focus timer. */
    val timerViewWhenIdle: Boolean = true,
    /** Seconds without a touch before that fade starts. */
    val timerViewAfterSec: Int = IdleView.DEFAULT_TIMER_SEC,
    /** How dark it gets, in percent of the phone's own brightness. */
    val dimPercent: Int = 10,
    val userBreak: UserBreak? = null,
    /** Tasks skipped on [skippedDay] (an ISO date), the one skipped last at the end. Only that day counts. */
    val skippedIds: List<Long> = emptyList(),
    val skippedDay: String = "",
    /** When each of [skippedIds] was skipped. */
    val skippedTimes: Map<Long, LocalDateTime> = emptyMap(),
    val lockWarningDismissed: Boolean = false,
    val lastAlertAt: Long = 0L,
    val seeded: Boolean = false,
) {
    /** The tasks put aside today. */
    fun skippedOn(day: LocalDate): List<Long> = if (skippedDay == day.toString()) skippedIds else emptyList()
    fun skipTimesOn(day: LocalDate): Map<Long, LocalDateTime> = if (skippedDay == day.toString()) skippedTimes else emptyMap()
}

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
        val longAfter = intPreferencesKey("long_after_min")
        val mode = stringPreferencesKey("alert_mode")
        val maxSound = intPreferencesKey("max_sound_sec")
        val fade = booleanPreferencesKey("fade")
        val keepOn = booleanPreferencesKey("keep_screen_on")
        val wallpaper = booleanPreferencesKey("wallpaper_colors")
        val dimIdle = booleanPreferencesKey("dim_when_idle")
        val dimPct = intPreferencesKey("dim_percent")
        val dimAfter = intPreferencesKey("dim_after_sec")
        val timerView = booleanPreferencesKey("timer_view_idle")
        val timerViewAfter = intPreferencesKey("timer_view_after_sec")
        val ubFrom = longPreferencesKey("user_break_from")
        val ubUntil = longPreferencesKey("user_break_until")
        val skipDay = stringPreferencesKey("skipped_day")
        val skipIds = stringPreferencesKey("skipped_ids")
        val skipTimes = stringPreferencesKey("skipped_times")
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
                longBreakAfterMin = (this[K.longAfter] ?: d.longBreakAfterMin).coerceAtLeast(15),
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
            keepScreenOn = this[K.keepOn] ?: false,
            wallpaperColors = this[K.wallpaper] ?: false,
            dimWhenIdle = this[K.dimIdle] ?: true,
            dimPercent = (this[K.dimPct] ?: 10).coerceIn(5, 80),
            dimAfterSec = (this[K.dimAfter] ?: IdleView.DEFAULT_DIM_SEC).coerceIn(IdleView.MIN_DIM_SEC, IdleView.MAX_DIM_SEC),
            timerViewWhenIdle = this[K.timerView] ?: true,
            timerViewAfterSec = (this[K.timerViewAfter] ?: IdleView.DEFAULT_TIMER_SEC).coerceIn(IdleView.MIN_TIMER_SEC, IdleView.MAX_TIMER_SEC),
            userBreak = if (ubF != null && ubU != null) UserBreak(ubF.fromStored(), ubU.fromStored()) else null,
            skippedIds = this[K.skipIds].orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            skippedDay = this[K.skipDay].orEmpty(),
            skippedTimes = this[K.skipTimes].orEmpty().split(',').mapNotNull { e ->
                val (id, at) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val t = runCatching { LocalDateTime.parse(at) }.getOrNull() ?: return@mapNotNull null
                id.toLongOrNull()?.let { it to t }
            }.toMap(),
            lockWarningDismissed = this[K.lockDismissed] ?: false,
            lastAlertAt = this[K.lastAlert] ?: 0L,
            seeded = this[K.seeded] ?: false,
        )
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setCycle(work: Int? = null, short: Int? = null, long: Int? = null, countdown: Int? = null, longAfter: Int? = null) = edit {
        work?.let { v -> it[K.work] = v.coerceIn(1, 180) }
        short?.let { v -> it[K.short] = v.coerceIn(0, 60) }
        long?.let { v -> it[K.long] = v.coerceIn(0, 120) }
        countdown?.let { v -> it[K.countdown] = v.coerceIn(0, 60) }
        longAfter?.let { v -> it[K.longAfter] = v.coerceIn(15, 1440) }
    }

    suspend fun resetCycle() = edit {
        it[K.work] = 25; it[K.short] = 5; it[K.long] = 15; it[K.longAfter] = 120
    }

    suspend fun setMode(m: AlertMode) = edit { it[K.mode] = m.name }
    suspend fun setMaxSound(sec: Int) = edit { it[K.maxSound] = sec.coerceIn(1, 60) }
    suspend fun setFade(on: Boolean) = edit { it[K.fade] = on }
    suspend fun setKeepScreenOn(on: Boolean) = edit { it[K.keepOn] = on }
    suspend fun setWallpaperColors(on: Boolean) = edit { it[K.wallpaper] = on }
    suspend fun setDimWhenIdle(on: Boolean) = edit { it[K.dimIdle] = on }
    suspend fun setDimPercent(pct: Int) = edit { it[K.dimPct] = pct.coerceIn(5, 80) }
    suspend fun setDimAfterSec(sec: Int) = edit { it[K.dimAfter] = sec.coerceIn(IdleView.MIN_DIM_SEC, IdleView.MAX_DIM_SEC) }
    suspend fun setTimerViewWhenIdle(on: Boolean) = edit { it[K.timerView] = on }
    suspend fun setTimerViewAfterSec(sec: Int) = edit { it[K.timerViewAfter] = sec.coerceIn(IdleView.MIN_TIMER_SEC, IdleView.MAX_TIMER_SEC) }
    suspend fun setSound(slot: SoundSlot, source: String, label: String) = edit {
        it[K.src(slot)] = source; it[K.label(slot)] = label
    }
    suspend fun setSoundEnabled(slot: SoundSlot, on: Boolean) = edit { it[K.on(slot)] = on }
    suspend fun setUserBreak(b: UserBreak?) = edit {
        if (b == null) { it.remove(K.ubFrom); it.remove(K.ubUntil) }
        else { it[K.ubFrom] = b.from.toStored(); it[K.ubUntil] = b.until.toStored() }
    }
    suspend fun setSkipped(day: LocalDate, ids: List<Long>, times: Map<Long, LocalDateTime> = emptyMap()) = edit {
        it[K.skipDay] = day.toString(); it[K.skipIds] = ids.joinToString(",")
        it[K.skipTimes] = ids.mapNotNull { id -> times[id]?.let { t -> "$id=${t.withNano(0)}" } }.joinToString(",")
    }
    suspend fun setLockWarningDismissed(v: Boolean) = edit { it[K.lockDismissed] = v }
    suspend fun setLastAlertAt(v: Long) = edit { it[K.lastAlert] = v }
    suspend fun setSeeded() = edit { it[K.seeded] = true }

    /** Puts every setting back from a copy. Alerts start from now, so old ones do not ring again. */
    suspend fun replaceAll(s: Settings) = edit {
        it[K.work] = s.cycle.workMin; it[K.short] = s.cycle.shortBreakMin
        it[K.long] = s.cycle.longBreakMin; it[K.countdown] = s.cycle.countdownMin
        it[K.longAfter] = s.cycle.longBreakAfterMin
        it[K.mode] = s.alertMode.name; it[K.maxSound] = s.maxSoundSec; it[K.fade] = s.fade
        it[K.keepOn] = s.keepScreenOn; it[K.wallpaper] = s.wallpaperColors; it[K.dimIdle] = s.dimWhenIdle; it[K.dimPct] = s.dimPercent.coerceIn(5, 80)
        it[K.dimAfter] = s.dimAfterSec.coerceIn(IdleView.MIN_DIM_SEC, IdleView.MAX_DIM_SEC); it[K.timerView] = s.timerViewWhenIdle
        it[K.timerViewAfter] = s.timerViewAfterSec.coerceIn(IdleView.MIN_TIMER_SEC, IdleView.MAX_TIMER_SEC)
        SoundSlot.values().forEach { slot ->
            val c = s.sounds[slot] ?: defaultSounds().getValue(slot)
            it[K.src(slot)] = c.source; it[K.label(slot)] = c.label; it[K.on(slot)] = c.enabled
        }
        val b = s.userBreak
        if (b == null) { it.remove(K.ubFrom); it.remove(K.ubUntil) }
        else { it[K.ubFrom] = b.from.toStored(); it[K.ubUntil] = b.until.toStored() }
        it[K.lockDismissed] = s.lockWarningDismissed
        it[K.lastAlert] = LocalDateTime.now().toStored()
        it[K.seeded] = true
    }
}
