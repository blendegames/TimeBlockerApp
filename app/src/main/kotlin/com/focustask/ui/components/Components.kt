package com.focustask.ui.components

import com.focustask.ui.theme.palette
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.Words
import com.focustask.core.BarKind
import com.focustask.core.FocusState
import com.focustask.core.Mode
import com.focustask.core.TimerBar
import com.focustask.data.AlertMode
import com.focustask.ui.theme.LocalWallpaper
import com.focustask.ui.theme.Palette
import com.focustask.ui.theme.SunFillColor
import com.focustask.ui.theme.SunRingColor
import com.focustask.ui.theme.Tone
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun Pill(text: String, p: Palette, dot: Boolean = false) {
    Row(
        Modifier.clip(CircleShape).background(p.pill).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(p.pillInk))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = p.pillInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round 44dp button with an outline, like the sound button of the timer. */
@Composable
fun RoundOutlineButton(p: Palette, size: Dp = 44.dp, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Color.Transparent, shape = CircleShape, border = BorderStroke(2.dp, p.accent), modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

fun alertIcon(m: AlertMode): ImageVector = when (m) {
    AlertMode.SOUND -> Icons.AutoMirrored.Rounded.VolumeUp
    AlertMode.VIBRATE -> Icons.Rounded.Vibration
    AlertMode.SILENT -> Icons.AutoMirrored.Rounded.VolumeOff
}

@Composable
fun AlertModeButton(mode: AlertMode, p: Palette, size: Dp = 44.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    RoundOutlineButton(p, size, "Alert mode: ${mode.name.lowercase()}. Tap to change", onClick, modifier) {
        Icon(alertIcon(mode), null, tint = p.accent, modifier = Modifier.size(size * 0.5f))
    }
}

// ---------------------------------------------------------------- sun strip

private fun DrawScope.drawSun(center: Offset, radius: Float, density: Float) {
    drawCircle(SunFillColor, radius, center)
    drawCircle(SunRingColor, radius, center, style = Stroke(width = 2f * density))
    val inner = radius * 0.28f
    drawCircle(SunRingColor, inner, center, style = Stroke(width = 1.6f * density))
    for (i in 0 until 8) {
        val a = i * PI / 4
        val from = radius * 0.5f
        val to = radius * 0.72f
        drawLine(
            SunRingColor,
            Offset(center.x + cos(a).toFloat() * from, center.y + sin(a).toFloat() * from),
            Offset(center.x + cos(a).toFloat() * to, center.y + sin(a).toFloat() * to),
            strokeWidth = 1.6f * density, cap = StrokeCap.Round,
        )
    }
}

/** Set to false in tests: an endless frame loop would keep the test clock from ever going idle. */
object TimerAnimation {
    @Volatile var smooth: Boolean = true
}

private fun LocalDateTime.epochMs(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Where the sun is right now, to the millisecond. The state only changes once a second, so between
 * two ticks the position is computed from the clock; this keeps the movement smooth.
 */
fun sunAt(bar: TimerBar, nowMs: Long): Float {
    val s = bar.startsAt ?: return bar.sun.toFloat()
    val e = bar.endsAt ?: return bar.sun.toFloat()
    val a = s.epochMs()
    val total = e.epochMs() - a
    // A "waiting" bar that has not started yet restarts its own start every tick: keep the tick's value.
    if (total <= 0 || bar.kind == BarKind.WAIT_NEXT && bar.sun == 0.0) return bar.sun.toFloat()
    return ((nowMs - a).toDouble() / total).toFloat().coerceIn(0f, 1f)
}

/** 13 bubbles from the start to the end of the period, with the sun at the elapsed fraction. */
@Composable
fun SunStrip(bar: TimerBar, p: Palette, compact: Boolean, modifier: Modifier = Modifier) {
    val h = if (compact) 28.dp else 44.dp
    // Read only while drawing, so the strip redraws without recomposing anything. It redraws just often enough for
    // the sun to move by about half a pixel each time: every frame for a short period, once a second (or less) for a
    // long block, where the sun only moves a pixel every few seconds. Redrawing at every frame for nothing would keep
    // the whole screen busy and make everything else less smooth.
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var widthPx by remember { mutableIntStateOf(0) }
    if (TimerAnimation.smooth) {
        val periodMs = bar.startsAt?.let { s -> bar.endsAt?.let { e -> java.time.Duration.between(s, e).toMillis() } } ?: 0L
        LaunchedEffect(periodMs, widthPx) {
            if (periodMs <= 0 || widthPx <= 0) return@LaunchedEffect
            // the time it takes the sun to move half a pixel
            val stepMs = (periodMs / (widthPx * 2.0)).toLong().coerceIn(16L, 1000L)
            while (true) {
                if (stepMs <= 16L) withFrameMillis { } else delay(stepMs)
                clock.longValue = System.currentTimeMillis()
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height(h).onSizeChanged { widthPx = it.width }) {
        val d = density
        val bigD = (if (compact) 10f else 16f) * d
        val smallD = (if (compact) 5f else 8f) * d
        val markerR = (if (compact) 12f else 20f) * d
        val left = markerR
        val right = size.width - markerR
        val cy = size.height / 2
        val frac = if (TimerAnimation.smooth) sunAt(bar, clock.longValue) else bar.sun.toFloat()
        val sunX = left + (right - left) * frac.coerceIn(0f, 1f)
        for (i in 0..12) {
            val x = left + (right - left) * i / 12f
            val large = i in bar.largeBubbles
            val dia = if (large) bigD else smallD
            val elapsed = x <= sunX
            val color = if (large) p.bubble else p.bubble.copy(alpha = 0.65f)
            if (elapsed) {
                drawCircle(color, dia / 2, Offset(x, cy))
            } else {
                val sw = (if (large) 2f else 1.5f) * d
                drawCircle(color, dia / 2 - sw / 2, Offset(x, cy), style = Stroke(sw))
            }
        }
        drawSun(Offset(sunX, cy), markerR - d, d)
    }
}

// ---------------------------------------------------------------- timer bar

data class BarTexts(
    val labelBig: String,
    val labelSmall: String,
    val seconds: Long,
    val title: String,
    val sub: String,
    val compactRight: String,
    val startLabel: String,
    val endLabel: String,
    /** Big red "block ends in" countdown, when the block is about to end. */
    val endingSeconds: Long?,
)

fun barTexts(s: FocusState): BarTexts {
    val b = s.bar
    val start = b.startsAt?.let { Fmt.time(it) } ?: ""
    val end = b.endsAt?.let { Fmt.time(it) } ?: ""
    return when (b.kind) {
        BarKind.FOCUS_WORK, BarKind.FOCUS_SHORT_BREAK -> {
            val work = b.kind == BarKind.FOCUS_WORK
            val phaseEnd = b.phaseEndsAt?.let { Fmt.time(it) } ?: ""
            val blockEnds = b.endsAt?.let { Fmt.time(it) } ?: ""
            val toBlockEnd = s.active?.let { Duration.between(s.now, it.end).seconds }
            BarTexts(
                labelBig = if (work) "FOCUS LEFT" else "BREAK LEFT",
                labelSmall = if (work) "FOCUS" else "BREAK",
                seconds = b.seconds,
                title = "Cycle ${b.cycleNumber} of ${b.cycleCount}",
                sub = if (b.phaseEndsAt == b.endsAt) "Block ends $blockEnds" else if (work) "Break at $phaseEnd" else "Work at $phaseEnd",
                compactRight = "${b.blockLeftMin ?: 0} min left",
                startLabel = start, endLabel = end,
                endingSeconds = if (b.ending) toBlockEnd else null,
            )
        }
        BarKind.WAIT_NEXT -> BarTexts(
            labelBig = (b.nextName ?: "Next").uppercase() + " IN", labelSmall = "NEXT",
            seconds = b.seconds, title = b.nextName ?: "", sub = "starts $end",
            compactRight = "starts $end", startLabel = start, endLabel = end, endingSeconds = null,
        )
        BarKind.LONG_BREAK -> BarTexts(
            labelBig = "BREAK", labelSmall = "BREAK", seconds = b.seconds,
            title = "Long break", sub = "ends $end", compactRight = "ends $end",
            startLabel = start, endLabel = end, endingSeconds = null,
        )
        BarKind.IDLE -> BarTexts(
            labelBig = "NO TIME BLOCKS", labelSmall = "FOCUS", seconds = 0,
            title = Words.NOTHING_PLANNED, sub = "Add one in the Day tab", compactRight = "",
            startLabel = "", endLabel = "", endingSeconds = null,
        )
    }
}

fun barTone(s: FocusState): Tone = when {
    s.mode == Mode.BREAK -> Tone.BREAK
    s.bar.ending && (s.bar.kind == BarKind.FOCUS_WORK || s.bar.kind == BarKind.FOCUS_SHORT_BREAK) -> Tone.ENDING
    else -> Tone.FOCUS
}

@Composable
fun TimerBarView(
    state: FocusState,
    p: Palette,
    big: Boolean,
    alertMode: AlertMode,
    onAlertMode: () -> Unit,
    modifier: Modifier = Modifier,
    /** In a narrow, tall pane beside Today (wide windows): the parts stacked instead of side by side. */
    stacked: Boolean = false,
) {
    val t = barTexts(state)
    val dim = if (state.bar.kind == BarKind.IDLE) "--:--" else Fmt.countdown(t.seconds)
    val endingRed = p.danger
    Surface(color = p.bar, modifier = modifier.fillMaxWidth()) {
        if (big && stacked) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.labelBig, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, modifier = Modifier.barPart(TimerPart.LABEL))
                        Text(dim, color = p.ink, fontSize = 56.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.barPart(TimerPart.NUMBER))
                    }
                    AlertModeButton(alertMode, p, 52.dp, Modifier.barPart(TimerPart.SOUND), onAlertMode)
                }
                if (t.endingSeconds != null) {
                    Text("Block ends in ${Fmt.countdown(t.endingSeconds)}", color = endingRed, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.barPart(TimerPart.ENDING))
                } else {
                    Text(t.title, color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.barPart(TimerPart.TITLE))
                    Text(t.sub, color = p.muted, fontSize = 15.sp, modifier = Modifier.barPart(TimerPart.SUB))
                }
                Spacer(Modifier.height(12.dp))
                SunStrip(state.bar, p, compact = false, modifier = Modifier.barPart(TimerPart.STRIP))
                if (t.startLabel.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.startLabel, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.barPart(TimerPart.START))
                        Text(t.endLabel, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.barPart(TimerPart.END))
                    }
                }
            }
        } else if (big) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.labelBig, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, modifier = Modifier.barPart(TimerPart.LABEL))
                        Text(dim, color = p.ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.barPart(TimerPart.NUMBER))
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                        if (t.endingSeconds != null) {
                            Text("Block ends in", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.barOnly())
                            Text(Fmt.countdown(t.endingSeconds), color = endingRed, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.barPart(TimerPart.ENDING))
                        } else {
                            Text(t.title, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.barPart(TimerPart.TITLE))
                            Text(t.sub, color = p.muted, fontSize = 14.sp, modifier = Modifier.barPart(TimerPart.SUB))
                        }
                    }
                    AlertModeButton(alertMode, p, 52.dp, Modifier.barPart(TimerPart.SOUND), onAlertMode)
                }
                Spacer(Modifier.height(6.dp))
                SunStrip(state.bar, p, compact = false, modifier = Modifier.barPart(TimerPart.STRIP))
                if (t.startLabel.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.startLabel, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.barPart(TimerPart.START))
                        Text(t.endLabel, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.barPart(TimerPart.END))
                    }
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(t.labelSmall, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, modifier = Modifier.barPart(TimerPart.LABEL))
                    Text(dim, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.barPart(TimerPart.NUMBER))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SunStrip(state.bar, p, compact = true, modifier = Modifier.barPart(TimerPart.STRIP))
                    if (t.compactRight.isNotEmpty()) {
                        Text(
                            if (t.endingSeconds != null) "Block ends in ${Fmt.countdown(t.endingSeconds)}" else t.compactRight,
                            color = if (t.endingSeconds != null) endingRed else p.accent,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End).then(
                                if (t.endingSeconds != null) Modifier.barPart(TimerPart.ENDING) else Modifier.barOnly()
                            ),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                AlertModeButton(alertMode, p, 40.dp, Modifier.barPart(TimerPart.SOUND), onAlertMode)
            }
        }
    }
}

