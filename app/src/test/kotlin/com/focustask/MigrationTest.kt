package com.focustask

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focustask.data.AppDatabase
import com.focustask.data.BLOCK_COLORS
import com.focustask.data.MIGRATION_1_2
import com.focustask.data.MIGRATION_2_3
import com.focustask.data.MIGRATION_3_4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Existing installs keep their blocks, lists and colors when the app is updated to "many blocks per list". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    @Test fun versionTwoDataSurvivesTheUpgrade() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val path = ctx.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        // a database exactly as version 2 of the app wrote it
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE `blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `color` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE `lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `blockId` INTEGER, `position` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, `title` TEXT NOT NULL, `deadline` INTEGER, `position` INTEGER NOT NULL, `done` INTEGER NOT NULL, `parentId` INTEGER, `completedAt` INTEGER)")
            db.execSQL("CREATE TABLE `overrides` (`blockId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, PRIMARY KEY(`blockId`, `epochDay`))")
            db.execSQL("CREATE TABLE `weekday_times` (`blockId` INTEGER NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, PRIMARY KEY(`blockId`, `dayOfWeek`))")
            db.execSQL("INSERT INTO blocks VALUES (1, 540, 660, 31, 2)")      // Work: color 2
            db.execSQL("INSERT INTO blocks VALUES (2, 1080, 1200, 127, 4)")   // Home: color 4
            db.execSQL("INSERT INTO blocks VALUES (3, 600, 660, 1, 1)")       // a block no list shows
            db.execSQL("INSERT INTO lists VALUES (10, 'Work', 1, 0)")
            db.execSQL("INSERT INTO lists VALUES (20, 'Home', 2, 1)")
            db.execSQL("INSERT INTO lists VALUES (30, 'Someday', NULL, 2)")
            db.execSQL("INSERT INTO tasks VALUES (1, 10, 'Write', NULL, 0, 0, NULL, NULL)")
            db.execSQL("INSERT INTO overrides VALUES (1, 100, 600, 700, 0)")
            db.execSQL("INSERT INTO overrides VALUES (3, 100, 600, 700, 0)")
            db.execSQL("INSERT INTO weekday_times VALUES (1, 2, 600, 700)")
            db.version = 2
        }
        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            val dao = room.dao()
            runBlocking {
                val blocks = dao.blocksNow().sortedBy { it.id }
                assertEquals("the block without a list is dropped", listOf(1L, 2L), blocks.map { it.id })
                assertEquals(10L, blocks[0].listId)
                assertEquals(20L, blocks[1].listId)
                assertEquals(540, blocks[0].startMin)
                assertEquals(31, blocks[0].daysMask)
                assertEquals("color moved to the list", 2, dao.list(10)!!.color)
                assertEquals(4, dao.list(20)!!.color)
                assertNotNull(dao.list(30))
                assertEquals("Work", dao.list(10)!!.title)
                assertEquals(1, dao.overridesOn(100).size)
                assertEquals(1, dao.weekdayTimesNow().size)
                assertNull(dao.task(99))
                assertEquals(1L, dao.task(1)!!.id)
                assertNull("old tasks do not repeat", dao.task(1)!!.repeatEvery)
                assertNull(dao.task(1)!!.repeatUnit)
                dao.updateTask(dao.task(1)!!.copy(repeatEvery = 3, repeatUnit = "DAY"))
                assertEquals(3, dao.task(1)!!.repeatEvery)
                assertEquals(BLOCK_COLORS.size, 6)
            }
        } finally {
            room.close()
        }
    }
}
