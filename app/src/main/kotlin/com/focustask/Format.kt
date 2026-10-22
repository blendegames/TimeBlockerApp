package com.focustask

import com.focustask.core.TimerBar
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("MMM d")

    fun time(t: LocalDateTime): String = t.format(hm)
    fun time(t: LocalTime): String = t.format(hm)
    fun dayShort(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    fun dayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    fun monthDay(d: LocalDate): String = d.format(md)

    /** 13:00 for 13 minutes, 1:18:00 for 78 minutes. */
    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    fun minutesLong(min: Long): String = when {
        min < 60 -> "$min min"
        min % 60 == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    /** "2 hours", "1 hour", "1.5 hours", "45 min". */
    fun hours(min: Int): String = when {
        min < 60 -> "$min min"
        min == 60 -> "1 hour"
        min % 60 == 0 -> "${min / 60} hours"
        else -> "%.1f hours".format(min / 60f)
    }

    /** Short version for list rows: always with the time: "Today 12:00", "Tmrw 17:00", "Tue 17:00", "Oct 24 17:00". */
    fun deadlineShort(d: LocalDateTime, now: LocalDateTime): String {
        val days = Duration.between(now.toLocalDate().atStartOfDay(), d.toLocalDate().atStartOfDay()).toDays()
        return when {
            days == 0L -> "Today ${time(d)}"
            days == 1L -> "Tmrw ${time(d)}"
            days in 2..6 -> "${dayShort(d.toLocalDate())} ${time(d)}"
            else -> "${monthDay(d.toLocalDate())} ${time(d)}"
        }
    }

    /** "2 days late", "35 min late". */
    fun late(deadline: LocalDateTime, now: LocalDateTime): String {
        val d = Duration.between(deadline, now)
        val days = d.toDays()
        return when {
            days >= 1 -> "$days ${if (days == 1L) "day" else "days"} late"
            d.toHours() >= 1 -> "${d.toHours()} h late"
            else -> "${d.toMinutes().coerceAtLeast(1)} min late"
        }
    }

    private fun dayWord(d: LocalDateTime, now: LocalDateTime, long: Boolean): String {
        val days = Duration.between(now.toLocalDate().atStartOfDay(), d.toLocalDate().atStartOfDay()).toDays()
        return when {
            days == 0L -> if (long) "today" else "Today"
            days == 1L -> if (long) "tomorrow" else "Tmrw"
            days == -1L -> "yesterday"
            days in -6..6 -> dayShort(d.toLocalDate())
            else -> monthDay(d.toLocalDate())
        }
    }

    /**
     * A deadline, the short way, for rows, chips and pills: "Today 12:00", "Tmrw 17:00", "Tue 17:00", "Oct 24 17:00";
     * once it has passed: "3 h late". Every screen, the widgets and the notification use this or [dueLong].
     */
    fun due(d: LocalDateTime, now: LocalDateTime): String =
        if (d < now) late(d, now) else deadlineShort(d, now)

    /**
     * A deadline, the long way, for cards: "Due today 12:00", "Due Tue 17:00"; once passed: "Was due today 09:30";
     * "No deadline" when there is none.
     */
    fun dueLong(d: LocalDateTime?, now: LocalDateTime): String =
        if (d == null) NO_DEADLINE else (if (d < now) "Was due " else "Due ") + dayWord(d, now, long = true) + " " + time(d)

    const val NO_DEADLINE = "No deadline"

    /** A text version of the sun strip for the notification. */
    fun stripText(bar: TimerBar): String = buildString {
        val sunIdx = Math.round(bar.sun * 12).toInt().coerceIn(0, 12)
        for (i in 0..12) {
            val large = i in bar.largeBubbles
            append(
                when {
                    i == sunIdx -> "☀"
                    i < sunIdx -> if (large) "●" else "•"
                    else -> if (large) "○" else "·"
                }
            )
            if (i < 12) append(' ')
        }
    }
}

/**
 * The words for the state of the day, shared by Today, the timer, the widgets and the notification, so each says the
 * same thing the same way.
 *
 * Glossary:
 * - "time block" in headings and sentences, "block" in short labels ("Block ends in", "New block").
 * - "Done" is the action and the caps label ("DONE 14:04"); done tasks sit under "Completed".
 * - "Skip" is the action, "SKIPPED" the label.
 * - "Break" is the short break inside a cycle; "Long break" is the one after a long block or before the next one
 *   ("Long break until Work starts"). The other tabs are locked only during a long break.
 * - "Subtask" (never "step"); "Overdue" for the state, "3 h late" for how late.
 */
object Words {
    const val LONG_BREAK = "Long break"
    const val ALL_DONE = "All done"
    const val WAITING = "Waiting for the next time block"
    const val WAITING_SHORT = "Between time blocks"
    const val NOTHING_PLANNED = "No time blocks planned"

    /** "Long break until Work starts" */
    fun longBreakUntil(blockName: String) = "$LONG_BREAK until $blockName starts"

    /** "Work starts 14:00" (or "Work starts Thu 09:00" on another day). */
    fun starts(blockName: String, at: java.time.LocalDateTime, now: java.time.LocalDateTime): String {
        val day = if (at.toLocalDate() == now.toLocalDate()) "" else "${Fmt.dayShort(at.toLocalDate())} "
        return "$blockName starts $day${Fmt.time(at)}"
    }

    /** "Back at 14:00 · Work" */
    fun backAt(until: java.time.LocalDateTime, nextName: String?) =
        "Back at ${Fmt.time(until)}" + (nextName?.let { " · $it" } ?: "")

    /** "Subtask 2 of 3 · Task 1 of 4 in Work" (the list name left out when [list] is null). */
    fun position(c: com.focustask.core.Carousel, list: String?): String {
        val task = "Task ${c.listIndex} of ${c.listCount}" + (list?.let { " in $it" } ?: "")
        return if (c.parent != null) "Subtask ${c.stepIndex} of ${c.stepCount} · $task" else task
    }
}
