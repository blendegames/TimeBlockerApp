package com.focustask.ui.screens

import com.focustask.ui.theme.TitleSize
import com.focustask.core.AlertKind
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.alerts.Alerts
import com.focustask.alerts.BuiltInSounds
import com.focustask.alerts.SoundPlayer
import com.focustask.core.ScreenDim
import com.focustask.data.AlertMode
import com.focustask.data.LoadedCopy
import com.focustask.data.SoundSlot
import com.focustask.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The groups the settings are sorted into. */
enum class SettingsSection(val title: String, val icon: ImageVector) {
    TIMER("Timer", Icons.Rounded.Timer),
    ALERTS("Alerts", Icons.Rounded.NotificationsActive),
    SCREEN("Screen", Icons.Rounded.Smartphone),
    DATA("Your data", Icons.Rounded.Storage),
}

/** The settings: a short list of groups, each opening its own page ([section] null is the list). */
@Composable
fun SettingsScreen(
    ui: UiState, vm: MainViewModel, p: Palette,
    section: SettingsSection?, onSection: (SettingsSection?) -> Unit, onOpenSounds: () -> Unit,
) {
    if (section == null) SettingsHome(ui, vm, p, onSection)
    else SettingsPage(ui, vm, p, section, onBack = { onSection(null) }, onOpenSounds)
}

private fun summary(section: SettingsSection, ui: UiState, allowed: Boolean): String {
    val s = ui.data.settings
    val c = s.cycle
    return when (section) {
        SettingsSection.TIMER -> "${c.workMin} min work · ${c.shortBreakMin} min break · " +
            if (c.longBreakMin == 0) "no long break" else "${c.longBreakMin} min long break"
        SettingsSection.ALERTS -> when (s.alertMode) {
            AlertMode.SOUND -> "Sound"; AlertMode.VIBRATE -> "Vibrate"; AlertMode.SILENT -> "Silent"
        } + if (allowed) "" else " · notifications are off"
        SettingsSection.SCREEN -> listOfNotNull(
            if (s.keepScreenOn) "Stays on" else "Normal timeout",
            if (s.timerViewWhenIdle) "timer view after ${secondsText(s.timerViewAfterSec)}" else null,
            if (s.keepScreenOn && s.dimWhenIdle) "dims" else null,
        ).joinToString(" · ")
        SettingsSection.DATA -> "Saved inside the app"
    }
}

private fun secondsText(sec: Int): String =
    if (sec < 60) "$sec sec" else (sec / 60f).let { m -> (if (m == m.toInt().toFloat()) "${m.toInt()}" else "%.1f".format(m)) + " min" }

