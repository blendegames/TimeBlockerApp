package com.focustask.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.core.Task
import com.focustask.ui.theme.Palette
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

fun daysLabel(days: Set<DayOfWeek>): String = when {
    days.size == 7 -> "Every day"
    days == WEEKDAYS -> "Mon to Fri"
    days.isEmpty() -> "Never"
    else -> DayOfWeek.values().filter { it in days }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

fun tasksLabel(n: Int) = if (n == 1) "1 task" else "$n tasks"

/** Open top-level tasks of a list in the order they will be shown: deadline first, then manual order. */
fun upcomingTasks(tasks: List<Task>, listId: Long): List<Task> =
    tasks.filter { it.listId == listId && !it.done && it.parentId == null }
        .sortedWith(
            compareBy<Task>({ it.deadline == null }, { it.deadline }, { it.position }, { it.id })
        )

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
