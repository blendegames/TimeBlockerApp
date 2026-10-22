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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.focustask.core.Mode

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

/** The small widget: the task card of Today (list color, tick circle) and the focus timer. */
class FocusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = loadWidgetModel(context)
        provideContent { SmallContent(context, model) }
    }
}

@Composable
fun SmallContent(ctx: Context, m: WidgetModel) {
    Column(
        GlanceModifier.fillMaxSize().background(m.background.cp()).widgetCorners()
            .clickable(openAction()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (m.focus.mode == Mode.FOCUS && m.focus.carousel != null) {
            CurrentTaskCard(m, titleSize = 18, titleLines = 2)
        } else {
            StatusPill(m)
            Spacer(GlanceModifier.height(6.dp))
            StateText(m, titleSize = 20)
        }
        Spacer(GlanceModifier.height(8.dp))
        Column(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp)) { TimerRow(ctx, m, numberSp = 26f) }
    }
}