@Composable
private fun SettingsHome(ui: UiState, vm: MainViewModel, p: Palette, onSection: (SettingsSection) -> Unit) {
    val ctx = LocalContext.current
    val allowed = Alerts.notificationsAllowed(ctx)
    val status by vm.copyStatus.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(
            "Settings", color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
        Card(p) {
            SettingsSection.values().forEachIndexed { i, sec ->
                if (i > 0) Divider(p)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onSection(sec) }
                        .padding(vertical = 14.dp).testTag("settings-${sec.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(p.pill), contentAlignment = Alignment.Center) {
                        Icon(sec.icon, null, tint = p.accent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sec.title, color = p.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (sec == SettingsSection.DATA && status.folderName != null) "Copy kept in ${status.folderName}" else summary(sec, ui, allowed),
                            color = p.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = p.ink)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsPage(ui: UiState, vm: MainViewModel, p: Palette, section: SettingsSection, onBack: () -> Unit, onOpenSounds: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack).testTag("settings-back"), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.ink)
            }
            Text(section.title, color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (section == SettingsSection.TIMER) {
                Text(
                    "Reset to 25 / 5 / 15", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.settings { resetCycle() } }.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (section) {
            SettingsSection.TIMER -> TimerSettings(ui, vm, p)
            SettingsSection.ALERTS -> AlertSettings(ui, vm, p, onOpenSounds)
            SettingsSection.SCREEN -> ScreenSettings(ui, vm, p)
            SettingsSection.DATA -> YourDataCard(ui, vm, p)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TimerSettings(ui: UiState, vm: MainViewModel, p: Palette) {
    val c = ui.data.settings.cycle
    Card(p) {
        StepperRow("Work period", "Focus time in each cycle", c.workMin, "min", p, 1, 180) { v -> vm.settings { setCycle(work = v) } }
        Divider(p)
        StepperRow("Short break", "Rest after each cycle", c.shortBreakMin, "min", p, 0, 60) { v -> vm.settings { setCycle(short = v) } }
        Divider(p)
        StepperRow("Long break", "How long it lasts (0 turns long breaks off)", c.longBreakMin, "min", p, 0, 120) { v -> vm.settings { setCycle(long = v) } }
        if (c.longBreakMin > 0) {
            Divider(p)
            StepperRow(
                "Long break after blocks of", "A block this long or longer is followed by a long break",
                c.longBreakAfterMin, if (c.longBreakAfterMin == 60) "hour" else "hours", p, 30, 720, step = 30,
                shown = (c.longBreakAfterMin / 60f).let { h -> if (h == h.toInt().toFloat()) "${h.toInt()}" else "%.1f".format(h) },
            ) { v -> vm.settings { setCycle(longAfter = v) } }
        }
        Divider(p)
        StepperRow("Block countdown", "Big timer before a block ends", c.countdownMin, "min", p, 0, 60) { v -> vm.settings { setCycle(countdown = v) } }
    }
}

@Composable
private fun AlertSettings(ui: UiState, vm: MainViewModel, p: Palette, onOpenSounds: () -> Unit) {
    val ctx = LocalContext.current
    val s = ui.data.settings
    val allowed = Alerts.notificationsAllowed(ctx)
    val exact = Alerts.canScheduleExact(ctx)
    Card(p) {
        Text("Alerts", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.chip).padding(4.dp)) {
            Seg("Sound", Icons.AutoMirrored.Rounded.VolumeUp, s.alertMode == AlertMode.SOUND, p, Modifier.weight(1f)) { vm.settings { setMode(AlertMode.SOUND) } }
            Seg("Vibrate", Icons.Rounded.Vibration, s.alertMode == AlertMode.VIBRATE, p, Modifier.weight(1f)) { vm.settings { setMode(AlertMode.VIBRATE) } }
            Seg("Silent", Icons.AutoMirrored.Rounded.VolumeOff, s.alertMode == AlertMode.SILENT, p, Modifier.weight(1f)) { vm.settings { setMode(AlertMode.SILENT) } }
        }
        Text(
            "Sound plays the sound and vibrates at the same time. Vibrate only buzzes. The round button by the timer switches the two. Android's notification permission and silent mode always win.",
            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp),
        )
        // each moment has its own feel: try them here
        Text("Feel the vibrations", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, kind) in listOf("Block starts" to AlertKind.BLOCK_STARTING, "Break" to AlertKind.PHASE_CUE, "Back to work" to AlertKind.CYCLE_CHIME)) {
                ContainedButton(label, p, maxLines = 2, modifier = Modifier.weight(1f).testTag("feel-${kind.name}")) { Alerts.vibrate(ctx, kind) }
            }
        }
        Text(
            "Block starts or ends: pulses that grow stronger, the strongest right on the moment, then the sound. Break: a short soft wave that fades. Back to work: the same wave, but hard.",
            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
        // the whole way an alert comes: alarm, vibration and sound, also with the screen off
        var testSent by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (testSent) "Coming in ${Alerts.TEST_DELAY_SEC} s: lock the phone to check it comes through" else "Try a whole alert, the way it comes when a block starts",
                color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            ContainedButton("Test alert", p, tone = ButtonTone.PRIMARY, modifier = Modifier.testTag("test-alert")) { Alerts.scheduleTest(ctx); testSent = true }
        }
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
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenSounds).padding(vertical = 8.dp).testTag("alert-sounds-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Alert sounds", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Back to work: ${s.sounds.getValue(SoundSlot.CHIME).label} · break · block soon",
                    color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = p.ink)
        }
    }
}

private val TIMER_VIEW_DELAYS = listOf(10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800)
private val DIM_DELAYS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 300, 600, 900, 1800)

