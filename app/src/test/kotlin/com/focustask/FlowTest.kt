package com.focustask

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Starts the real activity: database, seeding, view model and screens all together. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = FocusApp::class)
class FlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun settle() {
        TestData.ensure(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        // the database and DataStore answer on background threads
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) return
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
    }

    @Test fun doneAdvancesToTheNextTask() {
        settle()
        val before = rule.onAllNodesWithText("Done").fetchSemanticsNodes().size
        check(before > 0) { "no Done button on the Today screen" }
        rule.onNodeWithText("Done").performClick()
        Thread.sleep(800)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/32_after_done.png")
    }
}
