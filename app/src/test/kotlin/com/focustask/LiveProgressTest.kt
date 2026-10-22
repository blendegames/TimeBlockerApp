package com.focustask

import com.focustask.alerts.Notifier
import com.focustask.core.CycleSettings
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The progress bar of the Live Update: the block split into its work and break parts. */
class LiveProgressTest {
    private val start = LocalDateTime.of(2026, 10, 21, 9, 0)

    @Test fun aBlockIsSplitIntoWorkAndBreaks() {
        // 70 minutes of 25 + 5: work, break, work, break, and 10 minutes of work at the end
        val p = Notifier.LiveProgress.ofBlock(start, start.plusMinutes(70), start.plusMinutes(31), CycleSettings())
        assertEquals(listOf(25, 5, 25, 5, 10), p.parts.map { it.first / 60 })
        assertEquals(31 * 60, p.at)
        assertEquals(70 * 60, p.parts.sumOf { it.first })
    }

    @Test fun withoutShortBreaksItIsOneWorkPartPerCycle() {
        val p = Notifier.LiveProgress.ofBlock(start, start.plusMinutes(50), start, CycleSettings(workMin = 25, shortBreakMin = 0))
        assertEquals(listOf(25, 25), p.parts.map { it.first / 60 })
        assertEquals(0, p.at)
    }
}
