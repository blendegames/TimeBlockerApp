package com.focustask.ui.screens

import com.focustask.ui.theme.TitleSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focustask.Fmt
import com.focustask.Words
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.Carousel
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.ui.components.Pill
import com.focustask.ui.theme.LocalReduceMotion
import com.focustask.ui.theme.OnDanger
import com.focustask.ui.theme.OverdueRed
import com.focustask.ui.theme.Palette
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(ui: UiState, vm: MainViewModel, p: Palette) {
    val f = ui.focus
    val c = f.carousel
    val overdue = f.mode == Mode.FOCUS && c?.current?.reason == Reason.OVERDUE
    // The cards of the wheel may roll past the edges of the wheel's own space (they fade as they go round): only the
    // edges of the tab cut them off, never the wheel's box, so no card is cut in half while it turns.
    Column(Modifier.fillMaxSize().clipToBounds().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().zIndex(1f).padding(top = 20.dp, end = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when {
                f.mode == Mode.BREAK -> {
                    val left = Duration.between(f.now, f.longBreak!!.until).seconds
                    Pill("Long break · ${(left + 59) / 60} min", p)
                }
                // overdue is marked in red on the normal screen: this pill, and a red outline round the card
                overdue -> Row(
                    Modifier.clip(CircleShape).background(OverdueRed).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = OnDanger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("OVERDUE", color = OnDanger, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                f.mode == Mode.FOCUS -> {
                    val cur = c!!.current
                    val act = f.active
                    val text = when {
                        act != null -> "${act.block.name} · until ${Fmt.time(act.end)}"
                        else -> "${Words.WAITING_SHORT} · ${cur.list.title}"
                    }
                    Pill(text, p, dot = true)
                }
                else -> Pill(f.active?.let { "${it.block.name} · ${Words.ALL_DONE.lowercase()}" } ?: Words.WAITING_SHORT, p)
            }
            Text(
                "${Fmt.dayShort(f.now.toLocalDate())} ${Fmt.time(f.now)}",
                color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
        }
        when (f.mode) {
            Mode.FOCUS -> FocusBody(ui, vm, c!!, p, overdue)
            Mode.ALL_DONE -> if (f.active == null) WaitingBody(ui, p) else AllDoneBody(ui, vm, p)
            Mode.BREAK -> BreakBody(ui, p)
        }
    }
}

/** A task on the wheel at slot [slot]: -1 the one before, 0 the task at hand, 1 the next one (±2 further round). */
private data class Face(
    val key: String,
    val slot: Int,
    val title: String,
    /** The small caps line on the card: "DONE 14:04", "SKIPPED", "UP NEXT". */
    val label: String,
    val done: Boolean = false,
    val skipped: Boolean = false,
    val deadline: LocalDateTime? = null,
    /** "Start of the list" / "End of the list": nothing to turn to on that side. */
    val edge: Boolean = false,
    /** The color of the task's list: the card is drawn in it. */
    val color: Color = Color.Unspecified,
)

/** The colors of a card drawn in its list's color: white text on it, a white tick. */
private data class CardInk(val bg: Color, val ink: Color, val muted: Color)

private fun cardInk(listColor: Color) = CardInk(listColor, Color.White, Color.White.copy(alpha = 0.78f))

/**
 * Angle between two tasks on the wheel, in degrees. Like a picker wheel, the tasks sit edge to edge around it: the task
 * at hand faces you, the one before and the one after tilt away above and below it, and the wheel turns them all.
 */
private const val WHEEL_STEP = 55f

/** Past this angle a task has gone round the back of the wheel. */
private const val WHEEL_HIDDEN = 100f

/** Keeps the latest key of the wheel and the key of the state a drag started on. */
private class WheelKeys { var latest: Any? = null; var dragOn: Any? = null }

@Composable
private fun ColumnScope.FocusBody(
    ui: UiState, vm: MainViewModel, carousel: Carousel, p: Palette, overdue: Boolean,
) {
    val now = ui.now
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val current = carousel.current.task.id
    val previous = carousel.previous
    val previousSkipped = carousel.previousSkipped
    // which way the wheel turns for the next change: 1 = up (done, skip), -1 = down (back), 0 = no turn
    val direction = remember { intArrayOf(0) }
    val keys = remember { WheelKeys() }
    // the wheel follows the finger, in tasks (1 = one whole task)
    val drag = remember { Animatable(0f) }

    fun skip() { direction[0] = 1; vm.run { skipTask(current) } }
    fun back(): Boolean {
        val last = previous ?: return false
        direction[0] = -1
        if (previousSkipped) vm.run { unskip(last.id) } else vm.run { reopen(last.id) }
        return true
    }
    fun done() { direction[0] = 1; vm.run { complete(current) } }

    // the task at hand, the ones before and after it, and one more on each side that comes round while it turns
    fun colorOf(t: com.focustask.core.Task) = Color(ui.data.colorOfListAlways(t.listId))
    fun before(t: com.focustask.core.Task, label: String?, skipped: Boolean, slot: Int) = Face(
        "t${t.id}", slot, label ?: t.title,
        if (skipped) "SKIPPED" else t.completedAt?.let { "DONE ${Fmt.time(it)}" } ?: "DONE",
        done = !skipped, skipped = skipped, deadline = t.deadline, color = colorOf(t),
    )
    val faces = buildList {
        carousel.previous2?.let { add(before(it, carousel.previous2Label, carousel.previous2Skipped, -2)) }
        if (previous != null) add(before(previous, carousel.previousLabel, previousSkipped, -1))
        else add(Face("start", -1, "Start of the list", "", edge = true))
        add(Face("t$current", 0, carousel.current.task.title, "", deadline = carousel.current.deadline, color = colorOf(carousel.current.task)))
        val nxt = carousel.next
        if (nxt != null) add(Face("t${nxt.id}", 1, carousel.nextLabel ?: nxt.title, "UP NEXT", deadline = carousel.nextDeadline, color = colorOf(nxt)))
        else add(Face("end", 1, "End of the list", "", edge = true))
        carousel.next2?.let { add(Face("t${it.id}", 2, carousel.next2Label ?: it.title, "UP NEXT", deadline = it.deadline, color = colorOf(it))) }
    }
    // the wheel turns one task when the task at hand changes; it starts where the finger left it
    val wheelKey = listOf(previous?.id, previousSkipped, current, carousel.next?.id)
    val turn = remember(wheelKey) {
        val carried = if (keys.dragOn != null && keys.dragOn != wheelKey) drag.value else 0f
        Animatable(if (keys.latest == null) 0f else direction[0] + carried)
    }
    SideEffect { keys.latest = wheelKey }
    LaunchedEffect(wheelKey) {
        direction[0] = 0
        if (keys.dragOn != null && keys.dragOn != wheelKey) { keys.dragOn = null; drag.snapTo(0f) }
        // a picker wheel coming to rest: quick at first, settling softly
        turn.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 260f))
    }
    // how far the wheel is turned right now, in tasks; a drag only counts on the state it started on
    fun offset(): Float = turn.value + if (keys.dragOn == null || keys.dragOn == wheelKey) drag.value else 0f

    var cardPx by remember { mutableIntStateOf(0) }
    // the cards before and after are smaller than the one at hand
    var otherPx by remember { mutableIntStateOf(0) }
    val gapPx = with(density) { 16.dp.toPx() }
    val stepRad = Math.toRadians(WHEEL_STEP.toDouble()).toFloat()
    // the tasks sit edge to edge around the wheel: one task's height is one step of its rim
    fun radius(): Float {
        val cur = if (cardPx > 0) cardPx.toFloat() else with(density) { 200.dp.toPx() }
        val other = if (otherPx > 0) otherPx.toFloat() else cur
        // one step of the rim spans half of each neighbour plus the gap between them
        return ((cur + other) / 2f + gapPx) / stepRad
    }
    // how far the finger moves for one task
    fun slotPx(): Float = (radius() * sin(stepRad)).coerceAtLeast(1f)

    Column(Modifier.weight(1f).fillMaxWidth()) {
        carousel.parent?.let { parent ->
            // the main task of the step on screen: its name, and its deadline on the right
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(parent.title, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                parent.deadline?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(Fmt.due(it, now), color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().testTag("carousel")
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Skip this task") { skip(); true },
                        CustomAccessibilityAction(if (previousSkipped) "Back to the skipped task" else "Undo the last completion") { back() },
                    )
                }
                .pointerInput(wheelKey) {
                    var total = 0f
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f; tracker.resetTracking(); keys.dragOn = wheelKey },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().y / slotPx()   // tasks per second
                            val at = drag.value
                            // one task per swipe. Up: skip the task; down: back to the one before
                            // (a finished one is reopened, a skipped one comes back)
                            val up = carousel.next != null && (at < -0.35f || (at < -0.1f && v < -2f))
                            val down = previous != null && (at > 0.35f || (at > 0.1f && v > 2f))
                            scope.launch {
                                when {
                                    up -> { skip(); drag.animateTo(-1f, tween(180, easing = FastOutSlowInEasing)) }
                                    down -> { back(); drag.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
                                }
                                if (up || down) delay(1500)
                                // nothing changed after all (or the swipe was too short): the wheel turns back
                                if (keys.latest == wheelKey) { drag.animateTo(0f, spring(dampingRatio = 0.75f)); keys.dragOn = null }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f); keys.dragOn = null } },
                    ) { change, dy ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        total += dy
                        var f = total / slotPx()
                        // nothing to turn to on that side: the wheel only gives a little
                        if (f > 0f && previous == null) f *= 0.2f
                        if (f < 0f && carousel.next == null) f *= 0.2f
                        // never more than one task per swipe
                        scope.launch { drag.snapTo(f.coerceIn(-1f, 1f)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            faces.forEach { face ->
                androidx.compose.runtime.key(face.key) {
                    WheelFace(
                        face, carousel, now, p, overdue,
                        angle = { (face.slot + offset()) * WHEEL_STEP },
                        radius = ::radius,
                        onCardSize = { if (face.slot == 0) cardPx = it else if (abs(face.slot) == 1 && !face.edge) otherPx = it },
                        onDone = ::done,
                    )
                }
            }
        }
        val listName = carousel.current.list.title
        val footer = when {
            overdue -> "Overdue first, then ${ui.focus.active?.block?.name ?: "your day"} continues"
            else -> Words.position(carousel, listName)
        }
        Text(footer, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * A task on the wheel, drawn where the wheel's turn puts it: it rolls over the top (or the bottom) of the wheel,
 * tilting away, getting smaller and fainter as it goes, like the numbers of a picker wheel.
 */
@Composable
private fun BoxScope.WheelFace(
    face: Face, c: Carousel, now: LocalDateTime, p: Palette, overdue: Boolean,
    angle: () -> Float, radius: () -> Float, onCardSize: (Int) -> Unit, onDone: () -> Unit,
) {
    val a0 = angle()
    // a task round the back of the wheel is not drawn at all (and not there for the screen reader either)
    if (abs(a0) >= WHEEL_HIDDEN && face.slot != 0) return
    val density = LocalDensity.current.density
    val cosHidden = cos(Math.toRadians(WHEEL_HIDDEN.toDouble())).toFloat()
    Box(
        // the task nearest the front is drawn on top
        Modifier.align(Alignment.Center).fillMaxWidth().zIndex(-abs(a0)).graphicsLayer {
            val a = angle()
            val rad = Math.toRadians(a.toDouble())
            val cos = cos(rad).toFloat()
            translationY = radius() * sin(rad).toFloat()
            rotationX = -a
            cameraDistance = 7f * density
            // further round the wheel is further away: a little smaller
            val depth = 1f / (1f + (1f - cos) / 4f)
            scaleX = depth; scaleY = depth
            // fainter as it turns away, gone round the back
            alpha = ((cos - cosHidden) / (1f - cosHidden)).coerceIn(0f, 1f)
        }.onSizeChanged { onCardSize(it.height) },
        contentAlignment = Alignment.Center,
    ) {
        if (face.slot == 0) CurrentCard(c, now, p, cardInk(face.color), overdue, onDone) else OtherCard(face, now, p)
    }
}

@Composable
private fun EdgeRow(text: String, p: Palette) {
    Text(text, color = p.muted.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
}

/** The card of the task before or after the one at hand: the same card, a little lighter, with what became of it. */
@Composable
private fun OtherCard(face: Face, now: LocalDateTime, p: Palette) {
    if (face.edge) { EdgeRow(face.title, p); return }
    val k = cardInk(face.color)
    // smaller than the card at hand, as on a picker wheel: the one at hand stands out
    Surface(color = k.bg.copy(alpha = 0.82f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (face.skipped) {
                    // skipped stands out: it is still to do
                    Text(
                        "SKIPPED", color = k.bg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                        modifier = Modifier.clip(CircleShape).background(k.ink).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                } else {
                    Text(face.label, color = k.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    face.title, color = k.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (face.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                Spacer(Modifier.height(4.dp))
                Text(Fmt.dueLong(face.deadline, now), color = k.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            TickCircle(face.done, k, size = 48.dp)
        }
    }
}

/** The round tick box of the Today card: an empty circle, filled with a tick once the task is done. */
@Composable
private fun TickCircle(done: Boolean, k: CardInk, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(
        modifier.size(size).clip(CircleShape)
            .then(if (done) Modifier.background(k.ink) else Modifier.border(3.dp, k.ink.copy(alpha = 0.7f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, null, tint = k.bg, modifier = Modifier.size(size * 0.53f))
    }
}

@Composable
private fun CurrentCard(c: Carousel, now: LocalDateTime, p: Palette, k: CardInk, overdue: Boolean, onDone: () -> Unit) {
    val cur = c.current
    // a tap ticks the circle; a moment later the wheel turns to the next task
    var ticked by remember(cur.task.id) { mutableStateOf(false) }
    val still = LocalReduceMotion.current
    LaunchedEffect(ticked) { if (ticked) { if (!still) delay(280); onDone() } }
    Surface(
        color = k.bg, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
        border = if (overdue) androidx.compose.foundation.BorderStroke(4.dp, p.danger) else null,
    ) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val label = when {
                    overdue -> Fmt.late(cur.deadline ?: now, now).uppercase()
                    c.parent != null -> "SUBTASK ${c.stepIndex} OF ${c.stepCount}"
                    else -> "YOUR ONE TASK"
                }
                if (overdue) {
                    // how late, in the same red pill style as SKIPPED on the other cards
                    Text(
                        label, color = OnDanger, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                        modifier = Modifier.clip(CircleShape).background(OverdueRed).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                } else {
                    Text(label, color = k.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(cur.task.title, color = k.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("current-title"))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = k.ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    val meta = Fmt.dueLong(cur.deadline, now)
                    Text(meta, color = k.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (c.parent == null) Text("  ·  ${cur.list.title} list", color = k.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            // the tick circle: done with this task
            TickCircle(
                ticked, k,
                Modifier.clickable(enabled = !ticked) { ticked = true }.testTag("done-button")
                    .semantics { contentDescription = "Done" },
            )
        }
    }
}

@Composable
private fun ColumnScope.AllDoneBody(ui: UiState, vm: MainViewModel, p: Palette) {
    val f = ui.focus
    val next = f.next
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = p.accent, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(Words.ALL_DONE, color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (next != null) {
            val left = Duration.between(f.now, next.start).toMinutes()
            Text(
                "${next.block.name} starts ${Fmt.time(next.start)}. Being done early starts a long break for the ${Fmt.minutesLong(left.coerceAtLeast(1))} left.",
                color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val mins = Duration.between(f.now, next.start).toMinutes()
                    if (mins > 60) confirm = true else vm.run { startUserBreak(f.now, next.start) }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            ) {
                Icon(Icons.Rounded.Pause, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(Words.longBreakUntil(next.block.name), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = p.muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Other tabs stay locked during the long break", color = p.muted, fontSize = 13.sp)
            }
        } else {
            Text(
                "Nothing is scheduled. Add a time block in the Day tab and its list will show up here.",
                color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    if (confirm && next != null) {
        val mins = Duration.between(f.now, next.start).toMinutes()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("${Words.LONG_BREAK} for ${Fmt.minutesLong(mins)}?") },
            text = { Text("The break lasts until ${next.block.name} starts at ${Fmt.time(next.start)}. The other tabs stay locked until then.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.run { startUserBreak(f.now, next.start) } }) { Text("Start the long break") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not now") } },
        )
    }
}

/** Between two time blocks there is no list to work on: the tab waits for the next block. */
@Composable
private fun ColumnScope.WaitingBody(ui: UiState, p: Palette) {
    val f = ui.focus
    val next = f.next
    Column(
        Modifier.weight(1f).fillMaxWidth().testTag("waiting"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // the hourglass turns over every ten seconds
        // (with animations removed on the phone it stands still)
        val turns = if (LocalReduceMotion.current) 0 else f.now.toLocalTime().toSecondOfDay() / 10
        val spin by animateFloatAsState(turns * 180f, tween(900), label = "hourglass")
        Box(Modifier.size(120.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.HourglassTop, null, tint = p.accent,
                modifier = Modifier.size(56.dp).graphicsLayer { rotationZ = spin },
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            Words.WAITING, color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 34.sp,
        )
        Spacer(Modifier.height(12.dp))
        val line = if (next != null) {
            val mins = Duration.between(f.now, next.start).toMinutes().coerceAtLeast(1)
            val day = if (next.start.toLocalDate() == f.now.toLocalDate()) "" else "${Fmt.dayShort(next.start.toLocalDate())} "
            "${next.block.name} starts $day${Fmt.time(next.start)}, in ${Fmt.minutesLong(mins)}."
        } else "No time blocks are planned. Add one in the Day or Week tab."
        Text(line, color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ColumnScope.BreakBody(ui: UiState, p: Palette) {
    val f = ui.focus
    val lb = f.longBreak!!
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val seconds = f.bar.seconds
        val progress = (1f - f.bar.sun.toFloat()).coerceIn(0f, 1f)
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 14.dp.toPx()
                val inset = sw / 2
                val arcSize = Size(size.width - sw, size.height - sw)
                drawArc(p.pill, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                drawArc(p.accent, -90f + (1f - progress) * 360f, progress * 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.countdown(seconds), color = p.accent, fontSize = 56.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("left", color = p.muted, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(Words.LONG_BREAK, color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(Words.backAt(lb.until, f.bar.nextName), color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center)
        Text(
            if (lb.userInitiated) "Take a real break. It lasts exactly until then." else "Blocks of ${Fmt.hours(ui.data.settings.cycle.longBreakAfterMin)} or more are followed by a long break.",
            color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(CircleShape).background(p.pill).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = p.pillInk, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Breaks can't be skipped", color = p.pillInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(24.dp))
}
