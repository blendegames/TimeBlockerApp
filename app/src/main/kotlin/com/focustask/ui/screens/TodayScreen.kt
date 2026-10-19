package com.focustask.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.Carousel
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.ui.components.Pill
import com.focustask.ui.theme.Palette
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun TodayScreen(ui: UiState, vm: MainViewModel, p: Palette) {
    val f = ui.focus
    val c = f.carousel
    val overdue = f.mode == Mode.FOCUS && c?.current?.reason == Reason.OVERDUE
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when {
                f.mode == Mode.BREAK -> {
                    val left = Duration.between(f.now, f.longBreak!!.until).seconds
                    Pill("Long break · ${(left + 59) / 60} min", p)
                }
                overdue -> Row(
                    Modifier.clip(CircleShape).background(p.pill).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Warning, null, tint = p.pillInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("OVERDUE", color = p.pillInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                f.mode == Mode.FOCUS -> {
                    val cur = c!!.current
                    val act = f.active
                    val text = when {
                        act != null -> "${act.block.name} · until ${Fmt.time(act.end)}"
                        else -> "Anytime · ${cur.list.title}"
                    }
                    Pill(text, p, dot = true)
                }
                else -> Pill(f.active?.let { "${it.block.name} · nothing left" } ?: "Between blocks", p)
            }
            Text(
                "${Fmt.dayShort(f.now.toLocalDate())} ${Fmt.time(f.now)}",
                color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
        }
        when (f.mode) {
            Mode.FOCUS -> FocusBody(ui, vm, c!!, p, overdue)
            Mode.ALL_DONE -> AllDoneBody(ui, vm, p)
            Mode.BREAK -> BreakBody(ui, p)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FocusBody(
    ui: UiState, vm: MainViewModel, carousel: Carousel, p: Palette, overdue: Boolean,
) {
    val now = ui.now
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        AnimatedContent(
            targetState = carousel,
            transitionSpec = {
                (slideInVertically { it / 4 } + fadeIn()) togetherWith (slideOutVertically { -it / 4 } + fadeOut())
            },
            label = "carousel",
        ) { c ->
            Column {
                c.parent?.let { parent ->
                    Column(Modifier.padding(bottom = 18.dp)) {
                        Text("TASK IN ${c.current.list.title.uppercase()}", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        Text(parent.title, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Rounded.Schedule, null, tint = p.ink, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            val due = parent.deadline?.let { "Due ${Fmt.deadline(it, now)}" } ?: "No deadline"
                            Text("$due  •  ${c.stepIndex - 1} of ${c.stepCount} steps done", color = p.ink, fontSize = 14.sp)
                        }
                    }
                }
                // previous
                val prev = c.previous
                if (prev != null) {
                    CarouselRow(prev.title, done = true, right = prev.completedAt?.let { "Done ${Fmt.time(it)}" } ?: "", p = p)
                } else {
                    Text("Start of the list", color = p.muted.copy(alpha = 0.8f), fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                }
                Spacer(Modifier.height(6.dp))
                CurrentCard(c, now, p, overdue)
                Spacer(Modifier.height(6.dp))
                val nxt = c.next
                if (nxt != null) {
                    val right = nxt.deadline?.let { d ->
                        if (c.parent != null) Fmt.time(d) else Fmt.deadlineShort(d, now)
                    } ?: ""
                    CarouselRow(nxt.title, done = false, right = right, p = p)
                } else {
                    Text("End of the list", color = p.muted.copy(alpha = 0.8f), fontSize = 13.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.run { complete(carousel.current.task.id) } },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
        ) {
            Icon(Icons.Rounded.Check, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("Done", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        val listName = carousel.current.list.title
        val footer = when {
            overdue -> "Overdue first, then ${ui.focus.active?.block?.name ?: "your day"} continues"
            carousel.parent != null -> "Step ${carousel.stepIndex} of ${carousel.stepCount} · Task ${carousel.listIndex} of ${carousel.listCount} in $listName"
            else -> "Task ${carousel.listIndex} of ${carousel.listCount} in $listName"
        }
        Text(footer, color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CarouselRow(title: String, done: Boolean, right: String, p: Palette) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(p.muted.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = p.bg, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.Transparent)
                .then(Modifier), contentAlignment = Alignment.Center) {
                Surface(Modifier.size(26.dp), shape = CircleShape, color = Color.Transparent, border = BorderStroke(2.dp, p.muted)) {}
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            title, color = p.muted, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
        )
        if (right.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(right, color = p.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CurrentCard(c: Carousel, now: LocalDateTime, p: Palette, overdue: Boolean) {
    val cur = c.current
    Surface(color = p.card, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            val label = when {
                overdue -> Fmt.late(cur.deadline ?: now, now).uppercase()
                c.parent != null -> "SUBTASK ${c.stepIndex} OF ${c.stepCount}"
                else -> "YOUR ONE TASK"
            }
            Text(label, color = if (overdue) p.bubble else p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(cur.task.title, color = p.cardInk, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = p.cardInk, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val d = cur.deadline
                val meta = when {
                    d == null -> "No deadline"
                    overdue -> Fmt.wasDue(d, now)
                    else -> "Due ${Fmt.deadline(d, now)}"
                }
                Text(meta, color = p.cardInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (c.parent == null) Text("  ·  ${cur.list.title} list", color = p.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AllDoneBody(ui: UiState, vm: MainViewModel, p: Palette) {
    val f = ui.focus
    val next = f.next
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(p.card), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = p.accent, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("All done", color = p.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (next != null) {
            val left = Duration.between(f.now, next.start).toMinutes()
            Text(
                "${next.block.name} starts ${Fmt.time(next.start)}. Finishing early starts a long break for the ${Fmt.minutesLong(left.coerceAtLeast(1))} left.",
                color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val mins = Duration.between(f.now, next.start).toMinutes()
                    if (mins > 60) confirm = true else vm.run { startUserBreak(f.now, next.start) }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            ) {
                Icon(Icons.Rounded.Pause, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Break until ${next.block.name} starts", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = p.muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Other tabs stay locked during the break", color = p.muted, fontSize = 13.sp)
            }
        } else {
            Text(
                "Nothing is scheduled. Add a time block in the Day tab and its list will show up here.",
                color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    if (confirm && next != null) {
        val mins = Duration.between(f.now, next.start).toMinutes()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Break for ${Fmt.minutesLong(mins)}?") },
            text = { Text("The break lasts until ${next.block.name} starts at ${Fmt.time(next.start)}. The other tabs stay locked until then.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.run { startUserBreak(f.now, next.start) } }) { Text("Start the break") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BreakBody(ui: UiState, p: Palette) {
    val f = ui.focus
    val lb = f.longBreak!!
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val seconds = f.bar.seconds
        val progress = (1f - f.bar.sun.toFloat()).coerceIn(0f, 1f)
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 14.dp.toPx()
                val inset = sw / 2
                val arcSize = Size(size.width - sw, size.height - sw)
                drawArc(p.pill, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                drawArc(p.accent, -90f + (1f - progress) * 360f, progress * 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.countdown(seconds), color = p.accent, fontSize = 56.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("left", color = p.muted, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text("Take a real break", color = p.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        val line1 = f.next?.let { "${it.block.name} starts at ${Fmt.time(it.start)}" } ?: "Back at ${Fmt.time(lb.until)}"
        Text(line1, color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center)
        Text(
            if (lb.userInitiated) "This break lasts exactly until then." else "Every block ends with a real break.",
            color = p.muted, fontSize = 17.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.clip(CircleShape).background(p.pill).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = p.pillInk, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Breaks can't be skipped", color = p.pillInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
    Spacer(Modifier.height(24.dp))
}
