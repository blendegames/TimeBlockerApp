package com.focustask.ui.screens

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.core.Task
import com.focustask.ui.theme.Palette


fun tasksLabel(n: Int) = if (n == 1) "1 task" else "$n tasks"

@Composable
fun AddButton(label: String, p: Palette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(p.card).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = p.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = p.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** One row of a list's preview: the thing to do, and its main task when it is a subtask. */
data class PreviewItem(val task: Task, val parent: Task?) {
    /** A subtask without a deadline of its own counts with its main task's. */
    val deadline get() = task.deadline ?: parent?.deadline
}

/**
 * What a list will show next, in the order the app will serve it: a main task with open subtasks is
 * replaced by those subtasks (soonest deadline first), everything else ordered by deadline, then manual order.
 */
fun previewItems(tasks: List<Task>, listId: Long): List<PreviewItem> {
    val inList = tasks.filter { it.listId == listId && !it.done }
    val tops = inList.filter { it.parentId == null }
    val subsOf = inList.filter { it.parentId != null }.groupBy { it.parentId }
    val items = tops.flatMap { t ->
        val subs = subsOf[t.id].orEmpty()
        if (subs.isEmpty()) listOf(PreviewItem(t, null)) else subs.map { PreviewItem(it, t) }
    }
    return items.sortedWith(
        compareBy<PreviewItem>(
            { it.deadline == null }, { it.deadline },
            { (it.parent ?: it.task).position }, { (it.parent ?: it.task).id }, { it.task.position }, { it.task.id },
        ),
    )
}

enum class ButtonTone { NORMAL, PRIMARY, DANGER }

/** A text button with a container around it, so it reads as a button. */
@Composable
fun ContainedButton(
    text: String, p: Palette, modifier: Modifier = Modifier, tone: ButtonTone = ButtonTone.NORMAL,
    enabled: Boolean = true, maxLines: Int = 1, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    val bg = when (tone) { ButtonTone.PRIMARY -> p.accent; ButtonTone.DANGER -> p.dangerSoft; ButtonTone.NORMAL -> p.chip }
    val fg = when (tone) { ButtonTone.PRIMARY -> p.onAccent; ButtonTone.DANGER -> p.danger; ButtonTone.NORMAL -> p.ink }
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
            Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** A small round "i" button in the top right corner of a tab; it explains the tab in a dialog. */
@Composable
fun InfoButton(title: String, text: String, p: Palette, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier.size(40.dp).clip(CircleShape).background(p.chip)
            .clickable { open = true }.testTag("info-button"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Info, "About this tab", tint = p.ink, modifier = Modifier.size(22.dp))
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        containerColor = p.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = p.ink, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = p.muted, fontSize = 15.sp) },
        confirmButton = { ContainedButton("Got it", p, tone = ButtonTone.PRIMARY, onClick = { open = false }) },
    )
}

/** What the info button of each tab says. */
object TabInfo {
    const val TODAY = "The one task to do right now. It comes from the list of the time block that is running; when no block " +
        "is running, from the lists that have no time block. Tap the tick when it is done. Can't do it now? Swipe up to skip it: " +
        "it goes to the back of the line and comes back when nothing else is left. Swipe down to turn back to the task before: " +
        "a skipped task comes back, a done one is opened again. Between time blocks the tab waits for the next block. " +
        "A task past its deadline is marked OVERDUE. During a long break the other tabs stay locked."
    const val DAY = "Today's time blocks, hour by hour. Tap an empty spot to add a block. Tap a block to select it, then drag " +
        "it to move it or drag its handles to change its length. Blocks never overlap: the block you push into gets shorter. " +
        "Changes here apply to today only, until you choose Update weekly schedule to make today's layout your plan for this weekday."
    const val WEEK = "Your plan, repeating every week: whatever you set here comes back every week, starting tomorrow (today stays as it is). Each column is a weekday. Tap an empty spot to add a block, tap a block to " +
        "select it and drag it to move it or change its length, and long-press to pick several. Blocks never overlap: the block " +
        "you push into gets shorter. Tap a list's button under the grid to change its color."
    const val LISTS = "All your lists, with the next tasks of each in the order they will show. Tap a list to unfold it and edit " +
        "its tasks, subtasks, deadlines and repeats right here; tap it again to fold it up. Tick a task, here or on the " +
        "folded list, and after a second it moves down to Completed (tap it within that second to take it back). " +
        "A list with a time block shows during that block; lists without one fill the time when nothing else is due."
}

/** The names of BLOCK_COLORS, for the screen reader. */
val COLOR_NAMES = listOf("Green", "Amber", "Blue", "Purple", "Orange", "Teal")

/** A round color swatch to pick: read out as "Green, selected" and so on. */
fun Modifier.colorChoice(index: Int, selected: Boolean, onClick: () -> Unit): Modifier =
    selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = COLOR_NAMES.getOrElse(index) { "Color ${index + 1}" } }
