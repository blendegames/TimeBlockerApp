package com.focustask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.focustask.core.FocusEngine
import com.focustask.core.Task
import com.focustask.core.TimeBlock
import com.focustask.core.TodoList
import com.focustask.core.UserBreak
import com.focustask.data.AppData
import com.focustask.data.Settings
import com.focustask.ui.AppScaffold
import com.focustask.ui.BlockTarget
import com.focustask.ui.components.BottomNav
import com.focustask.ui.components.Tab
import com.focustask.ui.components.TimerBarView
import com.focustask.ui.components.barTone
import com.focustask.ui.screens.AlertSoundsScreen
import com.focustask.ui.screens.BlockEditorScreen
import com.focustask.ui.screens.ListEditScreen
import com.focustask.ui.theme.FocusTheme
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.paletteFor
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = FocusApp::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun at(h: Int, m: Int = 0, day: Int = 19) = LocalDateTime.of(2026, 10, day, h, m)

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val blocks = listOf(
        TimeBlock(1, "Work", LocalTime.of(9, 0), LocalTime.of(11, 0), weekdays),
        TimeBlock(2, "Admin", LocalTime.of(11, 30), LocalTime.of(12, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        TimeBlock(3, "Home", LocalTime.of(18, 0), LocalTime.of(20, 0)),
    )
    private val lists = listOf(
        TodoList(10, "Work", 1, 0), TodoList(20, "Admin", 2, 1), TodoList(30, "Home", 3, 2), TodoList(40, "Someday", null, 3),
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
    ): UiState {
        val data = AppData(
            blocks = blocks,
            colors = mapOf(1L to 0, 2L to 1, 3L to 2),
            lists = lists,
            tasks = tasks,
            changes = emptyMap(),
            settings = Settings(userBreak = userBreak),
        )
        val focus = FocusEngine.build(now, data::blocksFor, data.lists, data.tasks, data.settings.cycle, data.settings.userBreak)
        return UiState(data, focus, now)
    }

    private fun vm() = MainViewModel(ApplicationProvider.getApplicationContext<FocusApp>())

    private fun save(name: String) {
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun app(name: String, s: UiState, tab: Tab = Tab.TODAY) {
        val vm = vm()
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { AppScaffold(s, vm, tab) } } }
        rule.waitForIdle()
        save(name)
    }

    /** A screen shown directly, with the compact timer bar and the tab bar under it. */
    private fun direct(name: String, s: UiState, tab: Tab, content: @Composable (com.focustask.ui.theme.Palette, MainViewModel) -> Unit) {
        val vm = vm()
        rule.setContent {
            FocusTheme {
                val p = paletteFor(Tone.FOCUS, false)
                Box(Modifier.size(390.dp, 844.dp).background(p.bg)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).statusBarsPadding()) { content(p, vm) }
                        TimerBarView(s.focus, paletteFor(barTone(s.focus, false), false), false, s.data.settings.alertMode, {})
                        BottomNav(tab, false, p) {}
                    }
                }
            }
        }
        rule.waitForIdle()
        save(name)
    }

    @Test fun today_focus() = app("01_today_focus", state(at(10, 12), tasks()))
    @Test fun today_subtask() = app("02_today_subtask", state(at(10, 12), tasks(subtasks = true)))
    @Test fun today_overdue() = app("03_today_overdue", state(at(10, 12), tasks(overdue = true)))
    @Test fun today_all_done() = app("04_today_all_done", state(at(11, 20), tasks(allDone = true)))
    @Test fun today_break() = app("05_today_break", state(at(11, 7), tasks(allDone = true), UserBreak(at(11, 7), at(11, 30))))
    @Test fun lock_dialog_in_break() {
        val s = state(at(11, 7), tasks(allDone = true), UserBreak(at(11, 7), at(11, 30)))
        val vm = vm()
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { AppScaffold(s, vm, Tab.TODAY) } } }
        rule.onNodeWithText("Day").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Nothing changes during a break").assertIsDisplayed()
        File("build/screens").mkdirs()
        captureScreenRoboImage("build/screens/06b_lock_dialog.png")
    }

    @Test fun today_start_of_list() {
        app("06_today_start", state(at(10, 12), tasks().filterNot { it.id == 1L }))
    }
    @Test fun today_ending() = app("07_today_ending", state(at(10, 51), tasks()))
    @Test fun day() = app("10_day", state(at(10, 12), tasks(overdue = true)), Tab.DAY)
    @Test fun week() = app("11_week", state(at(10, 12), tasks()), Tab.WEEK)
    @Test fun lists() = app("12_lists", state(at(10, 12), tasks(overdue = true)), Tab.LISTS)
    @Test fun settings() = app("13_settings", state(at(10, 12), tasks()), Tab.SETTINGS)

    @Test fun list_edit() {
        val s = state(at(10, 12), tasks(subtasks = true))
        direct("20_list_edit", s, Tab.LISTS) { p, vm -> ListEditScreen(s, vm, 10, p) {} }
    }

    @Test fun list_edit_selected() {
        val s = state(at(10, 12), tasks(subtasks = true))
        val vm = vm()
        rule.setContent {
            FocusTheme {
                val p = paletteFor(Tone.FOCUS, false)
                Box(Modifier.size(390.dp, 844.dp).background(p.bg).statusBarsPadding()) { ListEditScreen(s, vm, 10, p) {} }
            }
        }
        rule.onNodeWithText("Write the Q3 budget summary").performClick()
        rule.waitForIdle()
        save("23_list_edit_selected")
    }

    @Test fun sounds() {
        val s = state(at(10, 12), tasks())
        direct("21_sounds", s, Tab.SETTINGS) { p, vm -> AlertSoundsScreen(s, vm, p) {} }
    }

    @Test fun block_editor() {
        val s = state(at(10, 12), tasks())
        direct("22_block_editor", s, Tab.WEEK) { p, vm -> BlockEditorScreen(s, vm, BlockTarget.Edit(1), p) {} }
    }
}
