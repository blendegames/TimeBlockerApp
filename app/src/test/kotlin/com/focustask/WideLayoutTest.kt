package com.focustask

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.focustask.ui.components.TimerAnimation
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A phone on its side: the tabs move to a rail, Today puts the timer beside the wheel, Lists gets two panes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w844dp-h390dp-land-xhdpi", application = TestFocusApp::class)
class WideLayoutTest {
    init { TimerAnimation.smooth = false }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun settle() {
        TestData.ensure(ApplicationProvider.getApplicationContext())
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle()
            if (rule.onAllNodesWithContentDescription("Done").fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
    }

    @Test fun landscapeUsesARailTheTimerBesideTheWheelAndTwoPanes() {
        settle()
        File("build/screens").mkdirs()
        rule.onNodeWithTag("side-nav").assertIsDisplayed()
        rule.onNodeWithTag("carousel").assertIsDisplayed()
        rule.onNodeWithTag("timer-bar").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/screens/70_landscape_today.png")

        rule.onNodeWithText("Lists").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pick a list on the left to see all its tasks here.").assertIsDisplayed()
        // open the first list: its tasks show in the pane on the right, the overview stays on the left
        val first = (rule.activity.application as FocusApp).repo.data.value!!.lists.first().title
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(first))
        rule.onAllNodesWithText(first)[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("list-pane").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("list-open").fetchSemanticsNodes().isNotEmpty())
        rule.onRoot().captureRoboImage("build/screens/71_landscape_lists.png")
    }
}
