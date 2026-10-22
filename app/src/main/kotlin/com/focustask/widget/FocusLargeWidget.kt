package com.focustask.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.focustask.Words
import com.focustask.core.Carousel
import com.focustask.core.Mode

class FocusLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusLargeWidget()
}

/** The large widget: the wheel of Today (the task before, the one at hand, the next one) and the timer. */
class FocusLargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = loadWidgetModel(context)
        provideContent { LargeContent(context, model) }
    }
}

@Composable
fun LargeContent(ctx: Context, m: WidgetModel) {
    val f = m.focus
    Column(
        GlanceModifier.fillMaxSize().background(m.background.cp()).widgetCorners()
            .clickable(openAction()).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        StatusPill(m)
        Spacer(GlanceModifier.height(8.dp))
        if (f.mode == Mode.FOCUS && f.carousel != null) {
            Wheel(m, f.carousel!!)
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                StateText(m, titleSize = 24)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        StripImage(ctx, m)
        Spacer(GlanceModifier.height(6.dp))
        TimerRow(ctx, m, numberSp = 34f)
    }
}

@Composable
private fun ColumnScope.Wheel(m: WidgetModel, c: Carousel) {
    val p = m.palette
    Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        c.parent?.let { Text(it.title, style = text(p.ink, 13, bold = true), maxLines = 1) ; Spacer(GlanceModifier.height(4.dp)) }
        c.previous?.let { OtherTaskCard(m, it, c.previousLabel, before = true, skipped = c.previousSkipped); Spacer(GlanceModifier.height(6.dp)) }
        CurrentTaskCard(m, titleSize = 20, titleLines = 2)
        c.next?.let { Spacer(GlanceModifier.height(6.dp)); OtherTaskCard(m, it, c.nextLabel, before = false, skipped = false) }
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(
                if (m.overdue) "Overdue first, then ${m.focus.active?.block?.name ?: "your day"} continues"
                else Words.position(c, c.current.list.title),
                style = text(p.muted, 12), maxLines = 1,
            )
        }
    }
}
