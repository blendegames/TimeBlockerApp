package com.focustask

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.focustask.core.FocusEngine
import com.focustask.core.Task
import com.focustask.core.TimeBlock
import com.focustask.core.TodoList
import com.focustask.data.AppData
import com.focustask.data.Settings
import com.focustask.ui.AppScaffold
import com.focustask.ui.components.Tab
import com.focustask.ui.theme.FocusTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-night-xhdpi", application = FocusApp::class)
class DarkScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun darkToday() {
        val now = LocalDateTime.of(2026, 10, 19, 10, 12)
        val blocks = listOf(TimeBlock(1, "Work", LocalTime.of(9, 0), LocalTime.of(11, 0)))
        val lists = listOf(TodoList(10, "Work", 1, 0))
        val tasks = listOf(
            Task(1, 10, "Pull the Q3 numbers", done = true, completedAt = now.withHour(9).withMinute(41)),
            Task(2, 10, "Write the Q3 budget summary", deadline = now.withHour(12).withMinute(0), position = 1),
            Task(3, 10, "Review pull request #42", deadline = now.plusDays(1).withHour(17).withMinute(0), position = 2),
        )
        val data = AppData(blocks, mapOf(1L to 0), lists, tasks, emptyMap(), Settings())
        val focus = FocusEngine.build(now, data::blocksFor, lists, tasks, data.settings.cycle, null)
        val vm = MainViewModel(ApplicationProvider.getApplicationContext<FocusApp>())
        rule.setContent { FocusTheme { Box(Modifier.size(390.dp, 844.dp)) { AppScaffold(UiState(data, focus, now), vm, Tab.TODAY) } } }
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/50_dark_today.png")
    }
}
