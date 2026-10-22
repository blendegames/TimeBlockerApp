package com.focustask.core

/**
 * What the screen does while nobody touches the app, in two steps:
 *
 * 1. After [timerAfterSec] seconds the full-screen timer drops down over the app in [DROP_MS], pure black with the focus timer.
 * 2. Once the timer fills the screen, dimming starts after [dimAfterSec] more seconds (see [ScreenDim]).
 *
 * Each step can be turned off. With the timer view off, dimming counts from the last touch instead.
 */
object IdleView {
    /** The full-screen timer takes this long to drop down: the same when it comes by itself and when it is pulled down. */
    const val DROP_MS = 700L

    const val MIN_TIMER_SEC = 10
    const val MAX_TIMER_SEC = 1800
    const val DEFAULT_TIMER_SEC = 60

    const val MIN_DIM_SEC = 5
    const val MAX_DIM_SEC = 1800
    const val DEFAULT_DIM_SEC = 30

    /** When, in milliseconds after the last touch, the timer view starts to fade in. Null when it is off. */
    fun timerStartMs(timerOn: Boolean, timerAfterSec: Int): Long? =
        if (timerOn) timerAfterSec.coerceIn(MIN_TIMER_SEC, MAX_TIMER_SEC) * 1000L else null

    /** When, in milliseconds after the last touch, dimming begins: after the timer view is complete, or after the touch. */
    fun dimStartMs(timerOn: Boolean, timerAfterSec: Int, dimAfterSec: Int): Long =
        (timerStartMs(timerOn, timerAfterSec)?.plus(DROP_MS) ?: 0L) + dimAfterSec.coerceIn(MIN_DIM_SEC, MAX_DIM_SEC) * 1000L
}
