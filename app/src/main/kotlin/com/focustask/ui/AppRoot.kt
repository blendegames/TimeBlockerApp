package com.focustask.ui

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import com.focustask.ui.components.barOnly
import com.focustask.ui.components.LocalTimerAnchors
import com.focustask.ui.components.TimerAnchors
import com.focustask.ui.components.SideNav
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focustask.Fmt
import com.focustask.MainViewModel
import com.focustask.UiState
import com.focustask.core.BarKind
import com.focustask.core.IdleView
import com.focustask.core.Mode
import com.focustask.core.Reason
import com.focustask.ui.components.BottomNav
import com.focustask.ui.components.IdleTimerView
import com.focustask.ui.components.Tab
import com.focustask.ui.components.TimerBarView
import com.focustask.ui.components.barTone
import com.focustask.ui.screens.AlertSoundsScreen
import com.focustask.ui.screens.BlockEditorScreen
import com.focustask.ui.screens.DayScreen
import com.focustask.ui.screens.InfoButton
import com.focustask.ui.screens.ListsScreen
import com.focustask.ui.screens.SettingsScreen
import com.focustask.ui.screens.SettingsSection
import com.focustask.ui.screens.TabInfo
import com.focustask.ui.screens.TodayScreen
import com.focustask.ui.screens.WeekScreen
import com.focustask.ui.theme.Tone
import com.focustask.ui.theme.palette
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which block the editor shows: a new one, or an existing one. */
sealed interface BlockTarget {
    /**
     * [fromDay]: added from the Day tab, so it also appears today.
     * [start]/[day] come from tapping an empty spot of a grid: the editor opens with that time and weekday.
     */
    data class New(
        val fromDay: Boolean,
        val start: LocalTime? = null,
        val end: LocalTime? = null,
        val day: DayOfWeek? = null,
        val listId: Long? = null,
    ) : BlockTarget
    data class Edit(val blockId: Long) : BlockTarget
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state = ui
    if (state == null) {
        Box(Modifier.fillMaxSize().background(palette(Tone.FOCUS, isSystemInDarkTheme()).bg))
        return
    }
    AppScaffold(state, vm)
}

private const val IDLE_TICK_MS = 250L
private const val IDLE_WAKE_MS = 200

