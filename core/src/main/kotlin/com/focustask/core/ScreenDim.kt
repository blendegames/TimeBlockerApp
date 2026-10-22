package com.focustask.core

/**
 * How dark the screen gets while the app is open and nobody touches it.
 *
 * After [IDLE_MS] (or the given start) without a touch the screen slowly fades from the phone's own brightness down to [FLOOR]
 * over [FADE_MS]. Touching the screen, or a change between work and break, ends the fade at once.
 */
object ScreenDim {
    const val IDLE_MS = 20_000L
    const val FADE_MS = 40_000L
    /** The screen never goes darker than this, so it can always be read and never looks switched off. */
    const val FLOOR = 0.01f
    const val MIN_PERCENT = 5
    const val MAX_PERCENT = 80
    const val DEFAULT_PERCENT = 10

    /**
     * The brightness (0..1) to ask for after [idleMs] without a touch, given the phone's own brightness [system] (0..1)
     * and how far to dim: down to [percent] percent of [system]. Null means leave the phone's own brightness alone.
     */
    fun level(idleMs: Long, system: Float, percent: Int = DEFAULT_PERCENT, startMs: Long = IDLE_MS): Float? {
        val target = (system * percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f).coerceAtLeast(FLOOR)
        if (idleMs <= startMs || system <= target) return null
        val progress = ((idleMs - startMs).toFloat() / FADE_MS).coerceIn(0f, 1f)
        return system + (target - system) * progress
    }
}
