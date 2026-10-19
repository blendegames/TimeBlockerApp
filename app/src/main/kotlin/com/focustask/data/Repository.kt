package com.focustask.data

import android.content.Context
import androidx.room.Room
import com.focustask.core.Task
import com.focustask.core.TimeBlock
import com.focustask.core.TodoList
import com.focustask.core.UserBreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

val BLOCK_COLORS = listOf(0xFF2F5D50, 0xFF8A5A00, 0xFF3B4F9A, 0xFF8A3FA0, 0xFFB4532A, 0xFF1F7A8C)

fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
fun maskToDays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

data class DayChange(val startMin: Int, val endMin: Int, val skipped: Boolean)

/** Everything the screens and the engine need, in plain Kotlin objects. */
data class AppData(
    val blocks: List<TimeBlock>,
    val colors: Map<Long, Int>,
    val lists: List<TodoList>,
    val tasks: List<Task>,
    val changes: Map<Pair<Long, LocalDate>, DayChange>,
    val settings: Settings,
) {
    private val blocksById = blocks.associateBy { it.id }

    /** The blocks as they apply on [date], with one-day changes applied. */
    fun blocksFor(date: LocalDate): List<TimeBlock> = blocks.mapNotNull { b ->
        val c = changes[b.id to date] ?: return@mapNotNull b
        if (c.skipped) null else b.copy(start = minutesToTime(c.startMin), end = minutesToTime(c.endMin))
    }

    fun changeFor(blockId: Long, date: LocalDate): DayChange? = changes[blockId to date]
    fun block(id: Long): TimeBlock? = blocksById[id]
    fun listOfBlock(blockId: Long): TodoList? = lists.firstOrNull { it.blockId == blockId }
    fun colorOf(blockId: Long): Long = BLOCK_COLORS[(colors[blockId] ?: 0) % BLOCK_COLORS.size]
    fun colorOfList(listId: Long): Long =
        lists.firstOrNull { it.id == listId }?.blockId?.let { colorOf(it) } ?: 0xFF6B7570
    fun openCount(listId: Long): Int = tasks.count { it.listId == listId && !it.done && it.parentId == null }
}

