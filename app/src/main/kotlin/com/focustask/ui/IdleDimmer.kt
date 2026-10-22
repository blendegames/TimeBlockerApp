package com.focustask.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import com.focustask.core.ScreenDim

/**
 * Dims the app's window while nobody touches it, and gives the phone's own brightness back on [wake].
 * Only this window is changed, and only while the app is open: the phone's brightness setting is never touched.
 */
class IdleDimmer(
    private val window: Window,
    private val systemLevel: () -> Float,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** How dark it gets, in percent of the phone's own brightness. */
    var percent: Int = ScreenDim.DEFAULT_PERCENT

    /** How long without a touch until the fade begins. */
    var startMs: Long = ScreenDim.IDLE_MS

    private var lastActivity = now()

    /** A touch, a change between work and break, or coming back to the app: full brightness, and the wait starts again. */
    fun wake() {
        lastActivity = now()
        apply(null)
    }

    /** Call about twice a second while the screen is kept on. */
    fun tick() {
        val idle = now() - lastActivity
        // the phone's brightness is only asked for once dimming has begun: asking costs a call to the system
        apply(if (idle < startMs) null else ScreenDim.level(idle, systemLevel(), percent, startMs))
    }

    /** Give the phone's brightness back. */
    fun release() = apply(null)

    private fun apply(level: Float?) {
        val wanted = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        val lp = window.attributes
        if (lp.screenBrightness != wanted) {
            lp.screenBrightness = wanted
            window.attributes = lp
        }
    }
}

/** The phone's own brightness, 0..1 (what the slider says). */
fun phoneBrightness(ctx: Context): Float =
    (Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f).coerceIn(0f, 1f)

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
