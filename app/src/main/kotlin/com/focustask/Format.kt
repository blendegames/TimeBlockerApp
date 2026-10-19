package com.focustask

import com.focustask.core.TimerBar
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private val md = DateTimeFormatter.ofPattern("MMM d")

    fun time(t: LocalDateTime): String = t.format(hm)
    fun time(t: java.time.LocalTime): String = t.format(hm)
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

    /** "today, 12:00", "tomorrow, 12:00", "Tue 17:00", "Oct 24, 17:00". */
    fun deadline(d: LocalDateTime, now: LocalDateTime): String {
        val days = Duration.between(now.toLocalDate().atStartOfDay(), d.toLocalDate().atStartOfDay()).toDays()
        return when {
            days == 0L -> "today, ${time(d)}"
            days == 1L -> "tomorrow, ${time(d)}"
            days in 2..6 -> "${dayShort(d.toLocalDate())} ${time(d)}"
            else -> "${monthDay(d.toLocalDate())}, ${time(d)}"
        }
    }

    /** Short version for list rows: "Today 12:00", "Tue 17:00". */
    fun deadlineShort(d: LocalDateTime, now: LocalDateTime): String {
        val days = Duration.between(now.toLocalDate().atStartOfDay(), d.toLocalDate().atStartOfDay()).toDays()
        return when {
            days == 0L -> "Today ${time(d)}"
            days == 1L -> "Tomorrow"
            days in 2..6 -> dayShort(d.toLocalDate())
            else -> monthDay(d.toLocalDate())
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

    fun wasDue(deadline: LocalDateTime, now: LocalDateTime): String {
        val days = Duration.between(deadline.toLocalDate().atStartOfDay(), now.toLocalDate().atStartOfDay()).toDays()
        return when {
            days == 0L -> "Was due today, ${time(deadline)}"
            days in 1..6 -> "Was due ${dayShort(deadline.toLocalDate())} ${time(deadline)}"
            else -> "Was due ${monthDay(deadline.toLocalDate())}"
        }
    }

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
