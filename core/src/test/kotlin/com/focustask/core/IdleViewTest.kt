package com.focustask.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdleViewTest {
    @Test fun theTimerViewStartsAfterTheChosenDelay() {
        assertEquals(60_000L, IdleView.timerStartMs(true, 60))
        assertEquals(10_000L, IdleView.timerStartMs(true, 10))
    }

    @Test fun turnedOffThereIsNoTimerView() {
        assertNull(IdleView.timerStartMs(false, 60))
    }

    @Test fun theDelayStaysInRange() {
        assertEquals(10_000L, IdleView.timerStartMs(true, 1))
        assertEquals(1_800_000L, IdleView.timerStartMs(true, 99_999))
    }

    @Test fun theTimerDropsDownQuickly() {
        assertTrue(IdleView.DROP_MS in 400L..1_000L)
    }

    @Test fun dimmingStartsAfterTheTimerFillsTheScreen() {
        // 60 s to the view + its drop + 30 s
        assertEquals(90_000L + IdleView.DROP_MS, IdleView.dimStartMs(true, 60, 30))
    }

    @Test fun withoutTheTimerViewDimmingCountsFromTheLastTouch() {
        assertEquals(30_000L, IdleView.dimStartMs(false, 60, 30))
    }

    @Test fun dimmingFollowsTheStartTime() {
        val start = IdleView.dimStartMs(true, 60, 30)
        assertNull(ScreenDim.level(start, 0.5f, 10, start))
        assertNotNull(ScreenDim.level(start + 1_000, 0.5f, 10, start))
        assertEquals(0.05f, ScreenDim.level(start + ScreenDim.FADE_MS, 0.5f, 10, start)!!, 0.0001f)
    }
}
