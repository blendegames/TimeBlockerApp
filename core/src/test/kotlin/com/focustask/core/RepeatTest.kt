package com.focustask.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepeatTest {
    // 2026-10-19 is a Monday
    private val doneAt = LocalDateTime.of(2026, 10, 19, 14, 30)

    @Test fun everyDayComesBackAtTheNextMidnight() {
        assertEquals(LocalDateTime.of(2026, 10, 20, 0, 0), Repeat(1, RepeatUnit.DAY).comesBackAfter(doneAt))
    }

    @Test fun everyTwoDaysSkipsADay() {
        assertEquals(LocalDateTime.of(2026, 10, 21, 0, 0), Repeat(2, RepeatUnit.DAY).comesBackAfter(doneAt))
    }

    @Test fun everyWeekComesBackOnTheSameWeekday() {
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), Repeat(1, RepeatUnit.WEEK).comesBackAfter(doneAt))
    }

    @Test fun everyMonthKeepsTheDayAndShortensToTheEndOfAShortMonth() {
        assertEquals(LocalDateTime.of(2026, 11, 19, 0, 0), Repeat(1, RepeatUnit.MONTH).comesBackAfter(doneAt))
        assertEquals(LocalDateTime.of(2026, 2, 28, 0, 0), Repeat(1, RepeatUnit.MONTH).comesBackAfter(LocalDateTime.of(2026, 1, 31, 23, 59)))
    }

    @Test fun completedJustBeforeMidnightStillWaitsAFullDay() {
        assertEquals(LocalDateTime.of(2026, 10, 20, 0, 0), Repeat(1, RepeatUnit.DAY).comesBackAfter(LocalDateTime.of(2026, 10, 19, 23, 59)))
    }

    @Test fun labelsReadNaturally() {
        assertEquals("Every day", Repeat(1, RepeatUnit.DAY).label())
        assertEquals("Every 2 days", Repeat(2, RepeatUnit.DAY).label())
        assertEquals("Every week", Repeat(1, RepeatUnit.WEEK).label())
        assertEquals("Every 3 weeks", Repeat(3, RepeatUnit.WEEK).label())
        assertEquals("Every month", Repeat(1, RepeatUnit.MONTH).label())
        assertEquals("Every 6 months", Repeat(6, RepeatUnit.MONTH).label())
    }

    @Test fun zeroIsNotARepeat() {
        assertFailsWith<IllegalArgumentException> { Repeat(0, RepeatUnit.DAY) }
    }
}
