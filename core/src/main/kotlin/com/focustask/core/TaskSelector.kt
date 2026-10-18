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
)

/**
 * Decides the ONE task to show right now.
 *
 * Priority:
 *  1. Most overdue task, from any list or block (worst offender first).
 *  2. Within the active time block(s): soonest deadline first, then manual order.
 *  3. Fallback: same ordering across "anytime" lists.
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
    ): CurrentTask? {
        val listsById = lists.associateBy { it.id }
        val blocksById = blocks.associateBy { it.id }
        val open = tasks.filter { !it.done && it.listId in listsById }
        val order = taskOrder(listsById)

        fun wrap(task: Task, reason: Reason): CurrentTask {
            val list = listsById.getValue(task.listId)
            return CurrentTask(task, list, list.blockId?.let { blocksById[it] }, reason)
        }

        // 1. Worst offender: the earliest missed deadline.
        if (overdueInterrupts) {
            open.filter { it.deadline != null && it.deadline < now }
                .minWithOrNull(order)
                ?.let { return wrap(it, Reason.OVERDUE) }
        }

        // 2. Tasks of the block(s) active right now.
        val activeBlockIds = blocks.filter { it.isActiveAt(now) }.map { it.id }.toSet()
        open.filter { listsById.getValue(it.listId).blockId in activeBlockIds }
            .minWithOrNull(order)
            ?.let { return wrap(it, Reason.IN_BLOCK) }

        // 3. Anytime lists.
        open.filter { listsById.getValue(it.listId).blockId == null }
            .minWithOrNull(order)
            ?.let { return wrap(it, Reason.ANYTIME) }

        return null
    }

    /** Soonest deadline first (no deadline last), then list order, task order, id. */
    private fun taskOrder(lists: Map<Long, TodoList>): Comparator<Task> =
        Comparator<Task> { a, b ->
            val da = a.deadline
            val db = b.deadline
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
