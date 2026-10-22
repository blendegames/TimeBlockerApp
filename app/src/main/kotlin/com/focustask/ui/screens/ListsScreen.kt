package com.focustask.ui.screens

import com.focustask.ui.theme.TitleSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.TodoList
import com.focustask.ui.theme.Palette

@Composable
fun ListsScreen(ui: UiState, vm: MainViewModel, p: Palette, openId: Long?, twoPane: Boolean = false, onOpen: (Long?) -> Unit) {
    val data = ui.data
    var newList by remember { mutableStateOf(false) }
    val lazyState = rememberLazyListState()
    // on a wide window the open list has a pane of its own, on the right
    val paneState = rememberLazyListState()
    // the open list unfolds right here: its tasks become part of this list
    val openItems = openId?.let { openListItems(ui, vm, it, p, if (twoPane) paneState else lazyState, onClose = { onOpen(null) }) }
    LaunchedEffect(openId) {
        if (openId != null && !twoPane) {
            // a list unfolds where it is, like a dropdown; only one that sits low on the screen is brought up
            val info = lazyState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == "list-$openId" }
            val at = item?.index ?: data.lists.indexOfFirst { it.id == openId }.coerceAtLeast(0)
            if (item == null) lazyState.animateScrollToItem(at)
            else if (item.offset > (info.viewportEndOffset - info.viewportStartOffset) * 0.55f) lazyState.animateScrollBy(item.offset.toFloat())
        }
    }
    Row(Modifier.fillMaxSize()) {
    Column(Modifier.weight(if (twoPane) 0.45f else 1f).fillMaxHeight().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Lists", color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold)
            AddButton("New list", p, { newList = true })
        }
        Text(
            "Next three tasks of each list, in the order they will show. Tap a list to unfold it.",
            color = p.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp),
        )
        // no spacing here: an unfolded list is made of several items that join up into one card
        LazyColumn(Modifier.weight(1f), state = lazyState) {
            if (data.lists.isEmpty()) item {
                Text("No lists yet. Tap New list to make your first one.", color = p.muted, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp))
            }
            data.lists.forEach { list ->
                if (!twoPane && list.id == openId && openItems != null) {
                    openItems()
                } else item(key = "list-${list.id}") {
                    FoldedListCard(ui, vm, p, list, Modifier.animateItem().padding(bottom = 12.dp), selected = twoPane && list.id == openId) { onOpen(list.id) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
    if (twoPane) {
        // the open list, unfolded, beside the overview; or a hint while none is open
        Box(Modifier.weight(0.55f).fillMaxHeight().padding(top = 64.dp, end = 16.dp)) {
            if (openItems != null) LazyColumn(Modifier.fillMaxSize().testTag("list-pane"), state = paneState) {
                openItems()
                item { Spacer(Modifier.height(8.dp)) }
            } else Text(
                "Pick a list on the left to see all its tasks here.", color = p.muted, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
    }
    if (newList) NewListDialog(data.suggestedColor(), p, onDismiss = { newList = false }) { name, color ->
        newList = false
        vm.run { addList(name, color) }
    }
}

/** A folded list: its name and the next three tasks, each with a tick box that works right here. */
@Composable
private fun FoldedListCard(ui: UiState, vm: MainViewModel, p: Palette, list: TodoList, modifier: Modifier, selected: Boolean = false, onOpen: () -> Unit) {
    val data = ui.data
    val preview = previewItems(data.tasks, list.id)
    val hasOverdue = preview.any { it.deadline?.let { d -> d < ui.now } == true }
    val listColor = Color(data.colorOfListAlways(list.id))
    Surface(
        shape = RoundedCornerShape(20.dp), color = p.surface,
        border = if (hasOverdue) BorderStroke(2.dp, p.danger) else if (selected) BorderStroke(3.dp, listColor) else null,
        modifier = modifier.fillMaxWidth().testTag("list-card"),
    ) {
        Column(Modifier.clickable(onClick = onOpen).padding(bottom = 10.dp)) {
            // the list's name on a band of its color: the same color as its cards on Today and its blocks in Day and Week
            ListHeaderBand(list.title, listColor) { Icon(Icons.Rounded.ExpandMore, "Unfold", tint = Color.White) }
            Column(Modifier.padding(start = 6.dp, end = 16.dp, top = 4.dp)) {
            if (preview.isEmpty()) {
                Text("Nothing to do here", color = p.muted, fontSize = 15.sp, modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 4.dp))
            }
            var lastParent: Long? = null
            preview.take(3).forEach { item ->
                val t = item.task
                val dl = item.deadline
                val late = dl?.let { it < ui.now } == true
                val ticked = vm.shownDone(t)
                // main task title above its subtasks
                if (item.parent != null && item.parent.id != lastParent) {
                    Text(
                        item.parent.title, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 48.dp, top = 6.dp),
                    )
                }
                lastParent = item.parent?.id
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // the tick box: ticks the task (it moves to Completed a second later), it does not open the list
                    TickBox(ticked, late, p, Modifier.testTag("preview-tick-${t.id}"), fill = listColor) { vm.tickSoon(t) }
                    Text(
                        t.title, color = if (ticked) p.muted else p.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        textDecoration = if (ticked) TextDecoration.LineThrough else null,
                    )
                    if (dl != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Fmt.due(dl, ui.now),
                            color = if (late) p.danger else p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (preview.size > 3) Text("+ ${preview.size - 3} more", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 48.dp, top = 2.dp))
            }
        }
    }
}

/** The top of a list card, folded or open: the list's name in white on its color, and the buttons on the right. */
@Composable
fun ListHeaderBand(title: String, color: Color, modifier: Modifier = Modifier, end: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(color).heightIn(min = 52.dp).padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        end()
    }
}

/** A round tick box with a 48 dp touch area: hollow when open, filled with a tick when done. A checkbox for the screen reader. */
@Composable
fun TickBox(done: Boolean, late: Boolean, p: Palette, modifier: Modifier = Modifier, size: Dp = 22.dp, fill: Color = p.accent, onClick: () -> Unit) {
    Box(
        modifier.size(48.dp).clip(CircleShape).toggleable(done, role = Role.Checkbox) { onClick() }
            .semantics { contentDescription = "Done" },
        contentAlignment = Alignment.Center,
    ) {
        if (done) Box(Modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        } else Surface(Modifier.size(size), shape = CircleShape, color = Color.Transparent, border = BorderStroke(2.dp, if (late) p.danger else p.muted)) {}
    }
}
