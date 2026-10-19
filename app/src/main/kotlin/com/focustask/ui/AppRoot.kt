package com.focustask.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustask.MainViewModel
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.ui.components.BottomNav
import com.focustask.ui.components.Tab
import com.focustask.ui.components.TimerBarView
import com.focustask.ui.components.barTone
import com.focustask.ui.screens.AlertSoundsScreen
import com.focustask.ui.screens.BlockEditorScreen
import com.focustask.ui.screens.DayScreen
import com.focustask.ui.screens.ListEditScreen
import com.focustask.ui.screens.ListsScreen
import com.focustask.ui.screens.SettingsScreen
import com.focustask.ui.screens.TodayScreen
import com.focustask.ui.screens.WeekScreen
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.paletteFor
import androidx.compose.foundation.isSystemInDarkTheme

/** Which block the editor shows: a new one, or an existing one. */
sealed interface BlockTarget {
    data object New : BlockTarget
    data class Edit(val blockId: Long) : BlockTarget
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state = ui
    if (state == null) {
        Box(Modifier.fillMaxSize().background(paletteFor(Tone.FOCUS, isSystemInDarkTheme()).bg))
        return
    }
    AppScaffold(state, vm)
}

/** The whole app for one state. Split out from [AppRoot] so it can be rendered with any state. */
@Composable
fun AppScaffold(state: com.focustask.UiState, vm: MainViewModel, startTab: Tab = Tab.TODAY) {
    val dark = isSystemInDarkTheme()
    val focus = state.focus
    val settings = state.data.settings

    var tab by rememberSaveable { mutableStateOf(startTab) }
    var openListId by rememberSaveable { mutableStateOf<Long?>(null) }
    var soundsOpen by rememberSaveable { mutableStateOf(false) }
    var blockTarget by remember { mutableStateOf<BlockTarget?>(null) }
    var lockDialog by remember { mutableStateOf(false) }

    val locked = focus.locked
    LaunchedEffect(locked) {
        if (locked) { tab = Tab.TODAY; openListId = null; soundsOpen = false; blockTarget = null }
    }

    val overdue = focus.mode == Mode.FOCUS && focus.carousel?.current?.reason == Reason.OVERDUE
    val onToday = tab == Tab.TODAY && blockTarget == null
    val screenTone = when {
        !onToday -> Tone.FOCUS
        focus.mode == Mode.BREAK -> Tone.BREAK
        overdue -> Tone.OVERDUE
        else -> Tone.FOCUS
    }
    val bp = paletteFor(screenTone, dark)
    val barPalette = paletteFor(barTone(focus, overdue && onToday), dark)
    val bigBar = onToday
    val navPalette = bp

    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !bp.dark
    }

    BackHandler(enabled = blockTarget != null) { blockTarget = null }
    BackHandler(enabled = blockTarget == null && soundsOpen) { soundsOpen = false }
    BackHandler(enabled = blockTarget == null && !soundsOpen && tab == Tab.LISTS && openListId != null) { openListId = null }
    BackHandler(enabled = blockTarget == null && !soundsOpen && !(tab == Tab.LISTS && openListId != null) && tab != Tab.TODAY) {
        tab = Tab.TODAY
    }

    Box(Modifier.fillMaxSize().background(bp.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
                val target = blockTarget
                if (target != null) {
                    BlockEditorScreen(state, vm, target, bp) { blockTarget = null }
                } else when (tab) {
                    Tab.TODAY -> TodayScreen(state, vm, bp)
                    Tab.DAY -> DayScreen(state, vm, bp, onAddBlock = { blockTarget = BlockTarget.New })
                    Tab.WEEK -> WeekScreen(state, vm, bp,
                        onAddBlock = { blockTarget = BlockTarget.New },
                        onEditBlock = { blockTarget = BlockTarget.Edit(it) })
                    Tab.LISTS -> {
                        val id = openListId
                        if (id == null) ListsScreen(state, vm, bp) { openListId = it }
                        else ListEditScreen(state, vm, id, bp, onBack = { openListId = null })
                    }
                    Tab.SETTINGS ->
                        if (soundsOpen) AlertSoundsScreen(state, vm, bp) { soundsOpen = false }
                        else SettingsScreen(state, vm, bp) { soundsOpen = true }
                }
            }
            TimerBarView(
                focus, barPalette, big = bigBar, alertMode = settings.alertMode,
                onAlertMode = { vm.toggleAlertMode(settings.alertMode) },
            )
            if (blockTarget == null) {
                BottomNav(tab, locked, navPalette) { t ->
                    if (locked && t != Tab.TODAY) {
                        if (!settings.lockWarningDismissed) lockDialog = true
                    } else {
                        tab = t
                        if (t != Tab.LISTS) openListId = null
                        if (t != Tab.SETTINGS) soundsOpen = false
                    }
                }
            }
        }
    }

    if (lockDialog) LockDialog(
        nextName = focus.next?.block?.name,
        endsAt = focus.longBreak?.until?.let { com.focustask.Fmt.time(it) } ?: "",
        onClose = { dontShow ->
            lockDialog = false
            if (dontShow) vm.settings { setLockWarningDismissed(true) }
        },
    )
}

@Composable
private fun LockDialog(nextName: String?, endsAt: String, onClose: (Boolean) -> Unit) {
    var dont by remember { mutableStateOf(false) }
    val p = paletteFor(Tone.BREAK, isSystemInDarkTheme())
    AlertDialog(
        onDismissRequest = { onClose(dont) },
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(52.dp).clip(CircleShape).background(p.pill), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = p.accent)
            }
        },
        title = { Text("Nothing changes during a break", fontWeight = FontWeight.Bold, color = p.ink) },
        text = {
            Column {
                Text(
                    "Editing opens again at $endsAt" + (nextName?.let { ", when $it starts" } ?: "") + ". Rest now, edit after.",
                    color = p.muted, fontSize = 16.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dont = !dont }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (dont) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                        tint = if (dont) p.accent else p.muted, modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Don't show this warning again", color = p.ink, fontSize = 15.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onClose(dont) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Back to my break", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        },
    )
}