@Composable
private fun ScreenSettings(ui: UiState, vm: MainViewModel, p: Palette) {
    val s = ui.data.settings
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        Card(p) {
            SwitchRow(
                "Match my wallpaper",
                "Backgrounds and accents take their colors from your wallpaper. The task cards on Today keep the color of their list",
                s.wallpaperColors, p, "wallpaper-colors",
            ) { v -> vm.settings { setWallpaperColors(v) } }
        }
        Spacer(Modifier.height(12.dp))
    }
    Card(p) {
        SwitchRow(
            "Timer view when idle",
            "When you don't touch the app, the full-screen timer drops down over every tab: pure black with the focus timer. You can also pull it down from the timer bar any time. A tap or a swipe up sends it back",
            s.timerViewWhenIdle, p, "timer-view-idle",
        ) { v -> vm.settings { setTimerViewWhenIdle(v) } }
        if (s.timerViewWhenIdle) {
            Divider(p)
            SecondsStepperRow(
                "Switch to it after", "Without a touch", s.timerViewAfterSec, TIMER_VIEW_DELAYS, p,
                Modifier.testTag("timer-view-delay"),
            ) { v -> vm.settings { setTimerViewAfterSec(v) } }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(p) {
        SwitchRow("Keep screen on", "The screen stays awake while FocusTask is open", s.keepScreenOn, p, "keep-screen-on") { v -> vm.settings { setKeepScreenOn(v) } }
        if (s.keepScreenOn) {
            Divider(p)
            SwitchRow(
                "Dim when idle",
                "Slowly fades the screen. A touch, or a change between work and break, brings your normal brightness back",
                s.dimWhenIdle, p, "dim-when-idle",
            ) { v -> vm.settings { setDimWhenIdle(v) } }
            if (s.dimWhenIdle) {
                Divider(p)
                SecondsStepperRow(
                    "Start dimming after",
                    if (s.timerViewWhenIdle) "Once the timer fills the screen" else "Without a touch",
                    s.dimAfterSec, DIM_DELAYS, p, Modifier.testTag("dim-delay"),
                ) { v -> vm.settings { setDimAfterSec(v) } }
                var pct by remember(s.dimPercent) { mutableStateOf(s.dimPercent.toFloat()) }
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("How dark", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${pct.toInt()}% of your brightness", color = p.muted, fontSize = 14.sp, modifier = Modifier.testTag("dim-percent-label"))
                    }
                    Slider(
                        value = pct, onValueChange = { pct = it },
                        onValueChangeFinished = { vm.settings { setDimPercent(pct.toInt()) } },
                        valueRange = ScreenDim.MIN_PERCENT.toFloat()..com.focustask.core.ScreenDim.MAX_PERCENT.toFloat(),
                        steps = (ScreenDim.MAX_PERCENT - ScreenDim.MIN_PERCENT) / 5 - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.chip,
                            activeTickColor = p.onAccent, inactiveTickColor = p.muted,
                        ),
                        modifier = Modifier.testTag("dim-percent"),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("Darker", color = p.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("Lighter", color = p.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, p: Palette, tag: String, onChange: (Boolean) -> Unit) {
    // the whole row is the switch: one big target, read out as one switch with its title
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange).testTag(tag)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = p.muted, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null, colors = switchColors(p))
    }
}

/** The folder picker, asking Android to let the app keep using the folder later on. */
private class PickFolder : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
}

/**
 * Your data: it is always saved as JSON files inside the app. Picking a folder keeps a second copy
 * there, which is what survives uninstalling the app, and can be restored from again.
 */
@Composable
private fun YourDataCard(ui: UiState, vm: MainViewModel, p: Palette) {
    val ctx = LocalContext.current
    val status by vm.copyStatus.collectAsState()
    var pending by remember { mutableStateOf<LoadedCopy?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val keepPicker = rememberLauncherForActivityResult(PickFolder()) { uri: Uri? -> if (uri != null) vm.keepCopyIn(uri) }
    val restorePicker = rememberLauncherForActivityResult(PickFolder()) { uri: Uri? ->
        if (uri != null) vm.checkCopyIn(uri) { r ->
            r.onSuccess { pending = it }
                .onFailure { Toast.makeText(ctx, it.message ?: "That folder could not be read.", Toast.LENGTH_LONG).show() }
        }
    }
    val kept = status.folderName != null
    Card(p) {
        Text("Your data", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Lists, tasks, time blocks and settings are saved as readable JSON files inside the app, so updating the app never loses them.",
            color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Divider(p, Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep a copy outside the app", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (kept) "In ${status.folderName} › FocusTask data" else "Survives uninstalling the app. Choose a folder.",
                    color = p.muted, fontSize = 13.sp,
                )
                if (kept) {
                    val saved = if (status.savedAt > 0) "Saved ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(status.savedAt))}" else "Saving…"
                    Text(status.error ?: saved, color = if (status.error != null) p.danger else p.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Switch(
                checked = kept,
                onCheckedChange = { on -> if (on) keepPicker.launch(null) else vm.stopKeepingCopy() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = p.accent, checkedThumbColor = p.onAccent,
                    uncheckedTrackColor = p.chip, uncheckedThumbColor = p.muted, uncheckedBorderColor = p.line,
                ),
                modifier = Modifier.testTag("keep-copy-switch"),
            )
        }
        if (kept) {
            Text(
                "Use another folder", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { keepPicker.launch(null) }.padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
        Divider(p)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { restorePicker.launch(null) }.padding(vertical = 10.dp).testTag("restore-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Restore from a folder", color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("After reinstalling, choose the folder you kept the copy in.", color = p.muted, fontSize = 13.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = p.ink)
        }
        Divider(p)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { askDelete = true }.padding(vertical = 10.dp).testTag("delete-all-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Delete all data", color = p.danger, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Removes every list, task, time block and setting.", color = p.muted, fontSize = 13.sp)
            }
        }
    }
    if (askDelete) {
        val d = ui.data
        AlertDialog(
            onDismissRequest = { if (!deleting) askDelete = false },
            title = { Text("Delete all your data?") },
            text = {
                Text(
                    "This permanently deletes ${d.lists.size} lists, ${d.tasks.size} tasks and ${d.blocks.size} time blocks, " +
                        "your one-day changes, your settings and the alert sounds you added.\n\n" +
                        (if (kept) "The copy in ${status.folderName} is emptied too, so it cannot be used to restore.\n\n" else "") +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(enabled = !deleting, modifier = Modifier.testTag("delete-all-confirm"), onClick = {
                    deleting = true
                    vm.deleteAllData {
                        deleting = false; askDelete = false
                        Toast.makeText(ctx, "All data deleted", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete everything", color = p.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { askDelete = false }) { Text("Cancel") } },
        )
    }
    pending?.let { copy ->
        val i = copy.info
        AlertDialog(
            onDismissRequest = { if (!restoring) pending = null },
            title = { Text("Restore this copy?") },
            text = {
                Text(
                    "${i.lists} lists, ${i.tasks} tasks and ${i.blocks} time blocks, saved ${i.savedAt.replace('T', ' ')}.\n\n" +
                        "This replaces everything that is in the app now.",
                )
            },
            confirmButton = {
                TextButton(enabled = !restoring, onClick = {
                    restoring = true
                    vm.restoreCopy(copy) {
                        restoring = false; pending = null
                        Toast.makeText(ctx, "Your data is back", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(enabled = !restoring, onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Divider(p: Palette, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
fun Card(p: Palette, content: @Composable ColumnScope.() -> Unit) {
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = p.ink)
            }
            Text("Alert sounds", color = p.ink, fontSize = TitleSize, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        SoundCard(
            "Back to work", "At every large bubble (every ${s.cycle.cycleMin} min) and when a break is over",
            SoundSlot.CHIME, ui, vm, p,
            onPickBuiltIn = { pickFor = SoundSlot.CHIME },
            onPickFile = { fileFor = SoundSlot.CHIME; launcher.launch(arrayOf("audio/*")) },
        )
        Spacer(Modifier.height(12.dp))
        SoundCard(
            "Break", "Quieter, when it is time for a break",
            SoundSlot.CUE, ui, vm, p,
            onPickBuiltIn = { pickFor = SoundSlot.CUE },
            onPickFile = { fileFor = SoundSlot.CUE; launcher.launch(arrayOf("audio/*")) },
        )
        Spacer(Modifier.height(12.dp))
        SoundCard(
            "Block starting or ending", "Right when a block starts and when it ends",
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
            CircleBtn(Icons.AutoMirrored.Rounded.FormatListBulleted, "Built-in sounds", p, onClick = onPickBuiltIn)
            CircleBtn(Icons.Rounded.FolderOpen, "Choose a file", p, onClick = onPickFile)
        }
    }
}

@Composable
private fun CircleBtn(icon: ImageVector, desc: String, p: Palette, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = CircleShape, color = if (filled) p.accent else Color.Transparent,
        border = if (filled) null else BorderStroke(2.dp, p.line),
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (filled) p.onAccent else p.accent, modifier = Modifier.size(22.dp))
        }
    }
}
