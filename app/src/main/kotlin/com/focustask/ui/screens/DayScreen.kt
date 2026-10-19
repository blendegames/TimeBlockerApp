package com.focustask.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.data.toMinutes
import com.focustask.ui.theme.Palette
import java.time.LocalDate

@Composable
fun DayScreen(ui: UiState, vm: MainViewModel, p: Palette, onAddBlock: () -> Unit) {
    val data = ui.data
    val today = ui.now.toLocalDate()
    val nowMin = ui.now.toLocalTime().toMinutes()
    var selected by remember { mutableStateOf<Long?>(null) }

    val todays = data.blocksFor(today).filter { today.dayOfWeek in it.days }
    val skipped = data.blocks.filter { b ->
        today.dayOfWeek in b.days && data.changeFor(b.id, today)?.skipped == true
    }
    val activeIds = ui.focus.active?.block?.id
    val longBreak = data.settings.cycle.longBreakMin
    val gridBlocks = todays.map { b ->
        com.focustask.ui.screens.GridBlock(
            id = b.id, column = 0, startMin = b.start.toMinutes(),
            endMin = if (b.crossesMidnight) 24 * 60 else b.end.toMinutes(),
            color = Color(data.colorOf(b.id)), editable = !b.crossesMidnight,
        )
    }
    val anytime = data.lists.filter { it.blockId == null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("${Fmt.dayName(today)} · changes apply today only", color = p.muted, fontSize = 13.sp)
                Text(Fmt.monthDay(today), color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            AddButton("Add block", p, onAddBlock)
        }
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
            blocks = gridBlocks,
            hourHeight = 56.dp,
            p = p,
            selectedId = selected,
            todayColumn = null,
            nowMin = nowMin,
            initialScrollHour = (nowMin / 60 - 1).coerceAtLeast(0),
            onSelect = { selected = if (selected == it.id) null else it.id },
            onChange = { b, s, e ->
                vm.run { changeForDay(b.id, today, com.focustask.data.minutesToTime(s), com.focustask.data.minutesToTime(e % (24 * 60))) }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            behind = { _, perMin ->
                // the long break after each block
                todays.filter { !it.crossesMidnight }.forEach { b ->
                    val end = b.end.toMinutes()
                    if (longBreak > 0) {
                        Row(
                            Modifier.offset(y = perMin * end + 2.dp).padding(horizontal = 14.dp)
                                .clip(CircleShape).background(p.chip).padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Pause, null, tint = p.muted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            val to = b.end.plusMinutes(longBreak.toLong())
                            Text("Long break · ${Fmt.time(b.end)}–${Fmt.time(to)}", color = p.muted, fontSize = 12.sp)
                        }
                    }
                }
            },
        ) { gb, isSel ->
            val b = data.block(gb.id)!!
            val list = data.listOfBlock(b.id)
            val open = list?.let { data.openCount(it.id) } ?: 0
            val overdue = list?.let { l ->
                data.tasks.filter { it.listId == l.id && !it.done && it.deadline?.let { d -> d < ui.now } == true }
                    .minByOrNull { it.deadline!! }
            }
            val firstUp = list?.let { upcomingTasks(data.tasks, it.id).firstOrNull() }
            val color = Color(data.colorOf(b.id))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = p.surface,
                shadowElevation = 1.dp,
                border = BorderStroke(
                    if (isSel || overdue != null || activeIds == b.id) 2.dp else 1.dp,
                    when {
                        overdue != null -> p.danger
                        isSel || activeIds == b.id -> p.accent
                        else -> p.line
                    },
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(b.name, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (activeIds == b.id) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "NOW", color = p.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).background(p.accent).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${Fmt.time(gb.startMin.toTime())} to ${Fmt.time(gb.endMin.toTime())}", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Text("${tasksLabel(open)} · ${daysLabel(b.days)}", color = p.muted, fontSize = 12.sp, maxLines = 1)
                    if (overdue != null) {
                        Row(
                            Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.dangerSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Warning, null, tint = p.danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${overdue.title} · ${Fmt.late(overdue.deadline!!, ui.now)}", color = p.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (firstUp != null && gb.endMin - gb.startMin >= 45) {
                        Text(firstUp.title, color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        // actions for the selected block
        val sel = selected?.let { data.block(it) }
        if (sel != null) {
            val changed = data.changeFor(sel.id, today)
            Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sel.name, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text("Drag to change · today only", color = p.muted, fontSize = 12.sp)
                    }
                    if (changed != null) TextButton(onClick = { vm.run { restoreDay(sel.id, today) } }) { Text("Reset") }
                    TextButton(onClick = {
                        vm.run { skipForDay(sel.id, today, sel.start, sel.end) }
                        selected = null
                    }) { Text("Skip today", color = p.danger) }
                }
            }
        }
        if (skipped.isNotEmpty()) {
            skipped.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${b.name} skipped today", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.run { restoreDay(b.id, today) } }) { Text("Restore") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun Int.toTime(): java.time.LocalTime =
    if (this >= 24 * 60) java.time.LocalTime.of(23, 59) else java.time.LocalTime.of(this / 60, this % 60)
