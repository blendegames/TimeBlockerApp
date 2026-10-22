package com.focustask.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.focustask.core.FocusEngine
import com.focustask.core.FocusState
import com.focustask.core.Repeat
import com.focustask.core.RepeatUnit
import com.focustask.core.Task
import com.focustask.core.TimeBlock
import com.focustask.core.TodoList
import com.focustask.core.UserBreak
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

val BLOCK_COLORS = listOf(0xFF2F5D50, 0xFF8A5A00, 0xFF3B4F9A, 0xFF8A3FA0, 0xFFB4532A, 0xFF1F7A8C)

/** Index of the color that the fewest of [used] have, so lists look different from each other. */
fun leastUsedColor(used: Collection<Int>): Int =
    BLOCK_COLORS.indices.minByOrNull { c -> used.count { it == c } } ?: 0

fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
fun maskToDays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

data class DayChange(val startMin: Int, val endMin: Int, val skipped: Boolean)

/** Everything the screens and the engine need, in plain Kotlin objects. */
data class AppData(
    val blocks: List<TimeBlock>,
    /** Color index per LIST: every block of a list is drawn in its list's color. */
    val colors: Map<Long, Int>,
    val lists: List<TodoList>,
    val tasks: List<Task>,
    val changes: Map<Pair<Long, LocalDate>, DayChange>,
    val settings: Settings,
    /** Weekly plan: times that differ from the block's own on one weekday. */
    val dayTimes: Map<Pair<Long, DayOfWeek>, Pair<Int, Int>> = emptyMap(),
) {
    private val blocksById = blocks.associateBy { it.id }

    /** [b] with the times the weekly plan gives it on [dow]. */
    fun blockOn(b: TimeBlock, dow: DayOfWeek): TimeBlock =
        dayTimes[b.id to dow]?.let { (s, e) -> b.copy(start = minutesToTime(s), end = minutesToTime(e)) } ?: b

    /** The weekly plan for [date]'s weekday, ignoring everything that was changed for that one day. */
    fun planFor(date: LocalDate): List<TimeBlock> =
        blocks.filter { date.dayOfWeek in it.days }.map { blockOn(it, date.dayOfWeek) }

    /**
     * The blocks as they apply on [date]: the weekly plan with one-day changes applied.
     * A block changed on that day counts as being on that day even if the plan says otherwise.
     */
    fun blocksFor(date: LocalDate): List<TimeBlock> = blocks.mapNotNull { b0 ->
        val b = blockOn(b0, date.dayOfWeek)
        val c = changes[b.id to date] ?: return@mapNotNull b
        if (c.skipped) null
        else b.copy(start = minutesToTime(c.startMin), end = minutesToTime(c.endMin), days = b.days + date.dayOfWeek)
    }

    /** True when [date] no longer looks like the weekly plan (a day edit, or the plan changed after the day began). */
    fun differsFromPlan(date: LocalDate): Boolean {
        fun key(l: List<TimeBlock>) = l.associate { it.id to (it.start to it.end) }
        return key(planFor(date)) != key(blocksFor(date).filter { date.dayOfWeek in it.days })
    }

    fun changeFor(blockId: Long, date: LocalDate): DayChange? = changes[blockId to date]
    fun block(id: Long): TimeBlock? = blocksById[id]
    fun listOfBlock(blockId: Long): TodoList? = blocksById[blockId]?.listId?.let { id -> lists.firstOrNull { it.id == id } }
    fun blocksOfList(listId: Long): List<TimeBlock> = blocks.filter { it.listId == listId }
    /** Lists without any time block: shown when nothing else is due. */
    fun anytimeLists(): List<TodoList> = lists.filter { l -> blocks.none { it.listId == l.id } }
    fun colorOfListIndex(listId: Long): Int = colors[listId] ?: 0
    /** The color a new list starts with: the one the fewest lists use. */
    fun suggestedColor(): Int = leastUsedColor(colors.values)
    fun colorOf(blockId: Long): Long = blocksById[blockId]?.listId?.let { colorOfListColor(it) } ?: BLOCK_COLORS[0]
    private fun colorOfListColor(listId: Long): Long = BLOCK_COLORS[colorOfListIndex(listId) % BLOCK_COLORS.size]
    /** The color picked for the list, whether or not it has a block yet. */
    fun colorOfListAlways(listId: Long): Long = colorOfListColor(listId)
    fun openCount(listId: Long): Int = tasks.count { it.listId == listId && !it.done && it.parentId == null }

    /** What the Today tab, the widget and the alerts show at [now]. */
    fun focusAt(now: LocalDateTime): FocusState = FocusEngine.build(
        now, ::blocksFor, lists, tasks, settings.cycle, settings.userBreak,
        skipped = settings.skippedOn(now.toLocalDate()), skipTimes = settings.skipTimesOn(now.toLocalDate()),
    )
}

