package com.focustask

import androidx.test.core.app.ApplicationProvider
import com.focustask.core.CycleSettings
import com.focustask.core.UserBreak
import com.focustask.data.AlertMode
import com.focustask.data.BlockEntity
import com.focustask.data.DATA_SCHEMA
import com.focustask.data.DataFiles
import com.focustask.data.DataFolder
import com.focustask.data.DataFormatException
import com.focustask.data.DataMirror
import com.focustask.data.DataSnapshot
import com.focustask.data.FILE_BLOCKS
import com.focustask.data.FILE_CHANGES
import com.focustask.data.FILE_LISTS
import com.focustask.data.FILE_MANIFEST
import com.focustask.data.FILE_SETTINGS
import com.focustask.data.FileDataFolder
import com.focustask.data.ListEntity
import com.focustask.data.OverrideEntity
import com.focustask.data.Settings
import com.focustask.data.SoundConfig
import com.focustask.data.SoundSlot
import com.focustask.data.TaskEntity
import com.focustask.data.WeekdayTimeEntity
import com.focustask.data.daysToMask
import com.focustask.data.defaultSounds
import com.focustask.data.toStored
import com.focustask.data.writeText
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The JSON copy of the user's data: writing it, reading it back, and putting it in place of what the app holds. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class DataCopyTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApp>()
    private val repo get() = app.repo

    private val at = LocalDateTime.of(2026, 10, 19, 14, 30)

    /** Every field of every row set to something other than its default, so a field that is forgotten shows up. */
    private fun fullSnapshot(): DataSnapshot {
        val sounds = defaultSounds().toMutableMap()
        sounds[SoundSlot.CUE] = SoundConfig("file:/data/user/0/com.focustask/files/sounds/cue_1.mp3", "My cue", enabled = false)
        return DataSnapshot(
            lists = listOf(
                ListEntity(id = 7, title = "Work \"quoted\" ünïcode ✓", position = 1, color = 4),
                ListEntity(id = 3, title = "Home", position = 0, color = 2),
            ),
            tasks = listOf(
                TaskEntity(id = 10, listId = 7, title = "Main", deadline = at.toStored(), position = 2, done = true, completedAt = at.plusHours(1).toStored(), repeatEvery = 2, repeatUnit = "WEEK"),
                TaskEntity(id = 11, listId = 7, title = "Step B", deadline = null, position = 1, done = false, parentId = 10),
                TaskEntity(id = 12, listId = 7, title = "Step A", deadline = at.plusDays(1).toStored(), position = 0, done = true, parentId = 10, completedAt = at.toStored()),
                TaskEntity(id = 13, listId = 3, title = "Water the plants", position = 0),
            ),
            blocks = listOf(
                BlockEntity(id = 5, listId = 7, startMin = 9 * 60 + 15, endMin = 24 * 60, daysMask = daysToMask(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))),
                BlockEntity(id = 6, listId = 3, startMin = 0, endMin = 30, daysMask = 127),
            ),
            weekdayTimes = listOf(WeekdayTimeEntity(5, DayOfWeek.SUNDAY.value, 10 * 60, 11 * 60 + 45)),
            overrides = listOf(
                OverrideEntity(5, LocalDate.of(2026, 10, 19).toEpochDay(), 13 * 60, 14 * 60, skipped = false),
                OverrideEntity(6, LocalDate.of(2026, 10, 20).toEpochDay(), 8 * 60, 9 * 60, skipped = true),
            ),
            settings = Settings(
                cycle = CycleSettings(workMin = 50, shortBreakMin = 7, longBreakMin = 20, countdownMin = 3, longBreakAfterMin = 90),
                alertMode = AlertMode.VIBRATE, sounds = sounds, maxSoundSec = 17, fade = false, keepScreenOn = true, dimWhenIdle = false, dimPercent = 35, dimAfterSec = 45, timerViewWhenIdle = false, timerViewAfterSec = 120,
                userBreak = UserBreak(at, at.plusHours(2)), lockWarningDismissed = true, seeded = true,
            ),
        )
    }

    private fun read(files: Map<String, String>) = DataFiles.decode { files[it] }

    private fun same(a: DataSnapshot, b: DataSnapshot) {
        fun key(l: List<TaskEntity>) = l.sortedBy { it.id }
        assertEquals(a.lists.sortedBy { it.id }, b.lists.sortedBy { it.id })
        assertEquals(key(a.tasks), key(b.tasks))
        assertEquals(a.blocks.sortedBy { it.id }, b.blocks.sortedBy { it.id })
        assertEquals(a.weekdayTimes.sortedWith(compareBy({ it.blockId }, { it.dayOfWeek })), b.weekdayTimes.sortedWith(compareBy({ it.blockId }, { it.dayOfWeek })))
        assertEquals(a.overrides.sortedWith(compareBy({ it.blockId }, { it.epochDay })), b.overrides.sortedWith(compareBy({ it.blockId }, { it.epochDay })))
        assertEquals(a.settings.cycle, b.settings.cycle)
        assertEquals(a.settings.alertMode, b.settings.alertMode)
        assertEquals(a.settings.sounds, b.settings.sounds)
        assertEquals(a.settings.maxSoundSec, b.settings.maxSoundSec)
        assertEquals(a.settings.fade, b.settings.fade)
        assertEquals(a.settings.userBreak, b.settings.userBreak)
        assertEquals(a.settings.lockWarningDismissed, b.settings.lockWarningDismissed)
    }

    @Test fun everythingSurvivesAWriteAndARead() {
        val snap = fullSnapshot()
        val files = DataFiles.encode(snap, "1.2.3")
        assertEquals(setOf(FILE_MANIFEST, FILE_SETTINGS, FILE_LISTS, FILE_BLOCKS, FILE_CHANGES), files.keys)
        val back = read(files)
        // the sound the person added is saved under a name that does not depend on this phone's folders
        val expected = snap.copy(settings = snap.settings.copy(sounds = snap.settings.sounds + (SoundSlot.CUE to SoundConfig("file:sounds/cue_1.mp3", "My cue", false))))
        same(expected, back)
        // and the manifest says what is in it
        val info = DataFiles.info(files[FILE_MANIFEST])
        assertEquals(2, info.lists); assertEquals(4, info.tasks); assertEquals(2, info.blocks); assertEquals("1.2.3", info.appVersion)
    }

    @Test fun theFilesAreReadableAndKeepTasksInsideTheirList() {
        val files = DataFiles.encode(fullSnapshot(), "1")
        val lists = org.json.JSONObject(files.getValue(FILE_LISTS)).getJSONArray("lists")
        // lists in their order: Home (position 0) first
        assertEquals("Home", lists.getJSONObject(0).getString("title"))
        val work = lists.getJSONObject(1)
        val main = work.getJSONArray("tasks").getJSONObject(0)
        assertEquals("Main", main.getString("title"))
        assertEquals("2026-10-19T14:30", main.getString("deadline"))
        // subtasks sit under their main task, in their own order
        assertEquals(listOf("Step A", "Step B"), (0 until 2).map { main.getJSONArray("subtasks").getJSONObject(it).getString("title") })
        val blocks = org.json.JSONObject(files.getValue(FILE_BLOCKS)).getJSONArray("blocks")
        assertEquals("24:00", blocks.getJSONObject(0).getString("end"))
        assertEquals("10:00", blocks.getJSONObject(0).getJSONObject("dayTimes").getJSONObject("SUNDAY").getString("start"))
    }

    @Test fun aCopyWithUnknownOrMissingFieldsStillLoads() {
        val files = DataFiles.encode(fullSnapshot(), "1").toMutableMap()
        // a newer app added a field, an older one did not know about a setting
        files[FILE_LISTS] = org.json.JSONObject(files.getValue(FILE_LISTS)).apply {
            getJSONArray("lists").getJSONObject(0).put("emoji", "🏠")
        }.toString()
        files[FILE_SETTINGS] = """{"schema":1,"cycle":{"workMin":40}}"""
        val back = read(files)
        assertEquals(2, back.lists.size)
        assertEquals(40, back.settings.cycle.workMin)
        assertEquals(CycleSettings().shortBreakMin, back.settings.cycle.shortBreakMin)
        assertEquals(120, back.settings.cycle.longBreakAfterMin)
        assertEquals(AlertMode.SOUND, back.settings.alertMode)
        assertFalse("an older copy keeps the screen off, like before", back.settings.keepScreenOn)
        assertTrue(back.settings.dimWhenIdle); assertEquals(10, back.settings.dimPercent)
        assertTrue(back.settings.timerViewWhenIdle); assertEquals(60, back.settings.timerViewAfterSec); assertEquals(30, back.settings.dimAfterSec)
        files.remove(FILE_BLOCKS); files.remove(FILE_CHANGES)
        assertTrue(read(files).blocks.isEmpty())
    }

    private fun failsWith(part: String, files: Map<String, String>) {
        try { read(files); fail("should have been refused: $part") } catch (e: DataFormatException) { assertTrue(e.message, e.message!!.contains(part, ignoreCase = true)) }
    }

    @Test fun aDamagedUnfinishedOrNewerCopyIsRefused() {
        val good = DataFiles.encode(fullSnapshot(), "1")
        failsWith("no FocusTask copy", good - FILE_MANIFEST)
        failsWith("missing", good - FILE_LISTS)
        failsWith("damaged", good + (FILE_LISTS to "{ not json"))
        failsWith("newer", good + (FILE_BLOCKS to """{"schema":${DATA_SCHEMA + 1},"blocks":[]}"""))
        failsWith("not a FocusTask copy", good + (FILE_MANIFEST to """{"format":"something else"}"""))
        // the manifest was written last, so a list file that holds fewer lists than it says means the writing was cut short
        val cut = org.json.JSONObject(good.getValue(FILE_LISTS)).apply { put("lists", org.json.JSONArray()) }.toString()
        failsWith("unfinished", good + (FILE_LISTS to cut))
        failsWith("twice", good + (FILE_LISTS to good.getValue(FILE_LISTS).replace("\"id\": 3", "\"id\": 7")))
    }

    @Test fun aBlockOfAMissingListOrAChangeOfAMissingBlockIsLeftOut() {
        val files = DataFiles.encode(fullSnapshot(), "1").toMutableMap()
        files[FILE_BLOCKS] = org.json.JSONObject(files.getValue(FILE_BLOCKS)).apply {
            getJSONArray("blocks").put(org.json.JSONObject().put("id", 99).put("listId", 12345).put("start", "08:00").put("end", "09:00").put("days", org.json.JSONArray(listOf("MONDAY"))))
        }.toString()
        val back = read(files)
        assertEquals(setOf(5L, 6L), back.blocks.map { it.id }.toSet())
    }

    // ---------------------------------------------------------------- with the real database

    private fun tempFolder(): FileDataFolder = FileDataFolder(File(app.cacheDir, "copy-${System.nanoTime()}"))
    private fun mirror(inside: DataFolder) = DataMirror(app, repo, app.scope, "test", inside)

    @Test fun whatIsInTheAppComesBackFromItsCopyAfterTheDataIsLost() = runBlocking {
        TestData.ensure(app)
        val extra = repo.addList("Kept ${System.nanoTime()}", 5)
        val main = repo.addTask(extra, "A main task", at, null)
        repo.addTask(extra, "A step", null, main)
        repo.complete(main, at)
        val block = repo.saveBlock(null, extra, LocalTime.of(6, 5), LocalTime.of(7, 55), setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), 5)
        repo.moveBlock(block, DayOfWeek.FRIDAY, LocalTime.of(6, 30), LocalTime.of(8, 0))
        repo.settingsStore.setCycle(work = 33, short = 4)
        repo.settingsStore.setMode(AlertMode.SILENT)
        repo.settingsStore.setMaxSound(12)
        val before = repo.snapshot()
        assertTrue(before.blocks.any { it.id == block })

        val inside = tempFolder()
        val m = mirror(inside)
        m.saveNow(force = true)
        try {
            // the app loses everything
            repo.restore(before.copy(lists = emptyList(), tasks = emptyList(), blocks = emptyList(), weekdayTimes = emptyList(), overrides = emptyList()))
            assertTrue(repo.hasNoUserData())
            assertTrue("the copy brings it back", m.restoreIfEmpty())
            same(before, repo.snapshot())
            // the second time there is nothing to do: the app has data again
            assertFalse(m.restoreIfEmpty())
        } finally {
            repo.restore(before)
        }
    }

    @Test fun aCopyOfNothingIsNotRestoredOverAnEmptyApp() = runBlocking {
        TestData.ensure(app)
        val before = repo.snapshot()
        val inside = tempFolder()
        val empty = before.copy(lists = emptyList(), tasks = emptyList(), blocks = emptyList(), weekdayTimes = emptyList(), overrides = emptyList())
        DataFiles.encode(empty, "t").forEach { (n, t) -> inside.writeText(n, t) }
        try {
            repo.restore(empty)
            assertFalse(mirror(inside).restoreIfEmpty())
            assertTrue(repo.hasNoUserData())
        } finally {
            repo.restore(before)
        }
    }

    @Test fun aBrokenCopyLeavesTheAppAsItWas() = runBlocking {
        TestData.ensure(app)
        val before = repo.snapshot()
        val inside = tempFolder()
        val m = mirror(inside)
        m.saveNow(force = true)
        inside.writeText(FILE_LISTS, "{ broken")
        try {
            m.load(inside)
            fail("a broken copy must not load")
        } catch (e: DataFormatException) {
            assertTrue(e.message!!.contains("damaged"))
        }
        same(before, repo.snapshot())
    }

    @Test fun aSoundTheUserAddedComesBackWithTheCopy() = runBlocking {
        TestData.ensure(app)
        val before = repo.snapshot()
        val soundsDir = File(app.filesDir, "sounds").apply { mkdirs() }
        val sound = File(soundsDir, "chime_test_${System.nanoTime()}.mp3").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        repo.settingsStore.setSound(SoundSlot.CHIME, "file:${sound.absolutePath}", "Mine.mp3")
        val withSound = repo.snapshot()
        // the copy lives somewhere else (as it does outside the app) and carries its own sound file
        val folder = tempFolder()
        DataFiles.encode(withSound, "t").forEach { (n, t) -> folder.writeText(n, t) }
        folder.write("sounds/${sound.name}", sound.readBytes())
        sound.delete()
        try {
            val m = mirror(tempFolder())
            m.restore(m.load(folder))
            val restored = repo.snapshot().settings.sounds.getValue(SoundSlot.CHIME)
            assertEquals("Mine.mp3", restored.label)
            assertTrue(restored.source.startsWith("file:") && File(restored.source.removePrefix("file:")).readBytes().toList() == listOf<Byte>(1, 2, 3, 4))

            // when the sound did not come along, the slot falls back to the built-in one instead of pointing at nothing
            File(restored.source.removePrefix("file:")).delete()
            val bare = tempFolder()
            DataFiles.encode(withSound, "t").forEach { (n, t) -> bare.writeText(n, t) }
            m.restore(m.load(bare))
            assertEquals(defaultSounds().getValue(SoundSlot.CHIME), repo.snapshot().settings.sounds.getValue(SoundSlot.CHIME))
        } finally {
            repo.restore(before)
        }
    }

    @Test fun theAppKeepsItsOwnCopyUpToDate() = runBlocking {
        TestData.ensure(app)
        val list = repo.addList("Mirrored ${System.nanoTime()}", 2)
        repo.addTask(list, "Saved on its own", null)
        // the running app saves a moment after every change: wait for it
        val end = System.currentTimeMillis() + 8000
        var found = false
        while (System.currentTimeMillis() < end && !found) {
            Thread.sleep(100)
            found = runCatching { app.mirror.load(FileDataFolder(File(app.filesDir, "data"))).snapshot.tasks.any { it.title == "Saved on its own" } }.getOrDefault(false)
        }
        assertTrue("the app's own copy follows the data", found)
    }

    @Test fun deletingEverythingEmptiesTheAppAndBothCopiesAndNothingBringsItBack() = runBlocking {
        TestData.ensure(app)
        val before = repo.snapshot()
        val soundsDir = File(app.filesDir, "sounds").apply { mkdirs() }
        val sound = File(soundsDir, "gone_${System.nanoTime()}.mp3").apply { writeBytes(byteArrayOf(5)) }
        repo.settingsStore.setSound(SoundSlot.CUE, "file:${sound.absolutePath}", "Mine")
        repo.settingsStore.setCycle(work = 61)
        val inside = tempFolder()
        val m = mirror(inside)
        m.saveNow(force = true)
        assertTrue(m.load(inside).snapshot.let { it.tasks.isNotEmpty() || it.lists.isNotEmpty() })
        try {
            m.deleteEverything()
            assertTrue(repo.hasNoUserData())
            val now = repo.snapshot()
            assertEquals(CycleSettings().workMin, now.settings.cycle.workMin)
            assertEquals(defaultSounds().getValue(SoundSlot.CUE), now.settings.sounds.getValue(SoundSlot.CUE))
            assertFalse("the sound file the person added is gone", sound.exists())
            // the copy was emptied at once, so the app does not "restore" the old data the next time it starts
            assertTrue(m.load(inside).snapshot.isEmpty)
            assertFalse(m.restoreIfEmpty())
            assertTrue(repo.hasNoUserData())
        } finally {
            repo.restore(before)
        }
    }
}
