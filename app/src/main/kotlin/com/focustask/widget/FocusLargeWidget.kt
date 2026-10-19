package com.focustask.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.focustask.Fmt
import com.focustask.core.BarKind
import com.focustask.core.Carousel
import com.focustask.core.Mode

class FocusLargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusLargeWidget()
}

/** The large widget: the same carousel as the Today tab, plus the timer. */
class FocusLargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = loadWidgetModel(context)
        provideContent { LargeContent(context, model) }
    }
}

@Composable
fun LargeContent(ctx: Context, m: WidgetModel) {
    val p = m.palette
    val f = m.focus
    val bar = f.bar
    Column(
        GlanceModifier.fillMaxSize().background(m.background.cp()).cornerRadius(24.dp)
            .clickable(openAction()).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // header
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            val c = f.carousel
            val left = when {
                f.mode == Mode.FOCUS && c != null && c.parent != null ->
                    "Step ${c.stepIndex} of ${c.stepCount} · Task ${c.listIndex} of ${c.listCount}"
                f.mode == Mode.FOCUS && c != null -> "Task ${c.listIndex} of ${c.listCount} in ${c.current.list.title}"
                f.mode == Mode.BREAK -> "Long break"
                else -> "Between blocks"
            }
            Text(left, style = text(if (m.overdue) Color(0xFFFFE08A) else p.muted, 12, bold = m.overdue), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            val right = when {
                m.ending && f.active != null -> "Block ends in ${widgetTimer(java.time.Duration.between(m.now, f.active!!.end).seconds)}"
                f.active != null -> "${f.active!!.block.name} · ${bar.blockLeftMin ?: 0} min left"
                bar.kind == BarKind.WAIT_NEXT -> "${bar.nextName} starts ${bar.endsAt?.let { Fmt.time(it) } ?: ""}"
                else -> ""
            }
            Text(right, style = text(if (m.ending) p.danger else p.accent, 12, bold = true), maxLines = 1)
        }
        Spacer(GlanceModifier.height(8.dp))
        if (f.mode == Mode.FOCUS && f.carousel != null) {
            CarouselBlock(m, f.carousel!!)
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text(if (f.mode == Mode.BREAK) "Take a real break" else "All done", style = text(p.ink, 24, bold = true))
                val sub = when {
                    f.mode == Mode.BREAK -> "Back at ${f.longBreak?.until?.let { Fmt.time(it) } ?: ""}"
                    f.next != null -> "${f.next!!.block.name} starts ${Fmt.time(f.next!!.start)}"
                    else -> "Nothing scheduled"
                }
                Text(sub, style = text(p.muted, 14))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        StripImage(ctx, m)
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            val label = when (bar.kind) {
                BarKind.FOCUS_WORK -> "Focus ${widgetTimer(bar.seconds)}"
                BarKind.FOCUS_SHORT_BREAK -> "Break ${widgetTimer(bar.seconds)}"
                BarKind.WAIT_NEXT -> "${bar.nextName} in ${widgetTimer(bar.seconds)}"
                BarKind.LONG_BREAK -> "Break ${widgetTimer(bar.seconds)}"
                BarKind.IDLE -> "Nothing scheduled"
            }
            Text(label, style = text(p.ink, 22, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (f.mode == Mode.FOCUS) DoneButton(m)
        }
    }
}

@Composable
private fun androidx.glance.layout.ColumnScope.CarouselBlock(m: WidgetModel, c: Carousel) {
    val p = m.palette
    Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        c.parent?.let {
            Text(it.title, style = text(p.muted, 12, bold = true), maxLines = 1)
            Spacer(GlanceModifier.height(4.dp))
        }
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)) {
            Text(
                c.previous?.let { "✓  ${it.title}" } ?: "Start of the list",
                style = TextStyle(color = p.muted.cp(), fontSize = 14.sp(), textDecoration = if (c.previous != null) TextDecoration.LineThrough else TextDecoration.None),
                maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            )
        }
        Column(
            GlanceModifier.fillMaxWidth().background(p.card.cp()).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            val d = c.current.deadline
            val label = when {
                m.overdue -> d?.let { Fmt.late(it, m.now).uppercase() } ?: "OVERDUE"
                d != null -> "Due " + Fmt.deadline(d, m.now)
                else -> "No deadline"
            }
            Text(label, style = text(if (m.overdue) p.bubble else p.muted, 12), maxLines = 1)
            Text(c.current.task.title, style = text(p.cardInk, 20, bold = true), maxLines = 2)
        }
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp)) {
            Text(
                c.next?.let { "○  ${it.title}" } ?: "End of the list",
                style = text(p.muted, 14), maxLines = 1, modifier = GlanceModifier.defaultWeight(),
            )
            c.next?.deadline?.let {
                Text(if (c.parent != null) Fmt.time(it) else Fmt.deadlineShort(it, m.now), style = text(p.muted, 12), maxLines = 1)
            }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
