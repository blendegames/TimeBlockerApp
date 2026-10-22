package com.focustask.ui.screens

import com.focustask.ui.theme.TitleSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.BreakPlanner
import com.focustask.core.Schedule
import com.focustask.core.Span
import com.focustask.data.BLOCK_COLORS
import com.focustask.data.minutesToTime
import com.focustask.data.toMinutes
import com.focustask.ui.theme.Palette
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DayScreen(
    ui: UiState, vm: MainViewModel, p: Palette,
) {
    val data = ui.data
    val today = ui.now.toLocalDate()
    val nowMin = ui.now.toLocalTime().toMinutes()
    var selected by remember { mutableStateOf<Long?>(null) }
    // blocks picked together after a long press
    var multi by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // a tap on an empty spot shows a temporary one-hour block with quick settings, like a calendar app
    var ghost by remember { mutableStateOf<Ghost?>(null) }
    var ghostList by remember { mutableStateOf<Long?>(null) }
    var newListDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = ghost != null || multi.isNotEmpty()) { ghost = null; multi = emptySet() }

    // worked out again only when the data or the day changes, not on every redraw
    val todays = remember(data, today) { data.blocksFor(today).filter { today.dayOfWeek in it.days } }
    val activeIds = ui.focus.active?.block?.id
    val breaks = remember(data, today, ui.now.hour) {
        BreakPlanner.windows(Schedule.instances(ui.now, data::blocksFor), data.settings.cycle)
            .filter { it.from.toLocalDate() == today }
    }
    val gridBlocks = remember(todays, data) {
        todays.map { b ->
            GridBlock(
                id = b.id, column = 0, startMin = b.start.toMinutes(),
                endMin = if (b.crossesMidnight) 24 * 60 else b.end.toMinutes(),
                color = Color(data.colorOf(b.id)), editable = !b.crossesMidnight,
            )
        }
    }
    val ghostColor = Color(ghostList?.let { BLOCK_COLORS[data.colorOfListIndex(it) % BLOCK_COLORS.size] } ?: 0xFF2F5D50)
    val allGridBlocks = remember(gridBlocks, ghost, ghostColor) {
        gridBlocks + listOfNotNull(ghost?.let { GridBlock(id = GHOST_ID, column = 0, startMin = it.startMin, endMin = it.endMin, color = ghostColor) })
    }
    val anytime = data.anytimeLists()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "${Fmt.dayName(today)}, ${Fmt.monthDay(today)}", color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold,
            maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, end = 48.dp),
        )
        if (anytime.isNotEmpty()) {
            val count = anytime.sumOf { data.openCount(it.id) }
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .drawBehind {
                        drawRoundRect(
                            p.muted.copy(alpha = 0.6f), cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    }.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Text("Anytime", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${anytime.joinToString(", ") { it.title }} · ${tasksLabel(count)}, shown only when nothing else is due",
                        color = p.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TimeGrid(
            columns = 1,
            blocks = allGridBlocks,
            hourHeight = 56.dp,
            p = p,
            selectedId = if (ghost != null) GHOST_ID else selected,
            reveal = ghost?.let { it.startMin..it.endMin },
            todayColumn = null,
            nowMin = nowMin,
            initialScrollHour = (nowMin / 60 - 1).coerceAtLeast(0),
            multiSelected = multi.map { it to 0 }.toSet(),
            onLongPress = {
                if (it.id != GHOST_ID) { ghost = null; selected = null; multi = multi + it.id }
            },
            onSelect = {
                if (it.id == GHOST_ID) return@TimeGrid
                if (multi.isNotEmpty()) { multi = if (it.id in multi) multi - it.id else multi + it.id; return@TimeGrid }
                ghost = null; selected = if (selected == it.id) null else it.id
            },
            // a tap on an empty spot first lets go of the selected block, then shows a new one-hour block there
            onTapEmpty = { _, minute ->
                if (multi.isNotEmpty()) multi = emptySet()
                else if (selected != null) selected = null
                else ghost = Ghost.fitted(0, minute, gridBlocks.map { Span(it.id, it.startMin, it.endMin) })
            },
            onChange = { b, s, e ->
                if (b.id == GHOST_ID) { ghost = Ghost(0, s, e); return@TimeGrid }
                vm.run { changeForDay(b.id, today, minutesToTime(s), minutesToTime(e % (24 * 60))) }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            behind = { _, perMin ->
                // the long breaks of today: every few hours from the first block, whatever is running
                breaks.forEach { w ->
                    Row(
                        Modifier.offset(y = perMin * (w.from.hour * 60 + w.from.minute) + 2.dp).padding(horizontal = 14.dp)
                            .clip(CircleShape).background(p.chip).padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Pause, null, tint = p.muted, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Long break · ${Fmt.time(w.from)}–${Fmt.time(w.until)}", color = p.muted, fontSize = 12.sp)
                    }
                }
            },
        ) { gb, isSel ->
            if (gb.id == GHOST_ID) {
                ghost?.let { GhostContent(it, ghostList?.let { id -> data.lists.firstOrNull { l -> l.id == id }?.title }, ghostColor, wide = true) }
                return@TimeGrid
            }
            val b = data.block(gb.id)!!
            // like the blocks of the Week tab: a solid block in the list's color, with its name on the left and its times on the right
            val shape = RoundedCornerShape(12.dp)
            BoxWithConstraints(
                Modifier.fillMaxSize().clip(shape).background(gb.color)
                    .then(if (isSel) Modifier.border(3.dp, p.ink, shape) else Modifier),
            ) {
                val tight = maxHeight < 40.dp
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = if (tight) 0.dp else 6.dp),
                    verticalAlignment = if (tight) Alignment.CenterVertically else Alignment.Top,
                ) {
                    // the name (and NOW) take the room that is left, so the times end right at the edge of the block
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.name, color = Color.White, fontSize = if (tight) 14.sp else 17.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (activeIds == b.id) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "NOW", color = gb.color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Fmt.time(gb.startMin.toTime())} to ${Fmt.time(gb.endMin.toTime())}", color = Color.White,
                        fontSize = if (tight) 12.sp else 13.sp, maxLines = 1,
                        modifier = if (tight) Modifier else Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        val g = ghost
        if (g != null) {
            val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            QuickBlockPanel(
                data, p,
                title = "New block · ${clockText(g.startMin)} to ${clockText(g.endMin)}",
                note = "Shows today and repeats every $weekday",
                listId = ghostList, onList = { ghostList = it }, onNewList = { newListDialog = true },
                onSave = {
                    val id = ghostList ?: return@QuickBlockPanel
                    vm.run {
                        saveBlock(
                            null, id, minutesToTime(g.startMin), minutesToTime(g.endMin % (24 * 60)),
                            setOf(today.dayOfWeek), data.colorOfListIndex(id), showToday = true,
                        )
                    }
                    ghost = null; ghostList = null
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
                val doomed = todays.filter { it.id in multi }
                multi = emptySet()
                vm.run { doomed.forEach { skipForDay(it.id, today, it.start, it.end) } }
            },
        )
        val sel = todays.firstOrNull { it.id == selected }
        if (g == null && multi.isEmpty() && sel != null) {
            val changed = data.changeFor(sel.id, today)
            Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (changed != null) {
                        ContainedButton("Reset", p, modifier = Modifier.weight(1f), onClick = { vm.run { restoreDay(sel.id, today) } })
                    }
                    ContainedButton("Delete", p, modifier = Modifier.weight(1f), tone = ButtonTone.DANGER, onClick = {
                        vm.run { skipForDay(sel.id, today, sel.start, sel.end) }
                        selected = null
                    })
                }
            }
        }
        if (g == null && multi.isEmpty() && data.differsFromPlan(today)) {
            Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        "Today is different from your weekly schedule", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContainedButton("Keep weekly schedule", p, maxLines = 2, modifier = Modifier.weight(1f)) { vm.run { restoreWholeDay(today) } }
                        ContainedButton("Update weekly schedule", p, tone = ButtonTone.PRIMARY, maxLines = 2, modifier = Modifier.weight(1f)) { vm.run { makeDayThePlan(today) } }
                    }
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

private fun Int.toTime(): LocalTime =
    if (this >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(this / 60, this % 60)
