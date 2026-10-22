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
    /** The list's block that is active now (else its first block); null for "anytime" lists. */
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
 *  4. Tasks the person skipped go behind all the others, in the order they were skipped.
 *
 * Only tasks you can actually do are candidates: a task that still has open subtasks
 * is replaced by those subtasks.
 *
 * The list is empty when there is nothing to do right now.
 */
object TaskSelector {

    /** All candidates in the order they will be shown: the first one is the task to do now. */
    fun rank(
        now: LocalDateTime,
        blocks: List<TimeBlock>,
        lists: List<TodoList>,
        tasks: List<Task>,
        /** Tasks put aside for now ("skip"), the one skipped last at the end. They come back when nothing else is left. */
        skipped: List<Long> = emptyList(),
    ): List<CurrentTask> {
        val listsById = lists.associateBy { it.id }
        val blocksByList = blocks.filter { it.listId != null }.groupBy { it.listId!! }
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
            val own = blocksByList[list.id].orEmpty()
            val block = own.firstOrNull { it.isActiveAt(now) } ?: own.minByOrNull { it.start }
            return CurrentTask(task, list, block, reason, deadlineOf(task))
        }

        val result = ArrayList<CurrentTask>()
        val used = HashSet<Long>()

        // 1. Worst offender: the earliest missed deadline.
        actionable.filter { deadlineOf(it)?.let { d -> d < now } == true }
            .sortedWith(order)
            .forEach { result += wrap(it, Reason.OVERDUE); used += it.id }

        // 2. Tasks of the block(s) active right now.
        val activeListIds = blocks.filter { it.isActiveAt(now) }.mapNotNull { it.listId }.toSet()
        actionable.filter { it.id !in used && it.listId in activeListIds }
            .sortedWith(order)
            .forEach { result += wrap(it, Reason.IN_BLOCK); used += it.id }

        // 3. Anytime lists.
        actionable.filter { it.id !in used && it.listId !in blocksByList }
            .sortedWith(order)
            .forEach { result += wrap(it, Reason.ANYTIME); used += it.id }

        if (skipped.isEmpty()) return result
        val aside = skipped.mapNotNull { id -> result.firstOrNull { it.task.id == id } }
        return result.filter { it.task.id !in skipped } + aside
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
