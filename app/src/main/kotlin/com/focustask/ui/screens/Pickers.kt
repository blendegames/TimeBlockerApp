package com.focustask.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    title: String, initial: String, confirmLabel: String = "Save",
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
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSave(text) }) { Text(confirmLabel) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFB42318)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** "- 25 min +" row used by the settings. */
@Composable
fun StepperRow(
    title: String, subtitle: String, value: Int, unit: String, p: Palette,
    min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.muted, fontSize = 13.sp)
        }
        RoundStep(Icons.Rounded.Remove, "Less", p, enabled = value > min) { onChange((value - step).coerceAtLeast(min)) }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = p.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = p.muted, fontSize = 12.sp)
        }
        RoundStep(Icons.Rounded.Add, "More", p, enabled = value < max) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, p: Palette, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(p.chip)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (enabled) p.ink else p.muted.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
    }
}
