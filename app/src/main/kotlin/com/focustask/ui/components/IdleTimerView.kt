package com.focustask.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.focustask.Fmt
import com.focustask.core.BarKind
import com.focustask.core.FocusState
import com.focustask.data.AlertMode
import com.focustask.ui.theme.Palette
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.paletteFor

/** The colour of the idle timer view's background: a true black, so an OLED screen switches those pixels off. */
val IdleBlack = Color(0xFF000000)

/** The parts of the timer that are both in the timer bar and on the full-screen timer, and travel between the two. */
enum class TimerPart { LABEL, NUMBER, TITLE, SUB, ENDING, STRIP, START, END, SOUND }

/**
 * Where each part of the timer bar is on screen, so the full-screen timer can grow out of the bar: every part travels
 * from its place in the bar to its place on the full page, and back.
 */
@Stable
class TimerAnchors {
    val bar = mutableStateMapOf<TimerPart, Rect>()
    /** The whole timer bar. */
    var barBounds by mutableStateOf(Rect.Zero)
    /** How far the full-screen timer is out: 0 hidden, 1 filling the screen. */
    var progress: () -> Float = { 0f }
    /** The full-screen timer has been laid out at least once, so it knows where its parts go. */
    var pageReady by mutableStateOf(false)
}

val LocalTimerAnchors = staticCompositionLocalOf<TimerAnchors?> { null }

/** A part of the timer bar: reports where it is, and hides while the full-screen timer is out (it draws the part then). */
fun Modifier.barPart(part: TimerPart): Modifier = composed {
    val a = LocalTimerAnchors.current ?: return@composed this
    DisposableEffect(a, part) { onDispose { a.bar.remove(part) } }
    this.onGloballyPositioned { a.bar[part] = it.boundsInRoot() }
        .graphicsLayer { alpha = if (a.pageReady && a.progress() > 0f) 0f else 1f }
}

/** A part of the timer bar with no place on the full page: it fades out as the page grows. */
fun Modifier.barOnly(): Modifier = composed {
    val a = LocalTimerAnchors.current ?: return@composed this
    this.graphicsLayer { alpha = (1f - a.progress() * 3f).coerceIn(0f, 1f) }
}

/**
 * A part of the full page. At [progress] 0 it sits exactly on the same part of the bar (moved and scaled there), at 1
 * in its own place; in between, on the way. Text keeps its proportions; the strip stretches to the bar's size.
 */
private fun Modifier.pagePart(part: TimerPart, anchors: TimerAnchors?, progress: () -> Float): Modifier = composed {
    var target by remember { mutableStateOf<Rect?>(null) }
    this.onGloballyPositioned { target = it.boundsInRoot() }
        .graphicsLayer {
            val t = progress()
            val src = anchors?.bar?.get(part)
            val tgt = target
            transformOrigin = TransformOrigin(0f, 0f)
            if (src == null || tgt == null || tgt.height <= 0f || tgt.width <= 0f) {
                // nothing like it in the bar: it fades in where it belongs
                alpha = (t * t).coerceIn(0f, 1f)
                return@graphicsLayer
            }
            val k = 1f - t
            val sy = src.height / tgt.height
            val sx = if (part == TimerPart.STRIP) src.width / tgt.width else sy
            scaleX = lerp(sx, 1f, t)
            scaleY = lerp(sy, 1f, t)
            translationX = (src.left - tgt.left) * k
            translationY = (src.top - tgt.top) * k
        }
}

/**
 * The full-screen timer. It grows out of the timer bar: the bar's background stretches to fill the screen and turns
 * black, and every part of the timer (the label, the countdown, the cycle lines, the sun strip, the times and the
 * sound button) travels from its place in the bar to its place here, following [progress] (0..1).
 *
 * It catches every touch while it is out, so nothing hidden underneath gets pressed. A tap only wakes the screen
 * ([onTap]); the page goes away when it is pushed back up ([onDrag], in pixels, then [onDragEnd] with the speed in
 * pixels per second) or with Back.
 */
