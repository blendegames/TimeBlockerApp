package com.focustask

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.focustask.alerts.Alerts
import com.focustask.alerts.Notifier
import com.focustask.core.FocusEngine
import com.focustask.widget.FocusLargeWidget
import com.focustask.widget.FocusWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

/** Notification, alarms and widgets, run against Robolectric's fake Android. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xhdpi", application = FocusApp::class)
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
        val model = com.focustask.widget.loadWidgetModel(app)
        androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest {
            setContext(app)
            provideComposable { com.focustask.widget.SmallContent(app, model) }
            val task = model.focus.carousel!!.current.task.title
            onNode(androidx.glance.testing.unit.hasText(task)).assertExists()
            onNode(androidx.glance.testing.unit.hasText("Done")).assertExists()
        }
    }

    @Test fun largeWidgetComposesTheCarousel() = runBlocking {
        seeded()
        val model = com.focustask.widget.loadWidgetModel(app)
        androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest {
            setContext(app)
            provideComposable { com.focustask.widget.LargeContent(app, model) }
            val c = model.focus.carousel!!
            onNode(androidx.glance.testing.unit.hasText(c.current.task.title)).assertExists()
            onNode(androidx.glance.testing.unit.hasText("Done")).assertExists()
        }
    }

    @Test fun stripBitmapDraws() {
        val bar = com.focustask.core.TimerBar(
            com.focustask.core.BarKind.FOCUS_WORK, 780, 0.6, setOf(0, 3, 6, 9, 12), null, null,
        )
        val d = app.resources.displayMetrics.density
        val bmp = com.focustask.widget.StripRenderer.render(bar, (320 * d).toInt(), (28 * d).toInt(), 0xFF2F5D50.toInt(), d)
        val big = android.graphics.Bitmap.createBitmap(bmp.width, bmp.height, android.graphics.Bitmap.Config.ARGB_8888)
        big.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(big).drawBitmap(bmp, 0f, 0f, null)
        File("build/screens").mkdirs()
        java.io.FileOutputStream("build/screens/42_strip.png").use { big.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
