package com.focustask.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.alerts.Alerts
import com.focustask.alerts.BuiltInSounds
import com.focustask.alerts.SoundPlayer
import com.focustask.data.AlertMode
import com.focustask.data.SoundSlot
import com.focustask.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(ui: UiState, vm: MainViewModel, p: Palette, onOpenSounds: () -> Unit) {
    val ctx = LocalContext.current
    val s = ui.data.settings
    val c = s.cycle
    val allowed = Alerts.notificationsAllowed(ctx)
    val exact = Alerts.canScheduleExact(ctx)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = p.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Reset to 25 / 5 / 15", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.settings { resetCycle() } }.padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Card(p) {
            Text("Alerts", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.chip).padding(4.dp)) {
                Seg("Sound", Icons.Rounded.VolumeUp, s.alertMode == AlertMode.SOUND, p, Modifier.weight(1f)) { vm.settings { setMode(AlertMode.SOUND) } }
                Seg("Vibrate", Icons.Rounded.Vibration, s.alertMode == AlertMode.VIBRATE, p, Modifier.weight(1f)) { vm.settings { setMode(AlertMode.VIBRATE) } }
                Seg("Silent", Icons.Rounded.VolumeOff, s.alertMode == AlertMode.SILENT, p, Modifier.weight(1f)) { vm.settings { setMode(AlertMode.SILENT) } }
            }
            Text(
                "The round button by the timer switches Sound and Vibrate. Android's notification permission and silent mode always win.",
                color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (allowed) Icons.Rounded.Check else Icons.Rounded.Warning, null, tint = if (allowed) p.accent else p.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (allowed) "Notifications allowed" else "Notifications are off, so nothing will play",
                    color = if (allowed) p.accent else p.danger, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Text(
                    "Phone settings", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.padding(8.dp),
                )
            }
            if (!exact) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Alerts may be a minute late without exact alarms.", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Allow", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            runCatching {
                                ctx.startActivity(
                                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }.padding(8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(p) {
            StepperRow("Work period", "Focus time in each cycle", c.workMin, "min", p, 1, 180) { v -> vm.settings { setCycle(work = v) } }
            Divider(p)
            StepperRow("Short break", "Rest after each cycle", c.shortBreakMin, "min", p, 0, 60) { v -> vm.settings { setCycle(short = v) } }
            Divider(p)
            StepperRow("Long break", "After every block that ends on time", c.longBreakMin, "min", p, 0, 120) { v -> vm.settings { setCycle(long = v) } }
        }
        Spacer(Modifier.height(12.dp))
        Card(p) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenSounds).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Alert sounds", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Cycle chime: ${s.sounds.getValue(SoundSlot.CHIME).label} · cue · block ending",
                        color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = p.ink)
            }
            Divider(p)
            StepperRow("Block countdown", "Big timer before a block ends", c.countdownMin, "min", p, 0, 60) { v -> vm.settings { setCycle(countdown = v) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Divider(p: Palette) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
fun Card(p: Palette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = p.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), content = content)
    }
}

@Composable
private fun Seg(label: String, icon: ImageVector, selected: Boolean, p: Palette, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.accent else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) p.onAccent else p.ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) p.onAccent else p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

// ------------------------------------------------------------------ alert sounds

