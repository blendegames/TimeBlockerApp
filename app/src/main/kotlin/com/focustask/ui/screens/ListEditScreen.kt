package com.focustask.ui.screens

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.Task
import com.focustask.ui.theme.Palette
import java.time.Duration
import java.time.LocalDateTime

/**
 * The content of the list that is open in the Lists tab: its header, the "Add a task" row, its tasks and its completed tasks.
 * Returns the items to put into the tab's LazyColumn (null when the list is gone), and shows the dialogs of its tasks.
 */
@Composable
fun openListItems(
    ui: UiState, vm: MainViewModel, listId: Long, p: Palette, lazyState: LazyListState,
    onClose: () -> Unit,
): (LazyListScope.() -> Unit)? {
    val data = ui.data
    val list = data.lists.firstOrNull { it.id == listId }
    if (list == null) { LaunchedEffect(Unit) { onClose() }; return null }
    val now = ui.now
    val listColor = Color(data.colorOfListAlways(listId))

    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // the parts of the list unfold when it is opened, not every time they scroll into view
    val openedAt = remember(listId) { System.currentTimeMillis() }
    val unfolding = System.currentTimeMillis() - openedAt < 600
    var confirmDelete by remember { mutableStateOf(false) }

    var selectedId by remember { mutableStateOf<Long?>(null) }
    var newTitle by remember { mutableStateOf("") }
    var newDeadline by remember { mutableStateOf<LocalDateTime?>(null) }
    var pickingNew by remember { mutableStateOf(false) }
    var showCompleted by remember(listId) { mutableStateOf(true) }

    // dialogs for existing tasks
    var editTask by remember { mutableStateOf<Task?>(null) }
    var deadlineFor by remember { mutableStateOf<Task?>(null) }
    var repeatFor by remember { mutableStateOf<Task?>(null) }

    val all = data.tasks.filter { it.listId == listId }
    val savedOpen = all.filter { !it.done && it.parentId == null }.sortedWith(compareBy({ it.position }, { it.id }))
    val savedIds = savedOpen.map { it.id }
    // dragging a task with its handle: the order shown is the dragged one until it is saved
    val mainDrag = remember(listId) { DragReorder() }
    LaunchedEffect(savedIds) { mainDrag.sync(savedIds) }
    val open = mainDrag.effective(savedIds).mapNotNull { id -> savedOpen.firstOrNull { it.id == id } }
    val gapPx = with(LocalDensity.current) { 8.dp.toPx() }
    val edgePx = with(LocalDensity.current) { 72.dp.toPx() }
    fun itemInfo(id: Long) = lazyState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
    // keep the list scrolling while a task is held near the top or the bottom
    val scrollDir by remember {
        derivedStateOf {
            val item = mainDrag.dragId?.let { itemInfo(it) }
            if (item == null) 0 else {
                val top = mainDrag.visualTop
                val end = lazyState.layoutInfo.viewportEndOffset
                if (top < edgePx) -1 else if (top + item.size > end - edgePx) 1 else 0
            }
        }
    }
    LaunchedEffect(scrollDir) {
        if (scrollDir == 0) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val id = mainDrag.dragId ?: break
            val item = itemInfo(id) ?: continue
            val top = mainDrag.visualTop
            val bottom = top + item.size
            val end = lazyState.layoutInfo.viewportEndOffset
            val delta = when {
                top < edgePx -> -(minOf(edgePx, edgePx - top) / edgePx) * 24f
                bottom > end - edgePx -> (minOf(edgePx, bottom - (end - edgePx)) / edgePx) * 24f
                else -> 0f
            }
            if (delta == 0f) break
            val used = lazyState.scrollBy(delta)
            if (used == 0f) break   // at the end of the list: nothing more to do until the finger moves
            mainDrag.scroll(used)
        }
    }
    // a top-level task whose subtasks are all done and that was completed counts as completed
    val completed = all.filter { it.done && it.parentId == null }.sortedByDescending { it.completedAt }

    if (renaming) TextEditDialog("Rename list", list.title, onDismiss = { renaming = false }) { name ->
        renaming = false
        if (name.isNotBlank()) vm.run { renameList(listId, name) }
    }
    if (pickingNew) DeadlineDialog(newDeadline, { pickingNew = false }) { newDeadline = it; pickingNew = false }
    deadlineFor?.let { t ->
        DeadlineDialog(t.deadline, { deadlineFor = null }) { d -> deadlineFor = null; vm.run { setDeadline(t.id, d) } }
    }
    repeatFor?.let { t ->
        RepeatDialog(t.repeat, { repeatFor = null }) { r -> repeatFor = null; vm.run { setRepeat(t.id, r) } }
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
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.run { deleteList(listId) }; onClose() }) { Text("Delete", color = p.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
    return {
        item(key = "list-$listId") {
            // the unfolded list is one card made of the items below: this is its top
            Box(Modifier.animateItem().segment(p, top = true).testTag("list-open")) {
                // the same band of the list's color as the folded card
                ListHeaderBand(
                    list.title, listColor,
                    Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).clickable(onClick = onClose),
                ) {
                    Box {
                        Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MoreVert, "More", tint = Color.White)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename list") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete list", color = p.danger) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                    Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable(onClick = onClose).testTag("list-fold"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ExpandLess, "Fold up", tint = Color.White)
                    }
                }
            }
        }
        item(key = "add-$listId") {
            Box(Modifier.animateItem().segment(p)) {
                Unfold(unfolding) {
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = p.bg, border = BorderStroke(2.dp, p.line),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
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
                                Text(Fmt.due(newDeadline!!, now), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(Modifier.size(48.dp).clip(CircleShape).clickable { pickingNew = true }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.CalendarToday, "Set a deadline", tint = if (newDeadline != null) p.accent else p.ink, modifier = Modifier.size(22.dp))
                            }
                            if (newTitle.isNotBlank()) {
                                Box(
                                    Modifier.minimumInteractiveComponentSize().size(40.dp).clip(CircleShape).background(p.accent).clickable {
                                        val t = newTitle; val d = newDeadline; newTitle = ""; newDeadline = null
                                        vm.run { addTask(listId, t, d) }
                                    },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.Check, "Add", tint = p.onAccent) }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }
        }
        items(open, key = { it.id }) { t ->
            val subs = all.filter { it.parentId == t.id }.sortedWith(compareBy({ it.position }, { it.id }))
            val held = mainDrag.dragId == t.id
            Box(
                // the other cards glide to their new place; the held one follows the finger instead
                Modifier
                    .animateItem(placementSpec = if (held) null else spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = IntOffset.Companion.VisibilityThreshold,
                    ))
                    .zIndex(if (held) 1f else 0f)
                    .segment(p),
            ) {
                Unfold(unfolding) {
                    TaskCard(
                        ui, vm, p, t, subs, selected = selectedId == t.id,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            .then(if (held) Modifier.graphicsLayer {
                                translationY = mainDrag.visualTop - (itemInfo(t.id)?.offset?.toFloat() ?: mainDrag.visualTop)
                            } else Modifier),
                        lifted = held,
                        canDrag = open.size > 1,
                        onDragStart = {
                            itemInfo(t.id)?.let { me ->
                                mainDrag.begin(t.id, open.map { it.id }, { k -> itemInfo(k)?.size?.toFloat() }, gapPx, me.offset.toFloat())
                            }
                        },
                        onDrag = { mainDrag.drag(it) },
                        onDragEnd = { mainDrag.finish(savedIds)?.let { ids -> vm.run { reorderTasks(ids) } } },
                        onSelect = { selectedId = if (selectedId == t.id) null else t.id },
                        onEdit = { editTask = it },
                        onDeadline = { deadlineFor = it },
                        onRepeat = { repeatFor = it },
                    )
                }
            }
        }
        if (open.isEmpty()) {
            item(key = "empty-$listId") {
                Box(Modifier.animateItem().segment(p)) {
                    Text("No open tasks. Add the first one above.", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                }
            }
        }
        // finished tasks go all the way down, into their own part of the card
        item(key = "done-head-$listId") {
            Box(Modifier.animateItem().segment(p)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { showCompleted = !showCompleted }
                        .padding(horizontal = 12.dp, vertical = 14.dp).testTag("completed-$listId"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Completed · ${completed.size}", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(if (showCompleted) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (showCompleted) "Hide completed" else "Show completed", tint = p.ink)
                }
            }
        }
        if (showCompleted) {
            items(completed, key = { "c" + it.id }) { t ->
                Box(Modifier.animateItem().segment(p)) {
                    // a task that was just finished unfolds into its place here
                    Unfold(unfolding || t.completedAt?.let { Duration.between(it, now).seconds < 3 } == true) {
                        val ticked = vm.shownDone(t)
                        Surface(shape = RoundedCornerShape(16.dp), color = p.bg, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)) {
                            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                // tapping the tick opens the task again (a second later it moves back up)
                                TickBox(ticked, false, p, size = 26.dp, fill = listColor) { vm.tickSoon(t) }
                                Text(
                                    t.title, color = p.muted, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (ticked) TextDecoration.LineThrough else TextDecoration.None,
                                )
                                val back = t.repeat?.let { r -> t.completedAt?.let(r::comesBackAfter) }
                                if (back != null) Text("Back ${Fmt.deadlineShort(back, now).removeSuffix(" 00:00")}", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("back-${t.id}"))
                                else t.completedAt?.let { Text(Fmt.deadlineShort(it, now), color = p.muted, fontSize = 12.sp) }
                                Box(Modifier.minimumInteractiveComponentSize().size(40.dp).clip(CircleShape).clickable { vm.run { deleteTask(t.id) } }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Delete, "Delete", tint = p.muted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "end-$listId") {
            Column(Modifier.animateItem()) {
                Box(Modifier.segment(p, bottom = true).height(12.dp))
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** A piece of the unfolded list's card: the card's color, with rounded corners only at its top and its bottom. */
private fun Modifier.segment(p: Palette, top: Boolean = false, bottom: Boolean = false): Modifier {
    val r = 20.dp
    return this.fillMaxWidth().background(
        p.surface,
        RoundedCornerShape(
            topStart = if (top) r else 0.dp, topEnd = if (top) r else 0.dp,
            bottomStart = if (bottom) r else 0.dp, bottomEnd = if (bottom) r else 0.dp,
        ),
    )
}

/** Unfolds its content downwards when it first shows (only while the list is being opened). */
@Composable
private fun Unfold(animate: Boolean, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(!animate).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
    ) { content() }
}

/** A task that is not selected shows this many of its subtasks. */
private const val PREVIEW_SUBTASKS = 3

@Composable
private fun TaskCard(
    ui: UiState, vm: MainViewModel, p: Palette,
    task: Task, subs: List<Task>, selected: Boolean,
    modifier: Modifier = Modifier, lifted: Boolean = false, canDrag: Boolean = false,
    onDragStart: () -> Unit = {}, onDrag: (Float) -> Unit = {}, onDragEnd: () -> Unit = {},
    onSelect: () -> Unit, onEdit: (Task) -> Unit, onDeadline: (Task) -> Unit, onRepeat: (Task) -> Unit = {},
) {
    val now = ui.now
    val listColor = Color(ui.data.colorOfListAlways(task.listId))
    var subTitle by remember(task.id) { mutableStateOf("") }
    var subDeadline by remember(task.id) { mutableStateOf<LocalDateTime?>(null) }
    var pickSub by remember(task.id) { mutableStateOf(false) }
    val doneSubs = subs.count { it.done }
    val late = task.deadline?.let { it < now } == true
    val ticked = vm.shownDone(task)
    // finished subtasks go to the bottom of the task, under "Completed"; the open ones keep their order
    val openSubs = subs.filter { !it.done }
    val finishedSubs = subs.filter { it.done }.sortedByDescending { it.completedAt }
    var showDoneSubs by remember(task.id) { mutableStateOf(true) }
    // subtasks can only be moved among the subtasks of this task
    val subDrag = remember(task.id) { DragReorder() }
    val subSavedIds = openSubs.map { it.id }
    LaunchedEffect(subSavedIds) { subDrag.sync(subSavedIds) }
    val shownSubs = subDrag.effective(subSavedIds).mapNotNull { id -> openSubs.firstOrNull { it.id == id } }
    val onSubsReordered = { ids: List<Long> -> vm.run { reorderTasks(ids) }; Unit }

    Surface(
        shape = RoundedCornerShape(16.dp), color = p.bg,
        border = if (selected) BorderStroke(2.dp, p.accent) else null,
        shadowElevation = if (lifted) 8.dp else 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onSelect).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(4.dp))
                // ticking waits a second before the task moves down to Completed: tap again to take it back
                TickBox(ticked, late, p, Modifier.testTag("tick-${task.id}"), size = 28.dp, fill = listColor) { vm.tickSoon(task) }
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        task.title, color = if (ticked) p.muted else p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = if (selected) 3 else 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (ticked) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (task.deadline != null) {
                            if (selected) Icon(Icons.Rounded.Schedule, null, tint = if (late) p.danger else p.ink, modifier = Modifier.size(14.dp).padding(end = 0.dp))
                            if (selected) Spacer(Modifier.width(4.dp))
                            Text(
                                if (selected) Fmt.dueLong(task.deadline, now) else Fmt.due(task.deadline!!, now),
                                color = if (late) p.danger else p.muted, fontSize = 13.sp,
                                fontWeight = if (selected || late) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                        task.repeat?.let { r ->
                            if (task.deadline != null) Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.Repeat, null, tint = p.muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(r.label(), color = p.muted, fontSize = 13.sp, modifier = Modifier.testTag("repeat-label-${task.id}"))
                        }
                        if (subs.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text("$doneSubs/${subs.size}", color = p.muted, fontSize = 13.sp)
                        }
                    }
                }
                DragHandle(p, enabled = canDrag, onStart = onDragStart, onDrag = onDrag, onEnd = onDragEnd)
            }
            if (!selected && openSubs.isNotEmpty()) {
                // Open subtasks stay visible when the task is not selected; selecting adds the actions and the finished ones.
                Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
                    // only the first few; selecting the task shows all of them
                    SubtaskRows(p, shownSubs.take(PREVIEW_SUBTASKS), subSavedIds, now, onEdit, onDeadline, vm, subDrag, onSubsReordered, interactive = false, listColor = listColor)
                    if (openSubs.size > PREVIEW_SUBTASKS) {
                        Text(
                            "+ ${openSubs.size - PREVIEW_SUBTASKS} more", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            if (selected) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        MiniAction(Icons.Rounded.CalendarToday, "Deadline", p) { onDeadline(task) }
                        MiniAction(Icons.Rounded.Repeat, "Repeat", p, active = task.repeat != null) { onRepeat(task) }
                        MiniAction(Icons.Rounded.Edit, "Rename", p) { onEdit(task) }
                    }
                    SubtaskRows(p, shownSubs, subSavedIds, now, onEdit, onDeadline, vm, subDrag, onSubsReordered, listColor = listColor)
                    // add a subtask: only on the selected task
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
                        subDeadline?.let { Text(Fmt.due(it, now), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable { pickSub = true }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CalendarToday, "Subtask deadline", tint = if (subDeadline != null) p.accent else p.ink, modifier = Modifier.size(20.dp))
                        }
                        if (subTitle.isNotBlank()) {
                            Box(
                                Modifier.minimumInteractiveComponentSize().size(36.dp).clip(CircleShape).background(p.accent).clickable {
                                    val t = subTitle; val d = subDeadline; subTitle = ""; subDeadline = null
                                    vm.run { addTask(task.listId, t, d, parentId = task.id) }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Check, "Add subtask", tint = p.onAccent, modifier = Modifier.size(20.dp)) }
                        }
                    }
                    if (finishedSubs.isNotEmpty()) {
                        // the subtasks that are done, at the bottom of the task
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showDoneSubs = !showDoneSubs }
                                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 6.dp).testTag("completed-subs-${task.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Completed · ${finishedSubs.size}", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(if (showDoneSubs) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = p.muted, modifier = Modifier.size(20.dp))
                        }
                        if (showDoneSubs) SubtaskRows(p, finishedSubs, emptyList(), now, onEdit, onDeadline, vm, subDrag, onSubsReordered, reorderable = false, listColor = listColor)
                    }
                }
            }
        }
    }
    if (pickSub) DeadlineDialog(subDeadline, { pickSub = false }) { subDeadline = it; pickSub = false }
}

