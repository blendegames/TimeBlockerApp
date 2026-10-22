package com.focustask

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.focustask.core.Repeat
import com.focustask.core.RepeatUnit
import com.focustask.data.Repository
import com.focustask.ui.components.TimerAnimation
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Starts the real activity: database, seeding, view model and screens all together. */
@RunWith(RobolectricTestRunner::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class FlowTest {
    init { TimerAnimation.smooth = false }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun settle() {
        TestData.ensure(ApplicationProvider.getApplicationContext())
        // the database and DataStore answer on background threads
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle()
            if (rule.onAllNodesWithContentDescription("Done").fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
    }

    @Test fun appStartsSeedsAndNavigates() {
        settle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/30_real_start.png")
        // go through every tab
        for (tab in listOf("Day", "Week", "Lists", "Settings", "Today")) {
            rule.onNodeWithText(tab).performClick()
            rule.waitForIdle()
        }
        rule.onRoot().captureRoboImage("build/screens/31_real_back_on_today.png")
        keepScreenOnSwitch()
    }

    private fun anyViewKeepsScreenOn(v: View): Boolean =
        v.keepScreenOn || (v is ViewGroup && (0 until v.childCount).any { anyViewKeepsScreenOn(v.getChildAt(it)) })

    /** Settings has a "Keep screen on" switch: on keeps the window awake, off lets it sleep again. */
    private fun keepScreenOnSwitch() {
        val repo = (rule.activity.application as FocusApp).repo
        org.junit.Assert.assertFalse("off by default", anyViewKeepsScreenOn(rule.activity.window.decorView))
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        // the settings are sorted into groups; the screen ones are under "Screen"
        for (group in listOf("timer", "alerts", "screen", "data")) rule.onNodeWithTag("settings-$group").assertIsDisplayed()
        rule.onNodeWithTag("settings-screen").performClick()
        rule.waitForIdle()
        // timer view when idle: on by default, with a delay; off hides the delay
        rule.onNodeWithText("Timer view when idle").assertIsDisplayed()
        rule.onNodeWithTag("timer-view-delay").assertIsDisplayed()
        rule.onNodeWithTag("timer-view-idle").performClick()
        waitFor("timer view off") { !repo.data.value!!.settings.timerViewWhenIdle }
        org.junit.Assert.assertTrue(rule.onAllNodesWithTag("timer-view-delay").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("timer-view-idle").performClick()
        waitFor("timer view on") { repo.data.value!!.settings.timerViewWhenIdle }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("keep-screen-on"))
        rule.onNodeWithText("Keep screen on").assertIsDisplayed()
        rule.onNodeWithTag("keep-screen-on").performClick()
        waitFor("screen stays on") { repo.data.value!!.settings.keepScreenOn && anyViewKeepsScreenOn(rule.activity.window.decorView) }
        // dimming: on by default, with a slider for how dark; off hides the slider
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("dim-percent"))
        rule.onNodeWithText("Dim when idle").assertIsDisplayed()
        rule.onNodeWithTag("dim-delay").assertIsDisplayed()
        rule.onNodeWithTag("dim-percent").performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        waitFor("dim level saved") { repo.data.value!!.settings.dimPercent == 40 }
        rule.onNodeWithTag("dim-percent-label", useUnmergedTree = true).assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/44_keep_screen_on.png")
        rule.onNodeWithTag("dim-when-idle").performClick()
        waitFor("dimming off") { !repo.data.value!!.settings.dimWhenIdle }
        org.junit.Assert.assertTrue(rule.onAllNodesWithTag("dim-percent").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("dim-when-idle").performClick()   // back to the default
        waitFor("dimming on") { repo.data.value!!.settings.dimWhenIdle }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("keep-screen-on"))
        rule.onNodeWithTag("keep-screen-on").performClick()
        waitFor("screen may sleep again") { !repo.data.value!!.settings.keepScreenOn && !anyViewKeepsScreenOn(rule.activity.window.decorView) }
        rule.onNodeWithTag("settings-back").performClick()   // back to the list of groups
        rule.waitForIdle()
        rule.onNodeWithTag("settings-screen").assertIsDisplayed()
        rule.onNodeWithText("Today").performClick()   // later tests expect to start on Today
        rule.waitForIdle()
    }

    @Test fun doneAdvancesToTheNextTask() {
        settle()
        val before = rule.onAllNodesWithContentDescription("Done").fetchSemanticsNodes().size
        check(before > 0) { "no tick button on the Today screen" }
        // one tick button, on the task at hand: no "Done" bar, no ticks on the rows before and after it
        org.junit.Assert.assertEquals(1, before)
        // the wheel turns: catch it half way
        val start = carouselTexts()
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithContentDescription("Done").performClick()
            val end = System.currentTimeMillis() + 8000
            while (System.currentTimeMillis() < end && carouselTexts() == start) {
                rule.mainClock.advanceTimeByFrame()
                Thread.sleep(20)
            }
            File("build/screens").mkdirs()
            rule.mainClock.advanceTimeBy(130)
            rule.onRoot().captureRoboImage("build/screens/32a_wheel_turning.png")
            rule.mainClock.advanceTimeBy(100)
            rule.onRoot().captureRoboImage("build/screens/32b_wheel_turning.png")
        } finally {
            rule.mainClock.autoAdvance = true
        }
        Thread.sleep(800)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/32_after_done.png")
    }

    /** Every text inside the carousel: tells which cards are on screen. */
    private fun carouselTexts(): List<String> {
        fun walk(n: SemanticsNode): List<String> =
            (if (n.config.contains(SemanticsProperties.Text)) n.config[SemanticsProperties.Text].map { it.text } else emptyList<String>()) + n.children.flatMap { walk(it) }
        // sorted: the cards of the wheel are drawn in whatever order, only what is on it counts. Times are left out:
        // "3 h late" or "Due in 5 min" change by themselves when the minute turns, which is not the wheel turning.
        return walk(rule.onNodeWithTag("carousel").fetchSemanticsNode()).filterNot { TIME_TEXT.containsMatchIn(it) }.sorted()
    }

    private val TIME_TEXT = Regex("(?i)\\b(late|due|done)\\b|\\d{1,2}:\\d{2}")

    private fun currentTitle(): String =
        rule.onNodeWithTag("current-title", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun waitForCarouselToChangeFrom(old: List<String>, what: String): List<String> {
        waitFor(what) { carouselTexts() != old }
        // and for the wheel to come to rest: then only the task at hand is on it
        var last = carouselTexts()
        waitFor("$what: the wheel at rest") {
            Thread.sleep(250)
            val now = carouselTexts()
            (now == last).also { last = now }
        }
        return last
    }

    @Test fun swipeUpSkipsTheTaskAndSwipeDownUndoesTheLastCompletion() {
        settle()
        rule.onNodeWithText("Today").performClick()
        rule.waitForIdle()
        val start = carouselTexts()
        val startTitle = currentTitle()
        rule.onNodeWithTag("carousel").performTouchInput { swipeUp() }
        // the wheel turns at once; the task at hand changes when the skip is saved
        waitFor("another task is at hand after the skip") { currentTitle() != startTitle && "SKIPPED" in carouselTexts() }
        val afterSkip = waitForCarouselToChangeFrom(start, "the wheel at rest after the skip")
        val skippedTo = currentTitle()
        // the skipped task shows as the one before, marked "SKIPPED"
        org.junit.Assert.assertTrue("SKIPPED" in afterSkip)
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/32c_after_skip.png")
        // finish the task now at hand with its tick; swiping down brings the same screen back
        rule.onNodeWithContentDescription("Done").performClick()
        waitFor("the next task is at hand after the tick") { currentTitle() != skippedTo }
        val afterDone = waitForCarouselToChangeFrom(afterSkip, "the wheel at rest after the tick")
        rule.onNodeWithTag("carousel").performTouchInput { swipeDown() }
        waitFor("the completion is undone") { currentTitle() == skippedTo }
        val afterUndo = waitForCarouselToChangeFrom(afterDone, "the wheel at rest after the undo")
        org.junit.Assert.assertNotEquals(afterDone, afterUndo)
        org.junit.Assert.assertEquals("undo brings the finished task back", skippedTo, currentTitle())
    }

    @Test fun pullingTheTimerBarDownDropsTheFullScreenTimer() {
        settle()
        fun shown() = rule.onAllNodesWithTag("idle-timer-view").fetchSemanticsNodes().isNotEmpty()
        fun full() = rule.onAllNodesWithTag("idle-timer-view").fetchSemanticsNodes().firstOrNull()
            ?.config?.let { if (it.contains(SemanticsProperties.StateDescription)) it[SemanticsProperties.StateDescription] else null } == "Full screen"
        check(!shown())
        // halfway: the timer grows out of the bar with the finger
        rule.onNodeWithTag("timer-bar").performTouchInput { down(Offset(centerX, top + 20f)); moveBy(Offset(0f, 300f)); moveBy(Offset(0f, 300f)) }
        rule.waitForIdle()
        org.junit.Assert.assertTrue("it follows the finger", shown())
        org.junit.Assert.assertFalse("not all the way yet", full())
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/46_timer_pulled_halfway.png")
        // let go past a third of the screen: it grows all the way
        rule.onNodeWithTag("timer-bar").performTouchInput { moveBy(Offset(0f, 200f)); up() }
        waitFor("the full-screen timer is out") { full() }
        rule.onRoot().captureRoboImage("build/screens/47_timer_pulled_down.png")
        // the sound button came along
        rule.onNodeWithTag("idle-alert-mode").assertIsDisplayed()
        // a tap only wakes the screen: the timer stays
        rule.onNodeWithTag("idle-timer-view").performClick()
        advance(600)
        org.junit.Assert.assertTrue("a tap does not send it away", full())
        // pushing it up sends it back
        rule.onNodeWithTag("idle-timer-view").performTouchInput { swipeUp() }
        waitFor("the full-screen timer went back up") { !shown() }
        // a short pull is not enough: it goes back up by itself
        rule.onNodeWithTag("timer-bar").performTouchInput {
            down(Offset(centerX, top + 20f))
            repeat(10) { moveBy(Offset(0f, 10f), delayMillis = 60) }
            advanceEventTime(200)
            up()
        }
        waitFor("a short pull goes back up") { !shown() }
    }

    @Test fun infoButtonExplainsEachTabButSettings() {
        settle()
        fun infoCount() = rule.onAllNodesWithTag("info-button", useUnmergedTree = true).fetchSemanticsNodes().size
        for ((tab, word) in listOf("Today" to "marked OVERDUE", "Day" to "today only", "Week" to "every week", "Lists" to "unfold it and edit")) {
            rule.onNodeWithText(tab).performClick()
            rule.waitForIdle()
            org.junit.Assert.assertEquals("$tab has an info button", 1, infoCount())
            rule.onNodeWithTag("info-button", useUnmergedTree = true).performClick()
            rule.waitForIdle()
            rule.onNodeWithText(word, substring = true).assertIsDisplayed()
            rule.onNodeWithText("Got it").performClick()
            rule.waitForIdle()
            org.junit.Assert.assertTrue("the dialog closes", rule.onAllNodesWithText("Got it").fetchSemanticsNodes().isEmpty())
        }
        // the Week tab has no Add block button: tap the grid
        rule.onNodeWithText("Week").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Add block").fetchSemanticsNodes().isEmpty())
        rule.onRoot().captureRoboImage("build/screens/38_week_info_button.png")
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals("Settings has none", 0, infoCount())
        rule.onNodeWithText("Today").performClick()
        rule.waitForIdle()
    }

    @Test fun tappingAnEmptySpotOfTheWeekGridShowsAOneHourBlockWithQuickSettings() {
        settle()
        rule.onNodeWithText("Week").performClick()
        rule.waitForIdle()
        // far right = Sunday, which has no block in the seed. The test data adds a block around "now" to every
        // day, so the middle of the grid can be taken: try a few heights until one is empty.
        // The gap between the blocks of the test data depends on the time of day, so take a spot where the whole hour fits.
        for (f in listOf(0.5f, 0.3f, 0.7f, 0.15f, 0.85f, 0.4f, 0.6f, 0.22f, 0.78f, 0.08f, 0.92f)) {
            rule.onNodeWithTag("timegrid").performTouchInput { click(Offset(width * 0.93f, height * f)) }
            rule.waitForIdle()
            if (rule.onAllNodesWithText("New block", substring = true).fetchSemanticsNodes().isNotEmpty()) {
                if (rule.onAllNodesWithTag("block--1-6", useUnmergedTree = true).fetchSemanticsNodes()
                        .firstOrNull()?.let { it.size.height >= 80 } == true) break
                rule.onNodeWithText("Cancel").performClick()
                rule.waitForIdle()
            }
        }
        File("build/screens").mkdirs()
        rule.onNodeWithText("New block", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Pick a list").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/33_tap_week_grid.png")
        // changing the new block twice before it is saved: the block itself follows the finger each time, not only the bubble
        fun ghostHeight() = rule.onNodeWithTag("block--1-6", useUnmergedTree = true).fetchSemanticsNode().size.height.toFloat()
        for (round in 1..2) {
            val h0 = ghostHeight()
            rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { down(center); moveBy(Offset(0f, 60f)) }
            rule.waitForIdle()
            val held = ghostHeight()
            org.junit.Assert.assertTrue("round $round: the block grew while the finger is still down: $h0 -> $held", held > h0 + 20f)
            rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { moveBy(Offset(0f, -30f)) }
            rule.waitForIdle()
            org.junit.Assert.assertTrue("round $round: and it shrinks with the finger too: $held -> ${ghostHeight()}", ghostHeight() < held - 10f)
            rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { up() }
            rule.waitForIdle()
        }
        // dragging the block itself follows the finger as well
        rule.onNodeWithTag("block--1-6", useUnmergedTree = true).performTouchInput { down(Offset(width / 2f, height / 2f)); moveBy(Offset(0f, 80f)) }
        rule.waitForIdle()
        val moved = rule.onNodeWithTag("block--1-6", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        rule.onNodeWithTag("block--1-6", useUnmergedTree = true).performTouchInput { up() }
        rule.waitForIdle()
        val settled = rule.onNodeWithTag("block--1-6", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        org.junit.Assert.assertTrue("the block was already where the finger left it: $moved vs $settled", abs(moved - settled) < 12f)
        // choosing a list enables Save and saves a second block for that list
        rule.onAllNodesWithTag("list-chip")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/34_quick_list_chosen.png")
        rule.onNodeWithText("More options").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("New time block").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/35_more_options.png")
    }

    /**
     * The test data puts a block around "now" into every day, so an empty hour is at the end of the Day grid, or, late
     * in the evening, at its start. Scrolls there and taps it: the quick block opens.
     */
    private fun tapAnEmptyHourOfTheDay() {
        val late = java.time.LocalTime.now().hour >= 21
        repeat(4) { rule.onNodeWithTag("timegrid").performTouchInput { if (late) swipeDown() else swipeUp() } }
        rule.waitForIdle()
        val y = if (late) 0.04f else 0.97f
        rule.onNodeWithTag("timegrid").performTouchInput { click(Offset(width * 0.5f, height * y)) }
        rule.waitForIdle()
        if (rule.onAllNodesWithText("Pick a list").fetchSemanticsNodes().isEmpty()) {
            // the first tap may only have let go of a selection: tap again
            rule.onNodeWithTag("timegrid").performTouchInput { click(Offset(width * 0.5f, height * y)) }
            rule.waitForIdle()
        }
        rule.onNodeWithText("Pick a list").assertIsDisplayed()
    }

    @Test fun tappingAnEmptySpotOfTheDayGridShowsAOneHourBlockWithQuickSettings() {
        settle()
        rule.onNodeWithText("Day").performClick()
        rule.waitForIdle()
        tapAnEmptyHourOfTheDay()
        // the Day tab edits today only: no "Add block" button (tap the calendar), no "More options", no explaining text
        for (gone in listOf("Add block", "More options")) {
            org.junit.Assert.assertTrue("$gone should not be on the Day tab", rule.onAllNodesWithText(gone).fetchSemanticsNodes().isEmpty())
        }
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("changes apply today only", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/36_tap_day_grid.png")
        // resizing: a bubble beside the block says which time the finger is on, and goes away on release
        check(rule.onAllNodesWithTag("time-bubble", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { down(center); moveBy(Offset(0f, -60f)) }
        rule.waitForIdle()
        rule.onNodeWithTag("time-bubble", useUnmergedTree = true).assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/37_resize_bubble.png")
        rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { up() }
        rule.waitForIdle()
        check(rule.onAllNodesWithTag("time-bubble", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun bubble(): String = rule.onAllNodes(
        hasAnyAncestor(hasTestTag("time-bubble")) and hasText(":", substring = true),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().joinToString { n ->
        n.config[SemanticsProperties.Text].joinToString { it.text }
    }

    @Test fun holdingAHandleNearTheEdgeOfTheGridScrollsTheGridAndTheBlockFollows() {
        settle()
        rule.onNodeWithText("Day").performClick()
        rule.waitForIdle()
        // late in the evening the test data's block fills the end of the day: nothing to scroll to then
        org.junit.Assume.assumeTrue(java.time.LocalTime.now().hour < 21)
        tapAnEmptyHourOfTheDay()
        // push the bottom handle to the very bottom of the grid and keep holding it there
        rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { down(center) }
        val grid = rule.onNodeWithTag("timegrid").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { moveBy(Offset(0f, grid.bottom)) }
        rule.mainClock.advanceTimeBy(100)
        val before = bubble()
        rule.mainClock.advanceTimeBy(1500)
        val after = bubble()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/42_autoscroll.png")
        org.junit.Assert.assertTrue("held at the edge, the grid scrolled and the end followed it to midnight: $before -> $after", after.endsWith("24:00"))
        rule.onNodeWithTag("handle-bottom", useUnmergedTree = true).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
    }

    /** Long press picks several Week blocks; Delete removes only those days, without asking first. */
    private fun multiSelectAndDeleteInTheWeek(repo: Repository) {
        // a block of its own (the seed only exists in the first test of a whole run): 14:00 to 15:00, every day
        val homeId = runBlocking {
            val l = repo.addList("Multi")
            repo.saveBlock(null, l, LocalTime.of(14, 0), LocalTime.of(15, 0), DayOfWeek.values().toSet(), 0, showToday = false)
        }
        waitFor("block saved") { repo.data.value?.block(homeId) != null }
        val home = repo.data.value!!.block(homeId)!!
        org.junit.Assert.assertEquals(7, home.days.size)
        rule.onNodeWithText("Week").performClick()
        waitFor("block on screen") { rule.onAllNodesWithTag("block-${home.id}-5", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun block(col: Int) = rule.onNodeWithTag("block-${home.id}-$col", useUnmergedTree = true)
        block(5).performTouchInput { longClick() }   // Saturday
        rule.waitForIdle()
        rule.onNodeWithText("1 selected").assertIsDisplayed()
        block(6).performClick()                       // Sunday joins the selection
        rule.waitForIdle()
        rule.onNodeWithText("2 selected").assertIsDisplayed()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/43_multi_select.png")
        // a tap on a picked block lets it go, another tap picks it again
        block(6).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("1 selected").assertIsDisplayed()
        block(6).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("selection-delete").performClick()
        // no "are you sure": the blocks are simply gone from those two days
        waitFor("two days removed") { (repo.data.value!!.block(home.id)?.days?.size ?: 0) == 5 }
        val left = repo.data.value!!.block(home.id)!!.days
        org.junit.Assert.assertEquals(DayOfWeek.values().toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY, left)
        org.junit.Assert.assertTrue(rule.onAllNodesWithTag("selection-bar").fetchSemanticsNodes().isEmpty())
    }

    /** "New list" asks for a name and a color; the list is saved with the color that was tapped. */
    private fun createAListWithAChosenColor(repo: Repository) {
        rule.onNodeWithText("New list").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(6, (0..5).count { rule.onAllNodesWithTag("list-color-$it").fetchSemanticsNodes().isNotEmpty() })
        rule.onNodeWithTag("list-color-3").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Painted list")
        rule.onNodeWithText("Create").performClick()
        waitFor("list saved") { repo.data.value!!.lists.any { it.title == "Painted list" } }
        val saved = repo.data.value!!.lists.first { it.title == "Painted list" }
        org.junit.Assert.assertEquals(3, repo.data.value!!.colorOfListIndex(saved.id))
        org.junit.Assert.assertTrue(rule.onAllNodesWithTag("list-color-3").fetchSemanticsNodes().isEmpty())
    }

    /** Like waitUntil, but lets the main thread work between looks, so ViewModel writes can finish. */
    private fun waitFor(what: String, ms: Long = 8000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle()
            if (cond()) return
            Thread.sleep(50)
            // delays in the app run on the main thread's clock, which only moves when the test moves it
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            // and so do delays inside the screens (the tick waits a moment before the wheel turns)
            rule.mainClock.advanceTimeBy(50)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    /** Brings the card of the list called [title] into view on the Lists tab and opens it. */
    private fun openList(title: String) {
        waitFor("list $title on screen") {
            rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() || runCatching {
                rule.onNode(hasScrollAction()).performScrollToNode(hasText(title))
            }.isSuccess && rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(title).performClick()
    }

    // Named to run first: later tests in the same JVM see the first test's screens and data, not their own,
    // so everything that needs the seeded data or writes through the repository lives in this one.
    /**
     * Waits until the data has stopped changing (300 ms without a new version). Writing to the database from the test
     * sends a burst of updates; touching the screen while they still arrive can make the test's frame and the app's
     * recomposition run at the same time, which Compose's test rule does not allow.
     */
    private fun quiet(repo: Repository) {
        var last = repo.data.value
        var calm = 0
        val end = System.currentTimeMillis() + 5000
        while (calm < 6 && System.currentTimeMillis() < end) {
            rule.waitForIdle()
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(50)
            val v = repo.data.value
            if (v === last) calm++ else { calm = 0; last = v }
        }
        rule.waitForIdle()
    }

    @Test fun a1_longPressDeletesAndDraggingTheGripReorders() {
        settle()
        // the repository the running activity uses (a second one on the same file would not notify the screens)
        val repo = (rule.activity.application as FocusApp).repo
        quiet(repo)
        multiSelectAndDeleteInTheWeek(repo)
        val (alpha, beta, gamma, subs) = runBlocking {
            val many = repo.addList("Many subs")
            val big = repo.addTask(many, "Big task", null)
            (1..5).forEach { repo.addTask(many, "Step $it", null, big) }
            val l = repo.addList("Reorder me")
            val a = repo.addTask(l, "Alpha", null)
            val b = repo.addTask(l, "Beta", null)
            val c = repo.addTask(l, "Gamma", null)
            val s = listOf("Sub one", "Sub two", "Sub three").map { repo.addTask(l, it, null, a) }
            listOf(a, b, c, s)
        }.let { Quad(it[0] as Long, it[1] as Long, it[2] as Long, @Suppress("UNCHECKED_CAST") (it[3] as List<Long>)) }
        quiet(repo)

        fun order(parent: Long?): List<Long> {
            val end = System.currentTimeMillis() + 3000
            var last = emptyList<Long>()
            while (System.currentTimeMillis() < end) {
                rule.waitForIdle()
                last = repo.data.value!!.tasks.filter { it.parentId == parent && it.id in (listOf(alpha, beta, gamma) + subs) }
                    .sortedWith(compareBy({ it.position }, { it.id })).map { it.id }
                Thread.sleep(50)
            }
            return last
        }

        rule.onNodeWithText("Lists").performClick()
        rule.waitForIdle()
        // a task that is not selected shows only its first three subtasks; selecting it shows all
        openList("Many subs")
        rule.waitForIdle()
        fun step(n: Int) = rule.onAllNodesWithText("Step $n", substring = true).fetchSemanticsNodes().isNotEmpty()
        org.junit.Assert.assertTrue(step(1) && step(2) && step(3))
        org.junit.Assert.assertTrue(!step(4) && !step(5))
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("+ 2 more", substring = true).fetchSemanticsNodes().isNotEmpty())
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/40_three_subtasks.png")
        rule.onNodeWithText("Big task").performTouchInput { click(Offset(width * 0.4f, 40f)) }
        rule.waitForIdle()
        org.junit.Assert.assertTrue((1..5).all { step(it) })
        rule.onRoot().captureRoboImage("build/screens/41_all_subtasks.png")
        rule.onNodeWithContentDescription("Fold up").performClick()
        rule.waitForIdle()
        openList("Reorder me")
        rule.waitForIdle()
        fun handles() = rule.onAllNodesWithTag("drag-handle", useUnmergedTree = true)
        // not selected: one grip per task, on the right, and no move buttons any more
        org.junit.Assert.assertEquals(3, handles().fetchSemanticsNodes().size)
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Move up").fetchSemanticsNodes().isEmpty())
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/36_list_with_grips.png")

        // select Alpha: its subtasks get their own grips; drag the first one far past the others
        rule.onNodeWithText("Alpha").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(6, handles().fetchSemanticsNodes().size)
        rule.onRoot().captureRoboImage("build/screens/37_subtask_grips.png")
        handles()[1].performTouchInput {
            down(center)
            for (i in 1..10) moveBy(Offset(0f, 12f))
            org.junit.Assert.assertTrue(true)
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screens/38_dragging_subtask.png")
        handles()[1].performTouchInput {
            for (i in 1..40) moveBy(Offset(0f, 25f))   // way beyond the last subtask, and beyond the card
            up()
        }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(listOf(subs[1], subs[2], subs[0]), order(alpha))
        // the other tasks did not get any of them, and the main order is unchanged
        org.junit.Assert.assertEquals(listOf(alpha, beta, gamma), order(null))
        org.junit.Assert.assertTrue(repo.data.value!!.tasks.filter { it.id in subs }.all { it.parentId == alpha })

        // deselect, then drag Alpha below Beta
        // (the card is one node, so tap near its top, on the title, not in the middle where subtasks are)
        rule.onNodeWithText("Alpha").performTouchInput { click(Offset(width * 0.4f, 40f)) }
        rule.waitForIdle()
        handles()[0].performTouchInput {
            down(center)
            for (i in 1..16) moveBy(Offset(0f, 25f))
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1000)
        rule.onRoot().captureRoboImage("build/screens/39_dragging_task.png")
        handles()[0].performTouchInput { up() }
        rule.waitForIdle()
        val after = order(null)
        org.junit.Assert.assertTrue("Alpha moved down: $after", after.indexOf(alpha) > 0)
        org.junit.Assert.assertEquals(setOf(alpha, beta, gamma), after.toSet())
        org.junit.Assert.assertEquals(listOf(subs[1], subs[2], subs[0]), order(alpha))

        ticksWaitASecondBeforeTheTaskMoves(repo, gamma)
        makeATaskRepeat(repo, beta)

        // last: back on the Lists tab, a new list is created with a chosen color
        rule.onNodeWithContentDescription("Fold up").performClick()
        rule.waitForIdle()
        // the tick boxes of a folded list work right there: they tick the task instead of unfolding the list
        val firstSub = subs[1]
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("preview-tick-$firstSub"))
        rule.onNodeWithTag("preview-tick-$firstSub").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertTrue("the list stays folded", rule.onAllNodesWithTag("list-open").fetchSemanticsNodes().isEmpty())
        advance(1500)
        waitFor("the subtask is done") { repo.data.value!!.tasks.first { it.id == firstSub }.done }
        createAListWithAChosenColor(repo)
    }

    /** Delays in the app run on the main thread's clock, which only moves when the test moves it. */
    private fun advance(ms: Long) {
        repeat((ms / 100).toInt()) {
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
            rule.waitForIdle()
        }
    }

    /** Gamma is on screen in its unfolded list. A tick shows at once but only counts after a second; a second tap takes it back. */
    private fun ticksWaitASecondBeforeTheTaskMoves(repo: Repository, id: Long) {
        fun done() = repo.data.value!!.tasks.first { it.id == id }.done
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("tick-$id"))
        rule.onNodeWithTag("tick-$id").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tick-$id").assertIsOn()
        org.junit.Assert.assertFalse("not saved yet", done())
        rule.onNodeWithTag("tick-$id").performClick()   // a mistake: take it back
        rule.waitForIdle()
        rule.onNodeWithTag("tick-$id").assertIsOff()
        advance(1500)
        org.junit.Assert.assertFalse("taken back in time: still open", done())
        // tick it for real: after a second it is done and sits under Completed
        rule.onNodeWithTag("tick-$id").performClick()
        advance(400)
        org.junit.Assert.assertFalse("not yet after 0.4 s", done())
        advance(1200)
        waitFor("done after a second") { done() }
        waitFor("moved to Completed") { rule.onAllNodesWithTag("tick-$id").fetchSemanticsNodes().isEmpty() }
        rule.onRoot().captureRoboImage("build/screens/45_ticked_to_completed.png")
        // and back: it is open again
        runBlocking { repo.reopen(id) }
        waitFor("open again") { !done() }
        quiet(repo)
    }

    /** Beta is on screen: select it, make it repeat every 2 weeks, see the label, complete it and see when it comes back. */
    private fun makeATaskRepeat(repo: Repository, id: Long) {
        if (rule.onAllNodesWithContentDescription("Repeat").fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithText("Beta").performTouchInput { click(Offset(width * 0.4f, 40f)) }
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("Repeat").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("repeat-more").performClick()
        rule.onNodeWithTag("repeat-unit-WEEK").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Every 2 weeks").assertIsDisplayed()
        rule.onNodeWithText("Repeat").performClick()
        waitFor("repeat saved") { repo.data.value!!.tasks.first { it.id == id }.repeat == Repeat(2, RepeatUnit.WEEK) }
        rule.waitForIdle()
        org.junit.Assert.assertTrue(rule.onAllNodesWithTag("repeat-label-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("repeat-label-$id", useUnmergedTree = true).assertIsDisplayed()
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("Every 2 weeks").fetchSemanticsNodes().isNotEmpty())
        rule.onRoot().captureRoboImage("build/screens/42_repeating_task.png")

        // completing it moves it to "Completed", where it says when it comes back
        runBlocking { repo.complete(id) }
        waitFor("beta completed") { repo.data.value!!.tasks.first { it.id == id }.done }
        quiet(repo)
        // the Completed part of the list is open: scroll down to it
        waitFor("comes-back label shows") {
            runCatching { rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("back-$id")) }.isSuccess
        }
        waitFor("comes-back label") { rule.onAllNodesWithTag("back-$id", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screens/43_repeating_completed.png")
    }

    private data class Quad(val a: Long, val b: Long, val c: Long, val d: List<Long>)
}
