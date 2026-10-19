package com.focustask.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.focustask.Fmt
import com.focustask.core.BarKind
import com.focustask.core.Mode
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

/** The small widget: one task, the sun strip, the focus timer and Done. */
class FocusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = loadWidgetModel(context)
        provideContent { SmallContent(context, model) }
    }
}

/** "13 min" style timer text. Widgets update every minute, so no seconds. */
fun widgetTimer(seconds: Long): String {
    val m = ((seconds + 59) / 60).coerceAtLeast(0)
    return when {
        m < 60 -> "$m min"
        m % 60 == 0L -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }
}

@Composable
fun SmallContent(ctx: Context, m: WidgetModel) {
    val p = m.palette
    val f = m.focus
    val bar = f.bar
    val ink = p.ink
    Column(
        GlanceModifier.fillMaxSize().background(m.background.cp()).cornerRadius(24.dp)
            .clickable(openAction()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        // top line: due text + block name / time left
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            val top = when (f.mode) {
                Mode.FOCUS -> {
                    val cur = f.carousel!!.current
                    when {
                        m.overdue -> "⚠ " + (cur.deadline?.let { Fmt.late(it, m.now).uppercase() } ?: "OVERDUE")
                        cur.deadline != null -> "Due " + Fmt.deadline(cur.deadline!!, m.now)
                        else -> "No deadline"
                    }
                }
                Mode.BREAK -> "Long break"
                Mode.ALL_DONE -> "Between blocks"
            }
            Text(top, style = text(if (m.overdue) Color(0xFFFFE08A) else p.muted, 12, bold = m.overdue), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            val right = when {
                m.ending && f.active != null -> {
                    val left = java.time.Duration.between(m.now, f.active!!.end).seconds
                    "Block ends in ${widgetTimer(left)}"
                }
                f.active != null -> "${f.active!!.block.name} · ${bar.blockLeftMin ?: 0} min left"
                bar.kind == BarKind.WAIT_NEXT -> "${bar.nextName} · starts ${bar.endsAt?.let { Fmt.time(it) } ?: ""}"
                bar.kind == BarKind.LONG_BREAK -> "ends ${bar.endsAt?.let { Fmt.time(it) } ?: ""}"
                else -> ""
            }
            Text(right, style = text(if (m.ending) p.danger else p.accent, 12, bold = true), maxLines = 1)
        }
        Spacer(GlanceModifier.height(6.dp))
        val title = when (f.mode) {
            Mode.FOCUS -> f.carousel!!.current.task.title
            Mode.BREAK -> "Take a real break"
            Mode.ALL_DONE -> "All done"
        }
        Text(title, style = text(ink, 20, bold = true), maxLines = 2, modifier = GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.height(6.dp))
        StripImage(ctx, m)
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            val label = when (bar.kind) {
                BarKind.FOCUS_WORK -> "Focus ${widgetTimer(bar.seconds)}"
                BarKind.FOCUS_SHORT_BREAK -> "Break ${widgetTimer(bar.seconds)}"
                BarKind.WAIT_NEXT -> "${bar.nextName} in ${widgetTimer(bar.seconds)}"
                BarKind.LONG_BREAK -> "Break ${widgetTimer(bar.seconds)}"
                BarKind.IDLE -> "Nothing scheduled"
            }
            Text(label, style = text(ink, 20, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (f.mode == Mode.FOCUS) {
                DoneButton(m)
            }
        }
    }
}

@Composable
fun DoneButton(m: WidgetModel) {
    val p = m.palette
    val cur = m.focus.carousel!!.current
    Button(
        text = "Done",
        onClick = doneAction(cur.task.id),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = (if (m.overdue) Color.White else p.accent).cp(),
            contentColor = (if (m.overdue) p.bg else p.onAccent).cp(),
        ),
    )
}
