package com.focustask.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FocusEngineTest {
    // 2026-10-19 is a Monday.
    private fun at(h: Int, m: Int = 0, s: Int = 0, day: Int = 19) = LocalDateTime.of(2026, 10, day, h, m, s)

    private val work = TimeBlock(1, "Work", LocalTime.of(9, 0), LocalTime.of(11, 0))
    private val admin = TimeBlock(2, "Admin", LocalTime.of(11, 30), LocalTime.of(12, 30))
    private val blocks = listOf(work, admin)
    private val workList = TodoList(10, "Work", blockId = 1)
    private val adminList = TodoList(20, "Admin", blockId = 2, position = 1)
    private val lists = listOf(workList, adminList)
    private val s = CycleSettings()

    private fun build(
        now: LocalDateTime,
        tasks: List<Task>,
        userBreak: UserBreak? = null,
        bl: List<TimeBlock> = blocks,
    ) = FocusEngine.build(now, { bl }, lists, tasks, s, userBreak)

    // ---- cycle clock ----

    private val inst = BlockInstance(work, at(9), at(11))

    @Test
    fun cycleClockInTheMiddleOfAWorkPeriod() {
        val c = CycleClock.at(inst, at(10, 12), s)   // 72 min in: cycle 3 starts at 60
        assertEquals(CyclePhase.WORK, c.phase)
        assertEquals(3, c.cycleNumber)
        assertEquals(4, c.cycleCount)
        assertEquals(Duration.ofMinutes(13), c.phaseLeft)   // work ends 10:25
        assertEquals(at(10, 25), c.phaseEndsAt)
        assertEquals(0.6, c.sunFraction, 1e-9)
        assertEquals(Duration.ofMinutes(48), c.blockLeft)
        assertFalse(c.countdownActive)
    }

    @Test
    fun cycleClockDuringTheShortBreak() {
        val c = CycleClock.at(inst, at(9, 27), s)
        assertEquals(CyclePhase.SHORT_BREAK, c.phase)
        assertEquals(Duration.ofMinutes(3), c.phaseLeft)
        assertEquals(at(9, 30), c.phaseEndsAt)
    }

    @Test
    fun cycleClockCountdownStartsTenMinutesBeforeTheEnd() {
        assertFalse(CycleClock.at(inst, at(10, 49, 59), s).countdownActive)
        assertTrue(CycleClock.at(inst, at(10, 50), s).countdownActive)
        assertEquals(Duration.ofSeconds(9), CycleClock.at(inst, at(10, 59, 51), s).blockLeft)
    }

    @Test
    fun lastPeriodIsCutByTheEndOfTheBlock() {
        val short = BlockInstance(work, at(9), at(9, 40))
        val c = CycleClock.at(short, at(9, 32), s)   // second cycle, work part
        assertEquals(CyclePhase.WORK, c.phase)
        assertEquals(Duration.ofMinutes(8), c.phaseLeft)
        assertEquals(short.end, c.phaseEndsAt)
    }

    @Test
    fun stripHasBigBubblesAtEveryCycleMark() {
        assertEquals(setOf(0, 3, 6, 9, 12), CycleClock.largeBubbles(Duration.ofHours(2), s))
        assertEquals(setOf(0, 12), CycleClock.largeBubbles(Duration.ofMinutes(20), s))
    }

    // ---- breaks ----

    @Test
    fun longBreakFollowsEveryBlockEvenWhenTheNextStartsRightAway() {
        val back = listOf(work, TimeBlock(3, "Next", LocalTime.of(11, 0), LocalTime.of(12, 0)))
        val state = build(at(11, 5), emptyList(), bl = back)
        assertEquals(Mode.BREAK, state.mode)
        assertNull(state.active)
        assertEquals(at(11, 15), state.longBreak?.until)
        assertTrue(state.locked)
        // after the break the next block runs
        assertEquals(Mode.ALL_DONE, build(at(11, 20), emptyList(), bl = back).mode)
        assertNotNull(build(at(11, 20), emptyList(), bl = back).active)
    }

    @Test
    fun noBreakBeforeTheFirstBlockOfTheDay() {
        val s0 = build(at(8), emptyList())
        assertNull(s0.longBreak)
    }

    @Test
    fun finishingEarlyAllowsABreakUntilTheNextBlockStarts() {
        val userBreak = UserBreak(at(10, 12), at(11, 30).minusMinutes(0))
        // not a break yet without the user's choice
        assertEquals(Mode.ALL_DONE, build(at(10, 12), emptyList()).mode)
        val state = build(at(10, 12), emptyList(), userBreak)
        assertEquals(Mode.BREAK, state.mode)
        assertTrue(state.longBreak!!.userInitiated)
        assertEquals(BarKind.LONG_BREAK, state.bar.kind)
        assertEquals(Duration.ofMinutes(78).seconds, state.bar.seconds)
    }

    @Test
    fun shortUserBreakMayBeJustFiveMinutes() {
        val userBreak = UserBreak(at(11, 25), at(11, 30))
        val state = build(at(11, 26), emptyList(), userBreak)
        assertEquals(Mode.BREAK, state.mode)
        assertEquals(240, state.bar.seconds)
    }

    @Test
    fun regularBreakUsesTheLongBreakSetting() {
        val state = build(at(11, 7), emptyList())
        assertEquals(Mode.BREAK, state.mode)       // 11:00 + 15 min
        assertEquals(Duration.ofMinutes(8).seconds, state.bar.seconds)
        assertEquals(setOf(0, 12), state.bar.largeBubbles)
    }

    // ---- modes and bars ----

    @Test
    fun focusModeShowsTheFocusTimer() {
        val tasks = listOf(Task(1, 10, "Write"))
        val state = build(at(10, 12), tasks)
        assertEquals(Mode.FOCUS, state.mode)
        assertEquals(BarKind.FOCUS_WORK, state.bar.kind)
        assertEquals(13 * 60L, state.bar.seconds)
        assertEquals(48, state.bar.blockLeftMin)
        assertEquals(setOf(0, 3, 6, 9, 12), state.bar.largeBubbles)
        assertEquals(3, state.bar.cycleNumber)
    }

    @Test
    fun allDoneCountsDownToTheNextBlock() {
        val state = build(at(10, 12), emptyList())  // nothing to do, inside the work block
        assertEquals(Mode.ALL_DONE, state.mode)
        assertEquals(BarKind.WAIT_NEXT, state.bar.kind)
        assertEquals("Admin", state.bar.nextName)
        assertEquals(78 * 60L, state.bar.seconds)
        assertEquals(setOf(0, 12), state.bar.largeBubbles)
    }

    @Test
    fun overdueTaskShowsEvenWithoutABlock() {
        val tasks = listOf(Task(1, 10, "Late", deadline = at(8, day = 17)))
        val state = build(at(14), tasks)
        assertEquals(Mode.FOCUS, state.mode)
        assertEquals(Reason.OVERDUE, state.carousel?.current?.reason)
    }

    @Test
    fun nothingScheduledGivesAnIdleBar() {
        val state = FocusEngine.build(at(14), { emptyList() }, lists, emptyList(), s)
        assertEquals(BarKind.IDLE, state.bar.kind)
        assertEquals(Mode.ALL_DONE, state.mode)
    }

    @Test
    fun oneDayChangeMovesTheBlockOnlyThatDay() {
        val moved = work.copy(start = LocalTime.of(13, 0), end = LocalTime.of(15, 0))
        val blocksFor = { d: LocalDate -> if (d == LocalDate.of(2026, 10, 19)) listOf(moved, admin) else blocks }
        val tasks = listOf(Task(1, 10, "Write"))
        val today = FocusEngine.build(at(10), blocksFor, lists, tasks, s)
        assertNull(today.active)   // moved to 13:00 today
        val tomorrow = FocusEngine.build(at(10, day = 20), blocksFor, lists, tasks, s)
        assertNotNull(tomorrow.active)
    }

    // ---- carousel ----

    @Test
    fun carouselShowsPreviousCurrentAndNext() {
        val tasks = listOf(
            Task(1, 10, "Pull numbers", done = true, completedAt = at(9, 41)),
            Task(2, 10, "Write summary", deadline = at(12)),
            Task(3, 10, "Review PR", deadline = at(17)),
        )
        val c = build(at(10, 12), tasks).carousel!!
        assertEquals("Pull numbers", c.previous?.title)
        assertEquals("Write summary", c.current.task.title)
        assertEquals("Review PR", c.next?.title)
        assertEquals(2, c.listIndex)
        assertEquals(3, c.listCount)
    }

    @Test
    fun carouselStartHasNoPreviousAndEndHasNoNext() {
        val tasks = listOf(Task(2, 10, "First", position = 0), Task(3, 10, "Second", position = 1))
        val start = build(at(10), tasks).carousel!!
        assertNull(start.previous)
        assertEquals("Second", start.next?.title)

        val end = build(
            at(10),
            listOf(
                Task(2, 10, "First", position = 0, done = true, completedAt = at(9, 30)),
                Task(3, 10, "Second", position = 1),
            ),
        ).carousel!!
        assertEquals("First", end.previous?.title)
        assertNull(end.next)
    }

    @Test
    fun yesterdaysCompletedTaskIsNotShownAsPrevious() {
        val tasks = listOf(
            Task(1, 10, "Old", done = true, completedAt = at(9, day = 18)),
            Task(2, 10, "Now"),
        )
        assertNull(build(at(10), tasks).carousel!!.previous)
    }

    @Test
    fun subtaskCarouselCyclesThroughTheSubtasks() {
        val tasks = listOf(
            Task(1, 10, "Write summary", deadline = at(12), position = 0),
            Task(2, 10, "Pull numbers", parentId = 1, position = 0, done = true, completedAt = at(9, 41)),
            Task(3, 10, "Draft", parentId = 1, position = 1, deadline = at(11)),
            Task(4, 10, "Send to Dana", parentId = 1, position = 2),
            Task(5, 10, "Other task", position = 1),
        )
        val c = build(at(10, 12), tasks).carousel!!
        assertEquals("Write summary", c.parent?.title)
        assertEquals("Pull numbers", c.previous?.title)
        assertEquals("Draft", c.current.task.title)
        assertEquals("Send to Dana", c.next?.title)
        assertEquals(2, c.stepIndex)
        assertEquals(3, c.stepCount)
        assertEquals(at(11), c.current.deadline)
    }

    @Test
    fun lastSubtaskHasNoNextSubtask() {
        val tasks = listOf(
            Task(1, 10, "Parent"),
            Task(2, 10, "Step 1", parentId = 1, position = 0, done = true, completedAt = at(9, 40)),
            Task(3, 10, "Step 2", parentId = 1, position = 1),
            Task(4, 10, "Other", position = 1),
        )
        val c = build(at(10), tasks).carousel!!
        assertEquals("Step 2", c.current.task.title)
        assertNull(c.next)
    }

    // ---- alerts ----

    @Test
    fun alertsFollowTheCycle() {
        val events = AlertPlanner.upcoming(at(9), listOf(inst), s, null)
        val first = events.first()
        assertEquals(AlertEvent(at(9, 25), AlertKind.PHASE_CUE), first)
        assertTrue(AlertEvent(at(9, 30), AlertKind.CYCLE_CHIME) in events)
        assertTrue(AlertEvent(at(10, 50), AlertKind.BLOCK_ENDING) in events)
        assertTrue(AlertEvent(at(11, 15), AlertKind.PHASE_CUE) in events)   // break over
        assertFalse(events.any { it.at <= at(9) })
    }

    @Test
    fun userBreakEndGetsACue() {
        val ub = UserBreak(at(10, 12), at(11, 30))
        val events = AlertPlanner.upcoming(at(10, 12), listOf(inst), s, ub)
        assertTrue(AlertEvent(at(11, 30), AlertKind.PHASE_CUE) in events)
    }
}
