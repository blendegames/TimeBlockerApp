package com.focustask.core

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TaskSelectorTest {
    // 2026-10-19 is a Monday.
    private fun at(h: Int, m: Int = 0, day: Int = 19) = LocalDateTime.of(2026, 10, day, h, m)

    private val morning = TimeBlock(1, "Morning", LocalTime.of(9, 0), LocalTime.of(11, 0), listId = 10)
    private val evening = TimeBlock(2, "Evening", LocalTime.of(18, 0), LocalTime.of(20, 0), listId = 20)
    private val blocks = listOf(morning, evening)

    private val work = TodoList(10, "Work", position = 0)
    private val home = TodoList(20, "Home", position = 1)
    private val someday = TodoList(30, "Someday", position = 2)
    private val lists = listOf(work, home, someday)

    private fun pick(now: LocalDateTime, tasks: List<Task>) = TaskSelector.rank(now, blocks, lists, tasks).firstOrNull()

    @Test
    fun returnsNullWhenThereAreNoTasks() {
        assertNull(pick(at(10), emptyList()))
    }

    @Test
    fun showsTaskOfTheActiveBlock() {
        val tasks = listOf(
            Task(1, 10, "Write report"),
            Task(2, 20, "Do laundry"),
        )
        val result = assertNotNull(pick(at(10), tasks))
        assertEquals("Write report", result.task.title)
        assertEquals(Reason.IN_BLOCK, result.reason)
        assertEquals(morning, result.block)
        assertEquals(work, result.list)
    }

    @Test
    fun otherBlocksTasksAreHiddenOutsideTheirBlock() {
        val tasks = listOf(Task(2, 20, "Do laundry"))
        // 10:00 is morning, laundry belongs to evening and there is no "anytime" task.
        assertNull(pick(at(10), tasks))
        assertEquals("Do laundry", pick(at(19), tasks)?.task?.title)
    }

    @Test
    fun soonestDeadlineBeatsManualOrder() {
        val tasks = listOf(
            Task(1, 10, "First in list", deadline = at(23), position = 0),
            Task(2, 10, "Due sooner", deadline = at(15), position = 5),
        )
        assertEquals("Due sooner", pick(at(10), tasks)?.task?.title)
    }

    @Test
    fun taskWithDeadlineBeatsTaskWithout() {
        val tasks = listOf(
            Task(1, 10, "No deadline", position = 0),
            Task(2, 10, "Has deadline", deadline = at(22), position = 9),
        )
        assertEquals("Has deadline", pick(at(10), tasks)?.task?.title)
    }

    @Test
    fun withoutDeadlinesManualOrderDecides() {
        val tasks = listOf(
            Task(1, 10, "Second", position = 1),
            Task(2, 10, "First", position = 0),
        )
        assertEquals("First", pick(at(10), tasks)?.task?.title)
    }

    @Test
    fun equalEverythingFallsBackToIdForStableResult() {
        val tasks = listOf(
            Task(7, 10, "B", position = 0),
            Task(3, 10, "A", position = 0),
        )
        assertEquals("A", pick(at(10), tasks)?.task?.title)
    }

    @Test
    fun overdueTaskFromAnotherBlockInterrupts() {
        val tasks = listOf(
            Task(1, 10, "Morning task", deadline = at(12)),
            Task(2, 20, "Forgotten evening task", deadline = at(8)),
        )
        val result = assertNotNull(pick(at(10), tasks))
        assertEquals("Forgotten evening task", result.task.title)
        assertEquals(Reason.OVERDUE, result.reason)
        assertEquals(evening, result.block)
    }

    @Test
    fun worstOffenderIsTheMostOverdue() {
        val tasks = listOf(
            Task(1, 10, "Slightly late", deadline = at(8)),
            Task(2, 20, "Very late", deadline = at(8, day = 17)),
            Task(3, 30, "Late too", deadline = at(7)),
        )
        assertEquals("Very late", pick(at(10), tasks)?.task?.title)
    }

    @Test
    fun overdueTaskStaysOnTopOutsideAnyBlock() {
        val tasks = listOf(Task(1, 10, "Missed", deadline = at(9)))
        // 14:00: no block is active, the missed task still shows.
        val result = assertNotNull(pick(at(14), tasks))
        assertEquals("Missed", result.task.title)
        assertEquals(Reason.OVERDUE, result.reason)
    }

    @Test
    fun deadlineExactlyNowIsNotOverdueYet() {
        val tasks = listOf(
            Task(1, 10, "Due right now", deadline = at(10)),
            Task(2, 10, "Earlier in list", position = -1),
        )
        // "Due right now" has a deadline, so it still wins the in-block ordering,
        // but it must not be flagged as overdue.
        val result = assertNotNull(pick(at(10), tasks))
        assertEquals("Due right now", result.task.title)
        assertEquals(Reason.IN_BLOCK, result.reason)
    }

    @Test
    fun doneTasksAreIgnored() {
        val tasks = listOf(
            Task(1, 10, "Finished", deadline = at(8), done = true),
            Task(2, 10, "Open"),
        )
        val result = assertNotNull(pick(at(10), tasks))
        assertEquals("Open", result.task.title)
        assertEquals(Reason.IN_BLOCK, result.reason)
    }

    @Test
    fun allDoneMeansNothingToShow() {
        val tasks = listOf(Task(1, 10, "Finished", done = true))
        assertNull(pick(at(10), tasks))
    }

    @Test
    fun anytimeListIsOnlyAFallback() {
        val tasks = listOf(
            Task(1, 30, "Someday task"),
            Task(2, 10, "Morning task"),
        )
        assertEquals("Morning task", pick(at(10), tasks)?.task?.title)
        val fallback = assertNotNull(pick(at(14), tasks))
        assertEquals("Someday task", fallback.task.title)
        assertEquals(Reason.ANYTIME, fallback.reason)
        assertNull(fallback.block)
    }

    @Test
    fun overlappingBlocksCompeteByDeadlineThenListOrder() {
        val overlapping = listOf(
            morning,
            TimeBlock(3, "Admin", LocalTime.of(10, 0), LocalTime.of(12, 0), listId = 40),
        )
        val adminList = TodoList(40, "Admin", position = 3)
        val tasks = listOf(
            Task(1, 10, "Work task", position = 0),
            Task(2, 40, "Admin task", deadline = at(17)),
        )
        val result = TaskSelector.rank(at(10, 30), overlapping, lists + adminList, tasks).firstOrNull()
        assertEquals("Admin task", result?.task?.title)
    }

    @Test
    fun unfinishedTaskComesBackWhenTheBlockRepeatsTomorrow() {
        val tasks = listOf(Task(1, 10, "Unfinished"))
        assertNull(pick(at(12, day = 19), tasks))
        assertEquals("Unfinished", pick(at(9, 30, day = 20), tasks)?.task?.title)
    }

    @Test
    fun tasksOfUnknownListsAreIgnored() {
        val tasks = listOf(Task(1, 999, "Orphan"))
        assertNull(pick(at(10), tasks))
    }

    // ---- subtasks ----

    @Test
    fun openSubtasksReplaceTheirParent() {
        val tasks = listOf(
            Task(1, 10, "Write summary", deadline = at(12)),
            Task(2, 10, "Draft", parentId = 1, position = 0),
            Task(3, 10, "Send", parentId = 1, position = 1),
        )
        val result = assertNotNull(pick(at(10), tasks))
        assertEquals("Draft", result.task.title)
    }

    @Test
    fun subtaskUsesItsOwnDeadlineAndInheritsOtherwise() {
        val tasks = listOf(
            Task(1, 10, "Parent", deadline = at(12)),
            Task(2, 10, "Own deadline", deadline = at(11), parentId = 1, position = 1),
            Task(3, 10, "Inherits", parentId = 1, position = 0),
        )
        val ranked = TaskSelector.rank(at(10), blocks, lists, tasks)
        assertEquals(listOf("Own deadline", "Inherits"), ranked.map { it.task.title })
        assertEquals(at(11), ranked[0].deadline)
        assertEquals(at(12), ranked[1].deadline)
    }

    @Test
    fun overdueSubtaskInterrupts() {
        val tasks = listOf(
            Task(1, 20, "Evening parent"),
            Task(2, 20, "Late step", deadline = at(8), parentId = 1),
            Task(3, 10, "Morning task"),
        )
        val result = assertNotNull(pick(at(10), tasks))
        assertEquals("Late step", result.task.title)
        assertEquals(Reason.OVERDUE, result.reason)
    }

    @Test
    fun parentReturnsWhenAllSubtasksAreDone() {
        val tasks = listOf(
            Task(1, 10, "Parent"),
            Task(2, 10, "Step", parentId = 1, done = true),
        )
        assertEquals("Parent", pick(at(10), tasks)?.task?.title)
    }

    @Test
    fun rankListsEverythingInOrder() {
        val tasks = listOf(
            Task(1, 10, "In block"),
            Task(2, 30, "Someday"),
            Task(3, 20, "Late", deadline = at(8)),
        )
        val ranked = TaskSelector.rank(at(10), blocks, lists, tasks)
        assertEquals(listOf("Late", "In block", "Someday"), ranked.map { it.task.title })
        assertEquals(
            listOf(Reason.OVERDUE, Reason.IN_BLOCK, Reason.ANYTIME),
            ranked.map { it.reason },
        )
    }

    @Test
    fun aListWithSeveralBlocksIsActiveInEachOfThem() {
        val twoBlocks = listOf(morning, TimeBlock(5, "Work again", LocalTime.of(14, 0), LocalTime.of(15, 0), listId = 10))
        val tasks = listOf(Task(1, 10, "Work task"), Task(2, 30, "Someday"))
        val afternoon = TaskSelector.rank(at(14, 30), twoBlocks, lists, tasks).firstOrNull()
        assertEquals("Work task", afternoon?.task?.title)
        assertEquals(Reason.IN_BLOCK, afternoon?.reason)
        assertEquals(5L, afternoon?.block?.id)
        // between the two blocks the list is not anytime: only the real anytime list shows
        val between = TaskSelector.rank(at(12), twoBlocks, lists, tasks).firstOrNull()
        assertEquals("Someday", between?.task?.title)
    }

    @Test
    fun aSkippedTaskGoesBehindTheOthersAndComesBackWhenNothingElseIsLeft() {
        val tasks = listOf(Task(1, 10, "A", position = 0), Task(2, 10, "B", position = 1), Task(3, 10, "C", position = 2))
        fun order(skipped: List<Long>) = TaskSelector.rank(at(10), blocks, lists, tasks, skipped).map { it.task.title }
        assertEquals(listOf("A", "B", "C"), order(emptyList()))
        assertEquals(listOf("B", "C", "A"), order(listOf(1)))
        // skipped one after the other: the one skipped last waits longest
        assertEquals(listOf("C", "A", "B"), order(listOf(1, 2)))
        // a lonely task that is skipped is still there
        assertEquals(listOf("A"), TaskSelector.rank(at(10), blocks, lists, listOf(tasks[0]), listOf(1)).map { it.task.title })
    }

    @Test
    fun skippingBeatsEvenAnOverdueTask() {
        val tasks = listOf(Task(1, 10, "Late", deadline = at(8)), Task(2, 10, "Fine", position = 1))
        assertEquals("Late", pick(at(10), tasks)?.task?.title)
        assertEquals("Fine", TaskSelector.rank(at(10), blocks, lists, tasks, listOf(1)).firstOrNull()?.task?.title)
    }
}
