package com.focustask.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenDimTest {
    @Test fun nothingChangesWhileSomeoneIsUsingTheApp() {
        assertNull(ScreenDim.level(0, 0.8f))
        assertNull(ScreenDim.level(ScreenDim.IDLE_MS, 0.8f))
    }

    @Test fun afterTheIdleTimeItFadesDownSlowlyAndStopsAtTheFloor() {
        val a = ScreenDim.level(ScreenDim.IDLE_MS + 10_000, 0.8f)!!
        val b = ScreenDim.level(ScreenDim.IDLE_MS + 30_000, 0.8f)!!
        assertTrue(a < 0.8f && b < a)
        assertEquals(0.08f, ScreenDim.level(ScreenDim.IDLE_MS + ScreenDim.FADE_MS, 0.8f)!!, 0.0001f)
        assertEquals(0.08f, ScreenDim.level(10 * 60_000, 0.8f)!!, 0.0001f)
    }

    @Test fun theFadeStartsFromThePhonesOwnBrightness() {
        assertEquals(0.5f, ScreenDim.level(ScreenDim.IDLE_MS + 1, 0.5f)!!, 0.001f)
    }

    @Test fun theChosenPercentSetsHowDarkItGets() {
        val dark = ScreenDim.level(10 * 60_000, 0.6f, 5)!!
        val light = ScreenDim.level(10 * 60_000, 0.6f, 50)!!
        assertEquals(0.03f, dark, 0.0001f)
        assertEquals(0.30f, light, 0.0001f)
        assertEquals(ScreenDim.level(10 * 60_000, 0.6f, 80), ScreenDim.level(10 * 60_000, 0.6f, 500), "out of range values are kept inside")
    }

    @Test fun theLevelIsAPercentageOfThePhonesBrightnessNotOfFull() {
        // phone at 50%, "80%" means 80% of that: 40%
        assertEquals(0.40f, ScreenDim.level(10 * 60_000, 0.5f, 80)!!, 0.0001f)
        // phone at 30%, "10%" means 3%
        assertEquals(0.03f, ScreenDim.level(10 * 60_000, 0.3f, 10)!!, 0.0001f)
    }

    @Test fun neverDarkerThanTheFloorEvenOnADimPhone() {
        assertEquals(ScreenDim.FLOOR, ScreenDim.level(10 * 60_000, 0.1f, 5)!!, 0.0001f)
    }

    @Test fun aPhoneAlreadyAtItsDarkestIsLeftAlone() {
        assertNull(ScreenDim.level(10 * 60_000, ScreenDim.FLOOR))
        assertNull(ScreenDim.level(10 * 60_000, 0f))
        assertNull(ScreenDim.level(10 * 60_000, 0.01f, 50))  // already as dark as it can get
    }
}
