package com.focustask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.core.Repeat
import com.focustask.core.RepeatUnit
import com.focustask.data.BLOCK_COLORS
import com.focustask.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Pick a day, then a time. [onSet] gets null when the user clears the deadline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineDialog(initial: LocalDateTime?, onDismiss: () -> Unit, onSet: (LocalDateTime?) -> Unit) {
    val base = initial ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    var date by remember { mutableStateOf<LocalDate?>(null) }
    if (date == null) {
        val ds = rememberDatePickerState(initialSelectedDateMillis = base.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text("Next") }
            },
            dismissButton = {
                Row {
                    if (initial != null) TextButton(onClick = { onSet(null) }) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            },
        ) { DatePicker(ds) }
    } else {
        val ts = rememberTimePickerState(initialHour = base.hour, initialMinute = base.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Time on ${date.toString()}") },
            text = { TimePicker(ts) },
            confirmButton = { TextButton(onClick = { onSet(date!!.atTime(ts.hour, ts.minute)) }) { Text("Set deadline") } },
            dismissButton = { TextButton(onClick = { date = null }) { Text("Back") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initial: LocalTime, onDismiss: () -> Unit, onSet: (LocalTime) -> Unit) {
    val ts = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(ts) },
        confirmButton = { TextButton(onClick = { onSet(LocalTime.of(ts.hour, ts.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One text field in a dialog, with optional delete. */
@Composable
fun TextEditDialog(
    title: String, initial: String,
    onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text) }),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFB42318)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Name and color of a new list. [initialColor] is the suggested index into BLOCK_COLORS. */
@Composable
fun NewListDialog(initialColor: Int, p: Palette, onDismiss: () -> Unit, onCreate: (name: String, color: Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(initialColor) }
    val focus = remember { FocusRequester() }
    fun create() { if (text.isNotBlank()) onCreate(text, color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New list") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { create() }),
                )
                Text("Color", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                BLOCK_COLORS.chunked(3).forEachIndexed { row, colors ->
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        colors.forEachIndexed { i, c ->
                            val index = row * 3 + i
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Color(c))
                                    .then(if (index == color) Modifier.border(3.dp, p.ink, CircleShape) else Modifier)
                                    .testTag("list-color-$index")
                                    .colorChoice(index, index == color) { color = index },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { create() }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "- 25 min +" row used by the settings. */
@Composable
fun StepperRow(
    title: String, subtitle: String, value: Int, unit: String, p: Palette,
    min: Int, max: Int, step: Int = 1, shown: String = "$value", onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.muted, fontSize = 13.sp)
        }
        RoundStep(Icons.Rounded.Remove, "Less", p, enabled = value > min) { onChange((value - step).coerceAtLeast(min)) }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(shown, color = p.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = p.muted, fontSize = 12.sp)
        }
        RoundStep(Icons.Rounded.Add, "More", p, enabled = value < max) { onChange((value + step).coerceAtMost(max)) }
    }
}

/** A stepper for a time in seconds that jumps between sensible [presets] and shows "45 sec" or "1.5 min". */
@Composable
fun SecondsStepperRow(
    title: String, subtitle: String, seconds: Int, presets: List<Int>, p: Palette, modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val i = presets.indexOfFirst { it >= seconds }.let { if (it < 0) presets.lastIndex else it }
    val v = presets[i]
    val shown = if (v < 60) "$v" else (v / 60f).let { m -> if (m == m.toInt().toFloat()) "${m.toInt()}" else "%.1f".format(m) }
    Box(modifier) {
        StepperRow(title, subtitle, i, if (v < 60) "sec" else "min", p, 0, presets.lastIndex, 1, shown) { onChange(presets[it]) }
    }
}

@Composable
private fun RoundStep(icon: ImageVector, desc: String, p: Palette, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(p.chip)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) p.ink else p.muted.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
    }
}

/**
 * Makes a task "permanent": once it is completed it comes back as not done (its subtasks too) at the start of the day,
 * every [n] days, weeks or months after it was completed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatDialog(initial: Repeat?, onDismiss: () -> Unit, onSet: (Repeat?) -> Unit) {
    var every by remember { mutableStateOf(initial?.every ?: 1) }
    var unit by remember { mutableStateOf(initial?.unit ?: RepeatUnit.DAY) }
    val rule = Repeat(every, unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat this task") },
        text = {
            Column {
                Text(
                    "Complete it today and it comes back, not done, at the start of the day. Its subtasks come back too.",
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Every", fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { if (every > 1) every-- }, modifier = Modifier.testTag("repeat-less")) {
                        Icon(Icons.Rounded.Remove, "Fewer")
                    }
                    Text("$every", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("repeat-count"))
                    IconButton(onClick = { if (every < 99) every++ }, modifier = Modifier.testTag("repeat-more")) {
                        Icon(Icons.Rounded.Add, "More")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatUnit.entries.forEach { u ->
                        val name = when (u) { RepeatUnit.DAY -> "day"; RepeatUnit.WEEK -> "week"; else -> "month" } + if (every == 1) "" else "s"
                        FilterChip(
                            selected = unit == u, onClick = { unit = u }, label = { Text(name) },
                            modifier = Modifier.testTag("repeat-unit-${u.name}"),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(rule.label(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = { TextButton(onClick = { onSet(rule) }) { Text("Repeat") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSet(null) }) { Text("Stop repeating") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
