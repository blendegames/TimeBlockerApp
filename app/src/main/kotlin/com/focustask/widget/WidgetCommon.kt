package com.focustask.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.focustask.FocusApp
import com.focustask.Fmt
import com.focustask.MainActivity
import com.focustask.R
import com.focustask.Words
import com.focustask.alerts.Alerts
import com.focustask.core.BarKind
import com.focustask.core.FocusState
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.core.Task
import com.focustask.data.AppData
import com.focustask.data.toEpochMillisLocal
import com.focustask.ui.components.barTexts
import com.focustask.ui.components.barTone
import com.focustask.ui.theme.OnDanger
import com.focustask.ui.theme.OverdueRed
import com.focustask.ui.theme.Palette
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.paletteFor
import java.time.LocalDateTime
import kotlinx.coroutines.delay

object FocusWidgets {
    suspend fun updateAll(ctx: Context) {
        runCatching { FocusWidget().updateAll(ctx) }
        runCatching { FocusLargeWidget().updateAll(ctx) }
    }
}

class WidgetModel(val data: AppData, val focus: FocusState, val tone: Tone, val palette: Palette, val dark: Boolean) {
    /** The task at hand is past its deadline: marked in red, like on Today. */
    val overdue get() = focus.mode == Mode.FOCUS && focus.carousel?.current?.reason == Reason.OVERDUE
    val ending get() = tone == Tone.ENDING
    val now: LocalDateTime get() = focus.now

    /** Background of the whole widget. */
    val background: Color
        get() = when (tone) {
            Tone.ENDING, Tone.BREAK -> palette.card
            Tone.FOCUS -> palette.surface
        }

    /** The color of a task's list: the cards are drawn in it, as on Today. */
    fun colorOf(t: Task): Color = Color(data.colorOfListAlways(t.listId))
}

