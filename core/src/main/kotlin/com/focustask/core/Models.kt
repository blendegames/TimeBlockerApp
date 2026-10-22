package com.focustask.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A window in the day, e.g. 09:00-11:00. A block shows one list and is named after it.
 * A list can have several blocks (09:00-11:00 on weekdays, 14:00-15:00 on Saturday, ...).
 *
 * If [end] is earlier than [start] the block crosses midnight (e.g. 22:00-02:00).
 * [days] are the days the block STARTS on.
 */
data class TimeBlock(
    val id: Long,
    val name: String,
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    /** The list this block shows. */
    val listId: Long? = null,
) {
    init {
        require(start != end) { "A time block must have a start different from its end" }
    }

    val crossesMidnight: Boolean get() = end < start

    /** The start time is inclusive, the end time exclusive. */
    fun isActiveAt(now: LocalDateTime): Boolean {
        val t = now.toLocalTime()
        return if (!crossesMidnight) {
            now.dayOfWeek in days && t >= start && t < end
        } else {
            (now.dayOfWeek in days && t >= start) ||
                (now.dayOfWeek.minus(1) in days && t < end)
        }
    }
}

/** A todo list with its own title. A list without any time block is an "anytime" list. */
data class TodoList(
    val id: Long,
    val title: String,
    /** Manual order of lists; lower comes first. */
    val position: Int = 0,
)

/**
 * A task. When [parentId] is set the task is a subtask of another task.
 * A subtask has its own [deadline]; without one it inherits the deadline of its parent.
 */
data class Task(
    val id: Long,
    val listId: Long,
    val title: String,
    val deadline: LocalDateTime? = null,
    /** Manual order inside the list (or inside the parent); lower comes first. */
    val position: Int = 0,
    val done: Boolean = false,
    val parentId: Long? = null,
    val completedAt: LocalDateTime? = null,
    /** A repeating ("permanent") task: when it is done it comes back by itself. Only main tasks repeat; their subtasks come back with them. */
    val repeat: Repeat? = null,
)

enum class RepeatUnit { DAY, WEEK, MONTH }

/** "Every [every] [unit]". A task that was completed comes back as not done at the start of the day that many units later. */
data class Repeat(val every: Int, val unit: RepeatUnit) {
    init { require(every >= 1) { "a task cannot repeat every $every" } }

    /** "Every day", "Every 2 weeks", "Every month" … */
    fun label(): String {
        val word = when (unit) { RepeatUnit.DAY -> "day"; RepeatUnit.WEEK -> "week"; RepeatUnit.MONTH -> "month" }
        return if (every == 1) "Every $word" else "Every $every ${word}s"
    }

    /** When a task completed at [completedAt] comes back: midnight, [every] [unit]s after the day it was completed. */
    fun comesBackAfter(completedAt: LocalDateTime): LocalDateTime {
        val day = completedAt.toLocalDate()
        val back = when (unit) {
            RepeatUnit.DAY -> day.plusDays(every.toLong())
            RepeatUnit.WEEK -> day.plusWeeks(every.toLong())
            RepeatUnit.MONTH -> day.plusMonths(every.toLong())
        }
        return back.atStartOfDay()
    }
}