@Composable
private fun SubtaskRows(
    p: Palette, subs: List<Task>, savedIds: List<Long>, now: LocalDateTime,
    onEdit: (Task) -> Unit, onDeadline: (Task) -> Unit, vm: MainViewModel,
    drag: DragReorder, onReordered: (List<Long>) -> Unit,
    /** Not selected: just a preview (only the tick box works). Tapping the card selects it. */
    interactive: Boolean = true,
    /** The finished subtasks can't be moved. */
    reorderable: Boolean = true,
    /** The color of the list: a ticked box is filled with it. */
    listColor: Color = p.accent,
) {
    val heights = remember { mutableStateMapOf<Long, Float>() }
    var above = 0f   // height of the rows before this one: where the layout puts it
    subs.forEach { s ->
        val top = above
        above += heights[s.id] ?: 0f
        key(s.id) {
            val sLate = !s.done && s.deadline?.let { it < now } == true
            val held = drag.dragId == s.id
            // rows glide to their new place while another row is dragged; the dragged one follows the finger
            val shown by animateFloatAsState(top, tween(150), label = "slide")
            val slide = when {
                held -> drag.dragTop - top
                drag.order != null -> shown - top
                else -> 0f
            }
            Row(
                Modifier.fillMaxWidth()
                    .onSizeChanged { heights[s.id] = it.height.toFloat() }
                    .zIndex(if (held) 1f else 0f)
                    .graphicsLayer { translationY = slide }
                    .then(if (held) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)).background(p.surface, RoundedCornerShape(12.dp)) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ticked = vm.shownDone(s)
                // a tick waits a second before the subtask moves (down to Completed, or back up)
                TickBox(ticked, sLate, p, Modifier.testTag("tick-${s.id}"), size = 24.dp, fill = listColor) { vm.tickSoon(s) }
                Text(
                    s.title, fontSize = 16.sp,
                    color = if (ticked) p.muted else p.ink,
                    textDecoration = if (ticked) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f).then(if (interactive) Modifier.clickable { onEdit(s) } else Modifier).padding(vertical = 10.dp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val label = s.deadline?.let { d -> Fmt.due(d, now) } ?: Fmt.NO_DEADLINE
                Text(
                    label, color = if (sLate) p.danger else p.muted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).then(if (interactive) Modifier.clickable { onDeadline(s) } else Modifier).padding(horizontal = 8.dp, vertical = 12.dp),
                )
                if (interactive && reorderable) DragHandle(
                    p, enabled = subs.size > 1, size = 40.dp,
                    onStart = { drag.begin(s.id, subs.map { it.id }, { k -> heights[k] }) },
                    onDrag = { drag.drag(it) },
                    onEnd = { drag.finish(savedIds)?.let(onReordered) },
                )
            }
        }
    }
}

/** The grip on the right of a row: hold it and move up or down to reorder. */
@Composable
private fun DragHandle(
    p: Palette, enabled: Boolean, size: Dp = 48.dp,
    onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit,
) {
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier.size(size).testTag("drag-handle").semantics { contentDescription = "Drag to reorder" }
            .then(
                if (enabled) Modifier
                    // a tap on the grip must not select the card behind it
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start() },
                            onDragEnd = { end() },
                            onDragCancel = { end() },
                            onDrag = { change, amount -> change.consume(); move(amount.y) },
                        )
                    }
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.DragIndicator, null,
            tint = if (enabled) p.muted else p.muted.copy(alpha = 0.3f), modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MiniAction(icon: ImageVector, desc: String, p: Palette, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (!enabled) p.muted.copy(alpha = 0.3f) else if (active) p.accent else p.muted, modifier = Modifier.size(20.dp))
    }
}
