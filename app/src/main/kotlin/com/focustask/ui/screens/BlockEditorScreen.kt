package com.focustask.ui.screens

import com.focustask.ui.theme.TitleSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.data.BLOCK_COLORS
import com.focustask.ui.BlockTarget
import com.focustask.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BlockEditorScreen(ui: UiState, vm: MainViewModel, target: BlockTarget, p: Palette, onClose: () -> Unit) {
    val data = ui.data
    val editing = (target as? BlockTarget.Edit)?.blockId?.let { data.block(it) }
    // Tapping an empty spot of a grid opens the editor with that time (one hour) and that weekday.
    val tapped = target as? BlockTarget.New

    var listId by remember { mutableStateOf<Long?>(editing?.let { data.listOfBlock(it.id)?.id } ?: tapped?.listId) }
    var start by remember { mutableStateOf(editing?.start ?: tapped?.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(editing?.end ?: tapped?.end ?: tapped?.start?.plusHours(1) ?: LocalTime.of(10, 0)) }
    var days by remember {
        mutableStateOf(editing?.days ?: tapped?.day?.let { setOf(it) } ?: DayOfWeek.values().toSet())
    }
    // The color belongs to the list: every block of a list looks the same.
    var color by remember { mutableStateOf(listId?.let { data.colors[it] } ?: 0) }
    LaunchedEffect(listId, listId?.let { data.colors.containsKey(it) }) {
        listId?.let { id -> data.colors[id]?.let { color = it } }
    }
    var menu by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }

    // Any list can have blocks, also one that already has some: a block is a preset of time and days.
    val choices = data.lists
    val chosen = data.lists.firstOrNull { it.id == listId }
    val valid = chosen != null && start != end && days.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Close", tint = p.ink)
            }
            Text(if (editing == null) "New time block" else "Edit time block", color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Label("List shown in this block", p)
            Box {
                Surface(
                    shape = RoundedCornerShape(16.dp), color = p.surface,
                    border = BorderStroke(2.dp, if (menu) p.accent else p.line),
                    modifier = Modifier.fillMaxWidth().clickable { menu = !menu },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(chosen?.title ?: "Choose a list", color = if (chosen == null) p.muted else p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (chosen != null) Text(tasksLabel(data.openCount(chosen.id)), color = p.muted, fontSize = 13.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(if (menu) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = p.ink)
                    }
                }
            }
            if (menu) {
                Surface(
                    shape = RoundedCornerShape(16.dp), color = p.surface, shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        choices.forEach { l ->
                            val sel = l.id == listId
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) p.card else Color.Transparent)
                                    .clickable { listId = l.id; menu = false }.padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(l.title, color = p.ink, fontSize = 16.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(tasksLabel(data.openCount(l.id)), color = p.muted, fontSize = 13.sp)
                                if (sel) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Check, null, tint = p.accent, modifier = Modifier.size(20.dp)) }
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(p.line))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { newList = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = p.accent)
                            Spacer(Modifier.width(12.dp))
                            Text("New list", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            if (chosen != null) Text("The block takes its name from the list.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(24.dp))
            Label("Time", p)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeBox("Starts", Fmt.time(start), p, Modifier.weight(1f)) { pickStart = true }
                TimeBox("Ends", Fmt.time(end), p, Modifier.weight(1f)) { pickEnd = true }
            }
            if (end < start) Text("This block runs past midnight.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            if (start == end) Text("Start and end must be different.", color = p.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(24.dp))
            Label("Repeats on", p)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DayOfWeek.values().forEach { d ->
                    val on = d in days
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (on) p.accent else Color.Transparent)
                            .border(2.dp, if (on) p.accent else p.line, CircleShape)
                            .toggleable(on, role = Role.Checkbox) { days = if (on) days - d else days + d }
                            .semantics { contentDescription = d.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = if (on) p.onAccent else p.muted, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                if ((target as? BlockTarget.New)?.fromDay == true) "It shows up today as well, and repeats on these days."
                else "Changes to the weekly plan start tomorrow. Today keeps the layout it began with.",
                color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )

            if (chosen != null) {
                Spacer(Modifier.height(24.dp))
                Label("Color of ${chosen.title}", p)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BLOCK_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(c))
                                .then(if (i == color) Modifier.border(3.dp, p.ink, CircleShape) else Modifier)
                                .colorChoice(i, i == color) { color = i },
                        )
                    }
                }
                val others = chosen?.let { c -> data.blocksOfList(c.id).count { it.id != editing?.id } } ?: 0
                Text(
                    if (others > 0) "All ${others + 1} blocks of this list use this color."
                    else "Every block of this list uses this color.",
                    color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (editing != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { vm.run { deleteBlock(editing.id) }; onClose() }) { Text("Delete this block", color = p.danger, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = {
                val id = editing?.id
                val timesChanged = editing == null || start != editing.start || end != editing.end
                val fromDay = (target as? BlockTarget.New)?.fromDay == true
                vm.run { saveBlock(id, listId!!, start, end, days, color, showToday = fromDay, resetDayTimes = timesChanged) }
                onClose()
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
        ) { Text("Save block", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }

    if (pickStart) TimeDialog("Starts", start, { pickStart = false }) { start = it; pickStart = false }
    if (pickEnd) TimeDialog("Ends", end, { pickEnd = false }) { end = it; pickEnd = false }
    if (newList) NewListDialog(data.suggestedColor(), p, onDismiss = { newList = false }) { name, pickedColor ->
        newList = false; menu = false
        vm.run { listId = addList(name, pickedColor) }
    }
}

@Composable
private fun Label(text: String, p: Palette) {
    Text(text, color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TimeBox(label: String, value: String, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = p.surface, border = BorderStroke(1.dp, p.line), modifier = modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, color = p.muted, fontSize = 12.sp)
            Text(value, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