suspend fun loadWidgetModel(ctx: Context): WidgetModel {
    val data = FocusApp.instance.repo.awaitData()
    val now = LocalDateTime.now()
    val focus = data.focusAt(now)
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val tone = barTone(focus)
    // "Match my wallpaper" covers the widgets too
    val wall = if (data.settings.wallpaperColors && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else null
    return WidgetModel(data, focus, tone, paletteFor(tone, dark, wall), dark)
}

fun Color.cp() = ColorProvider(this)

fun text(color: Color, size: Int, bold: Boolean = false) =
    TextStyle(color = color.cp(), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** The caps label style of the cards: "YOUR ONE TASK", "UP NEXT", "DONE 14:04". */
private fun caps(color: Color, size: Int = 11) =
    TextStyle(color = color.cp(), fontSize = size.sp, fontWeight = FontWeight.Bold)

/** The widget's own rounded corners: the launcher's on Android 12+, 24 dp before. */
fun GlanceModifier.widgetCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= 31) cornerRadius(android.R.dimen.system_app_widget_background_radius) else cornerRadius(24.dp)

@Composable
fun StripImage(ctx: Context, m: WidgetModel) {
    val heightDp = 28
    val d = ctx.resources.displayMetrics.density
    val bmp = StripRenderer.render(m.focus.bar, (320 * d).toInt(), (heightDp * d).toInt(), m.palette.bubble.toArgb(), d)
    Image(
        provider = ImageProvider(bmp), contentDescription = "Time strip",
        modifier = GlanceModifier.fillMaxWidth().height(heightDp.dp),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * The status line at the top, the same words as the pill on Today: the block and when it ends, OVERDUE in red,
 * "Between time blocks" or "Long break".
 */
@Composable
fun StatusPill(m: WidgetModel) {
    val f = m.focus
    if (m.overdue) {
        Text(
            "⚠  OVERDUE", style = caps(OnDanger, 12),
            modifier = GlanceModifier.background(OverdueRed.cp()).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 3.dp),
        )
        return
    }
    val p = m.palette
    val txt = when (f.mode) {
        Mode.FOCUS -> f.active?.let { "${it.block.name} · until ${Fmt.time(it.end)}" }
            ?: "${Words.WAITING_SHORT} · ${f.carousel!!.current.list.title}"
        Mode.BREAK -> f.longBreak?.let { "${Words.LONG_BREAK} · until ${Fmt.time(it.until)}" } ?: Words.LONG_BREAK
        Mode.ALL_DONE -> f.active?.let { "${it.block.name} · ${Words.ALL_DONE.lowercase()}" } ?: Words.WAITING_SHORT
    }
    Text(
        txt, style = text(p.pillInk, 12, bold = true), maxLines = 1,
        modifier = GlanceModifier.background(p.pill.cp()).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** The round tick of a card: a ring while open; a filled disc with a tick once done. A tap on the open one is Done. */
@Composable
fun TickCircle(done: Boolean, card: Color, size: Dp, onDone: androidx.glance.action.Action?) {
    val ring = GlanceModifier.size(size)
    Box(
        (if (onDone != null) ring.clickable(onDone).semantics { contentDescription = "Done" } else ring),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Image(ImageProvider(R.drawable.ic_w_disc), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(Color.White.cp()))
            Image(ImageProvider(R.drawable.ic_w_check), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(card.cp()))
        } else {
            Image(ImageProvider(R.drawable.ic_w_ring), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.75f).cp()))
        }
    }
}

/** The task at hand: the same card as on Today, in its list's color, with the tick circle. */
@Composable
fun CurrentTaskCard(m: WidgetModel, titleSize: Int, titleLines: Int) {
    val c = m.focus.carousel!!
    val cur = c.current
    val bg = m.colorOf(cur.task)
    Row(
        GlanceModifier.fillMaxWidth().background(bg.cp()).cornerRadius(20.dp).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            when {
                m.overdue -> Text(
                    Fmt.late(cur.deadline ?: m.now, m.now).uppercase(), style = caps(OnDanger),
                    modifier = GlanceModifier.background(OverdueRed.cp()).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                c.parent != null -> Text("SUBTASK ${c.stepIndex} OF ${c.stepCount}", style = caps(Color.White.copy(alpha = 0.78f)))
                else -> Text("YOUR ONE TASK", style = caps(Color.White.copy(alpha = 0.78f)))
            }
            Text(cur.task.title, style = text(Color.White, titleSize, bold = true), maxLines = titleLines)
            Text(Fmt.dueLong(cur.deadline, m.now), style = text(Color.White, 12, bold = true), maxLines = 1)
        }
        Spacer(GlanceModifier.width(8.dp))
        TickCircle(false, bg, 44.dp, doneAction(cur.task.id))
    }
}

/** The task before or after the one at hand: the same card, smaller and lighter, with what became of it. */
@Composable
fun OtherTaskCard(m: WidgetModel, t: Task, label: String?, before: Boolean, skipped: Boolean) {
    val bg = m.colorOf(t).copy(alpha = 0.72f)
    Row(
        GlanceModifier.fillMaxWidth().background(bg.cp()).cornerRadius(16.dp).padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            if (skipped) {
                Text(
                    "SKIPPED", style = caps(m.colorOf(t)),
                    modifier = GlanceModifier.background(Color.White.cp()).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 1.dp),
                )
            } else {
                val cap = if (before) t.completedAt?.let { "DONE ${Fmt.time(it)}" } ?: "DONE" else "UP NEXT"
                Text(cap, style = caps(Color.White.copy(alpha = 0.78f)))
            }
            Text(
                label ?: t.title, maxLines = 1,
                style = TextStyle(
                    color = Color.White.cp(), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    textDecoration = if (before && !skipped) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
        }
        TickCircle(before && !skipped, m.colorOf(t), 28.dp, null)
    }
}

/**
 * The timer: the same caps label and mm:ss countdown as the timer bar ("FOCUS LEFT 12:47"), counted down live by the
 * launcher, and the cycle line beside it.
 */
@Composable
fun TimerRow(ctx: Context, m: WidgetModel, numberSp: Float) {
    val p = m.palette
    val t = barTexts(m.focus)
    val bar = m.focus.bar
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(t.labelBig, style = caps(p.muted))
            val target = bar.phaseEndsAt ?: bar.endsAt
            if (target == null || bar.kind == BarKind.IDLE) Text("--:--", style = text(p.ink, numberSp.toInt(), bold = true))
            else Countdown(ctx, target, p.ink, numberSp)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (t.endingSeconds != null && m.focus.active != null) {
                Text("Block ends in", style = text(p.accent, 12, bold = true))
                Countdown(ctx, m.focus.active!!.end, p.danger, 18f)
            } else {
                if (t.title.isNotEmpty()) Text(t.title, style = text(p.accent, 12, bold = true), maxLines = 1)
                if (t.sub.isNotEmpty()) Text(t.sub, style = text(p.muted, 12), maxLines = 1)
            }
        }
    }
}

/** A countdown to [to], drawn and ticked by the launcher (a Chronometer), so it shows seconds like the app. */
@Composable
fun Countdown(ctx: Context, to: LocalDateTime, color: Color, sizeSp: Float) {
    val left = (to.toEpochMillisLocal() - System.currentTimeMillis()).coerceAtLeast(0)
    val rv = RemoteViews(ctx.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + left, null, true)
        setChronometerCountDown(R.id.countdown, true)
        setTextColor(R.id.countdown, color.toArgb())
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv)
}

/** Long break, all done, or waiting between blocks: the same title and line as on Today. */
@Composable
fun StateText(m: WidgetModel, titleSize: Int) {
    val f = m.focus
    val p = m.palette
    val (title, line) = when (f.mode) {
        Mode.BREAK -> Words.LONG_BREAK to (f.longBreak?.let { Words.backAt(it.until, f.bar.nextName) } ?: "")
        else -> (if (f.active != null) Words.ALL_DONE else Words.WAITING) to
            (f.next?.let { Words.starts(it.block.name, it.start, m.now) } ?: Words.NOTHING_PLANNED)
    }
    Text(title, style = text(p.ink, titleSize, bold = true), maxLines = 2)
    Text(line, style = text(p.muted, 14), maxLines = 2)
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
