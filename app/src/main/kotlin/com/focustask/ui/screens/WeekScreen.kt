package com.focustask.ui.screens

import com.focustask.ui.theme.TitleSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.Span
import com.focustask.data.BLOCK_COLORS
import com.focustask.data.minutesToTime
import com.focustask.data.toMinutes
import com.focustask.ui.BlockTarget
import com.focustask.ui.theme.Palette
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun WeekScreen(
    ui: UiState, vm: MainViewModel, p: Palette, onEditBlock: (Long) -> Unit,
    onMoreOptions: (BlockTarget.New) -> Unit = {},
) {
    val data = ui.data
    val today = ui.now.toLocalDate()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    var selected by remember { mutableStateOf<Long?>(null) }
    var selectedCol by remember { mutableStateOf<Int?>(null) }
    // blocks picked together after a long press: (block, day column)
    var multi by remember { mutableStateOf<Set<Pair<Long, Int>>>(emptySet()) }
    // a tap on an empty spot shows a temporary one-hour block with quick settings, like a calendar app
    var ghost by remember { mutableStateOf<Ghost?>(null) }
    var ghostList by remember { mutableStateOf<Long?>(null) }
    var newListDialog by remember { mutableStateOf(false) }
    // the list whose color is being chosen (tap in the legend)
    var colorFor by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = ghost != null || multi.isNotEmpty() || colorFor != null) { ghost = null; multi = emptySet(); colorFor = null }
    val days = DayOfWeek.values()

    // worked out again only when the data changes, not on every redraw
    val gridBlocks = remember(data) {
        data.blocks.flatMap { b ->
            days.indices.filter { days[it] in b.days }.map { col ->
                val onDay = data.blockOn(b, days[col])
                GridBlock(
                    id = b.id, column = col, startMin = onDay.start.toMinutes(),
                    endMin = if (onDay.crossesMidnight) 24 * 60 else onDay.end.toMinutes(),
                    color = Color(data.colorOf(b.id)), editable = !onDay.crossesMidnight,
                )
            }
        }
    }
    val todayCol = today.dayOfWeek.value - 1
    val ghostColor = Color(ghostList?.let { BLOCK_COLORS[data.colorOfListIndex(it) % BLOCK_COLORS.size] } ?: 0xFF2F5D50)
    val allGridBlocks = remember(gridBlocks, ghost, ghostColor) {
        gridBlocks + listOfNotNull(ghost?.let { GridBlock(id = GHOST_ID, column = it.column, startMin = it.startMin, endMin = it.endMin, color = ghostColor) })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val range = if (monday.month == sunday.month) "${Fmt.monthDay(monday)} to ${sunday.dayOfMonth}" else "${Fmt.monthDay(monday)} to ${Fmt.monthDay(sunday)}"
        Text(range, color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, end = 48.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).padding(start = 36.dp)) {
            days.forEachIndexed { i, d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (i == todayCol) p.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            color = if (i == todayCol) p.onAccent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        TimeGrid(
            columns = 7,
            blocks = allGridBlocks,
            hourHeight = 44.dp,
            p = p,
            selectedId = if (ghost != null) GHOST_ID else selected,
            selectedColumn = if (ghost != null) ghost?.column else selectedCol,
            reveal = ghost?.let { it.startMin..it.endMin },
            todayColumn = todayCol,
            nowMin = null,
            initialScrollHour = 8,
            multiSelected = multi,
            onLongPress = {
                if (it.id == GHOST_ID) return@TimeGrid
                ghost = null; selected = null; selectedCol = null
                multi = multi + (it.id to it.column)
            },
            onSelect = {
                if (it.id == GHOST_ID) return@TimeGrid
                colorFor = null
                if (multi.isNotEmpty()) {
                    val key = it.id to it.column
                    multi = if (key in multi) multi - key else multi + key
                    return@TimeGrid
                }
                ghost = null
                if (selected == it.id && selectedCol == it.column) { selected = null; selectedCol = null }
                else { selected = it.id; selectedCol = it.column }
            },
            onTapEmpty = { col, minute ->
                if (colorFor != null) colorFor = null
                else if (multi.isNotEmpty()) multi = emptySet()
                else if (selected != null) { selected = null; selectedCol = null }
                else ghost = Ghost.fitted(col, minute, gridBlocks.filter { it.column == col }.map { Span(it.id, it.startMin, it.endMin) })
            },
            onChange = { b, s, e ->
                if (b.id == GHOST_ID) { ghost = Ghost(b.column, s, e); return@TimeGrid }
                vm.run { moveBlock(b.id, days[b.column], minutesToTime(s), minutesToTime(e % (24 * 60))) } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { gb, isSel ->
            if (gb.id == GHOST_ID) {
                ghost?.let { GhostContent(it, null, ghostColor, wide = false) }
                return@TimeGrid
            }
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(gb.color)
                    .then(if (isSel) Modifier.border(3.dp, p.ink, RoundedCornerShape(12.dp)) else Modifier),
            )
        }
        val g = ghost
        if (g != null) {
            val day = days[g.column]
            val dayName = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            QuickBlockPanel(
                data, p,
                title = "New block · ${dayName.take(3)} ${clockText(g.startMin)} to ${clockText(g.endMin)}",
                note = "Repeats every $dayName · from tomorrow",
                listId = ghostList, onList = { ghostList = it }, onNewList = { newListDialog = true },
                onSave = {
                    val id = ghostList ?: return@QuickBlockPanel
                    vm.run {
                        saveBlock(
                            null, id, minutesToTime(g.startMin), minutesToTime(g.endMin % (24 * 60)),
                            setOf(day), data.colorOfListIndex(id), showToday = false,
                        )
                    }
                    ghost = null; ghostList = null
                },
                onMore = {
                    onMoreOptions(
                        BlockTarget.New(
                            fromDay = false, start = minutesToTime(g.startMin), end = minutesToTime(g.endMin % (24 * 60)),
                            day = day, listId = ghostList,
                        ),
                    )
                },
                onCancel = { ghost = null; ghostList = null },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // actions for the selected block
        if (multi.isNotEmpty()) SelectionBar(
            multi.size, p, modifier = Modifier.padding(top = 8.dp),
            onCancel = { multi = emptySet() },
            onDelete = {
                val doomed = multi
                multi = emptySet()
                vm.run { doomed.forEach { (id, col) -> removeFromWeekday(id, days[col]) } }
            },
        )
        val colorList = colorFor?.let { id -> data.lists.firstOrNull { it.id == id } }
        val sel = if (g == null && multi.isEmpty() && colorList == null) selected?.let { data.block(it) } else null
        if (sel != null) {
            val dayName = selectedCol?.let { days[it].getDisplayName(TextStyle.FULL, Locale.getDefault()) }
            Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sel.name, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text("Drag to change${dayName?.let { " $it" } ?: ""} · from tomorrow", color = p.muted, fontSize = 12.sp, maxLines = 1)
                    }
                    ContainedButton("Edit", p, onClick = { onEditBlock(sel.id) })
                    Spacer(Modifier.width(8.dp))
                    ContainedButton("Delete", p, tone = ButtonTone.DANGER, onClick = {
                        selectedCol?.let { col -> vm.run { removeFromWeekday(sel.id, days[col]) } }
                        selected = null; selectedCol = null
                    })
                }
            }
        }
        // legend: one row of buttons, a button per list in its color. Tapping one opens a small panel to pick that list's
        // color. Hidden while a new block is being set up, to leave room for its settings.
        if (colorList != null && g == null && multi.isEmpty()) {
            Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("color-panel")) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color of ${colorList.title}", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        ContainedButton("Done", p, tone = ButtonTone.PRIMARY, onClick = { colorFor = null })
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val current = data.colorOfListIndex(colorList.id)
                        BLOCK_COLORS.forEachIndexed { index, c ->
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Color(c))
                                    .then(if (index == current) Modifier.border(3.dp, p.ink, CircleShape) else Modifier)
                                    .testTag("list-color-$index")
                                    .colorChoice(index, index == current) { vm.run { setListColor(colorList.id, index) } },
                            )
                        }
                    }
                }
            }
        } else if (g == null && multi.isEmpty()) {
            val legend = data.blocks.mapNotNull { b -> data.listOfBlock(b.id) }.distinctBy { it.id }.sortedBy { it.position }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()).testTag("legend"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                legend.forEach { l ->
                    ContainedButton(
                        l.title, p,
                        leading = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(data.colorOfListAlways(l.id)))) },
                        onClick = { colorFor = l.id; selected = null; selectedCol = null },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (newListDialog) NewListDialog(data.suggestedColor(), p, onDismiss = { newListDialog = false }) { name, color ->
        newListDialog = false
        vm.run { ghostList = addList(name, color) }
    }
}
