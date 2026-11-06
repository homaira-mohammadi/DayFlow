package com.dayflow.ui.focus

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.FocusMode
import com.dayflow.domain.TimerStatus
import com.dayflow.ui.components.ProgressRing

fun formatClock(ms: Long): String {
    val total = (ms + 999) / 1000 // round up so the display shows 25:00 at the start, not 24:59
    return "%02d:%02d".format(total / 60, total % 60)
}

fun formatMinutes(min: Int): String = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"

private fun FocusMode.icon(): ImageVector = when (this) {
    FocusMode.FOCUS -> Icons.Filled.Psychology
    FocusMode.SHORT_BREAK -> Icons.Filled.LocalCafe
    FocusMode.LONG_BREAK -> Icons.Filled.HourglassBottom
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onOpenHistory: () -> Unit,
    vm: FocusViewModel = viewModel(factory = FocusViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val exactAllowed by vm.exactAlarmsAllowed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingTarget by remember { mutableStateOf(false) }
    var editingDurations by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        vm.refreshPermissions()
        onPauseOrDispose { }
    }
    val timer = state.timer
    val running = timer.status == TimerStatus.RUNNING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus") },
                actions = { IconButton(onClick = { editingDurations = true }) { Icon(Icons.Filled.Tune, "Timer lengths") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(
                        fraction = state.progress,
                        size = 250.dp,
                        stroke = 12.dp,
                        track = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(timer.mode.icon(), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(timer.mode.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatClock(state.remainingMs),
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Text(
                                when (timer.status) {
                                    TimerStatus.PAUSED -> "Paused"
                                    else -> timer.mode.subtitle
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    AssistChip(
                        onClick = { pickingTarget = true },
                        label = {
                            Text(
                                timer.linkLabel?.let { "Focusing on: $it" } ?: "Choose a task or habit",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Link, null, Modifier.size(18.dp)) },
                        modifier = Modifier.padding(top = 12.dp),
                    )

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = vm::startOrPause, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (timer.status) {
                                    TimerStatus.RUNNING -> "Pause"
                                    TimerStatus.PAUSED -> "Resume"
                                    TimerStatus.IDLE -> "Start"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        OutlinedButton(
                            onClick = vm::reset,
                            enabled = timer.status != TimerStatus.IDLE,
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Filled.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusMode.entries.forEach { m ->
                            ModeCard(m, selected = timer.mode == m, modifier = Modifier.weight(1f)) { vm.selectMode(m) }
                        }
                    }
                    Text(
                        if (timer.mode == FocusMode.FOCUS) {
                            "Session ${timer.completedInCycle + 1} of ${timer.longBreakEvery} · then a long break"
                        } else {
                            "${timer.completedInCycle} of ${timer.longBreakEvery} sessions done this round"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Alarm, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Allow alarms so the timer alerts you right on time, even when Dayflow is closed.",
                            Modifier.weight(1f).padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        }) { Text("Allow") }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Focus Radar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Live stats update as you focus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = onOpenHistory,
                    label = { Text("History") },
                    leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) },
                )
            }
            val elapsedMin = (timer.elapsedMs(state.now) / 60_000).toInt()
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RadarTile(Icons.Filled.Timelapse, "Session progress", "${(state.progress * 100).toInt()}%", "$elapsedMin min in", Modifier.weight(1f))
                RadarTile(
                    Icons.Filled.ErrorOutline, "Interruptions", "${timer.interruptions}",
                    when {
                        timer.status == TimerStatus.IDLE -> "Idle"
                        timer.interruptions == 0 -> "Uninterrupted"
                        else -> "Paused ${timer.interruptions}×"
                    },
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RadarTile(Icons.Filled.Today, "Today", formatMinutes(state.todayMinutes), "${state.todaySessions} sessions completed", Modifier.weight(1f))
                RadarTile(Icons.Filled.CalendarViewWeek, "This week", formatMinutes(state.weekMinutes), "of focused time", Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickingTarget) {
        TargetDialog(state.targets, timer.linkTaskId, timer.linkHabitId, onDismiss = { pickingTarget = false }, onPick = vm::link)
    }
    if (editingDurations) {
        DurationsDialog(timer.focusMinutes, timer.shortBreakMinutes, timer.longBreakMinutes, timer.longBreakEvery, onDismiss = { editingDurations = false }) { f, s, l, e ->
            vm.setDurations(f, s, l, e)
        }
    }
}

@Composable
private fun ModeCard(mode: FocusMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                mode.icon(), null, Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                mode.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun RadarTile(icon: ImageVector, label: String, value: String, caption: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(14.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                Icon(icon, null, Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TargetDialog(targets: List<FocusTarget>, taskId: Long?, habitId: Long?, onDismiss: () -> Unit, onPick: (FocusTarget?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What are you focusing on?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TargetRow("Nothing specific", null, selected = taskId == null && habitId == null) { onPick(null); onDismiss() }
                if (targets.isEmpty()) {
                    Text("Nothing left on today's plan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                targets.forEach { t ->
                    TargetRow(
                        t.label,
                        when {
                            t.habitId == null -> "Task"
                            t.addsMinutes -> "Habit · focus minutes count toward it"
                            else -> "Habit"
                        },
                        selected = (t.taskId != null && t.taskId == taskId) || (t.habitId != null && t.habitId == habitId),
                    ) { onPick(t); onDismiss() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TargetRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DurationsDialog(focus: Int, short: Int, long: Int, every: Int, onDismiss: () -> Unit, onSave: (Int, Int, Int, Int) -> Unit) {
    var f by remember { mutableIntStateOf(focus) }
    var s by remember { mutableIntStateOf(short) }
    var l by remember { mutableIntStateOf(long) }
    var e by remember { mutableIntStateOf(every) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer lengths") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Stepper("Focus", "$f min", { f = (f - 5).coerceAtLeast(5) }, { f = (f + 5).coerceAtMost(120) })
                Stepper("Short break", "$s min", { s = (s - 1).coerceAtLeast(1) }, { s = (s + 1).coerceAtMost(30) })
                Stepper("Long break", "$l min", { l = (l - 5).coerceAtLeast(5) }, { l = (l + 5).coerceAtMost(60) })
                Stepper("Long break after", "$e sessions", { e = (e - 1).coerceAtLeast(2) }, { e = (e + 1).coerceAtMost(8) })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(f, s, l, e); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = "Decrease $label" }) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(value, Modifier.width(92.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        TextButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = "Increase $label" }) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
