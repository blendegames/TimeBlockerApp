package com.focustask.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** One concrete occurrence of a repeating [TimeBlock] on the calendar. */
data class BlockInstance(
    val block: TimeBlock,
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    fun contains(t: LocalDateTime): Boolean = t >= start && t < end
    val length: Duration get() = Duration.between(start, end)
}

object Schedule {
    /**
     * Occurrences from [daysBack] days ago to [daysAhead] days from now, sorted by start.
     * [blocksFor] returns the blocks as they apply on a given date (with one-day changes).
     */
    fun instances(
        now: LocalDateTime,
        blocksFor: (LocalDate) -> List<TimeBlock>,
        daysBack: Long = 1,
        daysAhead: Long = 8,
    ): List<BlockInstance> {
        val today = now.toLocalDate()
        val out = ArrayList<BlockInstance>()
        var d = today.minusDays(daysBack)
        val last = today.plusDays(daysAhead)
        while (!d.isAfter(last)) {
            for (b in blocksFor(d)) {
                if (d.dayOfWeek !in b.days) continue
                val start = d.atTime(b.start)
                val end = if (b.crossesMidnight) d.plusDays(1).atTime(b.end) else d.atTime(b.end)
                out += BlockInstance(b, start, end)
            }
            d = d.plusDays(1)
        }
        return out.sortedBy { it.start }
    }
}

/** User-chosen timings. */
data class CycleSettings(
    val workMin: Int = 25,
    val shortBreakMin: Int = 5,
    /** How long a long break lasts. 0 means no long breaks. */
    val longBreakMin: Int = 15,
    /** Size of the big "block ends in" countdown. */
    val countdownMin: Int = 10,
    /** A block this long (in minutes) or longer is followed by a long break. */
    val longBreakAfterMin: Int = 120,
) {
    init {
        require(workMin > 0 && shortBreakMin >= 0 && longBreakMin >= 0 && countdownMin >= 0 && longBreakAfterMin >= 15)
    }

    val cycleMin: Int get() = workMin + shortBreakMin
}

enum class CyclePhase { WORK, SHORT_BREAK }

/** Where we are inside a running block. */
data class BlockClock(
    val phase: CyclePhase,
    val phaseLeft: Duration,
    /** When the current phase ends. */
    val phaseEndsAt: LocalDateTime,
    /** 1-based number of the current cycle. */
    val cycleNumber: Int,
    val cycleCount: Int,
    val blockLeft: Duration,
    /** 0.0 at the start of the block, 1.0 at its end. */
    val sunFraction: Double,
    /** True during the last minutes of the block: the UI makes the timer big. */
    val countdownActive: Boolean,
)

object CycleClock {
    const val STRIP_SEGMENTS = 12

    fun at(inst: BlockInstance, now: LocalDateTime, s: CycleSettings): BlockClock {
        val totalSec = inst.length.seconds
        val elapsed = Duration.between(inst.start, now).seconds.coerceIn(0, totalSec)
        val workSec = s.workMin * 60L
        val cycleSec = s.cycleMin * 60L
        val idx = elapsed / cycleSec
        val within = elapsed % cycleSec
        val blockLeft = Duration.ofSeconds(totalSec - elapsed)
        val phase: CyclePhase
        var left: Long
        var endsAt: LocalDateTime
        if (within < workSec) {
            phase = CyclePhase.WORK
            left = workSec - within
            endsAt = inst.start.plusSeconds(idx * cycleSec + workSec)
        } else {
            phase = CyclePhase.SHORT_BREAK
            left = cycleSec - within
            endsAt = inst.start.plusSeconds((idx + 1) * cycleSec)
        }
        if (left > blockLeft.seconds) {
            left = blockLeft.seconds
            endsAt = inst.end
        }
        val count = ((totalSec + cycleSec - 1) / cycleSec).toInt().coerceAtLeast(1)
        return BlockClock(
            phase = phase,
            phaseLeft = Duration.ofSeconds(left),
            phaseEndsAt = endsAt,
            cycleNumber = (idx + 1).toInt().coerceAtMost(count),
            cycleCount = count,
            blockLeft = blockLeft,
            sunFraction = if (totalSec == 0L) 0.0 else elapsed.toDouble() / totalSec,
            countdownActive = s.countdownMin > 0 && blockLeft.seconds <= s.countdownMin * 60L,
        )
    }

