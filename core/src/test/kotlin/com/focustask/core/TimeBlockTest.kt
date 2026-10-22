package com.focustask.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeBlockTest {
    // 2026-10-19 is a Monday.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    private val morning = TimeBlock(1, "Deep work", LocalTime.of(9, 0), LocalTime.of(11, 0))

    @Test
    fun startIsInclusiveAndEndIsExclusive() {
        assertTrue(morning.isActiveAt(at(19, 9, 0)))
        assertTrue(morning.isActiveAt(at(19, 10, 59)))
        assertFalse(morning.isActiveAt(at(19, 11, 0)))
        assertFalse(morning.isActiveAt(at(19, 8, 59)))
    }

    @Test
    fun respectsDaysOfWeek() {
        val weekdays = morning.copy(days = setOf(DayOfWeek.MONDAY))
        assertTrue(weekdays.isActiveAt(at(19, 10)))   // Monday
        assertFalse(weekdays.isActiveAt(at(20, 10)))  // Tuesday
    }

    @Test
    fun blockCrossingMidnightIsActiveOnBothSides() {
        val night = TimeBlock(2, "Night", LocalTime.of(22, 0), LocalTime.of(2, 0))
        assertTrue(night.crossesMidnight)
        assertTrue(night.isActiveAt(at(19, 23)))
        assertTrue(night.isActiveAt(at(20, 1)))
        assertFalse(night.isActiveAt(at(20, 2)))
        assertFalse(night.isActiveAt(at(19, 12)))
    }

    @Test
    fun midnightBlockUsesTheStartDay() {
        val night = TimeBlock(
            2, "Night", LocalTime.of(22, 0), LocalTime.of(2, 0),
            days = setOf(DayOfWeek.MONDAY),
        )
        assertTrue(night.isActiveAt(at(19, 23)))   // Monday 23:00
        assertTrue(night.isActiveAt(at(20, 1)))    // Tuesday 01:00, still Monday's block
        assertFalse(night.isActiveAt(at(20, 23)))  // Tuesday 23:00, not a start day
        assertFalse(night.isActiveAt(at(19, 1)))   // Monday 01:00, Sunday's block does not exist
    }

    @Test
    fun rejectsEmptyBlock() {
        assertFailsWith<IllegalArgumentException> {
            TimeBlock(3, "Nothing", LocalTime.of(9, 0), LocalTime.of(9, 0))
        }
    }
}
