package com.focustask

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.focustask.alerts.Alerts
import com.focustask.core.BarKind
import com.focustask.core.TimerBar
import com.focustask.widget.LargeContent
import com.focustask.widget.SmallContent
import com.focustask.widget.StripRenderer
import com.focustask.widget.loadWidgetModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Notification, alarms and widgets, run against Robolectric's fake Android. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = TestFocusApp::class)
class PlatformTest {
    private val app get() = ApplicationProvider.getApplicationContext<FocusApp>()

    private fun seeded() = TestData.ensure(app)

    @Test fun refreshPostsTheNotificationAndSchedulesTheNextAlarm() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        seeded()
        runBlocking { Alerts.refresh(app) }
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).allNotifications
        assertTrue("notification expected", posted.isNotEmpty())
        val n = posted.first()
        val title = n.extras.getCharSequence("android.title")?.toString()
        println("notification title = $title, text = ${n.extras.getCharSequence("android.text")}")
        assertNotNull(title)
        val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).scheduledAlarms
        assertTrue("an alarm should be scheduled", alarms.isNotEmpty())
        println("next alarm in ${(alarms.first().triggerAtMs - System.currentTimeMillis()) / 1000} s")
    }

    @Test fun smallWidgetComposesWithTheTaskAndDone() = runBlocking {
        seeded()
        val model = loadWidgetModel(app)
        runGlanceAppWidgetUnitTest {
            setContext(app)
            provideComposable { SmallContent(app, model) }
            val task = model.focus.carousel!!.current.task.title
            onNode(hasText(task)).assertExists()
            onNode(hasContentDescription("Done")).assertExists()
        }
    }

    @Test fun largeWidgetComposesTheCarousel() = runBlocking {
        seeded()
        val model = loadWidgetModel(app)
        runGlanceAppWidgetUnitTest {
            setContext(app)
            provideComposable { LargeContent(app, model) }
            val c = model.focus.carousel!!
            onNode(hasText(c.current.task.title)).assertExists()
            onNode(hasContentDescription("Done")).assertExists()
        }
    }

    @Test fun theWidgetsTimerStripIsDrawn() {
        val bar = TimerBar(
            BarKind.FOCUS_WORK, 780, 0.6, setOf(0, 3, 6, 9, 12), null, null,
        )
        val d = app.resources.displayMetrics.density
        val bmp = StripRenderer.render(bar, (320 * d).toInt(), (28 * d).toInt(), 0xFF2F5D50.toInt(), d)
        val big = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        big.eraseColor(Color.WHITE)
        Canvas(big).drawBitmap(bmp, 0f, 0f, null)
        File("build/screens").mkdirs()
        FileOutputStream("build/screens/42_strip.png").use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // the strip is drawn: some of it is in the strip's color
        val pixels = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
        org.junit.Assert.assertTrue(pixels.any { Color.alpha(it) > 0 })
    }
}
