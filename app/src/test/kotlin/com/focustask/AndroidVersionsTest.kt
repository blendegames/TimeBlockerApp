package com.focustask

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.focustask.alerts.Alerts
import com.focustask.ui.components.TimerAnimation
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The newest and the oldest Android the app runs on: Android 16 (API 36), where the notification is a Live Update and
 * the wallpaper colors can be switched on, and Android 8.0 (API 26), where neither exists and the app must still work.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class AndroidVersionsTest {
    init { TimerAnimation.smooth = false }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<FocusApp>()

    private fun settle() {
        TestData.ensure(app)
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end) {
            rule.waitForIdle()
            if (rule.onAllNodesWithContentDescription("Done").fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
    }

    private fun postedNotification(): Notification {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { Alerts.refresh(app) }
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return shadowOf(nm).allNotifications.single()
    }

    private fun actions(n: Notification) = n.actions.orEmpty().map { it.title.toString() }

    @Config(sdk = [36])
    @Test fun android16_theNotificationIsALiveUpdateWithTheBlockInParts() {
        settle()
        val n = postedNotification()
        assertEquals("a progress-style notification", "android.app.Notification\$ProgressStyle", n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertTrue("asks to be a Live Update", n.extras.getBoolean("android.requestPromotedOngoing"))
        assertTrue("ongoing", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        val style = Notification.Builder.recoverBuilder(app, n).style as Notification.ProgressStyle
        assertTrue("the block is split into work and break parts", style.progressSegments.size >= 2)
        assertTrue("how far along the block is", style.progress in 0..style.progressSegments.sumOf { it.length })
        // Google's rules for a Live Update (developer.android.com, "Live Updates"). Not n.hasPromotableCharacteristics():
        // Robolectric's Android 16 is an early build whose version of that check wants the opposite of the colorized rule.
        assertTrue("has a content title", !n.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty())
        assertFalse("not colorized", n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertTrue("no custom views", n.contentView == null && n.bigContentView == null)
        assertTrue("not a group summary", n.flags and Notification.FLAG_GROUP_SUMMARY == 0)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue("the channel is not of minimum importance", nm.getNotificationChannel(n.channelId).importance > NotificationManager.IMPORTANCE_MIN)
        val declared = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("declares the Live Update permission", "android.permission.POST_PROMOTED_NOTIFICATIONS" in declared)
        assertEquals(listOf("Done", "Skip"), actions(n))
        assertTrue("the countdown chip counts down", n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Config(sdk = [26])
    @Test fun android8_theNotificationIsTheClassicOne() {
        settle()
        val n = postedNotification()
        assertFalse("no Live Update template before Android 16", n.extras.getString(Notification.EXTRA_TEMPLATE)?.contains("ProgressStyle") == true)
        assertTrue("still has the block's progress bar", n.extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0)
        assertEquals(listOf("Done", "Skip"), actions(n))
    }

    private fun openScreenSettings() {
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("settings-screen").performClick()
        rule.waitForIdle()
    }

    @Config(sdk = [36])
    @Test fun android16_wallpaperColorsCanBeSwitchedOn() {
        settle()
        openScreenSettings()
        rule.onNodeWithTag("wallpaper-colors").assertIsDisplayed().performClick()
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end && app.repo.data.value?.settings?.wallpaperColors != true) { rule.waitForIdle(); Thread.sleep(50) }
        assertTrue(app.repo.data.value!!.settings.wallpaperColors)
        // the whole app is drawn with the wallpaper's colors: every tab still works
        for (tab in listOf("Day", "Week", "Lists", "Today")) { rule.onNodeWithText(tab).performClick(); rule.waitForIdle() }
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/72_android16_wallpaper_today.png")
        rule.onNodeWithTag("carousel").assertIsDisplayed()
    }

    @Config(sdk = [26])
    @Test fun android8_runsWithoutTheNewerFeatures() {
        settle()
        rule.onNodeWithTag("carousel").assertIsDisplayed()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/73_android8_today.png")
        for (tab in listOf("Day", "Week", "Lists", "Today")) { rule.onNodeWithText(tab).performClick(); rule.waitForIdle() }
        openScreenSettings()
        assertTrue("no wallpaper colors before Android 12", rule.onAllNodesWithTag("wallpaper-colors").fetchSemanticsNodes().isEmpty())
    }
}
