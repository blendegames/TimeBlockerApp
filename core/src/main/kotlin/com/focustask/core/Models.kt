package com.focustask.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A named window in the day, e.g. "Deep work" 09:00-11:00.
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

/** A todo list with its own title. [blockId] = null means "anytime" (no time block). */
data class TodoList(
    val id: Long,
    val title: String,
    val blockId: Long? = null,
    /** Manual order of lists; lower comes first. */
    val position: Int = 0,
)

data class Task(
    val id: Long,
    val listId: Long,
    val title: String,
    val deadline: LocalDateTime? = null,
    /** Manual order inside the list; lower comes first. */
    val position: Int = 0,
    val done: Boolean = false,
)