// ---------------------------------------------------------------- bottom navigation

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.CheckCircleOutline),
    DAY("Day", Icons.Rounded.CalendarToday),
    WEEK("Week", Icons.Rounded.GridView),
    LISTS("Lists", Icons.AutoMirrored.Rounded.FormatListBulleted),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun BottomNav(selected: Tab, locked: Boolean, p: Palette, onSelect: (Tab) -> Unit) {
    val light = p.navBg.luminance() > 0.5f
    val wall = LocalWallpaper.current
    val navInk = wall?.onSurface ?: if (light) Color(0xFF141A17) else Color(0xFFEEF2EF)
    val selBg = wall?.secondaryContainer ?: if (light) Color(0xFFDCE9E4) else Color(0xFF24312C)
    val selInk = wall?.onSecondaryContainer ?: if (light) Color(0xFF2F5D50) else Color(0xFF7CC4AE)
    Surface(color = p.navBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.values().forEach { tab ->
                val isSel = tab == selected
                val isLocked = locked && tab != Tab.TODAY
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        // one tab for the screen reader: its name, selected or not, and locked during a break
                        .selectable(selected = isSel, role = Role.Tab) { onSelect(tab) }
                        .semantics(mergeDescendants = true) { if (isLocked) stateDescription = "Locked during the break" }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Box(
                            Modifier.width(64.dp).height(32.dp).clip(CircleShape)
                                .background(if (isSel) selBg else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tab.icon, null,
                                tint = if (isSel) selInk else if (isLocked) navInk.copy(alpha = 0.35f) else navInk.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        if (isLocked) {
                            // the lock badge in the colors of the long break
                            val bp = palette(Tone.BREAK, !light)
                            Box(
                                Modifier.padding(end = 6.dp).size(16.dp).clip(CircleShape).background(bp.pill),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Lock, null, tint = bp.pillInk, modifier = Modifier.size(10.dp)) }
                        }
                    }
                    Text(
                        tab.label, fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = if (isSel) navInk else if (isLocked) navInk.copy(alpha = 0.4f) else navInk.copy(alpha = 0.75f),
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * The tabs as a rail down the left side, for wide windows (landscape, tablets, unfolded phones): the same tabs as
 * [BottomNav], stacked, so the screen keeps its full height.
 */
@Composable
fun SideNav(selected: Tab, locked: Boolean, p: Palette, onSelect: (Tab) -> Unit) {
    val light = p.navBg.luminance() > 0.5f
    val wall = LocalWallpaper.current
    val navInk = wall?.onSurface ?: if (light) Color(0xFF141A17) else Color(0xFFEEF2EF)
    val selBg = wall?.secondaryContainer ?: if (light) Color(0xFFDCE9E4) else Color(0xFF24312C)
    val selInk = wall?.onSecondaryContainer ?: if (light) Color(0xFF2F5D50) else Color(0xFF7CC4AE)
    Surface(color = p.navBg, modifier = Modifier.fillMaxHeight().testTag("side-nav")) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .width(88.dp).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Tab.values().forEach { tab ->
                val isSel = tab == selected
                val isLocked = locked && tab != Tab.TODAY
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(selected = isSel, role = Role.Tab) { onSelect(tab) }
                        .semantics(mergeDescendants = true) { if (isLocked) stateDescription = "Locked during the break" }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(56.dp).height(32.dp).clip(CircleShape).background(if (isSel) selBg else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isLocked) Icons.Rounded.Lock else tab.icon, null,
                            tint = if (isSel) selInk else if (isLocked) navInk.copy(alpha = 0.35f) else navInk.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        tab.label, fontSize = 12.sp, textAlign = TextAlign.Center,
                        color = if (isSel) navInk else if (isLocked) navInk.copy(alpha = 0.4f) else navInk.copy(alpha = 0.75f),
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
