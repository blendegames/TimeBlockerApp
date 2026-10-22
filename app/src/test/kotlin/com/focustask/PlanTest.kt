package com.focustask

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.focustask.alerts.Alerts
import com.focustask.core.AlertKind
import com.focustask.core.BarKind
import com.focustask.core.TimerBar
import com.focustask.data.AlertMode
import com.focustask.data.AppData
import com.focustask.data.BLOCK_COLORS
import com.focustask.ui.components.sunAt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The weekly plan / daily layout rule, the smooth sun and the vibration that goes with chimes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class PlanTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApp>()
    private val repo get() = app.repo

    private fun ready(): AppData = TestData.ensure(app)

    /** Room answers on a background thread: wait until [ok] holds for the newest data. */
    private fun await(what: String, ok: (AppData) -> Boolean): AppData {
        val end = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            repo.data.value?.let { if (ok(it)) return it }
            Thread.sleep(40)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private val today get() = LocalDate.now()
    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)

    /** A clean test block on every day, so the tests do not depend on the seed. */
    private fun freshBlock(): Long = runBlocking {
        val before = ready()
        before.blocks.forEach { repo.deleteBlock(it.id) }
        val list = repo.addList("Plan test ${System.nanoTime()}")
        val id = repo.saveBlock(null, list, t(9), t(11), DayOfWeek.values().toSet(), 0)
        await("block saved") { d -> d.block(id) != null && d.blocks.size == 1 }
        id
    }

    private fun DayOfWeek.next(n: Long = 1) = plus(n)

    @Test fun planEditNeverChangesTodayButChangesTomorrow() {
        val id = freshBlock()
        val tomorrow = today.plusDays(1)
        runBlocking { repo.moveBlock(id, today.dayOfWeek, t(14), t(15)) }
        runBlocking { repo.moveBlock(id, tomorrow.dayOfWeek, t(16), t(17)) }
        val d = await("plan edited") { it.dayTimes.containsKey(id to tomorrow.dayOfWeek) && it.dayTimes.containsKey(id to today.dayOfWeek) }
        // today is frozen at what it was when the day started
        assertEquals(t(9), d.blocksFor(today).single().start)
        assertEquals(t(11), d.blocksFor(today).single().end)
        // tomorrow and next week follow the plan
        assertEquals(t(16), d.blocksFor(tomorrow).single().start)
        assertEquals(t(14), d.blocksFor(today.plusDays(7)).single().start)
        assertTrue(d.differsFromPlan(today))
    }

    @Test fun removingTodayFromTheBlockKeepsItForToday() {
        val id = freshBlock()
        val d0 = repo.data.value!!
        val block = d0.block(id)!!
        runBlocking {
            repo.saveBlock(id, d0.listOfBlock(id)!!.id, block.start, block.end, DayOfWeek.values().toSet() - today.dayOfWeek, 0, resetDayTimes = false)
        }
        val d = await("days changed") { today.dayOfWeek !in (it.block(id)?.days ?: emptySet()) }
        assertEquals("still there today", 1, d.blocksFor(today).count { today.dayOfWeek in it.days })
        assertEquals("gone next week", 0, d.blocksFor(today.plusDays(7)).count { today.plusDays(7).dayOfWeek in it.days })
    }

    @Test fun newBlockFromTheWeekAppearsFromTomorrowFromTheDayAlsoToday() {
        val first = freshBlock()
        val d0 = repo.data.value!!
        val weekList = runBlocking { repo.addList("From the week") }
        val dayList = runBlocking { repo.addList("From the day") }
        val weekId = runBlocking { repo.saveBlock(null, weekList, t(13), t(14), DayOfWeek.values().toSet(), 1, showToday = false) }
        val dayId = runBlocking { repo.saveBlock(null, dayList, t(15), t(16), DayOfWeek.values().toSet(), 2, showToday = true) }
        val d = await("both saved") { it.block(weekId) != null && it.block(dayId) != null && it.changes.isNotEmpty() }
        val todays = d.blocksFor(today).filter { today.dayOfWeek in it.days }.map { it.id }
        assertTrue(first in todays)
        assertFalse("weekly additions wait for tomorrow", weekId in todays)
        assertTrue("a block added on the Day tab shows today", dayId in todays)
        val tomorrow = today.plusDays(1)
        val ids = d.blocksFor(tomorrow).filter { tomorrow.dayOfWeek in it.days }.map { it.id }
        assertTrue(weekId in ids && dayId in ids)
        check(d0.blocks.isNotEmpty())
    }

    @Test fun dayEditsStayOnTheDayUntilYouKeepThem() {
        val id = freshBlock()
        runBlocking { repo.changeForDay(id, today, t(10), t(12)) }
        var d = await("day edit") { it.changeFor(id, today) != null }
        assertEquals(t(10), d.blocksFor(today).single().start)
        assertEquals("next week unchanged", t(9), d.blocksFor(today.plusDays(7)).single().start)
        assertTrue(d.differsFromPlan(today))

        // "Keep for every <weekday>"
        runBlocking { repo.makeDayThePlan(today) }
        d = await("kept") { it.changeFor(id, today) == null && it.dayTimes.keys.any { k -> k.first == id } }
        assertEquals(t(10), d.blocksFor(today.plusDays(7)).single().start)
        assertEquals("other weekdays keep the old times", t(9), d.blocksFor(today.plusDays(1)).single().start)
        assertFalse(d.differsFromPlan(today))
    }

    @Test fun keepingASkippedDayRemovesTheWeekday() {
        val id = freshBlock()
        runBlocking { repo.skipForDay(id, today, t(9), t(11)) }
        await("skipped") { it.changeFor(id, today)?.skipped == true }
        runBlocking { repo.makeDayThePlan(today) }
        val d = await("kept") { today.dayOfWeek !in (it.block(id)?.days ?: emptySet()) }
        assertEquals(0, d.blocksFor(today.plusDays(7)).count { today.plusDays(7).dayOfWeek in it.days })
    }

    @Test fun backToPlanClearsTheDay() {
        val id = freshBlock()
        runBlocking { repo.changeForDay(id, today, t(10), t(12)) }
        await("edit") { it.changeFor(id, today) != null }
        runBlocking { repo.restoreWholeDay(today) }
        val d = await("restored") { it.changeFor(id, today) == null }
        assertFalse(d.differsFromPlan(today))
    }

    @Test fun deletingABlockRemovesItEverywhere() {
        val id = freshBlock()
        runBlocking { repo.moveBlock(id, today.plusDays(1).dayOfWeek, t(16), t(17)) }
        runBlocking { repo.deleteBlock(id) }
        val d = await("deleted") { it.block(id) == null }
        assertTrue(d.blocksFor(today).none { it.id == id })
        assertTrue(d.dayTimes.keys.none { it.first == id })
    }

    @Test fun deletingOneDayOfABlockLeavesTheOtherDaysAndTheOtherBlocks() {
        val id = freshBlock()   // every day, 09:00 to 11:00
        val list = repo.data.value!!.listOfBlock(id)!!
        val other = runBlocking { repo.saveBlock(null, list.id, t(14), t(15), setOf(DayOfWeek.WEDNESDAY), 0, showToday = false) }
        await("other block") { it.block(other) != null }
        // pick a day that is neither today nor tomorrow's Wednesday clash
        val victim = today.dayOfWeek.plus(2)
        runBlocking { repo.removeFromWeekday(id, victim) }
        val d = await("one day removed") { victim !in (it.block(id)?.days ?: DayOfWeek.values().toSet()) }
        assertEquals(DayOfWeek.values().toSet() - victim, d.block(id)!!.days)
        assertNotNull("the other block of the list is untouched", d.block(other))
        assertEquals(setOf(DayOfWeek.WEDNESDAY), d.block(other)!!.days)
        // today keeps the block whatever the plan edit did
        assertTrue(d.blocksFor(today).any { it.id == id })
        // removing the last remaining days one by one deletes the block at the end
        DayOfWeek.values().filter { it != victim }.forEach { runBlocking { repo.removeFromWeekday(id, it) } }
        await("block gone with its last day") { it.block(id) == null }
        assertNotNull(repo.data.value!!.block(other))
    }

    @Test fun aListCanHaveSeveralBlocksAndStaysAvailable() {
        val first = freshBlock()
        val d0 = repo.data.value!!
        val list = d0.listOfBlock(first)!!
        // a second block of the same list, on Saturday afternoon
        val second = runBlocking { repo.saveBlock(null, list.id, t(14), t(15), setOf(DayOfWeek.SATURDAY), 0, showToday = false) }
        val d = await("second block") { it.block(second) != null }
        assertEquals(list.id, d.block(second)!!.listId)
        assertEquals(setOf(first, second), d.blocksOfList(list.id).map { it.id }.toSet())
        assertEquals("both blocks carry the list's name", list.title, d.block(second)!!.name)
        assertFalse("a list with blocks is not an anytime list", list.id in d.anytimeLists().map { it.id })
    }

    @Test fun allBlocksOfAListShareOneColor() {
        val first = freshBlock()
        val list = repo.data.value!!.listOfBlock(first)!!
        val second = runBlocking { repo.saveBlock(null, list.id, t(14), t(15), setOf(DayOfWeek.SATURDAY), 3, showToday = false) }
        var d = await("recolored") { it.block(second) != null && it.colors[list.id] == 3 }
        assertEquals(d.colorOf(first), d.colorOf(second))
        assertEquals(BLOCK_COLORS[3], d.colorOf(first))
        // choosing another color while editing one block recolors the other too
        runBlocking { repo.saveBlock(first, list.id, t(9), t(11), DayOfWeek.values().toSet(), 5, showToday = false, resetDayTimes = false) }
        d = await("recolored again") { it.colors[list.id] == 5 }
        assertEquals(BLOCK_COLORS[5], d.colorOf(second))
    }

    @Test fun differentListsGetDifferentColors() {
        val a = runBlocking { repo.addList("Color A ${System.nanoTime()}") }
        val b = runBlocking { repo.addList("Color B ${System.nanoTime()}") }
        val d = await("two lists") { it.colors.containsKey(a) && it.colors.containsKey(b) }
        assertTrue("a new list takes a color few lists use", d.colors[a] != d.colors[b])
    }

    @Test fun aNewListTakesTheColorChosenForIt() {
        val a = runBlocking { repo.addList("Picked A ${System.nanoTime()}", 4) }
        val b = runBlocking { repo.addList("Picked B ${System.nanoTime()}", 4) }
        val c = runBlocking { repo.addList("Out of range ${System.nanoTime()}", 99) }
        val d = await("three lists") { it.colors.containsKey(a) && it.colors.containsKey(b) && it.colors.containsKey(c) }
        assertEquals(4, d.colors[a])
        assertEquals("the same color may be chosen twice", 4, d.colors[b])
        assertTrue("a color that does not exist falls back to a suggested one", d.colors[c] in BLOCK_COLORS.indices)
        assertEquals("a list without a block already shows its color", BLOCK_COLORS[4], d.colorOfListAlways(a))
    }

    @Test fun deletingOneBlockKeepsTheListAndItsOtherBlocks() {
        val first = freshBlock()
        val list = repo.data.value!!.listOfBlock(first)!!
        val second = runBlocking { repo.saveBlock(null, list.id, t(14), t(15), setOf(DayOfWeek.SATURDAY), 0, showToday = false) }
        await("second") { it.block(second) != null }
        runBlocking { repo.deleteBlock(first) }
        var d = await("first deleted") { it.block(first) == null }
        assertEquals(listOf(second), d.blocksOfList(list.id).map { it.id })
        assertFalse(list.id in d.anytimeLists().map { it.id })
        runBlocking { repo.deleteBlock(second) }
        d = await("both deleted") { it.block(second) == null }
        assertTrue("with no block left the list is an anytime list", list.id in d.anytimeLists().map { it.id })
        assertTrue(d.lists.any { it.id == list.id })
    }

    @Test fun deletingAListDeletesItsBlocks() {
        val first = freshBlock()
        val list = repo.data.value!!.listOfBlock(first)!!
        runBlocking { repo.deleteList(list.id) }
        val d = await("list deleted") { it.block(first) == null }
        assertTrue(d.lists.none { it.id == list.id })
    }

    @Test fun sunMovesSmoothlyBetweenTicks() {
        val zone = ZoneId.systemDefault()
        val start = LocalDateTime.of(2026, 10, 19, 9, 0)
        val end = start.plusMinutes(100)
        val bar = TimerBar(BarKind.FOCUS_WORK, 0, 0.0, setOf(0, 12), start, end)
        val t0 = start.plusMinutes(50).atZone(zone).toInstant().toEpochMilli()
        val a = sunAt(bar, t0)
        val b = sunAt(bar, t0 + 250)
        val c = sunAt(bar, t0 + 500)
        assertEquals(0.5f, a, 0.0001f)
        assertTrue("moves in small steps, not once a second", b > a && c > b)
        assertEquals("clamped before the start", 0f, sunAt(bar, t0 - 10_000_000_000L), 0f)
        assertEquals("clamped after the end", 1f, sunAt(bar, t0 + 10_000_000_000L), 0f)
    }

    @Test fun theAlarmBeforeABlockGoesOffSoTheVibrationEndsAsTheBlockStarts() {
        val day = java.time.LocalDate.of(2026, 10, 21)
        val block = com.focustask.core.TimeBlock(1, "Work", java.time.LocalTime.of(9, 0), java.time.LocalTime.of(11, 0), listId = 10)
        val instances = com.focustask.core.Schedule.instances(day.atTime(8, 50), { listOf(block) }, daysBack = 0, daysAhead = 0)
        val lead = com.focustask.core.Vibrations.leadMs(AlertKind.BLOCK_STARTING)
        assertTrue(lead > 1000)
        assertEquals(day.atTime(9, 0).minusNanos(lead * 1_000_000), Alerts.nextAlertAlarm(day.atTime(8, 50), instances, com.focustask.core.CycleSettings(), null))
        // once that alarm went off, the next one is for the first break of the block
        val after = Alerts.nextAlertAlarm(day.atTime(9, 0), instances, com.focustask.core.CycleSettings(), null)!!
        assertTrue(after > day.atTime(9, 0))
    }

    @Test fun soundModeAlsoVibratesAndSilentDoesNot() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val d = ready()
        val quick = d.settings.copy(maxSoundSec = 1)
        val before = Alerts.vibrations

        runBlocking { Alerts.playAlert(app, d.copy(settings = quick.copy(alertMode = AlertMode.SOUND)), AlertKind.CYCLE_CHIME) }
        assertEquals("the chime must come with a buzz", before + 1, Alerts.vibrations)
        // as an alarm, so Android lets it through while the app is in the background
        val vibrator = app.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        val attrs = shadowOf(vibrator).vibrationAttributesFromLastVibration as android.os.VibrationAttributes
        assertEquals(android.os.VibrationAttributes.USAGE_ALARM, attrs.usage)
        // a phone that can only switch the motor on and off gets the fading wave as shorter and shorter buzzes
        assertEquals(com.focustask.core.Vibrations.onOff(com.focustask.core.VibrationFeel.HARD_FADE).toList(), shadowOf(vibrator).pattern.toList())

        runBlocking { Alerts.playAlert(app, d.copy(settings = quick.copy(alertMode = AlertMode.VIBRATE)), AlertKind.CYCLE_CHIME) }
        assertEquals("vibrate mode buzzes once", before + 2, Alerts.vibrations)

        runBlocking { Alerts.playAlert(app, d.copy(settings = quick.copy(alertMode = AlertMode.SILENT)), AlertKind.CYCLE_CHIME) }
        assertEquals("silent means silent", before + 2, Alerts.vibrations)
    }

    @Test fun reorderingSavesTheNewOrderOfTasksAndOfSubtasks() = runBlocking {
        ready()
        val list = repo.addList("Order")
        val a = repo.addTask(list, "A", null)
        val b = repo.addTask(list, "B", null)
        val c = repo.addTask(list, "C", null)
        val s1 = repo.addTask(list, "S1", null, a)
        val s2 = repo.addTask(list, "S2", null, a)
        fun ids(parent: Long?, d: AppData) = d.tasks.filter { it.listId == list && it.parentId == parent }
            .sortedWith(compareBy({ it.position }, { it.id })).map { it.id }
        await("tasks saved") { ids(null, it).size == 3 && ids(a, it).size == 2 }
        repo.reorderTasks(listOf(c, a, b))
        repo.reorderTasks(listOf(s2, s1))
        val d = await("new order") { ids(null, it) == listOf(c, a, b) && ids(a, it) == listOf(s2, s1) }
        // moving subtasks never changes whose subtasks they are
        assertTrue(d.tasks.filter { it.id == s1 || it.id == s2 }.all { it.parentId == a })
    }
}
