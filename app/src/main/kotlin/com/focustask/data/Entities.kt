package com.focustask.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The list this block shows. A list can have several blocks. */
    val listId: Long,
    val startMin: Int,
    val endMin: Int,
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int,
)

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val position: Int = 0,
    /** Index into BLOCK_COLORS. Every block of the list is drawn in this color. */
    val color: Int = 0,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val title: String,
    val deadline: Long? = null,
    val position: Int = 0,
    val done: Boolean = false,
    val parentId: Long? = null,
    val completedAt: Long? = null,
    /** Repeating task: comes back every [repeatEvery] [repeatUnit] ("DAY", "WEEK" or "MONTH"). Both are null for a normal task. */
    val repeatEvery: Int? = null,
    val repeatUnit: String? = null,
)

/** A change that only applies to one date. [skipped] removes the block for that day. */
@Entity(tableName = "overrides", primaryKeys = ["blockId", "epochDay"])
data class OverrideEntity(
    val blockId: Long,
    val epochDay: Long,
    val startMin: Int,
    val endMin: Int,
    val skipped: Boolean = false,
)

/**
 * The weekly plan can give a block different times on some weekdays.
 * Without a row the block uses its own start and end.
 */
@Entity(tableName = "weekday_times", primaryKeys = ["blockId", "dayOfWeek"])
data class WeekdayTimeEntity(
    val blockId: Long,
    /** 1 = Monday ... 7 = Sunday. */
    val dayOfWeek: Int,
    val startMin: Int,
    val endMin: Int,
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM blocks") fun blocks(): Flow<List<BlockEntity>>
    @Query("SELECT * FROM lists ORDER BY position, id") fun lists(): Flow<List<ListEntity>>
    @Query("SELECT * FROM tasks") fun tasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM overrides WHERE epochDay >= :fromDay") fun overrides(fromDay: Long): Flow<List<OverrideEntity>>

    @Query("SELECT * FROM weekday_times") fun weekdayTimes(): Flow<List<WeekdayTimeEntity>>
    @Query("SELECT * FROM weekday_times") suspend fun weekdayTimesNow(): List<WeekdayTimeEntity>
    @Query("SELECT * FROM blocks") suspend fun blocksNow(): List<BlockEntity>
    @Query("SELECT * FROM overrides WHERE epochDay = :epochDay") suspend fun overridesOn(epochDay: Long): List<OverrideEntity>
    @Query("DELETE FROM overrides WHERE epochDay = :epochDay") suspend fun deleteOverridesOn(epochDay: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWeekdayTime(w: WeekdayTimeEntity)
    @Query("DELETE FROM weekday_times WHERE blockId = :blockId AND dayOfWeek = :dow")
    suspend fun deleteWeekdayTime(blockId: Long, dow: Int)
    @Query("DELETE FROM weekday_times WHERE blockId = :blockId") suspend fun deleteWeekdayTimesOf(blockId: Long)

    @Query("SELECT COUNT(*) FROM lists") suspend fun listCount(): Int

    // everything, for the JSON copy and for putting a copy back
    @Query("SELECT * FROM lists ORDER BY position, id") suspend fun listsNow(): List<ListEntity>
    @Query("SELECT * FROM tasks ORDER BY listId, position, id") suspend fun tasksNow(): List<TaskEntity>
    @Query("SELECT * FROM overrides") suspend fun overridesNow(): List<OverrideEntity>
    @Query("SELECT COUNT(*) FROM tasks") suspend fun taskCount(): Int
    @Query("SELECT COUNT(*) FROM blocks") suspend fun blockCount(): Int
    @Query("DELETE FROM lists") suspend fun clearLists()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM blocks") suspend fun clearBlocks()
    @Query("DELETE FROM overrides") suspend fun clearOverrides()
    @Query("DELETE FROM weekday_times") suspend fun clearWeekdayTimes()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLists(l: List<ListEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTasks(t: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBlocks(b: List<BlockEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOverrides(o: List<OverrideEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putWeekdayTimes(w: List<WeekdayTimeEntity>)

    @Query("SELECT * FROM blocks WHERE id = :id") suspend fun block(id: Long): BlockEntity?
    @Insert suspend fun insertBlock(b: BlockEntity): Long
    @Update suspend fun updateBlock(b: BlockEntity)
    @Query("DELETE FROM blocks WHERE id = :id") suspend fun deleteBlock(id: Long)

    @Insert suspend fun insertList(l: ListEntity): Long
    @Update suspend fun updateList(l: ListEntity)
    @Query("DELETE FROM lists WHERE id = :id") suspend fun deleteList(id: Long)
    @Query("SELECT * FROM lists WHERE id = :id") suspend fun list(id: Long): ListEntity?
    @Query("SELECT color FROM lists") suspend fun colorsInUse(): List<Int>
    @Query("SELECT * FROM blocks WHERE listId = :listId") suspend fun blocksOfList(listId: Long): List<BlockEntity>

    @Insert suspend fun insertTask(t: TaskEntity): Long
    @Update suspend fun updateTask(t: TaskEntity)
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: Long): TaskEntity?
    @Query("SELECT MAX(completedAt) FROM tasks WHERE done = 1") suspend fun lastCompletedAt(): Long?
    @Query("SELECT * FROM tasks WHERE parentId = :parentId") suspend fun children(parentId: Long): List<TaskEntity>
    @Query("DELETE FROM tasks WHERE id = :id OR parentId = :id") suspend fun deleteTaskTree(id: Long)
    @Query("DELETE FROM tasks WHERE listId = :listId") suspend fun deleteTasksOfList(listId: Long)
    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE listId = :listId AND parentId IS NULL")
    suspend fun maxTopPosition(listId: Long): Int
    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE parentId = :parentId")
    suspend fun maxChildPosition(parentId: Long): Int
    @Query("SELECT COALESCE(MAX(position), -1) FROM lists") suspend fun maxListPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putOverride(o: OverrideEntity)
    @Query("DELETE FROM overrides WHERE blockId = :blockId AND epochDay = :epochDay")
    suspend fun deleteOverride(blockId: Long, epochDay: Long)
    @Query("DELETE FROM overrides WHERE blockId = :blockId") suspend fun deleteOverridesOf(blockId: Long)
    @Query("DELETE FROM overrides WHERE epochDay < :beforeDay") suspend fun purgeOverrides(beforeDay: Long)
}

@Database(
    entities = [BlockEntity::class, ListEntity::class, TaskEntity::class, OverrideEntity::class, WeekdayTimeEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FocusDao
}

/** Version 2: per-weekday times for the weekly plan. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weekday_times` (`blockId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                "`startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, PRIMARY KEY(`blockId`, `dayOfWeek`))",
        )
    }
}

/**
 * Version 3: a list can have many blocks. The block now points at its list (blocks.listId) and
 * the color moved from the block to the list, so all blocks of a list share one color.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `lists_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `color` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `lists_new` (`id`, `title`, `position`, `color`) " +
                "SELECT l.`id`, l.`title`, l.`position`, " +
                "COALESCE((SELECT b.`color` FROM `blocks` b WHERE b.`id` = l.`blockId`), l.`position` % 6) FROM `lists` l",
        )
        db.execSQL(
            "CREATE TABLE `blocks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, " +
                "`startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL)",
        )
        // A block without a list has nothing to show, so it is dropped.
        db.execSQL(
            "INSERT INTO `blocks_new` (`id`, `listId`, `startMin`, `endMin`, `daysMask`) " +
                "SELECT b.`id`, (SELECT MIN(l.`id`) FROM `lists` l WHERE l.`blockId` = b.`id`), b.`startMin`, b.`endMin`, b.`daysMask` " +
                "FROM `blocks` b WHERE EXISTS (SELECT 1 FROM `lists` l WHERE l.`blockId` = b.`id`)",
        )
        db.execSQL("DELETE FROM `overrides` WHERE `blockId` NOT IN (SELECT `id` FROM `blocks_new`)")
        db.execSQL("DELETE FROM `weekday_times` WHERE `blockId` NOT IN (SELECT `id` FROM `blocks_new`)")
        db.execSQL("DROP TABLE `blocks`")
        db.execSQL("DROP TABLE `lists`")
        db.execSQL("ALTER TABLE `blocks_new` RENAME TO `blocks`")
        db.execSQL("ALTER TABLE `lists_new` RENAME TO `lists`")
    }
}

/** Version 4: repeating tasks (tasks.repeatEvery, tasks.repeatUnit). Existing tasks do not repeat. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatEvery` INTEGER")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatUnit` TEXT")
    }
}
