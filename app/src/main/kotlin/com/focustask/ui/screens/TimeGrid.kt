package com.focustask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val outlineDanger: Boolean = false,
)

private const val SNAP = 5
private const val MIN_LEN = 15

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
    modifier: Modifier = Modifier,
    behind: @Composable (column: Int, perMin: Dp) -> Unit = { _, _ -> },
    content: @Composable (GridBlock, Boolean) -> Unit,
) {
    val perMin = hourHeight / 60
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (hourHeight * initialScrollHour).roundToPx() }) }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(p.surface)) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth().height(hourHeight * 24)) {
                // hour lines and labels
                for (h in 0..23) {
                    Box(Modifier.offset(y = hourHeight * h).fillMaxWidth().height(1.dp).padding(start = 36.dp).background(p.line.copy(alpha = 0.6f)))
                    Text(
                        "%02d".format(h), color = p.muted, fontSize = 12.sp,
                        modifier = Modifier.offset(y = hourHeight * h - 8.dp).padding(start = 8.dp),
                    )
                }
                Row(Modifier.fillMaxSize().padding(start = 36.dp, end = 6.dp)) {
                    for (col in 0 until columns) {
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (col == todayColumn && columns > 1) p.bar else Color.Transparent),
                        ) {
                            behind(col, perMin)
                            blocks.filter { it.column == col }.forEach { b ->
                                androidx.compose.runtime.key(b.id, b.column) {
                                    DraggableBlock(b, b.id == selectedId && (selectedColumn == null || b.column == selectedColumn), perMin, p, onSelect, onChange) { sel -> content(b, sel) }
                                }
                            }
                        }
                    }
                }
                if (nowMin != null) {
                    Box(Modifier.offset(y = perMin * nowMin).fillMaxWidth().padding(start = 30.dp).height(2.dp).background(Color(0xFFD9930A)))
                }
            }
        }
    }
}

@Composable
private fun DraggableBlock(
    b: GridBlock,
    selected: Boolean,
    perMin: Dp,
    p: Palette,
    onSelect: (GridBlock) -> Unit,
    onChange: (GridBlock, Int, Int) -> Unit,
    content: @Composable (Boolean) -> Unit,
) {
    var s by remember(b.startMin, b.endMin) { mutableIntStateOf(b.startMin) }
    var e by remember(b.startMin, b.endMin) { mutableIntStateOf(b.endMin) }
    val pxPerMin = with(LocalDensity.current) { perMin.toPx() }
    val canEdit = b.editable && selected
    Box(
        Modifier.offset(y = perMin * s).fillMaxWidth().height((perMin * (e - s)).coerceAtLeast(14.dp))
            .padding(horizontal = 2.dp)
            .clickable { onSelect(b) }
            .then(
                if (canEdit) Modifier.pointerInput(b.startMin, b.endMin) {
                    var acc = 0f
                    val len = b.endMin - b.startMin
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { onChange(b, s, e) },
                        onDragCancel = { s = b.startMin; e = b.endMin },
                    ) { change, dy ->
                        change.consume()
                        acc += dy
                        val moved = ((b.startMin + acc / pxPerMin) / SNAP).roundToInt() * SNAP
                        val ns = moved.coerceIn(0, 24 * 60 - len)
                        s = ns; e = ns + len
                    }
                } else Modifier
            ),
    ) {
        content(selected)
        if (canEdit) {
            var base by remember { mutableIntStateOf(0) }
            Handle(
                Modifier.align(Alignment.TopCenter).offset(y = (-18).dp), p,
                onStart = { base = s },
                onDrag = { acc -> s = (((base + acc / pxPerMin) / SNAP).roundToInt() * SNAP).coerceIn(0, e - MIN_LEN) },
                onEnd = { onChange(b, s, e) },
            )
            Handle(
                Modifier.align(Alignment.BottomCenter).offset(y = 18.dp), p,
                onStart = { base = e },
                onDrag = { acc -> e = (((base + acc / pxPerMin) / SNAP).roundToInt() * SNAP).coerceIn(s + MIN_LEN, 24 * 60) },
                onEnd = { onChange(b, s, e) },
            )
        }
    }
}

/** A touch target with a small pill; reports the total vertical movement since the drag started. */
@Composable
private fun Handle(modifier: Modifier, p: Palette, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    Box(
        modifier.width(72.dp).height(36.dp).pointerInput(Unit) {
            var acc = 0f
            detectVerticalDragGestures(
                onDragStart = { acc = 0f; onStart() },
                onDragEnd = { onEnd() },
                onDragCancel = { onEnd() },
            ) { change, dy ->
                change.consume()
                acc += dy
                onDrag(acc)
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
