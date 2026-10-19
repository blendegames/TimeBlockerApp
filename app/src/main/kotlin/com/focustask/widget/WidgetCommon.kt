package com.focustask.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.focustask.FocusApp
import com.focustask.MainActivity
import com.focustask.alerts.Alerts
import com.focustask.core.BarKind
import com.focustask.core.FocusEngine
import com.focustask.core.FocusState
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.data.AppData
import com.focustask.ui.components.barTone
import com.focustask.ui.theme.Palette
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.paletteFor
import kotlinx.coroutines.delay
import java.time.LocalDateTime

object FocusWidgets {
    suspend fun updateAll(ctx: Context) {
        runCatching { FocusWidget().updateAll(ctx) }
        runCatching { FocusLargeWidget().updateAll(ctx) }
    }
}

class WidgetModel(val data: AppData, val focus: FocusState, val tone: Tone, val palette: Palette, val dark: Boolean) {
    val overdue get() = tone == Tone.OVERDUE
    val ending get() = tone == Tone.ENDING
    val now: LocalDateTime get() = focus.now

    /** Background of the whole widget. */
    val background: Color
        get() = when (tone) {
            Tone.OVERDUE -> palette.bg
            Tone.ENDING, Tone.BREAK -> palette.card
            Tone.FOCUS -> palette.surface
        }
}

suspend fun loadWidgetModel(ctx: Context): WidgetModel {
    val data = FocusApp.instance.repo.awaitData()
    val now = LocalDateTime.now()
    val focus = FocusEngine.build(now, data::blocksFor, data.lists, data.tasks, data.settings.cycle, data.settings.userBreak)
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val overdue = focus.mode == Mode.FOCUS && focus.carousel?.current?.reason == Reason.OVERDUE
    val tone = barTone(focus, overdue)
    return WidgetModel(data, focus, tone, paletteFor(tone, dark), dark)
}

fun Color.cp() = ColorProvider(this)

fun text(color: Color, size: Int, bold: Boolean = false) =
    TextStyle(color = color.cp(), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@androidx.compose.runtime.Composable
fun StripImage(ctx: Context, m: WidgetModel, heightDp: Int = 28) {
    val d = ctx.resources.displayMetrics.density
    val bmp = StripRenderer.render(m.focus.bar, (320 * d).toInt(), (heightDp * d).toInt(), m.palette.bubble.toArgb(), d)
    Image(
        provider = ImageProvider(bmp), contentDescription = "Time strip",
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
        contentScale = ContentScale.FillBounds,
    )
}

private val TaskIdKey = ActionParameters.Key<Long>("task_id")

fun doneAction(taskId: Long) = actionRunCallback<DoneAction>(actionParametersOf(TaskIdKey to taskId))
fun openAction() = actionStartActivity<MainActivity>()

class DoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        FocusApp.instance.repo.complete(id)
        delay(700)
        Alerts.refresh(context)
    }
}
