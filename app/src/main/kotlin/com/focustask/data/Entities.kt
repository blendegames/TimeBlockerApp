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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocks")
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMin: Int,
    val endMin: Int,
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    val daysMask: Int,
    val color: Int = 0,
)

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val blockId: Long? = null,
    val position: Int = 0,
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

@Dao
interface FocusDao {
    @Query("SELECT * FROM blocks") fun blocks(): Flow<List<BlockEntity>>
    @Query("SELECT * FROM lists ORDER BY position, id") fun lists(): Flow<List<ListEntity>>
    @Query("SELECT * FROM tasks") fun tasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM overrides WHERE epochDay >= :fromDay") fun overrides(fromDay: Long): Flow<List<OverrideEntity>>

    @Query("SELECT COUNT(*) FROM lists") suspend fun listCount(): Int

    @Query("SELECT * FROM blocks WHERE id = :id") suspend fun block(id: Long): BlockEntity?
    @Insert suspend fun insertBlock(b: BlockEntity): Long
    @Update suspend fun updateBlock(b: BlockEntity)
    @Query("DELETE FROM blocks WHERE id = :id") suspend fun deleteBlock(id: Long)

    @Insert suspend fun insertList(l: ListEntity): Long
    @Update suspend fun updateList(l: ListEntity)
    @Query("DELETE FROM lists WHERE id = :id") suspend fun deleteList(id: Long)
    @Query("SELECT * FROM lists WHERE id = :id") suspend fun list(id: Long): ListEntity?
    @Query("SELECT * FROM lists WHERE blockId = :blockId") suspend fun listsOfBlock(blockId: Long): List<ListEntity>
    @Query("UPDATE lists SET blockId = NULL WHERE blockId = :blockId") suspend fun detachBlock(blockId: Long)

    @Insert suspend fun insertTask(t: TaskEntity): Long
    @Update suspend fun updateTask(t: TaskEntity)
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: Long): TaskEntity?
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
    entities = [BlockEntity::class, ListEntity::class, TaskEntity::class, OverrideEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FocusDao
}