    /** Indexes (0..12) of the large bubbles of the time strip: both ends plus every cycle mark. */
    fun largeBubbles(blockLength: Duration, s: CycleSettings): Set<Int> {
        val out = linkedSetOf(0, STRIP_SEGMENTS)
        val totalMin = blockLength.seconds / 60.0
        if (totalMin <= 0 || s.cycleMin <= 0) return out
        var k = 1
        while (k * s.cycleMin < totalMin) {
            val i = Math.round(k * s.cycleMin / totalMin * STRIP_SEGMENTS).toInt()
            if (i in 1 until STRIP_SEGMENTS) out += i
            k++
        }
        return out
    }
}

/** A long break: mandatory rest in which nothing can be edited. */
data class LongBreak(
    val from: LocalDateTime,
    val until: LocalDateTime,
    /** True when the user chose "Break until X starts" after finishing early. */
    val userInitiated: Boolean,
)

data class UserBreak(val from: LocalDateTime, val until: LocalDateTime)

object BreakPlanner {
    /**
     * The times when a long break starts: when a block ends, if that block is at least
     * [CycleSettings.longBreakAfterMin] minutes long. Shorter blocks are followed by no long break.
     */
    fun starts(instances: List<BlockInstance>, s: CycleSettings): List<LocalDateTime> {
        if (s.longBreakMin <= 0) return emptyList()
        return instances.filter { it.length.toMinutes() >= s.longBreakAfterMin }.map { it.end }.sorted()
    }

    /** The long breaks, soonest first. */
    fun windows(instances: List<BlockInstance>, s: CycleSettings): List<LongBreak> =
        starts(instances, s).map { LongBreak(it, it.plusMinutes(s.longBreakMin.toLong()), false) }

    /**
     * - A long break follows every block of at least [CycleSettings.longBreakAfterMin] minutes (see [starts]).
     * - After finishing early the user can break until the next block starts; that break
     *   lasts exactly as long as the time left, however short.
     */
    fun current(
        now: LocalDateTime,
        instances: List<BlockInstance>,
        s: CycleSettings,
        userBreak: UserBreak?,
    ): LongBreak? {
        if (userBreak != null && now >= userBreak.from && now < userBreak.until) {
            return LongBreak(userBreak.from, userBreak.until, true)
        }
        return windows(instances, s).firstOrNull { now >= it.from && now < it.until }
    }
}

/**
 * [CYCLE_CHIME]: back to work, at every large bubble and when a break is over. [PHASE_CUE]: time for a break.
 * [BLOCK_STARTING] / [BLOCK_ENDING]: the moment a block starts or ends. Its vibration builds up to that moment
 * (see [Vibrations.leadMs]), and the sound plays right on it.
 */
enum class AlertKind { CYCLE_CHIME, PHASE_CUE, BLOCK_STARTING, BLOCK_ENDING }

data class AlertEvent(val at: LocalDateTime, val kind: AlertKind)

object AlertPlanner {
    /** Alerts after [now], earliest first. */
    fun upcoming(
        now: LocalDateTime,
        instances: List<BlockInstance>,
        s: CycleSettings,
        userBreak: UserBreak?,
    ): List<AlertEvent> {
        val maxInstances = 3
        val out = ArrayList<AlertEvent>()
        for (inst in instances.filter { it.end > now }.take(maxInstances)) {
            var k = 0
            while (true) {
                val workEnd = inst.start.plusMinutes(k.toLong() * s.cycleMin + s.workMin)
                val boundary = inst.start.plusMinutes((k + 1L) * s.cycleMin)
                if (s.shortBreakMin > 0 && workEnd < inst.end) out += AlertEvent(workEnd, AlertKind.PHASE_CUE)
                if (boundary < inst.end) out += AlertEvent(boundary, AlertKind.CYCLE_CHIME) else break
                k++
            }
            out += AlertEvent(inst.start, AlertKind.BLOCK_STARTING)
            out += AlertEvent(inst.end, AlertKind.BLOCK_ENDING)
        }
        // a long break: a break cue when it starts, and back to work when it is over
        for (w in BreakPlanner.windows(instances, s).filter { it.until > now }.take(maxInstances)) {
            out += AlertEvent(w.from, AlertKind.PHASE_CUE)
            out += AlertEvent(w.until, AlertKind.CYCLE_CHIME)
        }
        if (userBreak != null) out += AlertEvent(userBreak.until, AlertKind.CYCLE_CHIME)
        return out.filter { it.at > now }.distinct().sortedWith(compareBy({ it.at }, { it.kind }))
    }
}
