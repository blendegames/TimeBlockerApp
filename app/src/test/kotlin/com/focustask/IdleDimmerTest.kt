package com.focustask

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.focustask.core.ScreenDim
import com.focustask.ui.IdleDimmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The window dims while nobody touches it and gets the phone's own brightness back on a touch or a phase change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IdleDimmerTest {
    private val none = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    @Test fun dimsAfterTheIdleTimeAndWakeBringsBackThePhonesBrightness() {
        val window = Robolectric.buildActivity(ComponentActivity::class.java).setup().get().window
        var clock = 1_000_000L
        val dimmer = IdleDimmer(window, { 0.6f }, { clock })
        dimmer.percent = 20

        dimmer.wake(); dimmer.tick()
        assertEquals("in use: the phone's own brightness", none, window.attributes.screenBrightness, 0f)

        clock += ScreenDim.IDLE_MS + 10_000; dimmer.tick()
        val half = window.attributes.screenBrightness
        assertTrue("fading: $half", half in 0.03f..0.59f)

        clock += 10_000; dimmer.tick()
        assertTrue("darker still", window.attributes.screenBrightness < half)

        clock += 5 * 60_000; dimmer.tick()
        assertEquals("20% of 0.6", 0.12f, window.attributes.screenBrightness, 0.0001f)

        // a touch, or a change between work and break, calls wake()
        dimmer.wake()
        assertEquals(none, window.attributes.screenBrightness, 0f)
        // ...and the wait starts again
        clock += ScreenDim.IDLE_MS - 1_000; dimmer.tick()
        assertEquals(none, window.attributes.screenBrightness, 0f)
        clock += 10_000; dimmer.tick()
        assertTrue(window.attributes.screenBrightness < 0.6f)

        dimmer.release()
        assertEquals(none, window.attributes.screenBrightness, 0f)
    }
}
