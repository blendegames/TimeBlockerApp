package com.focustask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.data.minutesToTime
import com.focustask.data.toMinutes
import com.focustask.ui.theme.Palette
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
fun WeekScreen(ui: UiState, vm: MainViewModel, p: Palette, onAddBlock: () -> Unit, onEditBlock: (Long) -> Unit) {
    val data = ui.data
    val today = ui.now.toLocalDate()
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    var selected by remember { mutableStateOf<Long?>(null) }
    var selectedCol by remember { mutableStateOf<Int?>(null) }
    val days = DayOfWeek.values()

    val gridBlocks = data.blocks.flatMap { b ->
        days.indices.filter { days[it] in b.days }.map { col ->
            GridBlock(
                id = b.id, column = col, startMin = b.start.toMinutes(),
                endMin = if (b.crossesMidnight) 24 * 60 else b.end.toMinutes(),
                color = Color(data.colorOf(b.id)), editable = !b.crossesMidnight,
            )
        }
    }
    val todayCol = today.dayOfWeek.value - 1

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Repeats every week", color = p.muted, fontSize = 13.sp)
                val range = if (monday.month == sunday.month) "${Fmt.monthDay(monday)} to ${sunday.dayOfMonth}" else "${Fmt.monthDay(monday)} to ${Fmt.monthDay(sunday)}"
                Text(range, color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            AddButton("Add block", p, onAddBlock)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp).padding(start = 36.dp)) {
            days.forEachIndexed { i, d ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (i == todayCol) p.accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                            color = if (i == todayCol) p.onAccent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        TimeGrid(
            columns = 7,
            blocks = gridBlocks,
            hourHeight = 44.dp,
            p = p,
            selectedId = selected,
            selectedColumn = selectedCol,
            todayColumn = todayCol,
            nowMin = null,
            initialScrollHour = 8,
            onSelect = {
                if (selected == it.id && selectedCol == it.column) { selected = null; selectedCol = null }
                else { selected = it.id; selectedCol = it.column }
            },
            onChange = { b, s, e -> vm.run { moveBlock(b.id, minutesToTime(s), minutesToTime(e % (24 * 60))) } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { gb, isSel ->
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(gb.color)
                    .then(if (isSel) Modifier.border(3.dp, p.ink, RoundedCornerShape(12.dp)) else Modifier),
            )
        }
        // legend: tap to edit
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            data.blocks.sortedBy { it.start }.take(5).forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEditBlock(b.id) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(data.colorOf(b.id))))
                    Spacer(Modifier.width(10.dp))
                    Text(b.name, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "  ${Fmt.time(b.start)} to ${Fmt.time(b.end)} · ${daysLabel(b.days)}", color = p.muted, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