class Repository(context: Context, scope: CoroutineScope) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "focustask.db").build()
    private val dao = db.dao()
    val settingsStore = SettingsStore(context)

    val data: StateFlow<AppData?> = combine(
        dao.blocks(),
        dao.lists(),
        dao.tasks(),
        dao.overrides(LocalDate.now().minusDays(1).stored()),
        settingsStore.flow,
    ) { blocks, lists, tasks, overrides, settings ->
        val titles = lists.filter { it.blockId != null }.associate { it.blockId!! to it.title }
        val coreBlocks = blocks.map {
            TimeBlock(
                id = it.id,
                name = titles[it.id] ?: "Block",
                start = minutesToTime(it.startMin),
                end = minutesToTime(it.endMin),
                days = maskToDays(it.daysMask),
            )
        }
        AppData(
            blocks = coreBlocks,
            colors = blocks.associate { it.id to it.color },
            lists = lists.map { TodoList(it.id, it.title, it.blockId, it.position) },
            tasks = tasks.map {
                Task(
                    id = it.id, listId = it.listId, title = it.title,
                    deadline = it.deadline?.fromStored(), position = it.position, done = it.done,
                    parentId = it.parentId, completedAt = it.completedAt?.fromStored(),
                )
            },
            changes = overrides.associate {
                (it.blockId to LocalDate.ofEpochDay(it.epochDay)) to DayChange(it.startMin, it.endMin, it.skipped)
            },
            settings = settings,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun awaitData(): AppData = data.filterNotNull().first()

    // ---- first run ----

    suspend fun seedIfNeeded() {
        if (settingsStore.flow.first().seeded || dao.listCount() > 0) {
            settingsStore.setSeeded()
            return
        }
        val now = LocalDateTime.now()
        fun at(days: Long, h: Int, m: Int = 0) = now.toLocalDate().plusDays(days).atTime(h, m).toStored()
        val weekdays = daysToMask(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        val work = dao.insertBlock(BlockEntity(startMin = 9 * 60, endMin = 11 * 60, daysMask = weekdays, color = 0))
        val admin = dao.insertBlock(BlockEntity(startMin = 11 * 60 + 30, endMin = 12 * 60 + 30,
            daysMask = daysToMask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), color = 1))
        val home = dao.insertBlock(BlockEntity(startMin = 18 * 60, endMin = 20 * 60, daysMask = 127, color = 2))
        val wl = dao.insertList(ListEntity(title = "Work", blockId = work, position = 0))
        val al = dao.insertList(ListEntity(title = "Admin", blockId = admin, position = 1))
        val hl = dao.insertList(ListEntity(title = "Home", blockId = home, position = 2))
        dao.insertList(ListEntity(title = "Someday", blockId = null, position = 3))
        val budget = dao.insertTask(TaskEntity(listId = wl, title = "Write the Q3 budget summary", deadline = at(0, 12), position = 0))
        dao.insertTask(TaskEntity(listId = wl, title = "Pull the Q3 numbers", position = 0, parentId = budget, deadline = at(0, 9, 30)))
        dao.insertTask(TaskEntity(listId = wl, title = "Draft the summary", position = 1, parentId = budget, deadline = at(0, 11)))
        dao.insertTask(TaskEntity(listId = wl, title = "Send it to Dana", position = 2, parentId = budget, deadline = at(0, 12)))
        dao.insertTask(TaskEntity(listId = wl, title = "Review pull request #42", deadline = at(1, 17), position = 1))
        dao.insertTask(TaskEntity(listId = wl, title = "Email Dana about the contract", position = 2))
        dao.insertTask(TaskEntity(listId = al, title = "Submit the expense report", position = 0, deadline = at(2, 12)))
        dao.insertTask(TaskEntity(listId = al, title = "Book the dentist", position = 1))
        dao.insertTask(TaskEntity(listId = hl, title = "Pay the electricity bill", position = 0, deadline = at(3, 19)))
        dao.insertTask(TaskEntity(listId = hl, title = "Water the plants", position = 1))
        settingsStore.setSeeded()
    }

    suspend fun purgeOldChanges() = dao.purgeOverrides(LocalDate.now().minusDays(2).stored())

    // ---- tasks ----

    suspend fun addTask(listId: Long, title: String, deadline: LocalDateTime?, parentId: Long? = null): Long {
        val t = title.trim()
        if (t.isEmpty()) return -1
        val pos = if (parentId == null) dao.maxTopPosition(listId) + 1 else dao.maxChildPosition(parentId) + 1
        return dao.insertTask(TaskEntity(listId = listId, title = t, deadline = deadline?.toStored(), position = pos, parentId = parentId))
    }

    suspend fun renameTask(id: Long, title: String) {
        val t = dao.task(id) ?: return
        if (title.isNotBlank()) dao.updateTask(t.copy(title = title.trim()))
    }

    suspend fun setDeadline(id: Long, deadline: LocalDateTime?) {
        val t = dao.task(id) ?: return
        dao.updateTask(t.copy(deadline = deadline?.toStored()))
    }

    suspend fun complete(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val t = dao.task(id) ?: return
        val stamp = now.toStored()
        dao.updateTask(t.copy(done = true, completedAt = stamp))
        // Finishing a main task finishes its steps too.
        dao.children(id).filter { !it.done }.forEach { dao.updateTask(it.copy(done = true, completedAt = stamp)) }
    }

    suspend fun reopen(id: Long) {
        val t = dao.task(id) ?: return
        dao.updateTask(t.copy(done = false, completedAt = null))
        // A reopened step brings its main task back to "open".
        t.parentId?.let { p -> dao.task(p)?.takeIf { it.done }?.let { dao.updateTask(it.copy(done = false, completedAt = null)) } }
    }

    suspend fun deleteTask(id: Long) = dao.deleteTaskTree(id)

    /** Swap a task with its neighbour in manual order. [up] moves it earlier. */
    suspend fun move(id: Long, up: Boolean, siblings: List<Task>) {
        val ordered = siblings.sortedWith(compareBy({ it.position }, { it.id }))
        val i = ordered.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in ordered.indices) return
        val list = ordered.toMutableList()
        list[i] = ordered[j].also { list[j] = ordered[i] }
        list.forEachIndexed { index, task ->
            dao.task(task.id)?.let { e -> if (e.position != index) dao.updateTask(e.copy(position = index)) }
        }
    }

    // ---- lists ----

    suspend fun addList(title: String): Long {
        val t = title.trim().ifEmpty { "New list" }
        return dao.insertList(ListEntity(title = t, position = dao.maxListPosition() + 1))
    }

    suspend fun renameList(id: Long, title: String) {
        val l = dao.list(id) ?: return
        if (title.isNotBlank()) dao.updateList(l.copy(title = title.trim()))
    }

    suspend fun deleteList(id: Long) {
        val l = dao.list(id) ?: return
        dao.deleteTasksOfList(id)
        dao.deleteList(id)
        l.blockId?.let { dao.deleteOverridesOf(it); dao.deleteBlock(it) }
    }

    // ---- blocks ----

    suspend fun saveBlock(
        id: Long?, listId: Long, start: LocalTime, end: LocalTime, days: Set<DayOfWeek>, color: Int,
    ): Long {
        val entity = BlockEntity(
            id = id ?: 0, startMin = start.toMinutes(), endMin = end.toMinutes(),
            daysMask = daysToMask(days), color = color,
        )
        val blockId = if (id == null) dao.insertBlock(entity) else { dao.updateBlock(entity); id }
        // A block shows exactly one list: this one.
        dao.listsOfBlock(blockId).filter { it.id != listId }.forEach { dao.updateList(it.copy(blockId = null)) }
        // (The block picker only offers lists that are free or already in this block.)
        dao.list(listId)?.let { l -> dao.updateList(l.copy(blockId = blockId)) }
        return blockId
    }

    /** Changes the time of a block for every week (the Week tab). */
    suspend fun moveBlock(id: Long, start: LocalTime, end: LocalTime) {
        val b = dao.block(id) ?: return
        dao.updateBlock(b.copy(startMin = start.toMinutes(), endMin = end.toMinutes()))
    }

    suspend fun deleteBlock(id: Long) {
        dao.detachBlock(id)
        dao.deleteOverridesOf(id)
        dao.deleteBlock(id)
    }

    // ---- changes for one day ----

    suspend fun changeForDay(blockId: Long, date: LocalDate, start: LocalTime, end: LocalTime) =
        dao.putOverride(OverrideEntity(blockId, date.stored(), start.toMinutes(), end.toMinutes(), false))

    suspend fun skipForDay(blockId: Long, date: LocalDate, start: LocalTime, end: LocalTime) =
        dao.putOverride(OverrideEntity(blockId, date.stored(), start.toMinutes(), end.toMinutes(), true))

    suspend fun restoreDay(blockId: Long, date: LocalDate) = dao.deleteOverride(blockId, date.stored())

    // ---- breaks ----

    suspend fun startUserBreak(from: LocalDateTime, until: LocalDateTime) =
        settingsStore.setUserBreak(UserBreak(from, until))

    suspend fun clearUserBreak() = settingsStore.setUserBreak(null)
}
