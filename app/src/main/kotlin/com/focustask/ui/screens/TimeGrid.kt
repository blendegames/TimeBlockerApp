package com.focustask.ui.screens

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.core.BlockFit
import com.focustask.core.Span
import com.focustask.ui.theme.Palette
import kotlin.math.roundToInt

/** One block drawn in the grid. [id] identifies the block, [column] the day column. */
data class GridBlock(
    val id: Long,
    val column: Int,
    val startMin: Int,
    val endMin: Int,
    val color: Color,
    val editable: Boolean = true,
)

/** The id of the temporary block that shows where a new block would go. */
const val GHOST_ID = -1L

/** What the finger is changing right now: shown as a small bubble beside the block. */
internal data class DragInfo(
    val column: Int, val edgeMin: Int, val label: String,
    /** The minute that leads the movement: the grid scrolls to keep it in view. */
    val leadMin: Int = edgeMin,
)

/** Space above midnight and below the last hour, so the first and last hour labels are not cut off. */
private val GRID_PAD = 12.dp

private fun clock(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

private const val SNAP = 5
private const val MIN_LEN = BlockFit.MIN_LEN

/**
 * A scrollable day/week grid. The selected block shows two handles; dragging a handle changes the
 * start or end, dragging the block moves it. Changes are reported when the finger lifts.
 */
@Composable
fun TimeGrid(
    columns: Int,
    blocks: List<GridBlock>,
    hourHeight: Dp,
    p: Palette,
    selectedId: Long?,
    selectedColumn: Int? = null,
    todayColumn: Int?,
    nowMin: Int?,
    initialScrollHour: Int,
    onSelect: (GridBlock) -> Unit,
    onChange: (GridBlock, Int, Int) -> Unit,
    /** A tap on an empty spot: the day column and the minute of the day, rounded down to a quarter hour. */
    onTapEmpty: (column: Int, minute: Int) -> Unit = { _, _ -> },
    /** Minutes to bring into view (a new temporary block), or null. */
    reveal: IntRange? = null,
    /** A long press on a block. */
    onLongPress: (GridBlock) -> Unit = {},
    /** Blocks (id, column) picked together after a long press; while any are picked, blocks cannot be dragged. */
    multiSelected: Set<Pair<Long, Int>> = emptySet(),
    modifier: Modifier = Modifier,
    behind: @Composable (column: Int, perMin: Dp) -> Unit = { _, _ -> },
    content: @Composable (GridBlock, Boolean) -> Unit,
) {
    val perMin = hourHeight / 60
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (hourHeight * initialScrollHour).roundToPx() }) }
    var viewportPx by remember { mutableIntStateOf(0) }
    var drag by remember { mutableStateOf<DragInfo?>(null) }
    // blocks that the dragged block pushes aside: (block, column) to their shortened (start, end), shown until the data catches up
    var trims by remember { mutableStateOf<Map<Pair<Long, Int>, Pair<Int, Int>>>(emptyMap()) }
    LaunchedEffect(blocks) { trims = emptyMap() }
    val allBlocks by rememberUpdatedState(blocks)
    // keep a temporary block in view when it appears, also after the panel under the grid took some room
    LaunchedEffect(reveal, viewportPx) {
        val r = reveal ?: return@LaunchedEffect
        if (viewportPx == 0) return@LaunchedEffect
        val pxPerMin = with(density) { perMin.toPx() }
        val margin = with(density) { 24.dp.toPx() }
        val pad = with(density) { GRID_PAD.toPx() }
        val top = pad + r.first * pxPerMin
        val bottom = pad + r.last * pxPerMin
        when {
            top - margin < scroll.value -> scroll.animateScrollTo((top - margin).toInt().coerceAtLeast(0))
            bottom + margin > scroll.value + viewportPx -> scroll.animateScrollTo((bottom + margin - viewportPx).toInt().coerceAtLeast(0))
        }
    }
    // While a block is dragged near the top or bottom edge, the grid scrolls by itself. [extraPx] is how far it
    // has scrolled since the drag began: the finger stays still, so the block follows the moving grid.
    var extraPx by remember { mutableFloatStateOf(0f) }
    val edgePx = with(density) { 56.dp.toPx() }
    val scrollDir by remember {
        derivedStateOf {
            val d = drag
            if (d == null || viewportPx == 0) 0 else {
                val y = with(density) { GRID_PAD.toPx() + perMin.toPx() * d.leadMin } - scroll.value
                if (y < edgePx) -1 else if (y > viewportPx - edgePx) 1 else 0
            }
        }
    }
    val dragging = drag != null
    LaunchedEffect(dragging) { if (dragging) extraPx = 0f }
    LaunchedEffect(scrollDir) {
        if (scrollDir == 0) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val d = drag ?: break
            val y = with(density) { GRID_PAD.toPx() + perMin.toPx() * d.leadMin } - scroll.value
            val depth = if (scrollDir < 0) (edgePx - y) / edgePx else (y - (viewportPx - edgePx)) / edgePx
            if (depth <= 0f) break
            val step = scrollDir * (4f + 20f * depth.coerceAtMost(1.5f))
            val used = scroll.scrollBy(step)
            if (used == 0f) break   // end of the grid
            extraPx += used
        }
    }
    Box(modifier.testTag("timegrid").onSizeChanged { viewportPx = it.height }.clip(RoundedCornerShape(20.dp)).background(p.surface)) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().padding(vertical = GRID_PAD).height(hourHeight * 24)) {
                // today's column is tinted first, so the hour lines stay visible on top of it
                if (columns > 1 && todayColumn != null) {
                    Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 6.dp)) {
                        for (col in 0 until columns) {
                            Box(Modifier.weight(1f).fillMaxHeight().background(if (col == todayColumn) p.bar else Color.Transparent))
                        }
                    }
                }
                // hour lines and labels; the last line closes the grid at midnight
                Box(Modifier.offset(y = hourHeight * 24 - 1.dp).fillMaxWidth().height(1.dp).padding(start = 36.dp, end = if (columns > 1) 6.dp else 0.dp).background(p.line.copy(alpha = 0.6f)))
                for (h in 0..23) {
                    Box(Modifier.offset(y = hourHeight * h).fillMaxWidth().height(1.dp).padding(start = 36.dp, end = if (columns > 1) 6.dp else 0.dp).background(p.line.copy(alpha = 0.6f)))
                    Text(
                        "%02d".format(h), color = p.muted, fontSize = 12.sp,
                        modifier = Modifier.offset(y = hourHeight * h - 8.dp).padding(start = 8.dp),
                    )
                }
                Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 6.dp)) {
                    for (col in 0 until columns) {
                        val pxPerMin = with(density) { perMin.toPx() }
                        val lineColor = p.line
                        val lineWidth = with(density) { 1.dp.toPx() }
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                // a line before every day separates the days of the week
                                .drawBehind {
                                    if (columns > 1) drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), lineWidth)
                                    // the last day is closed by a line on its right, so the grid has an edge there too
                                    if (columns > 1 && col == columns - 1) drawLine(lineColor, Offset(size.width - lineWidth / 2, 0f), Offset(size.width - lineWidth / 2, size.height), lineWidth)
                                }
                                .pointerInput(col, pxPerMin) {
                                    detectTapGestures { pos ->
                                        val minute = (pos.y / pxPerMin).toInt().coerceIn(0, 24 * 60 - 15) / 15 * 15
                                        onTapEmpty(col, minute)
                                    }
                                },
                        ) {
                            behind(col, perMin)
                            blocks.filter { it.column == col }.forEach { original ->
                                val b = trims[original.id to original.column]?.let { original.copy(startMin = it.first, endMin = it.second) } ?: original
                                key(b.id, b.column) {
                                    DraggableBlock(
                                        b,
                                        if (multiSelected.isNotEmpty()) (b.id to b.column) in multiSelected
                                        else b.id == selectedId && (selectedColumn == null || b.column == selectedColumn),
                                        multiSelected.isNotEmpty(), onLongPress,
                                        perMin, p, onSelect, onChange, { drag = it }, { extraPx },
                                        neighbours = {
                                            allBlocks.filter { it.column == b.column && it.id != b.id && it.id != GHOST_ID }
                                                .map { Span(it.id, it.startMin, it.endMin, fixed = !it.editable) }
                                        },
                                        onTrims = { list -> trims = list.associate { (it.id to b.column) to (it.start to it.end) } },
                                    ) { sel -> content(b, sel) }
                                }
                            }
                        }
                    }
                }
                if (nowMin != null) {
                    Box(Modifier.offset(y = perMin * nowMin).fillMaxWidth().padding(start = 30.dp).height(2.dp).background(Color(0xFFD9930A)))
                }
                // the bubble that says which time the finger is picking, on top of everything
                drag?.let { d ->
                    Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 6.dp)) {
                        for (col in 0 until columns) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (col == d.column) TimeBubble(d, columns, col, perMin, p)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows the time under the finger next to the block: on the side with more room in the week,
 * at the right end of the block in the single-column day view.
 */
@Composable
private fun TimeBubble(d: DragInfo, columns: Int, col: Int, perMin: Dp, p: Palette) {
    val density = LocalDensity.current
    val edgeY = with(density) { (perMin * d.edgeMin).roundToPx() }
    val gap = with(density) { 6.dp.roundToPx() }
    val inset = with(density) { 12.dp.roundToPx() }
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            layout(constraints.maxWidth, placeable.height) {
                val x = when {
                    columns == 1 -> constraints.maxWidth - placeable.width - inset
                    col < columns / 2 -> constraints.maxWidth + gap
                    else -> -placeable.width - gap
                }
                // above the finger rather than under it
                val y = (edgeY - placeable.height - gap).coerceAtLeast(0)
                placeable.place(IntOffset(x, y))
            }
        },
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = p.ink, shadowElevation = 4.dp, modifier = Modifier.testTag("time-bubble")) {
            Text(
                d.label, color = p.bg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableBlock(
    b: GridBlock,
    selected: Boolean,
    multiMode: Boolean,
    onLongPress: (GridBlock) -> Unit,
    perMin: Dp,
    p: Palette,
    onSelect: (GridBlock) -> Unit,
    onChange: (GridBlock, Int, Int) -> Unit,
    onDragInfo: (DragInfo?) -> Unit,
    extra: () -> Float,
    neighbours: () -> List<Span>,
    onTrims: (List<Span>) -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    var s by remember(b.startMin, b.endMin) { mutableIntStateOf(b.startMin) }
    var e by remember(b.startMin, b.endMin) { mutableIntStateOf(b.endMin) }
    val pxPerMin = with(LocalDensity.current) { perMin.toPx() }
    val canEdit = b.editable && selected && !multiMode
    val haptic = LocalHapticFeedback.current
    val extraNow by rememberUpdatedState(extra)
    val neighboursNow by rememberUpdatedState(neighbours)
    // keeps the other blocks of the day out of the way while this one is dragged
    val fitHolder = remember { arrayOfNulls<BlockFit>(1) }
    // what the finger is doing: 0 nothing, 1 moving the block, 2 top handle, 3 bottom handle
    var mode by remember { mutableIntStateOf(0) }
    var acc by remember { mutableFloatStateOf(0f) }
    var baseS by remember { mutableIntStateOf(0) }
    var baseE by remember { mutableIntStateOf(0) }
    fun snap(v: Float) = (v / SNAP).roundToInt() * SNAP
    /** Works out the times from how far the finger (and the scrolling grid) has moved. */
    fun apply() {
        val dy = (acc + extraNow()) / pxPerMin
        when (mode) {
            1 -> {
                val (ns, ne) = fitHolder[0]!!.move(snap(baseS + dy))
                s = ns; e = ne
                onDragInfo(DragInfo(b.column, s, "${clock(s)} to ${clock(e)}", leadMin = if (dy >= 0) e else s))
            }
            2 -> {
                s = fitHolder[0]!!.top(snap(baseS + dy))
                onDragInfo(DragInfo(b.column, s, clock(s)))
            }
            3 -> {
                e = fitHolder[0]!!.bottom(snap(baseE + dy))
                onDragInfo(DragInfo(b.column, e, clock(e)))
            }
        }
        onTrims(fitHolder[0]!!.shortened())
    }
    fun start(m: Int) {
        mode = m; baseS = s; baseE = e; acc = 0f
        fitHolder[0] = BlockFit(s, e, neighboursNow(), canShrink = b.id != GHOST_ID)
        apply()
    }
    fun finish() {
        mode = 0; onDragInfo(null)
        val pushed = fitHolder[0]?.shortened().orEmpty()
        onChange(b, s, e)
        // the neighbours that were pushed aside keep their new, shorter times
        pushed.forEach { sp -> onChange(b.copy(id = sp.id, startMin = sp.start, endMin = sp.end), sp.start, sp.end) }
    }
    // the grid scrolled under a still finger: the block follows
    LaunchedEffect(Unit) { snapshotFlow { extraNow() }.collect { if (mode != 0) apply() } }
    Box(
        Modifier.offset(y = perMin * s).fillMaxWidth().height((perMin * (e - s)).coerceAtLeast(14.dp))
            .padding(horizontal = 2.dp)
            .testTag("block-${b.id}-${b.column}")
            // dragging is not possible with a screen reader: the same changes as actions, 15 minutes at a time
            .semantics {
                if (canEdit) customActions = listOf(
                    CustomAccessibilityAction("Move 15 minutes earlier") {
                        (b.startMin >= 15).also { if (it) onChange(b, b.startMin - 15, b.endMin - 15) }
                    },
                    CustomAccessibilityAction("Move 15 minutes later") {
                        (b.endMin <= 24 * 60 - 15).also { if (it) onChange(b, b.startMin + 15, b.endMin + 15) }
                    },
                    CustomAccessibilityAction("Make 15 minutes longer") {
                        (b.endMin <= 24 * 60 - 15).also { if (it) onChange(b, b.startMin, b.endMin + 15) }
                    },
                    CustomAccessibilityAction("Make 15 minutes shorter") {
                        (b.endMin - b.startMin > 15).also { if (it) onChange(b, b.startMin, b.endMin - 15) }
                    },
                )
            }
            .combinedClickable(
                onClick = { onSelect(b) },
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress(b) },
            )
            .then(
                if (canEdit) Modifier.pointerInput(b.startMin, b.endMin) {
                    detectVerticalDragGestures(
                        onDragStart = { start(1) },
                        onDragEnd = { finish() },
                        onDragCancel = { mode = 0; onDragInfo(null); onTrims(emptyList()); s = b.startMin; e = b.endMin },
                    ) { change, dy ->
                        change.consume()
                        acc += dy
                        apply()
                    }
                } else Modifier
            ),
    ) {
        content(selected)
        if (canEdit) {
            Handle(
                Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).testTag("handle-top"), p,
                onStart = { start(2) },
                onDrag = { total -> acc = total; apply() },
                onEnd = { finish() },
            )
            Handle(
                Modifier.align(Alignment.BottomCenter).offset(y = 18.dp).testTag("handle-bottom"), p,
                onStart = { start(3) },
                onDrag = { total -> acc = total; apply() },
                onEnd = { finish() },
            )
        }
    }
}

/** A touch target with a small pill; reports the total vertical movement since the drag started. */
@Composable
private fun Handle(modifier: Modifier, p: Palette, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    // The gesture below starts once and lives on. The block it belongs to gets new state whenever its times change
    // (after the first change, for instance), so the gesture must always call the newest callbacks, never the first ones.
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        modifier.width(72.dp).height(36.dp).pointerInput(Unit) {
            var acc = 0f
            detectVerticalDragGestures(
                onDragStart = { acc = 0f; start() },
                onDragEnd = { end() },
                onDragCancel = { end() },
            ) { change, dy ->
                change.consume()
                acc += dy
                drag(acc)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(30.dp).height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(Color.White).border(2.dp, p.ink, RoundedCornerShape(6.dp)),
        )
    }
}
