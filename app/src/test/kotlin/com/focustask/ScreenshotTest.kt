package com.focustask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.focustask.core.Task
import com.focustask.core.TimeBlock
import com.focustask.core.TodoList
import com.focustask.core.UserBreak
import com.focustask.data.AppData
import com.focustask.data.DayChange
import com.focustask.data.Settings
import com.focustask.ui.AppScaffold
import com.focustask.ui.BlockTarget
import com.focustask.ui.components.BottomNav
import com.focustask.ui.components.IdleTimerView
import com.focustask.ui.components.Tab
import com.focustask.ui.components.TimerAnimation
import com.focustask.ui.components.TimerBarView
import com.focustask.ui.components.barTone
import com.focustask.ui.screens.AlertSoundsScreen
import com.focustask.ui.screens.BlockEditorScreen
import com.focustask.ui.screens.ListsScreen
import com.focustask.ui.screens.SettingsSection
import com.focustask.ui.theme.FocusTheme
import com.focustask.ui.theme.Palette
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.paletteFor
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class ScreenshotTest {
    init { TimerAnimation.smooth = false }
    @get:Rule val rule = createComposeRule()

    private fun at(h: Int, m: Int = 0, day: Int = 19) = LocalDateTime.of(2026, 10, day, h, m)

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val blocks = listOf(
        TimeBlock(1, "Work", LocalTime.of(9, 0), LocalTime.of(11, 0), weekdays, listId = 10),
        TimeBlock(2, "Admin", LocalTime.of(11, 30), LocalTime.of(12, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), listId = 20),
        TimeBlock(3, "Home", LocalTime.of(18, 0), LocalTime.of(20, 0), listId = 30),
        // a second block of the Work list: same list, same color
        TimeBlock(4, "Work", LocalTime.of(14, 0), LocalTime.of(15, 30), setOf(DayOfWeek.SATURDAY), listId = 10),
    )
    private val lists = listOf(
        TodoList(10, "Work", 0), TodoList(20, "Admin", 1), TodoList(30, "Home", 2), TodoList(40, "Someday", 3),
    )

    private fun tasks(overdue: Boolean = false, allDone: Boolean = false, subtasks: Boolean = false): List<Task> {
        val l = ArrayList<Task>()
        l += Task(1, 10, "Pull the Q3 numbers", position = 0, done = true, completedAt = at(9, 41))
        if (subtasks) {
            l += Task(2, 10, "Write the Q3 budget summary", deadline = at(12), position = 1)
            l += Task(3, 10, "Pull the numbers", parentId = 2, position = 0, done = true, completedAt = at(9, 41))
            l += Task(4, 10, "Draft the summary", parentId = 2, position = 1, deadline = at(11))
            l += Task(5, 10, "Send it to Dana", parentId = 2, position = 2, deadline = at(12))
        } else {
            l += Task(2, 10, "Write the Q3 budget summary", deadline = at(12), position = 1, done = allDone, completedAt = if (allDone) at(10, 50) else null)
        }
        l += Task(6, 10, "Review pull request #42", deadline = at(17, day = 20), position = 2, done = allDone, completedAt = if (allDone) at(10, 55) else null)
        l += Task(7, 10, "Email Dana about the contract", position = 3, done = allDone, completedAt = if (allDone) at(10, 58) else null)
        l += Task(8, 20, "Submit the expense report", deadline = at(12, day = 21), position = 0)
        l += Task(9, 20, "Book the dentist", position = 1)
        l += Task(10, 30, "Pay the electricity bill", deadline = at(19, day = 23), position = 1)
        l += Task(11, 30, "Water the plants", position = 2)
        l += Task(12, 40, "Learn to juggle", position = 0)
        if (overdue) l += Task(13, 30, "Renew car insurance", deadline = at(16, day = 17), position = 0)
        return l
    }

    private fun state(
        now: LocalDateTime, tasks: List<Task>, userBreak: UserBreak? = null,
        changes: Map<Pair<Long, LocalDate>, DayChange> = emptyMap(),
    ): UiState {
        val data = AppData(
            blocks = blocks,
            colors = mapOf(10L to 0, 20L to 1, 30L to 2, 40L to 3),
            lists = lists,
            tasks = tasks,
            changes = changes,
            settings = Settings(userBreak = userBreak),
        )
        return UiState(data, data.focusAt(now), now)
    }

    private fun vm() = MainViewModel(ApplicationProvider.getApplicationContext<FocusApp>())

    private fun save(name: String) {
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    /** Shows the whole app on [tab], checks that [expect] is on screen and saves a picture to look at. */
    private fun app(name: String, s: UiState, tab: Tab = Tab.TODAY, section: SettingsSection? = null, expect: String? = null) {
        val vm = vm()
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { AppScaffold(s, vm, tab, section) } } }
        rule.waitForIdle()
        expect?.let { rule.onNodeWithText(it, substring = true).assertExists() }
        save(name)
    }

    /** A screen shown directly, with the compact timer bar and the tab bar under it. */
    private fun direct(name: String, s: UiState, tab: Tab, content: @Composable (Palette, MainViewModel) -> Unit) {
        val vm = vm()
        rule.setContent {
            FocusTheme {
                val p = paletteFor(Tone.FOCUS, false)
                Box(Modifier.size(390.dp, 844.dp).background(p.bg)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).statusBarsPadding()) { content(p, vm) }
                        TimerBarView(s.focus, paletteFor(barTone(s.focus), false), false, s.data.settings.alertMode, {})
                        BottomNav(tab, false, p) {}
                    }
                }
            }
        }
        rule.waitForIdle()
        save(name)
    }

    /** A fresh install: no lists, no blocks, no tasks. */
    private fun emptyState(): UiState {
        val data = AppData(
            blocks = emptyList(), colors = emptyMap(), lists = emptyList(), tasks = emptyList(),
            changes = emptyMap(), settings = Settings(),
        )
        val now = at(10, 12)
        return UiState(data, data.focusAt(now), now)
    }
    @Test fun aFreshInstallShowsEveryTabEmpty() {
        val vm = vm()
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { AppScaffold(emptyState(), vm) } } }
        rule.waitForIdle()
        rule.onNodeWithText("No time blocks are planned", substring = true).assertExists()
        save("60_fresh_today")
        for ((tab, name) in listOf("Day" to "61_fresh_day", "Week" to "62_fresh_week", "Lists" to "63_fresh_lists")) {
            rule.onNodeWithText(tab).performClick()
            rule.waitForIdle()
            save(name)
        }
        rule.onNodeWithText("No lists yet", substring = true).assertExists()
    }

    @Test fun today_focus() = app("01_today_focus", state(at(10, 12), tasks()), expect = "YOUR ONE TASK")
    @Test fun today_subtask() = app("02_today_subtask", state(at(10, 12), tasks(subtasks = true)), expect = "SUBTASK 2 OF 3")
    @Test fun today_overdue() = app("03_today_overdue", state(at(10, 12), tasks(overdue = true)), expect = "OVERDUE")
    @Test fun today_all_done() = app("04_today_all_done", state(at(10, 20), tasks(allDone = true).filter { it.listId != 40L }), expect = "All done")
    @Test fun today_waiting_between_blocks() =
        app("04b_today_waiting", state(at(11, 20), tasks().filter { it.listId != 40L }), expect = "Waiting for the next time block")
    @Test fun today_break() = app("05_today_break", state(at(11, 7), tasks(allDone = true), UserBreak(at(11, 7), at(11, 30))), expect = "Breaks can't be skipped")
    @Test fun lock_dialog_in_break() {
        val s = state(at(11, 7), tasks(allDone = true), UserBreak(at(11, 7), at(11, 30)))
        val vm = vm()
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { AppScaffold(s, vm, Tab.TODAY) } } }
        rule.onNodeWithText("Day").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Nothing changes during a long break").assertIsDisplayed()
        File("build/screens").mkdirs()
        captureScreenRoboImage("build/screens/06b_lock_dialog.png")
    }

    @Test @Config(qualifiers = "w390dp-h844dp-night-xhdpi")
    fun today_dark() = app("50_dark_today", state(at(10, 12), tasks()), expect = "YOUR ONE TASK")

    @Test fun dayDiffersFromPlan() {
        val now = at(10, 12)
        app("10b_day_differs", state(now, tasks(overdue = false),
            changes = mapOf((1L to now.toLocalDate()) to DayChange(10 * 60, 12 * 60, false))), Tab.DAY, expect = "Keep weekly schedule")
    }

    @Test fun day() = app("10_day", state(at(10, 12), tasks(overdue = true)), Tab.DAY)
    @Test fun week() = app("11_week", state(at(10, 12), tasks()), Tab.WEEK)
    @Test fun lists() = app("12_lists", state(at(10, 12), tasks(overdue = true)), Tab.LISTS, expect = "Renew car insurance")
    @Test fun settings() = app("13_settings", state(at(10, 12), tasks()), Tab.SETTINGS, expect = "Your data")
    @Test fun settings_timer() = app("13a_settings_timer", state(at(10, 12), tasks()), Tab.SETTINGS, SettingsSection.TIMER, expect = "Long break after blocks of")
    @Test fun settings_alerts() = app("13c_settings_alerts", state(at(10, 12), tasks()), Tab.SETTINGS, SettingsSection.ALERTS, expect = "Alert sounds")
    @Test fun settings_screen() = app("13d_settings_screen", state(at(10, 12), tasks()), Tab.SETTINGS, SettingsSection.SCREEN, expect = "Keep screen on")

    @Test fun idleTimerViewIsPureBlackWithTheTimer() {
        val s = state(at(10, 12), tasks())
        rule.setContent {
            FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { IdleTimerView(s.focus, Tone.FOCUS, { 1f }) } }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("idle-timer-view").assertIsDisplayed()
        save("66_idle_timer_view")
    }

    @Test fun idleTimerViewIsNotThereBeforeItStartsToFade() {
        val s = state(at(10, 12), tasks())
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { IdleTimerView(s.focus, Tone.FOCUS, { 0f }) } } }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(0, rule.onAllNodesWithTag("idle-timer-view").fetchSemanticsNodes().size)
    }
    @Test fun deleteAllDataAsksFirstAndOnlyDeletesAfterTheConfirmation() {
        val repo = ApplicationProvider.getApplicationContext<FocusApp>().repo
        fun waitFor(ms: Long = 8000, ok: () -> Boolean) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end && !ok()) { rule.waitForIdle(); Thread.sleep(50) }
            org.junit.Assert.assertTrue("timed out", ok())
        }
        // data of its own: only the first test of a run sees the example lists
        val mine = runBlocking { repo.addList("To be deleted") }
        waitFor { repo.data.value?.lists?.any { it.id == mine } == true }
        app("64_settings_delete", state(at(10, 12), tasks()), Tab.SETTINGS, SettingsSection.DATA)
        // a tap on the row only asks; "Cancel" leaves everything as it was
        rule.onNodeWithTag("delete-all-row", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("Delete all your data?").assertIsDisplayed()
        rule.onNodeWithText("This cannot be undone.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(0, rule.onAllNodesWithText("Delete all your data?").fetchSemanticsNodes().size)
        org.junit.Assert.assertFalse(runBlocking { repo.hasNoUserData() })
        // asking again and confirming deletes it
        rule.onNodeWithTag("delete-all-row", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("delete-all-confirm").performClick()
        waitFor { runBlocking { repo.hasNoUserData() } }
    }

    @Test fun list_edit() {
        val s = state(at(10, 12), tasks(subtasks = true))
        direct("20_list_edit", s, Tab.LISTS) { p, vm -> ListsScreen(s, vm, p, 10) {} }
        // nothing is selected: the subtasks still show, the "add a subtask" row does not
        rule.onNodeWithText("Draft the summary").assertExists()
        rule.onNodeWithText("Send it to Dana").assertExists()
        rule.onAllNodesWithText("Add a subtask").assertCountEquals(0)
        // selecting the task adds its actions and the "Add a subtask" row
        rule.onNodeWithText("Write the Q3 budget summary").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Add a subtask").assertExists()
        save("23_list_edit_selected")
    }

    /** The overview of a list shows three subtasks in total, under their main task. */
    @Test fun lists_summer() {
        val now = at(10, 12)
        val summer = listOf(
            Task(1, 10, "Summer", position = 0),
            Task(2, 10, "Get some ice cream", parentId = 1, deadline = at(15), position = 0),
            Task(3, 10, "Get a driver's license", parentId = 1, deadline = at(19, day = 26), position = 1),
            Task(4, 10, "Get some watermelons", parentId = 1, deadline = at(16, day = 20), position = 2),
            Task(5, 10, "Go for a walk", parentId = 1, deadline = LocalDateTime.of(2026, 11, 19, 9, 0), position = 3),
        )
        app("12b_lists_summer", state(now, summer), Tab.LISTS)
        rule.onNodeWithText("Summer").assertExists()
        rule.onNodeWithText("Get some ice cream").assertExists()
        rule.onNodeWithText("Get some watermelons").assertExists()
        rule.onNodeWithText("Get a driver's license").assertExists()
        rule.onAllNodesWithText("Go for a walk").assertCountEquals(0)
    }

    @Test fun sounds() {
        val s = state(at(10, 12), tasks())
        direct("21_sounds", s, Tab.SETTINGS) { p, vm -> AlertSoundsScreen(s, vm, p) {} }
    }

    @Test fun block_editor() {
        val s = state(at(10, 12), tasks())
        direct("22_block_editor", s, Tab.WEEK) { p, vm -> BlockEditorScreen(s, vm, BlockTarget.Edit(1), p) {} }
    }

    @Test fun block_editor_tapped_spot() {
        val s = state(at(10, 12), tasks())
        direct("24_block_editor_tapped", s, Tab.WEEK) { p, vm ->
            BlockEditorScreen(s, vm, BlockTarget.New(fromDay = false, start = LocalTime.of(14, 15), day = DayOfWeek.SUNDAY), p) {}
        }
    }
}
