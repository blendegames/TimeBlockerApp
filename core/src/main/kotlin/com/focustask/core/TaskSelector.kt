package com.focustask.core

import java.time.LocalDateTime

/** Why a task was chosen. Useful for the UI ("Overdue!" badge). */
enum class Reason {
    /** Past its deadline. Wins over everything, even from another block. */
    OVERDUE,

    /** Belongs to a list whose time block is active right now. */
    IN_BLOCK,

    /** Belongs to a list without a time block; only shown when nothing else is due. */
    ANYTIME,
}

data class CurrentTask(
    val task: Task,
    val list: TodoList,
    /** The list's block, or null for "anytime" lists. */
    val block: TimeBlock?,
    val reason: Reason,
    /** The deadline that counts: the task's own, or its parent's. */
    val deadline: LocalDateTime? = task.deadline,
)

/**
 * Decides the ONE task to show right now (and the order of the ones after it).
 *
 * Priority:
 *  1. Most overdue task, from any list or block (worst offender first).
 *  2. Within the active time block(s): soonest deadline first, then manual order.
 *  3. Fallback: same ordering across "anytime" lists.
 *
 * Only tasks you can actually do are candidates: a task that still has open subtasks
 * is replaced by those subtasks.
 *
 * Returns null when there is nothing to do right now.
 */
object TaskSelector {

    fun select(
        now: LocalDateTime,
        blocks: List<TimeBlock>,
        lists: List<TodoList>,
        tasks: List<Task>,
        overdueInterrupts: Boolean = true,
    ): CurrentTask? = rank(now, blocks, lists, tasks, overdueInterrupts).firstOrNull()

    /** All candidates in the order they will be shown. The first one is [select]'s answer. */
    fun rank(
        now: LocalDateTime,
        blocks: List<TimeBlock>,
        lists: List<TodoList>,
        tasks: List<Task>,
        overdueInterrupts: Boolean = true,
    ): List<CurrentTask> {
        val listsById = lists.associateBy { it.id }
        val blocksById = blocks.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }
        val parentsWithOpenChildren = tasks.filter { !it.done && it.parentId != null }
            .mapNotNull { it.parentId }.toSet()

        fun deadlineOf(t: Task): LocalDateTime? =
            t.deadline ?: t.parentId?.let { tasksById[it]?.deadline }

        val actionable = tasks.filter {
            !it.done && it.listId in listsById && it.id !in parentsWithOpenChildren
        }
        val order = taskOrder(listsById, ::deadlineOf)

        fun wrap(task: Task, reason: Reason): CurrentTask {
            val list = listsById.getValue(task.listId)
            return CurrentTask(task, list, list.blockId?.let { blocksById[it] }, reason, deadlineOf(task))
        }

        val result = ArrayList<CurrentTask>()
        val used = HashSet<Long>()

        // 1. Worst offender: the earliest missed deadline.
        if (overdueInterrupts) {
            actionable.filter { deadlineOf(it)?.let { d -> d < now } == true }
                .sortedWith(order)
                .forEach { result += wrap(it, Reason.OVERDUE); used += it.id }
        }

        // 2. Tasks of the block(s) active right now.
        val activeBlockIds = blocks.filter { it.isActiveAt(now) }.map { it.id }.toSet()
        actionable.filter { it.id !in used && listsById.getValue(it.listId).blockId in activeBlockIds }
            .sortedWith(order)
            .forEach { result += wrap(it, Reason.IN_BLOCK); used += it.id }

        // 3. Anytime lists.
        actionable.filter { it.id !in used && listsById.getValue(it.listId).blockId == null }
            .sortedWith(order)
            .forEach { result += wrap(it, Reason.ANYTIME); used += it.id }

        return result
    }

    /** Soonest deadline first (no deadline last), then list order, task order, id. */
    private fun taskOrder(
        lists: Map<Long, TodoList>,
        deadlineOf: (Task) -> LocalDateTime?,
    ): Comparator<Task> =
        Comparator<Task> { a, b ->
            val da = deadlineOf(a)
            val db = deadlineOf(b)
            when {
                da != null && db != null -> da.compareTo(db)
                da != null -> -1
                db != null -> 1
                else -> 0
            }
        }
            .thenComparingInt { lists.getValue(it.listId).position }
            .thenComparingInt { it.position }
            .thenComparingLong { it.id }
}
