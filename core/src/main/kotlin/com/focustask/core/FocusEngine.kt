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
    /** The main task of [previous] / [next] when they are steps of another task than the one on screen. */
    val previousParent: Task? = null,
    val nextParent: Task? = null,
    /** The deadline that counts for [next]: its own, or its main task's. */
    val nextDeadline: LocalDateTime? = null,
    /** True when [previous] is a task that was skipped (it is still open) rather than one that was finished. */
    val previousSkipped: Boolean = false,
    /** One further back and one further ahead: they come round on the wheel while it turns. */
    val previous2: Task? = null,
    val previous2Skipped: Boolean = false,
    val next2: Task? = null,
    val previous2Parent: Task? = null,
    val next2Parent: Task? = null,
) {
    /** "Main task › step" when the step belongs to another task, else just the title. */
    val previousLabel: String? get() = previous?.let { t -> previousParent?.let { "${it.title} › ${t.title}" } ?: t.title }
    val nextLabel: String? get() = next?.let { t -> nextParent?.let { "${it.title} › ${t.title}" } ?: t.title }
    val previous2Label: String? get() = previous2?.let { t -> previous2Parent?.let { "${it.title} › ${t.title}" } ?: t.title }
    val next2Label: String? get() = next2?.let { t -> next2Parent?.let { "${it.title} › ${t.title}" } ?: t.title }
}

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
        skipped: List<Long> = emptyList(),
        /** When each of [skipped] was skipped: the latest skip shows as the task before the current one. */
        skipTimes: Map<Long, LocalDateTime> = emptyMap(),
    ): FocusState {
        val instances = Schedule.instances(now, blocksFor)
        val longBreak = BreakPlanner.current(now, instances, settings, userBreak)
        val active = if (longBreak != null) null else instances.firstOrNull { it.contains(now) }
        val next = instances.filter { it.start > now }.minByOrNull { it.start }
        val ranking = TaskSelector.rank(now, blocksFor(now.toLocalDate()), lists, tasks, skipped)
        val carousel = if (ranking.isEmpty()) null else buildCarousel(now, ranking, tasks, skipped, skipTimes)

        val mode = when {
            longBreak != null -> Mode.BREAK
            carousel != null -> Mode.FOCUS
            else -> Mode.ALL_DONE
        }
        val lastEnded = instances.filter { it.end <= now }.maxByOrNull { it.end }
        // what comes after the break: the block that is still running then, else the next one
        val resumes = longBreak?.let { b -> instances.firstOrNull { it.contains(b.until) } } ?: next
        val bar = when {
            longBreak != null -> waitBar(BarKind.LONG_BREAK, now, longBreak.from, longBreak.until, resumes?.block?.name)
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

    fun buildCarousel(
        now: LocalDateTime,
        ranking: List<CurrentTask>,
        tasks: List<Task>,
        skipped: List<Long> = emptyList(),
        skipTimes: Map<Long, LocalDateTime> = emptyMap(),
    ): Carousel {
        val current = ranking.first()
        val byId = tasks.associateBy { it.id }
        val parent = current.task.parentId?.let { byId[it] }
        val today = now.toLocalDate()

        // What lies behind the task on screen, newest first: tasks finished today and tasks skipped today that are
        // still open. When a main task is completed its steps get the same time, so the main task wins a tie.
        class Back(val task: Task, val at: LocalDateTime, val skipped: Boolean)
        val doneToday = tasks.filter {
            it.done && it.completedAt != null && it.completedAt.toLocalDate() == today && it.completedAt <= now
        }
        val skippedBacks = skipped.mapNotNull { id ->
            val t = byId[id] ?: return@mapNotNull null
            val at = skipTimes[id] ?: return@mapNotNull null
            if (t.done || id == current.task.id || at.toLocalDate() != today) null else Back(t, at, true)
        }
        val history = (doneToday.map { Back(it, it.completedAt!!, false) } + skippedBacks).sortedWith(
            compareByDescending<Back> { it.at }.thenByDescending { if (it.task.parentId == null) 1 else 0 }.thenBy { it.task.position },
        )

        var stepIndex = 0
        var stepCount = 0
        var previous: Back? = history.firstOrNull()
        if (parent != null) {
            val siblings = tasks.filter { it.parentId == parent.id }.sortedWith(compareBy({ it.position }, { it.id }))
            // the step before this one (unless something was skipped after it); when this is the first step,
            // whatever was finished or skipped last
            val step = siblings.filter { it.done }
                .maxWithOrNull(compareBy<Task>({ it.completedAt ?: LocalDateTime.MIN }, { -it.position }))
            val skip = skippedBacks.maxByOrNull { it.at }
            if (step != null) {
                previous = if (skip != null && skip.at > (step.completedAt ?: LocalDateTime.MIN)) skip
                else Back(step, step.completedAt ?: now, false)
            }
            stepCount = siblings.size
            stepIndex = (siblings.count { it.done } + 1).coerceAtMost(stepCount)
        }
        val previous2 = previous?.let { p -> history.firstOrNull { it.task.id != p.task.id } }
        // What "Done" brings next: the next one in line, even when it is a step of another task.
        val upNext = ranking.getOrNull(1)
        val next = upNext?.task

        val onScreen = (parent ?: current.task).id
        fun foreignParent(t: Task?): Task? = t?.parentId?.takeIf { it != onScreen }?.let { byId[it] }

        val listId = (parent ?: current.task).listId
        val top = tasks.filter { it.listId == listId && it.parentId == null }
        val listCount = top.size
        val listIndex = (top.count { it.done } + 1).coerceAtMost(listCount.coerceAtLeast(1))
        return Carousel(
            previous?.task, current, next, parent, stepIndex, stepCount, listIndex, listCount,
            previousParent = foreignParent(previous?.task), nextParent = foreignParent(next), nextDeadline = upNext?.deadline,
            previousSkipped = previous?.skipped == true,
            previous2 = previous2?.task, previous2Skipped = previous2?.skipped == true, next2 = ranking.getOrNull(2)?.task,
            previous2Parent = foreignParent(previous2?.task), next2Parent = foreignParent(ranking.getOrNull(2)?.task),
        )
    }
}