@Composable
fun IdleTimerView(
    state: FocusState, tone: Tone, progress: () -> Float, modifier: Modifier = Modifier,
    barPalette: Palette = paletteFor(tone, false), alertMode: AlertMode = AlertMode.SOUND, onAlertMode: () -> Unit = {},
    onTap: () -> Unit = {}, onDrag: (Float) -> Unit = {}, onDragEnd: (Float) -> Unit = {}, onClose: () -> Unit = {},
) {
    if (progress() <= 0f) return
    val anchors = LocalTimerAnchors.current
    // always the dark palette: it is drawn on black
    val p = paletteFor(tone, true)
    val t = barTexts(state)
    val idle = state.bar.kind == BarKind.IDLE
    val big = if (idle) "--:--" else Fmt.countdown(t.seconds)
    val tap by rememberUpdatedState(onTap)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val close by rememberUpdatedState(onClose)
    DisposableEffect(anchors) { onDispose { anchors?.pageReady = false } }
    /** A colour of the bar turning into the same colour of the page. */
    fun c(bar: Color, page: Color) = lerp(bar, page, progress().coerceIn(0f, 1f))
    Box(
        modifier.fillMaxSize()
            .onGloballyPositioned { anchors?.pageReady = true }
            // the bar's background grows to fill the screen, from the bar's colour to black, its corners rounding while it moves
            .drawBehind {
                val k = progress().coerceIn(0f, 1f)
                val from = anchors?.barBounds?.takeIf { it.width > 0f } ?: Rect(0f, 0f, size.width, 0f)
                val r = Rect(
                    lerp(from.left, 0f, k), lerp(from.top, 0f, k),
                    lerp(from.right, size.width, k), lerp(from.bottom, size.height, k),
                )
                val corner = 28.dp.toPx() * (1f - k) * 4f * k   // round only while moving
                drawRoundRect(
                    lerp(barPalette.bar, IdleBlack, k), Offset(r.left, r.top), Size(r.width, r.height),
                    CornerRadius(corner, corner),
                )
            }
            .pointerInput(Unit) { detectTapGestures { tap() } }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = { end(tracker.calculateVelocity().y) },
                    onDragCancel = { end(0f) },
                ) { change, dy ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    drag(dy)
                }
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction("Close the full-screen timer") { close(); true })
                stateDescription = if (progress() >= 0.999f) "Full screen" else "Opening"
            }
            .testTag("idle-timer-view"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.labelBig, color = c(barPalette.muted, p.muted), fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp,
                modifier = Modifier.pagePart(TimerPart.LABEL, anchors, progress),
            )
            Text(
                big, color = c(barPalette.ink, p.ink), fontSize = if (big.length > 5) 64.sp else 96.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.pagePart(TimerPart.NUMBER, anchors, progress),
            )
            if (t.endingSeconds != null) {
                Text(
                    "Block ends in ${Fmt.countdown(t.endingSeconds)}", color = c(barPalette.danger, p.danger), fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.pagePart(TimerPart.ENDING, anchors, progress),
                )
            } else {
                Text(
                    t.title, color = c(barPalette.accent, p.accent), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.pagePart(TimerPart.TITLE, anchors, progress),
                )
                Text(t.sub, color = c(barPalette.muted, p.muted), fontSize = 17.sp, modifier = Modifier.pagePart(TimerPart.SUB, anchors, progress))
            }
            Spacer(Modifier.height(36.dp))
            SunStrip(
                state.bar, if (progress() < 0.5f) barPalette else p, compact = false,
                modifier = Modifier.pagePart(TimerPart.STRIP, anchors, progress),
            )
            if (t.startLabel.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        t.startLabel, color = c(barPalette.muted, p.muted), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.pagePart(TimerPart.START, anchors, progress),
                    )
                    Text(
                        t.endLabel, color = c(barPalette.muted, p.muted), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.pagePart(TimerPart.END, anchors, progress),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            // the sound button comes along: it still switches between sound and vibrate
            AlertModeButton(
                alertMode, if (progress() < 0.5f) barPalette else p, 56.dp,
                Modifier.pagePart(TimerPart.SOUND, anchors, progress).testTag("idle-alert-mode"), onAlertMode,
            )
        }
        // a handle at the bottom edge: push it back up (above the gesture bar, not on it)
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).size(width = 44.dp, height = 5.dp)
                .graphicsLayer { alpha = ((progress() - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(3.dp)).drawBehind { drawRect(p.muted.copy(alpha = 0.6f)) },
        )
    }
}
