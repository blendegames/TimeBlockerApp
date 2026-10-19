package com.focustask.core

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

enum class Mode {
    /** There is a task to do. */
    FOCUS,

    /** Nothing left to do right now. */
    ALL_DONE,

    /** A long break: the rest of the app is locked. */
    BREAK,
}

/** Previous, current and next task, like a picker wheel. */
data class Carousel(
    val previous: Task?,
    val current: CurrentTask,
    val next: Task?,
    /** The main task when [current] is a subtask. */
    val parent: Task?,
    /** For subtasks: which step this is and how many there are. */
    val stepIndex: Int,
    val stepCount: Int,
    /** Position among the top-level tasks of the current list. */
    val listIndex: Int,
    val listCount: Int,
)

enum class BarKind { FOCUS_WORK, FOCUS_SHORT_BREAK, WAIT_NEXT, LONG_BREAK, IDLE }

/** Everything the focus timer strip needs. */
data class TimerBar(
    val kind: BarKind,
    /** The big number of the bar, in seconds. */
    val seconds: Long,
    /** 0.0 to 1.0, where the sun sits. */
    val sun: Double,
    /** Which of the 13 bubbles (0..12) are large. */
    val largeBubbles: Set<Int>,
    val startsAt: LocalDateTime?,
    val endsAt: LocalDateTime?,
    /** Name of the block we count to (WAIT_NEXT, LONG_BREAK). */
    val nextName: String? = null,
    /** Minutes left in the block (FOCUS_*). */
    val blockLeftMin: Int? = null,
    val cycleNumber: Int = 0,
    val cycleCount: Int = 0,
    /** When the current work or break phase ends (FOCUS_*). */
    val phaseEndsAt: LocalDateTime? = null,
    /** True in the last minutes of the block: show the big countdown. */
    val ending: Boolean = false,
)

data class FocusState(
    val now: LocalDateTime,
    val mode: Mode,
    val carousel: Carousel?,
    val active: BlockInstance?,
    val next: BlockInstance?,
    val longBreak: LongBreak?,
    val bar: TimerBar,
) {
    val locked: Boolean get() = mode == Mode.BREAK
}

object FocusEngine {

    fun build(
        now: LocalDateTime,
        blocksFor: (LocalDate) -> List<TimeBlock>,
        lists: List<TodoList>,
        tasks: List<Task>,
        settings: CycleSettings = CycleSettings(),
        userBreak: UserBreak? = null,
        overdueInterrupts: Boolean = true,
    ): FocusState {
        val instances = Schedule.instances(now, blocksFor)
        val longBreak = BreakPlanner.current(now, instances, settings.longBreakMin, userBreak)
        val active = if (longBreak != null) null else instances.firstOrNull { it.contains(now) }
        val next = instances.filter { it.start > now }.minByOrNull { it.start }
        val ranking = TaskSelector.rank(now, blocksFor(now.toLocalDate()), lists, tasks, overdueInterrupts)
        val carousel = if (ranking.isEmpty()) null else buildCarousel(now, ranking, tasks)

        val mode = when {
            longBreak != null -> Mode.BREAK
            carousel != null -> Mode.FOCUS
            else -> Mode.ALL_DONE
        }
        val lastEnded = instances.filter { it.end <= now }.maxByOrNull { it.end }
        val bar = when {
            longBreak != null -> waitBar(BarKind.LONG_BREAK, now, longBreak.from, longBreak.until, next?.block?.name)
            mode == Mode.FOCUS && active != null -> focusBar(active, now, settings)
            next != null -> waitBar(BarKind.WAIT_NEXT, now, lastEnded?.end ?: now, next.start, next.block.name)
            else -> TimerBar(BarKind.IDLE, 0, 0.0, setOf(0, CycleClock.STRIP_SEGMENTS), null, null)
        }
        return FocusState(now, mode, carousel, active, next, longBreak, bar)
    }

    private fun focusBar(inst: BlockInstance, now: LocalDateTime, s: CycleSettings): TimerBar {
        val c = CycleClock.at(inst, now, s)
        return TimerBar(
            kind = if (c.phase == CyclePhase.WORK) BarKind.FOCUS_WORK else BarKind.FOCUS_SHORT_BREAK,
            seconds = c.phaseLeft.seconds,
            sun = c.sunFraction,
            largeBubbles = CycleClock.largeBubbles(inst.length, s),
            startsAt = inst.start,
            endsAt = inst.end,
            nextName = inst.block.name,
            blockLeftMin = ((c.blockLeft.seconds + 59) / 60).toInt(),
            cycleNumber = c.cycleNumber,
            cycleCount = c.cycleCount,
            phaseEndsAt = c.phaseEndsAt,
            ending = c.countdownActive,
        )
    }

    private fun waitBar(
        kind: BarKind,
        now: LocalDateTime,
        from: LocalDateTime,
        until: LocalDateTime,
        name: String?,
    ): TimerBar {
        val start = if (from > now) now else from
        val total = Duration.between(start, until).seconds
        val elapsed = Duration.between(start, now).seconds
        return TimerBar(
            kind = kind,
            seconds = Duration.between(now, until).seconds.coerceAtLeast(0),
            sun = if (total <= 0) 0.0 else (elapsed.toDouble() / total).coerceIn(0.0, 1.0),
            largeBubbles = setOf(0, CycleClock.STRIP_SEGMENTS),
            startsAt = start,
            endsAt = until,
            nextName = name,
        )
    }

    fun buildCarousel(now: LocalDateTime, ranking: List<CurrentTask>, tasks: List<Task>): Carousel {
        val current = ranking.first()
        val byId = tasks.associateBy { it.id }
        val parent = current.task.parentId?.let { byId[it] }
        val today = now.toLocalDate()

        val previous: Task?
        val next: Task?
        var stepIndex = 0
        var stepCount = 0
        if (parent != null) {
            val siblings = tasks.filter { it.parentId == parent.id }.sortedWith(compareBy({ it.position }, { it.id }))
            previous = siblings.filter { it.done }
                .maxWithOrNull(compareBy<Task>({ it.completedAt ?: LocalDateTime.MIN }, { -it.position }))
            next = ranking.drop(1).firstOrNull { it.task.parentId == parent.id }?.task
            stepCount = siblings.size
            stepIndex = (siblings.count { it.done } + 1).coerceAtMost(stepCount)
        } else {
            previous = tasks.filter {
                it.done && it.parentId == null && it.completedAt != null &&
                    it.completedAt.toLocalDate() == today && it.completedAt <= now
            }.maxByOrNull { it.completedAt!! }
            next = ranking.getOrNull(1)?.task
        }
        val listId = (parent ?: current.task).listId
        val top = tasks.filter { it.listId == listId && it.parentId == null }
        val listCount = top.size
        val listIndex = (top.count { it.done } + 1).coerceAtMost(listCount.coerceAtLeast(1))
        return Carousel(previous, current, next, parent, stepIndex, stepCount, listIndex, listCount)
    }
}