@Composable
fun AlertSoundsScreen(ui: UiState, vm: MainViewModel, p: Palette, onBack: () -> Unit) {
    val s = ui.data.settings
    var pickFor by remember { mutableStateOf<SoundSlot?>(null) }
    var fileFor by remember { mutableStateOf<SoundSlot?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = fileFor
        fileFor = null
        if (uri != null && slot != null) vm.importSound(slot, uri, s.sounds.getValue(slot).source)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = p.ink)
            }
            Text("Alert sounds", color = p.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        SoundCard(
            "Cycle chime", "At every large bubble, every ${s.cycle.cycleMin} min",
            SoundSlot.CHIME, ui, vm, p,
            onPickBuiltIn = { pickFor = SoundSlot.CHIME },
            onPickFile = { fileFor = SoundSlot.CHIME; launcher.launch(arrayOf("audio/*")) },
        )
        Spacer(Modifier.height(12.dp))
        SoundCard(
            "Work and break cue", "Quieter, when the phase changes",
            SoundSlot.CUE, ui, vm, p,
            onPickBuiltIn = { pickFor = SoundSlot.CUE },
            onPickFile = { fileFor = SoundSlot.CUE; launcher.launch(arrayOf("audio/*")) },
        )
        Spacer(Modifier.height(12.dp))
        SoundCard(
            "Block ending", "Plays when the countdown starts",
            SoundSlot.ENDING, ui, vm, p,
            onPickBuiltIn = { pickFor = SoundSlot.ENDING },
            onPickFile = { fileFor = SoundSlot.ENDING; launcher.launch(arrayOf("audio/*")) },
        )
        Spacer(Modifier.height(12.dp))
        Card(p) {
            StepperRow("Longest sound", "Longer files get shortened", s.maxSoundSec, "sec", p, 1, 60) { v -> vm.settings { setMaxSound(v) } }
            Divider(p)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fade out at the end", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Fades over the last 1.5 s instead of a hard cut", color = p.muted, fontSize = 13.sp)
                }
                Switch(checked = s.fade, onCheckedChange = { v -> vm.settings { setFade(v) } }, colors = switchColors(p))
            }
        }
        Text(
            "Any audio file works: MP3, OGG, WAV, M4A or FLAC. Your file is copied into the app, so it keeps working if you move or delete the original.",
            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }

    pickFor?.let { slot ->
        val current = s.sounds.getValue(slot)
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text("Choose a sound") },
            text = {
                Column {
                    BuiltInSounds.all.forEach { b ->
                        val sel = current.source == "builtin:${b.id}"
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                vm.chooseBuiltIn(slot, b.id, b.label, current.source)
                                vm.preview("builtin:${b.id}", s.maxSoundSec, s.fade)
                            }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, tint = p.muted)
                            Spacer(Modifier.width(12.dp))
                            Text(b.label, modifier = Modifier.weight(1f), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = p.ink)
                            if (sel) Icon(Icons.Rounded.Check, null, tint = p.accent)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun switchColors(p: Palette) = SwitchDefaults.colors(
    checkedTrackColor = p.accent, checkedThumbColor = p.onAccent,
    uncheckedTrackColor = p.chip, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line,
)

@Composable
private fun SoundCard(
    title: String, subtitle: String, slot: SoundSlot, ui: UiState, vm: MainViewModel, p: Palette,
    onPickBuiltIn: () -> Unit, onPickFile: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = ui.data.settings
    val cfg = s.sounds.getValue(slot)
    val duration by produceState<Long?>(null, cfg.source) {
        value = withContext(Dispatchers.IO) { SoundPlayer.durationMs(ctx, cfg.source) }
    }
    Card(p) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = p.muted, fontSize = 13.sp)
            }
            Switch(checked = cfg.enabled, onCheckedChange = { v -> vm.settings { setSoundEnabled(slot, v) } }, colors = switchColors(p))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp)).background(p.card).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = p.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(cfg.label, color = p.cardInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                duration?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("%.1f s".format(it / 1000.0), color = p.muted, fontSize = 12.sp)
                }
            }
            CircleBtn(Icons.Rounded.PlayArrow, "Play", p, filled = true) { vm.preview(cfg.source, s.maxSoundSec, s.fade) }
            CircleBtn(Icons.Rounded.FormatListBulleted, "Built-in sounds", p, onClick = onPickBuiltIn)
            CircleBtn(Icons.Rounded.FolderOpen, "Choose a file", p, onClick = onPickFile)
        }
    }
}

@Composable
private fun CircleBtn(icon: ImageVector, desc: String, p: Palette, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = CircleShape, color = if (filled) p.accent else Color.Transparent,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(2.dp, p.line),
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (filled) p.onAccent else p.accent, modifier = Modifier.size(22.dp))
        }
    }
}
