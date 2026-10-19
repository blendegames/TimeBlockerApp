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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.Task
import com.focustask.ui.theme.Palette
import java.time.LocalDateTime

@Composable
fun ListEditScreen(ui: UiState, vm: MainViewModel, listId: Long, p: Palette, onBack: () -> Unit) {
    val data = ui.data
    val list = data.lists.firstOrNull { it.id == listId }
    if (list == null) { onBack(); return }
    val now = ui.now

    var title by remember(list.id) { mutableStateOf(list.title) }
    val titleFocus = remember { FocusRequester() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    var selectedId by remember { mutableStateOf<Long?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newDeadline by remember { mutableStateOf<LocalDateTime?>(null) }
    var pickingNew by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }

    // dialogs for existing tasks
    var editTask by remember { mutableStateOf<Task?>(null) }
    var deadlineFor by remember { mutableStateOf<Task?>(null) }

    val all = data.tasks.filter { it.listId == listId }
    val open = all.filter { !it.done && it.parentId == null }.sortedWith(compareBy({ it.position }, { it.id }))
    // a top-level task whose subtasks are all done and that was completed counts as completed
    val completed = all.filter { it.done && it.parentId == null }.sortedByDescending { it.completedAt }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = p.ink)
            }
            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = title, onValueChange = { title = it },
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.run { renameList(listId, title) } }),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(p.line))
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { titleFocus.requestFocus() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Edit, "Rename list", tint = p.ink, modifier = Modifier.size(22.dp))
            }
            Box {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = p.ink)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Save title") }, onClick = { menu = false; vm.run { renameList(listId, title) } })
                    DropdownMenuItem(text = { Text("Delete list", color = p.danger) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        // keep the title saved when it changes
        androidx.compose.runtime.LaunchedEffect(title) {
            if (title.isNotBlank() && title != list.title) {
                kotlinx.coroutines.delay(600)
                vm.run { renameList(listId, title) }
            }
        }

        // add a task
        Surface(
            shape = RoundedCornerShape(20.dp), color = p.surface, border = BorderStroke(2.dp, p.line),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Add, null, tint = p.accent)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (newTitle.isEmpty()) Text("Add a task", color = p.muted, fontSize = 17.sp)
                    BasicTextField(
                        value = newTitle, onValueChange = { newTitle = it }, singleLine = true,
                        textStyle = TextStyle(color = p.ink, fontSize = 17.sp),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newTitle.isNotBlank()) { val t = newTitle; val d = newDeadline; newTitle = ""; newDeadline = null; vm.run { addTask(listId, t, d) } }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (newDeadline != null) {
                    Text(Fmt.deadlineShort(newDeadline!!, now), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { pickingNew = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CalendarToday, "Set a deadline", tint = if (newDeadline != null) p.accent else p.ink, modifier = Modifier.size(22.dp))
                }
                if (newTitle.isNotBlank()) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(p.accent).clickable {
                            val t = newTitle; val d = newDeadline; newTitle = ""; newDeadline = null
                            vm.run { addTask(listId, t, d) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, "Add", tint = p.onAccent) }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(open, key = { it.id }) { t ->
                val subs = all.filter { it.parentId == t.id }.sortedWith(compareBy({ it.position }, { it.id }))
                TaskCard(
                    ui, vm, p, t, subs, selected = selectedId == t.id,
                    siblings = open,
                    onSelect = { selectedId = if (selectedId == t.id) null else t.id },
                    onEdit = { editTask = it },
                    onDeadline = { deadlineFor = it },
                )
            }
            if (open.isEmpty()) {
                item { Text("No open tasks. Add the first one above.", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(16.dp)) }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showCompleted = !showCompleted }.padding(horizontal = 12.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Completed · ${completed.size}", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(if (showCompleted) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, null, tint = p.ink)
                }
            }
            if (showCompleted) {
                items(completed, key = { "c" + it.id }) { t ->
                    Surface(shape = RoundedCornerShape(16.dp), color = p.surface, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).clickable { vm.run { reopen(t.id) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, "Reopen", tint = p.onAccent, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(t.title, color = p.muted, fontSize = 16.sp, textDecoration = TextDecoration.LineThrough, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            t.completedAt?.let { Text(Fmt.deadlineShort(it, now), color = p.muted, fontSize = 12.sp) }
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.run { deleteTask(t.id) } }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Delete, "Delete", tint = p.muted, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (pickingNew) DeadlineDialog(newDeadline, { pickingNew = false }) { newDeadline = it; pickingNew = false }
    deadlineFor?.let { t ->
        DeadlineDialog(t.deadline, { deadlineFor = null }) { d -> deadlineFor = null; vm.run { setDeadline(t.id, d) } }
    }
    editTask?.let { t ->
        TextEditDialog(
            "Edit task", t.title, onDismiss = { editTask = null },
            onDelete = { editTask = null; if (selectedId == t.id) selectedId = null; vm.run { deleteTask(t.id) } },
        ) { name -> editTask = null; vm.run { renameTask(t.id, name) } }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete \"${list.title}\"?") },
        text = { Text("The list, its tasks and its time block are deleted.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.run { deleteList(listId) }; onBack() }) { Text("Delete", color = p.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun TaskCard(
    ui: UiState, vm: MainViewModel, p: Palette,
    task: Task, subs: List<Task>, selected: Boolean, siblings: List<Task>,
    onSelect: () -> Unit, onEdit: (Task) -> Unit, onDeadline: (Task) -> Unit,
) {
    val now = ui.now
    var subTitle by remember(task.id) { mutableStateOf("") }
    var subDeadline by remember(task.id) { mutableStateOf<LocalDateTime?>(null) }
    var pickSub by remember(task.id) { mutableStateOf(false) }
    val doneSubs = subs.count { it.done }
    val late = task.deadline?.let { it < now } == true

    Surface(
        shape = RoundedCornerShape(20.dp), color = p.surface,
        border = if (selected) BorderStroke(2.dp, p.accent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onSelect).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DragIndicator, null, tint = p.muted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { vm.run { complete(task.id) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(Modifier.size(28.dp), shape = CircleShape, color = Color.Transparent,
                        border = BorderStroke(2.dp, if (selected) p.accent else if (late) p.danger else p.muted)) {}
                }
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(task.title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = if (selected) 3 else 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.deadline != null) {
                            if (selected) Icon(Icons.Rounded.Schedule, null, tint = if (late) p.danger else p.ink, modifier = Modifier.size(14.dp).padding(end = 0.dp))
                            if (selected) Spacer(Modifier.width(4.dp))
                            Text(
                                if (late) Fmt.late(task.deadline!!, now) else if (selected) Fmt.deadline(task.deadline!!, now).replaceFirstChar { it.uppercase() } else Fmt.deadlineShort(task.deadline!!, now),
                                color = if (late) p.danger else p.muted, fontSize = 13.sp,
                                fontWeight = if (selected || late) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        if (subs.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text("$doneSubs/${subs.size}", color = p.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (selected) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
                    // actions for this task
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        MiniAction(Icons.Rounded.CalendarToday, "Deadline", p) { onDeadline(task) }
                        MiniAction(Icons.Rounded.Edit, "Rename", p) { onEdit(task) }
                        MiniAction(Icons.Rounded.ArrowUpward, "Move up", p, enabled = siblings.firstOrNull()?.id != task.id) {
                            vm.run { move(task.id, true, siblings) }
                        }
                        MiniAction(Icons.Rounded.ArrowDownward, "Move down", p, enabled = siblings.lastOrNull()?.id != task.id) {
                            vm.run { move(task.id, false, siblings) }
                        }
                    }
                    subs.forEach { s ->
                        val sLate = !s.done && s.deadline?.let { it < now } == true
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable { vm.run { if (s.done) reopen(s.id) else complete(s.id) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (s.done) Box(Modifier.size(24.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, "Reopen", tint = p.onAccent, modifier = Modifier.size(15.dp))
                                } else Surface(Modifier.size(24.dp), shape = CircleShape, color = Color.Transparent,
                                    border = BorderStroke(2.dp, if (sLate) p.danger else p.muted)) {}
                            }
                            Text(
                                s.title, fontSize = 16.sp,
                                color = if (s.done) p.muted else p.ink,
                                textDecoration = if (s.done) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f).clickable { onEdit(s) }.padding(vertical = 10.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            val label = s.deadline?.let { d -> if (d.toLocalDate() == now.toLocalDate()) Fmt.time(d) else Fmt.deadlineShort(d, now) } ?: "No deadline"
                            Text(
                                label, color = if (sLate) p.danger else p.muted, fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onDeadline(s) }.padding(horizontal = 8.dp, vertical = 12.dp),
                            )
                        }
                    }
                    // add a subtask
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = p.accent) }
                        Box(Modifier.weight(1f)) {
                            if (subTitle.isEmpty()) Text("Add a subtask", color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            BasicTextField(
                                value = subTitle, onValueChange = { subTitle = it }, singleLine = true,
                                textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                                cursorBrush = SolidColor(p.accent),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (subTitle.isNotBlank()) { val t = subTitle; val d = subDeadline; subTitle = ""; subDeadline = null; vm.run { addTask(task.listId, t, d, parentId = task.id) } }
                                }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        subDeadline?.let { Text(Fmt.deadlineShort(it, now), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { pickSub = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CalendarToday, "Subtask deadline", tint = if (subDeadline != null) p.accent else p.ink, modifier = Modifier.size(20.dp))
                        }
                        if (subTitle.isNotBlank()) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(p.accent).clickable {
                                    val t = subTitle; val d = subDeadline; subTitle = ""; subDeadline = null
                                    vm.run { addTask(task.listId, t, d, parentId = task.id) }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Check, "Add subtask", tint = p.onAccent, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }
    if (pickSub) DeadlineDialog(subDeadline, { pickSub = false }) { subDeadline = it; pickSub = false }
}

@Composable
private fun MiniAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, p: Palette, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) p.muted else p.muted.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
    }
}