/** The repeat rule stored on a task row, or null for a normal task. */
fun TaskEntity.toRepeat(): Repeat? {
    val every = repeatEvery ?: return null
    val unit = repeatUnit?.let { u -> runCatching { RepeatUnit.valueOf(u) }.getOrNull() } ?: return null
    return if (every >= 1) Repeat(every, unit) else null
}

class Repository(context: Context, scope: CoroutineScope) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "focustask.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
    private val dao = db.dao()
    val settingsStore = SettingsStore(context)

    val data: StateFlow<AppData?> = combine(
        combine(dao.blocks(), dao.weekdayTimes()) { b, w -> b to w },
        dao.lists(),
        dao.tasks(),
        dao.overrides(LocalDate.now().minusDays(1).stored()),
        settingsStore.flow,
    ) { (blocks, weekdayTimes), lists, tasks, overrides, settings ->
        val titles = lists.associate { it.id to it.title }
        val coreBlocks = blocks.map {
            TimeBlock(
                id = it.id,
                name = titles[it.listId] ?: "Block",
                listId = it.listId,
                start = minutesToTime(it.startMin),
                end = minutesToTime(it.endMin),
                days = maskToDays(it.daysMask),
            )
        }
        AppData(
            blocks = coreBlocks,
            colors = lists.associate { it.id to it.color },
            lists = lists.map { TodoList(it.id, it.title, it.position) },
            tasks = tasks.map {
                Task(
                    id = it.id, listId = it.listId, title = it.title,
                    deadline = it.deadline?.fromStored(), position = it.position, done = it.done,
                    parentId = it.parentId, completedAt = it.completedAt?.fromStored(),
                    repeat = it.toRepeat(),
                )
            },
            changes = overrides.associate {
                (it.blockId to LocalDate.ofEpochDay(it.epochDay)) to DayChange(it.startMin, it.endMin, it.skipped)
            },
            settings = settings,
            dayTimes = weekdayTimes.associate { (it.blockId to DayOfWeek.of(it.dayOfWeek)) to (it.startMin to it.endMin) },
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun awaitData(): AppData = data.filterNotNull().first()

    // ---- the JSON copy ----

    /** Everything the user has set up, read in one go so the copy is consistent. */
    suspend fun snapshot(): DataSnapshot {
        val settings = settingsStore.flow.first()
        return db.withTransaction {
            DataSnapshot(dao.listsNow(), dao.tasksNow(), dao.blocksNow(), dao.weekdayTimesNow(), dao.overridesNow(), settings)
        }
    }

    suspend fun hasNoUserData(): Boolean = dao.listCount() == 0 && dao.taskCount() == 0 && dao.blockCount() == 0

    /** Replaces everything in the app with [s]. */
    suspend fun restore(s: DataSnapshot) {
        db.withTransaction {
            dao.clearOverrides(); dao.clearWeekdayTimes(); dao.clearTasks(); dao.clearBlocks(); dao.clearLists()
            dao.putLists(s.lists); dao.putBlocks(s.blocks); dao.putTasks(s.tasks)
            dao.putWeekdayTimes(s.weekdayTimes); dao.putOverrides(s.overrides)
        }
        settingsStore.replaceAll(s.settings)
    }

    suspend fun purgeOldChanges() = dao.purgeOverrides(LocalDate.now().minusDays(2).stored())

    // ---- tasks ----

    suspend fun addTask(listId: Long, title: String, deadline: LocalDateTime?, parentId: Long? = null, repeat: Repeat? = null): Long {
        val t = title.trim()
        if (t.isEmpty()) return -1
        val pos = if (parentId == null) dao.maxTopPosition(listId) + 1 else dao.maxChildPosition(parentId) + 1
        // only main tasks repeat: a step comes back together with its main task
        val r = repeat.takeIf { parentId == null }
        return dao.insertTask(
            TaskEntity(
                listId = listId, title = t, deadline = deadline?.toStored(), position = pos, parentId = parentId,
                repeatEvery = r?.every, repeatUnit = r?.unit?.name,
            ),
        )
    }

    /** Makes a main task repeat (or stops it with null). A task that is done now comes back counting from the day it was completed. */
    suspend fun setRepeat(id: Long, repeat: Repeat?) {
        val t = dao.task(id) ?: return
        if (t.parentId != null) return
        dao.updateTask(t.copy(repeatEvery = repeat?.every, repeatUnit = repeat?.unit?.name))
    }

    /**
     * Brings back every repeating task that was completed and whose time has come: it becomes not done, and so do all its
     * subtasks. Returns how many tasks came back. Safe to call as often as wanted.
     */
    suspend fun resetRepeating(now: LocalDateTime = LocalDateTime.now()): Int = db.withTransaction {
        val all = dao.tasksNow()
        val due = all.filter { t ->
            val r = t.toRepeat()
            t.parentId == null && t.done && r != null && t.completedAt?.let { r.comesBackAfter(it.fromStored()) <= now } == true
        }
        due.forEach { t ->
            dao.updateTask(t.copy(done = false, completedAt = null))
            all.filter { it.parentId == t.id && (it.done || it.completedAt != null) }
                .forEach { dao.updateTask(it.copy(done = false, completedAt = null)) }
        }
        due.size
    }

    suspend fun renameTask(id: Long, title: String) {
        val t = dao.task(id) ?: return
        if (title.isNotBlank()) dao.updateTask(t.copy(title = title.trim()))
    }

    suspend fun setDeadline(id: Long, deadline: LocalDateTime?) {
        val t = dao.task(id) ?: return
        dao.updateTask(t.copy(deadline = deadline?.toStored()))
    }

    /**
     * The time to give a skip or a completion: now, or one second after the last of them when that is now too.
     * Both are kept to the second, and the wheel's "turn back" goes to whichever happened last, so a skip and a tick
     * within the same second must never tie.
     */
    private suspend fun nextEventTime(now: LocalDateTime): LocalDateTime {
        val t = now.withNano(0)
        val lastDone = dao.lastCompletedAt()?.fromStored()
        val lastSkip = settingsStore.flow.first().skipTimesOn(t.toLocalDate()).values.maxOrNull()?.withNano(0)
        val last = listOfNotNull(lastDone, lastSkip).maxOrNull() ?: return t
        return if (last >= t && java.time.Duration.between(t, last).seconds < 5) last.plusSeconds(1) else t
    }

    suspend fun complete(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val t = dao.task(id) ?: return
        val stamp = nextEventTime(now).toStored()
        dao.updateTask(t.copy(done = true, completedAt = stamp))
        // Finishing a main task finishes its steps too.
        dao.children(id).filter { !it.done }.forEach { dao.updateTask(it.copy(done = true, completedAt = stamp)) }
    }

    /** "Skip": puts the task behind all the others for today. It comes back when nothing else is left. */
    suspend fun skipTask(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val today = now.toLocalDate()
        val at = nextEventTime(now)
        val s = settingsStore.flow.first()
        settingsStore.setSkipped(today, s.skippedOn(today).filter { it != id } + id, s.skipTimesOn(today) + (id to at))
    }

    /** Takes a skipped task back: it is in line again where it was before. */
    suspend fun unskip(id: Long, today: LocalDate = LocalDate.now()) {
        val s = settingsStore.flow.first()
        settingsStore.setSkipped(today, s.skippedOn(today).filter { it != id }, s.skipTimesOn(today) - id)
    }

    suspend fun reopen(id: Long) {
        val t = dao.task(id) ?: return
        dao.updateTask(t.copy(done = false, completedAt = null))
        // A main task that is reopened gets back the steps that were finished together with it (by its own tick).
        if (t.parentId == null && t.completedAt != null) {
            dao.children(id).filter { it.done && it.completedAt == t.completedAt }
                .forEach { dao.updateTask(it.copy(done = false, completedAt = null)) }
        }
        // A reopened step brings its main task back to "open".
        t.parentId?.let { p -> dao.task(p)?.takeIf { it.done }?.let { dao.updateTask(it.copy(done = false, completedAt = null)) } }
    }

    suspend fun deleteTask(id: Long) = dao.deleteTaskTree(id)

    /**
     * Saves a new manual order: [orderedIds] are tasks that share a list (or share a main task),
     * first to last.
     */
    suspend fun reorderTasks(orderedIds: List<Long>) = db.withTransaction {
        orderedIds.forEachIndexed { index, id ->
            dao.task(id)?.let { e -> if (e.position != index) dao.updateTask(e.copy(position = index)) }
        }
    }

    // ---- lists ----

    /** A new list gets the chosen [color] (an index into BLOCK_COLORS), or else the one the fewest lists use. */
    suspend fun addList(title: String, color: Int? = null): Long {
        val t = title.trim().ifEmpty { "New list" }
        val c = color?.takeIf { it in BLOCK_COLORS.indices } ?: leastUsedColor(dao.colorsInUse())
        return dao.insertList(ListEntity(title = t, position = dao.maxListPosition() + 1, color = c))
    }

    /** Every block of the list is drawn in this color (an index into BLOCK_COLORS). */
    suspend fun setListColor(id: Long, color: Int) {
        val l = dao.list(id) ?: return
        if (color in BLOCK_COLORS.indices && l.color != color) dao.updateList(l.copy(color = color))
    }

    suspend fun renameList(id: Long, title: String) {
        val l = dao.list(id) ?: return
        if (title.isNotBlank()) dao.updateList(l.copy(title = title.trim()))
    }

    suspend fun deleteList(id: Long) {
        val l = dao.list(id) ?: return
        dao.deleteTasksOfList(id)
        dao.deleteList(id)
        db.withTransaction {
            dao.blocksOfList(id).forEach { dao.deleteOverridesOf(it.id); dao.deleteWeekdayTimesOf(it.id); dao.deleteBlock(it.id) }
        }
    }

    // ---- blocks ----

    private data class Slot(val present: Boolean, val start: Int, val end: Int)

    /** How every block stands on [date], read straight from the database. */
    private suspend fun layoutOn(date: LocalDate): Map<Long, Slot> {
        val dow = date.dayOfWeek.value
        val times = dao.weekdayTimesNow().associateBy { it.blockId to it.dayOfWeek }
        val changes = dao.overridesOn(date.stored()).associateBy { it.blockId }
        return dao.blocksNow().associate { b ->
            val o = changes[b.id]
            val w = times[b.id to dow]
            b.id to if (o != null) Slot(!o.skipped, o.startMin, o.endMin)
            else Slot((b.daysMask and (1 shl (dow - 1))) != 0, w?.startMin ?: b.startMin, w?.endMin ?: b.endMin)
        }
    }

    /**
     * The weekly plan fills in a day before it starts. Once today has begun, a change to the plan
     * must not touch it: whatever the plan edit would have changed today is pinned as a one-day change.
     * Tomorrow and later days follow the new plan.
     */
    private suspend fun keepTodayAsItIs(showNewBlockToday: Boolean = false, edit: suspend () -> Unit) =
        db.withTransaction { pinToday(showNewBlockToday, edit) }

    private suspend fun pinToday(showNewBlockToday: Boolean, edit: suspend () -> Unit) {
        val today = LocalDate.now()
        val before = layoutOn(today)
        edit()
        for ((id, a) in layoutOn(today)) {
            val b = before[id]
            if (b == null) {
                if (a.present && !showNewBlockToday) dao.putOverride(OverrideEntity(id, today.stored(), a.start, a.end, true))
                continue
            }
            val same = b.present == a.present && (!b.present || (b.start == a.start && b.end == a.end))
            if (!same) dao.putOverride(OverrideEntity(id, today.stored(), b.start, b.end, !b.present))
        }
    }

    /**
     * Saves a block of the weekly plan. The plan applies from tomorrow; today keeps its layout.
     * A new block added from the Day tab ([showToday]) also appears today.
     * [resetDayTimes] throws away per-weekday times (when the user typed new times).
     * [color] is the color of the LIST, so it changes every block of that list.
     */
    suspend fun saveBlock(
        id: Long?, listId: Long, start: LocalTime, end: LocalTime, days: Set<DayOfWeek>, color: Int,
        showToday: Boolean = true, resetDayTimes: Boolean = true,
    ): Long {
        var blockId = id ?: 0L
        keepTodayAsItIs(showNewBlockToday = showToday && id == null) {
            val entity = BlockEntity(
                id = id ?: 0, listId = listId, startMin = start.toMinutes(), endMin = end.toMinutes(),
                daysMask = daysToMask(days),
            )
            blockId = if (id == null) dao.insertBlock(entity) else { dao.updateBlock(entity); id }
            if (resetDayTimes) dao.deleteWeekdayTimesOf(blockId)
            else DayOfWeek.values().filter { it !in days }.forEach { dao.deleteWeekdayTime(blockId, it.value) }
            // The color belongs to the list, so all of its blocks match.
            dao.list(listId)?.let { l -> if (l.color != color) dao.updateList(l.copy(color = color)) }
        }
        return blockId
    }

    /** Changes a block's time on one weekday of the plan (the Week tab). Today is not touched. */
    suspend fun moveBlock(id: Long, day: DayOfWeek, start: LocalTime, end: LocalTime) {
        val b = dao.block(id) ?: return
        keepTodayAsItIs {
            if (start.toMinutes() == b.startMin && end.toMinutes() == b.endMin) dao.deleteWeekdayTime(id, day.value)
            else dao.putWeekdayTime(WeekdayTimeEntity(id, day.value, start.toMinutes(), end.toMinutes()))
        }
    }

    /**
     * Takes the block off ONE weekday of the plan (the Week tab); its other days stay. Today is not touched.
     * When that was its last day, the block is deleted.
     */
    suspend fun removeFromWeekday(id: Long, day: DayOfWeek) {
        val b = dao.block(id) ?: return
        val mask = b.daysMask and (1 shl (day.value - 1)).inv()
        if (mask == 0) { deleteBlock(id); return }
        keepTodayAsItIs {
            dao.updateBlock(b.copy(daysMask = mask))
            dao.deleteWeekdayTime(id, day.value)
        }
    }

    /** Removes the block from the plan, from every day. */
    suspend fun deleteBlock(id: Long) = db.withTransaction {
        dao.deleteOverridesOf(id)
        dao.deleteWeekdayTimesOf(id)
        dao.deleteBlock(id)
    }

    // ---- changes for one day ----

    suspend fun changeForDay(blockId: Long, date: LocalDate, start: LocalTime, end: LocalTime) =
        dao.putOverride(OverrideEntity(blockId, date.stored(), start.toMinutes(), end.toMinutes(), false))

    suspend fun skipForDay(blockId: Long, date: LocalDate, start: LocalTime, end: LocalTime) =
        dao.putOverride(OverrideEntity(blockId, date.stored(), start.toMinutes(), end.toMinutes(), true))

    suspend fun restoreDay(blockId: Long, date: LocalDate) = dao.deleteOverride(blockId, date.stored())

    /** Puts the whole day back to the weekly plan. */
    suspend fun restoreWholeDay(date: LocalDate) = dao.deleteOverridesOn(date.stored())

    /**
     * "Keep this layout every <weekday>": the plan for this weekday becomes what [date] looks like now,
     * so next week's same weekday starts with this layout.
     */
    suspend fun makeDayThePlan(date: LocalDate) = db.withTransaction {
        val layout = layoutOn(date)
        val dow = date.dayOfWeek.value
        val bit = 1 shl (dow - 1)
        for (b in dao.blocksNow()) {
            val slot = layout[b.id] ?: continue
            var mask = b.daysMask
            if (slot.present) {
                mask = mask or bit
                if (slot.start == b.startMin && slot.end == b.endMin) dao.deleteWeekdayTime(b.id, dow)
                else dao.putWeekdayTime(WeekdayTimeEntity(b.id, dow, slot.start, slot.end))
            } else {
                mask = mask and bit.inv()
                dao.deleteWeekdayTime(b.id, dow)
            }
            if (mask != b.daysMask) dao.updateBlock(b.copy(daysMask = mask))
        }
        dao.deleteOverridesOn(date.stored())
    }

    // ---- breaks ----

    suspend fun startUserBreak(from: LocalDateTime, until: LocalDateTime) =
        settingsStore.setUserBreak(UserBreak(from, until))

}
