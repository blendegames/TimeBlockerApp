package com.focustask.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.ui.theme.Palette

@Composable
fun ListsScreen(ui: UiState, vm: MainViewModel, p: Palette, onOpen: (Long) -> Unit) {
    val data = ui.data
    var newList by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Lists", color = p.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            AddButton("New list", p, { newList = true })
        }
        Text(
            "Next three tasks of each list, in the order they will show.",
            color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(data.lists, key = { it.id }) { list ->
                val up = upcomingTasks(data.tasks, list.id)
                val block = list.blockId?.let { data.block(it) }
                val worst = up.firstOrNull { it.deadline?.let { d -> d < ui.now } == true }
                val hasOverdue = worst != null
                Surface(
                    shape = RoundedCornerShape(20.dp), color = p.surface,
                    border = if (hasOverdue) BorderStroke(2.dp, p.danger) else null,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(list.id) },
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(data.colorOfList(list.id))))
                            Spacer(Modifier.width(10.dp))
                            Text(list.title, color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tasksLabel(up.size), color = p.muted, fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.ChevronRight, null, tint = p.ink)
                        }
                        Text(
                            if (block != null) "Block ${Fmt.time(block.start)} to ${Fmt.time(block.end)} · ${daysLabel(block.days)}" else "Anytime · shown when nothing else is due",
                            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 22.dp, bottom = 6.dp),
                        )
                        if (up.isEmpty()) {
                            Text("Nothing to do here", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(start = 22.dp, top = 4.dp))
                        }
                        up.take(3).forEach { t ->
                            val late = t.deadline?.let { it < ui.now } == true
                            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(20.dp), shape = CircleShape, color = Color.Transparent,
                                    border = BorderStroke(2.dp, if (late) p.danger else p.muted)) {}
                                Spacer(Modifier.width(12.dp))
                                Text(t.title, color = p.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (t.deadline != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (late) Fmt.late(t.deadline!!, ui.now) else Fmt.deadlineShort(t.deadline!!, ui.now),
                                        color = if (late) p.danger else p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        if (up.size > 3) Text("+ ${up.size - 3} more", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp, top = 2.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (newList) TextEditDialog("New list", "", "Create", onDismiss = { newList = false }) { name ->
        newList = false
        vm.run { val id = addList(name); id }
    }
}
