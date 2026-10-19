package com.focustask.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.focustask.core.BarKind
import com.focustask.core.FocusState
import com.focustask.core.TimerBar
import com.focustask.data.AlertMode
import com.focustask.ui.theme.Palette
import com.focustask.ui.theme.SunFillColor
import com.focustask.ui.theme.SunRingColor
import com.focustask.ui.theme.Tone
import java.time.Duration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Pill(text: String, p: Palette, dot: Boolean = false, bold: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(p.pill).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(p.pillInk))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = p.pillInk, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A round 44dp button with an outline, like the sound button of the timer. */
@Composable
fun RoundOutlineButton(p: Palette, size: Dp = 44.dp, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .then(Modifier.background(Color.Transparent)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Color.Transparent, shape = CircleShape, border = BorderStroke(2.dp, p.accent), modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

fun alertIcon(m: AlertMode): ImageVector = when (m) {
    AlertMode.SOUND -> Icons.Rounded.VolumeUp
    AlertMode.VIBRATE -> Icons.Rounded.Vibration
    AlertMode.SILENT -> Icons.Rounded.VolumeOff
}

@Composable
fun AlertModeButton(mode: AlertMode, p: Palette, size: Dp = 44.dp, onClick: () -> Unit) {
    RoundOutlineButton(p, size, "Alert mode: ${mode.name.lowercase()}. Tap to change", onClick) {
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

/** 13 bubbles from the start to the end of the period, with the sun at the elapsed fraction. */
@Composable
fun SunStrip(bar: TimerBar, p: Palette, compact: Boolean, modifier: Modifier = Modifier) {
    val h = if (compact) 28.dp else 44.dp
    Canvas(modifier.fillMaxWidth().height(h)) {
        val d = density
        val bigD = (if (compact) 10f else 16f) * d
        val smallD = (if (compact) 5f else 8f) * d
        val markerR = (if (compact) 12f else 20f) * d
        val left = markerR
        val right = size.width - markerR
        val cy = size.height / 2
        val sunX = left + (right - left) * bar.sun.toFloat().coerceIn(0f, 1f)
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
            labelBig = "NO BLOCKS", labelSmall = "FOCUS", seconds = 0,
            title = "Nothing scheduled", sub = "Add a block in Day", compactRight = "",
            startLabel = "", endLabel = "", endingSeconds = null,
        )
    }
}

fun barTone(s: FocusState, overdueOnToday: Boolean): Tone = when {
    s.mode == com.focustask.core.Mode.BREAK -> Tone.BREAK
    s.bar.ending && (s.bar.kind == BarKind.FOCUS_WORK || s.bar.kind == BarKind.FOCUS_SHORT_BREAK) -> Tone.ENDING
    overdueOnToday -> Tone.OVERDUE
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
) {
    val t = barTexts(state)
    val dim = if (state.bar.kind == BarKind.IDLE) "--:--" else Fmt.countdown(t.seconds)
    val endingRed = p.danger
    Surface(color = p.bar, modifier = modifier.fillMaxWidth()) {
        if (big) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.labelBig, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        Text(dim, color = p.ink, fontSize = 46.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                        if (t.endingSeconds != null) {
                            Text("Block ends in", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(Fmt.countdown(t.endingSeconds), color = endingRed, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        } else {
                            Text(t.title, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(t.sub, color = p.muted, fontSize = 14.sp)
                        }
                    }
                    AlertModeButton(alertMode, p, 52.dp, onAlertMode)
                }
                Spacer(Modifier.height(6.dp))
                SunStrip(state.bar, p, compact = false)
                if (t.startLabel.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t.startLabel, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(t.endLabel, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(t.labelSmall, color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    Text(dim, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SunStrip(state.bar, p, compact = true)
                    if (t.compactRight.isNotEmpty()) {
                        Text(
                            if (t.endingSeconds != null) "Block ends in ${Fmt.countdown(t.endingSeconds)}" else t.compactRight,
                            color = if (t.endingSeconds != null) endingRed else p.accent,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                AlertModeButton(alertMode, p, 40.dp, onAlertMode)
            }
        }
    }
}

// ---------------------------------------------------------------- bottom navigation

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.CheckCircleOutline),
    DAY("Day", Icons.Rounded.CalendarToday),
    WEEK("Week", Icons.Rounded.GridView),
    LISTS("Lists", Icons.Rounded.FormatListBulleted),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun BottomNav(selected: Tab, locked: Boolean, p: Palette, onSelect: (Tab) -> Unit) {
    val light = p.navBg.luminance() > 0.5f
    val navInk = if (light) Color(0xFF141A17) else Color(0xFFEEF2EF)
    val selBg = if (light) Color(0xFFDCE9E4) else Color(0xFF24312C)
    val selInk = if (light) Color(0xFF2F5D50) else Color(0xFF7CC4AE)
    Surface(color = p.navBg, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.values().forEach { tab ->
                val isSel = tab == selected
                val isLocked = locked && tab != Tab.TODAY
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab) { onSelect(tab) }
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
                                tab.icon, tab.label,
                                tint = if (isSel) selInk else if (isLocked) navInk.copy(alpha = 0.35f) else navInk.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        if (isLocked) {
                            Box(
                                Modifier.padding(end = 6.dp).size(16.dp).clip(CircleShape).background(Color(0xFFDDE3F3)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Lock, null, tint = Color(0xFF3B4F9A), modifier = Modifier.size(10.dp)) }
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