/** The whole app for one state. Split out from [AppRoot] so it can be rendered with any state. */
@Composable
fun AppScaffold(
    state: UiState, vm: MainViewModel, startTab: Tab = Tab.TODAY, startSection: SettingsSection? = null,
) {
    val dark = isSystemInDarkTheme()
    val focus = state.focus
    val settings = state.data.settings

    // "Keep screen on": the window stays awake only while the app is in the foreground
    val rootView = LocalView.current
    DisposableEffect(rootView, settings.keepScreenOn) {
        rootView.keepScreenOn = settings.keepScreenOn
        onDispose { rootView.keepScreenOn = false }
    }

    // While nobody touches the app: first it fades to a black screen with the focus timer, then the screen dims. Any touch,
    // and every change between work and break (or alert), brings everything back at once.
    val appContext = rootView.context
    val dimmer = remember(rootView) { appContext.findActivity()?.window?.let { w -> IdleDimmer(w, { phoneBrightness(appContext) }) } }
    val idleMs = remember { mutableLongStateOf(0L) }
    val touch: () -> Unit = remember(dimmer) { { idleMs.longValue = 0L; dimmer?.wake() } }
    LaunchedEffect(Unit) { while (true) { delay(IDLE_TICK_MS); idleMs.longValue += IDLE_TICK_MS } }
    LaunchedEffect(dimmer, settings.keepScreenOn, settings.dimWhenIdle, settings.dimPercent, settings.dimAfterSec,
        settings.timerViewWhenIdle, settings.timerViewAfterSec) {
        if (dimmer == null || !settings.keepScreenOn || !settings.dimWhenIdle) return@LaunchedEffect
        dimmer.percent = settings.dimPercent
        dimmer.startMs = IdleView.dimStartMs(settings.timerViewWhenIdle, settings.timerViewAfterSec, settings.dimAfterSec)
        dimmer.wake()
        try { while (true) { dimmer.tick(); delay(500) } } finally { dimmer.release() }
    }
    LaunchedEffect(focus.mode, settings.lastAlertAt) { touch() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { touch() }
    val timerStart = IdleView.timerStartMs(settings.timerViewWhenIdle, settings.timerViewAfterSec)
    val timerWanted by remember(timerStart, focus.bar.kind) {
        derivedStateOf { timerStart != null && focus.bar.kind != BarKind.IDLE && idleMs.longValue >= timerStart }
    }
    // The full-screen timer drops down over the app: by itself when nobody touches it, or pulled down from the timer bar.
    // drop is how far down it is (0..1); while a finger moves it, the finger decides.
    var pulled by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val drop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Once it has dropped down by itself it stays: a touch only brings the brightness back. It goes away when it is
    // pushed back up, or with Back.
    LaunchedEffect(timerWanted) { if (timerWanted) pulled = true }
    val dropDown = pulled
    val anchors = remember { TimerAnchors() }
    anchors.progress = { drop.value }
    LaunchedEffect(dropDown, dragging) {
        if (dragging) return@LaunchedEffect
        drop.animateTo(
            if (dropDown) 1f else 0f,
            tween(if (dropDown) IdleView.DROP_MS.toInt() else IDLE_WAKE_MS, easing = FastOutSlowInEasing),
        )
    }
    val timerProgress = drop.value
    var screenPx by remember { mutableFloatStateOf(1f) }
    // how far the finger has moved in this drag, down positive: a flick only counts in the direction the finger went
    var dragNet by remember { mutableFloatStateOf(0f) }
    fun dragBy(dy: Float) {
        if (!dragging) dragNet = 0f
        dragging = true; dragNet += dy
        scope.launch { drop.snapTo((drop.value + dy / screenPx).coerceIn(0f, 1f)) }
    }
    /** The finger let go: the timer drops all the way down or goes back up, whichever way it was going. */
    fun letGo(velocity: Float) {
        val fast = velocity / screenPx   // screens per second
        pulled = when {
            fast > 1.2f && dragNet > 0f -> true
            fast < -1.2f && dragNet < 0f -> false
            else -> drop.value > 0.35f
        }
        touch()
        dragging = false
    }

    // The clock ticks every second, but only the timer needs that: the screens get a state that changes when the data
    // or the minute changes, so they are not redrawn every second (a long break's countdown on Today still is).
    val minute = state.now.withSecond(0).withNano(0)
    val calm = remember(state.data, minute, focus.mode) { state }
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var openListId by rememberSaveable { mutableStateOf<Long?>(null) }
    var soundsOpen by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(startSection) }
    var blockTarget by remember { mutableStateOf<BlockTarget?>(null) }
    var lockDialog by remember { mutableStateOf(false) }

    val locked = focus.locked
    LaunchedEffect(locked) {
        if (locked) { tab = Tab.TODAY; openListId = null; soundsOpen = false; section = null; blockTarget = null }
    }

    val onToday = tab == Tab.TODAY && blockTarget == null
    val screenTone = when {
        !onToday -> Tone.FOCUS
        focus.mode == Mode.BREAK -> Tone.BREAK
        else -> Tone.FOCUS
    }
    val bp = palette(screenTone, dark)
    val barPalette = palette(barTone(focus), dark)
    // The timer sits under the status bar. With the keyboard open it gets compact and the tab bar steps aside.
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val conf = LocalConfiguration.current
    // window size classes: from 600 dp wide the window is "medium" or wider; under 480 dp high it is a phone on its side
    val wide = conf.screenWidthDp >= 600
    val lowWindow = conf.screenHeightDp < 480
    val bigBar = onToday && !imeUp
    val navPalette = bp

    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        // the status bar sits on the timer bar, or on black once the idle timer view is up
        WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = timerProgress < 0.5f && !barPalette.dark
    }

    BackHandler(enabled = pulled) { pulled = false }
    BackHandler(enabled = blockTarget != null) { blockTarget = null }
    BackHandler(enabled = blockTarget == null && soundsOpen) { soundsOpen = false }
    BackHandler(enabled = blockTarget == null && !soundsOpen && tab == Tab.LISTS && openListId != null) { openListId = null }
    BackHandler(enabled = blockTarget == null && !soundsOpen && tab == Tab.SETTINGS && section != null) { section = null }
    BackHandler(enabled = blockTarget == null && !soundsOpen && !(tab == Tab.LISTS && openListId != null) &&
        !(tab == Tab.SETTINGS && section != null) && tab != Tab.TODAY) {
        tab = Tab.TODAY
    }

    CompositionLocalProvider(LocalTimerAnchors provides anchors) {
    Box(
        Modifier.fillMaxSize().background(bp.bg).onSizeChanged { screenPx = it.height.toFloat().coerceAtLeast(1f) }.pointerInput(touch) {
            // watch every touch without taking it from the screen underneath
            awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); touch() } }
        },
    ) {
        // On a wide window (landscape, tablet, unfolded) the tabs move to a rail on the left, and on Today the timer
        // stands beside the task wheel instead of above it. A low window (a phone on its side) gets the small timer bar.
        val rail = wide && blockTarget == null && !imeUp
        val timerBeside = wide && onToday && !imeUp
        // the sides a camera cutout or a side navigation bar can be on; the rail takes care of the start side
        val sides = if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal
        val selectTab: (Tab) -> Unit = { t ->
            if (locked && t != Tab.TODAY) {
                if (!settings.lockWarningDismissed) lockDialog = true
            } else {
                tab = t
                if (t != Tab.LISTS) openListId = null
                if (t != Tab.SETTINGS) { soundsOpen = false; section = null }
            }
        }
        @Composable
        fun TimerBar(modifier: Modifier, big: Boolean, beside: Boolean) {
            Box(
                modifier.onGloballyPositioned { anchors.barBounds = it.boundsInRoot() }
                    .background(barPalette.bar).statusBarsPadding()
                    // the bar's color reaches into a camera cutout or a side navigation bar; its content stays clear of them
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(if (beside) WindowInsetsSides.Bottom else sides))
                    .testTag("timer-bar")
                    // the screen reader can't pull: the same as an action
                    .semantics { customActions = listOf(CustomAccessibilityAction("Show the full-screen timer") { pulled = true; true }) }
                    // pull the timer bar down: the full-screen timer drops down with the finger
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = { letGo(tracker.calculateVelocity().y) },
                            onDragCancel = { letGo(0f) },
                        ) { change, dy ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragBy(dy)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                TimerBarView(
                    focus, barPalette, big = big, alertMode = settings.alertMode,
                    onAlertMode = { vm.toggleAlertMode(settings.alertMode) }, stacked = beside,
                )
                // a small handle says it can be pulled down
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (beside) 16.dp else 4.dp).size(width = 36.dp, height = 4.dp)
                        .barOnly().clip(RoundedCornerShape(2.dp)).background(barPalette.muted.copy(alpha = 0.45f)),
                )
            }
        }
        Row(Modifier.fillMaxSize().imePadding()) {
            if (rail) SideNav(tab, locked, navPalette, selectTab)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!timerBeside) TimerBar(Modifier.fillMaxWidth(), big = bigBar && !lowWindow, beside = false)
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (timerBeside) TimerBar(Modifier.weight(0.42f).fillMaxHeight(), big = true, beside = true)
                    Box(
                        Modifier.weight(if (timerBeside) 0.58f else 1f).fillMaxHeight()
                            .then(if (timerBeside) Modifier.statusBarsPadding() else Modifier)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(sides))
                            // without the tab bar (block editor, or the rail on a wide window) the screen itself stays above the gesture bar
                            .then(if (blockTarget != null || wide) Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)) else Modifier),
                    ) {
                        val target = blockTarget
                        if (target != null) {
                            BlockEditorScreen(calm, vm, target, bp) { blockTarget = null }
                        } else when (tab) {
                            Tab.TODAY -> TodayScreen(if (focus.mode == Mode.BREAK) state else calm, vm, bp)
                            Tab.DAY -> DayScreen(calm, vm, bp)
                            Tab.WEEK -> WeekScreen(calm, vm, bp,
                                onMoreOptions = { blockTarget = it },
                                onEditBlock = { blockTarget = BlockTarget.Edit(it) })
                            Tab.LISTS -> {
                                ListsScreen(calm, vm, bp, openListId, twoPane = wide) { openListId = if (it == openListId) null else it }
                            }
                            Tab.SETTINGS ->
                                if (soundsOpen) AlertSoundsScreen(calm, vm, bp) { soundsOpen = false }
                                else SettingsScreen(calm, vm, bp, section, onSection = { section = it }) { soundsOpen = true }
                        }
                        // a small info button in the top right corner of each tab, except Settings and the pages inside a tab
                        val info = when {
                            target != null -> null
                            tab == Tab.TODAY -> "Today" to TabInfo.TODAY
                            tab == Tab.DAY -> "Day" to TabInfo.DAY
                            tab == Tab.WEEK -> "Week" to TabInfo.WEEK
                            tab == Tab.LISTS -> "Lists" to TabInfo.LISTS
                            else -> null
                        }
                        if (info != null) InfoButton(
                            info.first, info.second, bp, Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 12.dp),
                        )
                    }
                }
                if (!wide && blockTarget == null && !imeUp) BottomNav(tab, locked, navPalette, selectTab)
            }
        }
        IdleTimerView(
            focus, barTone(focus), anchors.progress,
            barPalette = barPalette, alertMode = settings.alertMode,
            onAlertMode = { touch(); vm.toggleAlertMode(settings.alertMode) },
            // a tap only wakes the screen; pushing it up sends it back
            onTap = touch,
            onDrag = ::dragBy,
            onDragEnd = ::letGo,
            onClose = { pulled = false; touch() },
        )
    }
    }

    if (lockDialog) LockDialog(
        nextName = focus.next?.block?.name,
        endsAt = focus.longBreak?.until?.let { Fmt.time(it) } ?: "",
        onClose = { dontShow ->
            lockDialog = false
            if (dontShow) vm.settings { setLockWarningDismissed(true) }
        },
    )
}

@Composable
private fun LockDialog(nextName: String?, endsAt: String, onClose: (Boolean) -> Unit) {
    var dont by remember { mutableStateOf(false) }
    val p = palette(Tone.BREAK, isSystemInDarkTheme())
    AlertDialog(
        onDismissRequest = { onClose(dont) },
        containerColor = p.surface,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(52.dp).clip(CircleShape).background(p.pill), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = p.accent)
            }
        },
        title = { Text("Nothing changes during a long break", fontWeight = FontWeight.Bold, color = p.ink) },
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
