package com.dayflow.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.R
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.domain.DayStats
import com.dayflow.domain.HabitDayItem
import com.dayflow.domain.HabitGoalType
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.HabitLogDialog
import com.dayflow.ui.components.PhotoImage
import com.dayflow.ui.components.PlanActions
import com.dayflow.ui.components.ProgressRing
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.TaskRow
import com.dayflow.ui.components.dayPlanItems
import java.io.File
import java.time.LocalDate

@Composable
fun TodayScreen(
    onAddTask: (LocalDate) -> Unit,
    onAddHabit: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenReflection: (LocalDate) -> Unit,
    onOpenReflections: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenCycle: () -> Unit,
    onOpenPrayerHistory: () -> Unit,
    hidePrivate: Boolean = false,
    vm: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prayers by vm.prayers.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshDate()
        onPauseOrDispose { }
    }
    var logging by remember { mutableStateOf<HabitDayItem?>(null) }
    val today = state.today
    val actions = remember(vm) {
        PlanActions(
            onToggleTask = vm::toggleTask,
            onOpenTask = { onOpenTask(it.id) },
            onReschedule = vm::reschedule,
            onCancelTask = vm::cancelTask,
            onQuickHabit = { item, date ->
                // Don't wipe a recorded amount with one tap; open the editor instead.
                if (item.habit.goalType != HabitGoalType.CHECK && item.value > 0) logging = item else vm.quickHabit(item, date)
            },
            onLogHabit = { logging = it },
            onFocusTask = { vm.focusOn(it); onOpenFocus() },
        )
    }

    Scaffold(
        floatingActionButton = { AddButton(onAddTask = { onAddTask(today) }, onAddHabit = onAddHabit) },
    ) { padding ->
        val plan = state.plan
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item(key = "header") {
                Header(
                    date = today,
                    greeting = Fmt.greeting(state.settings.name),
                    isRestDay = plan.isRestDay,
                    onToggleRest = { vm.setRestDay(today, !plan.isRestDay) },
                    onOpenSettings = onOpenSettings,
                )
            }
            item(key = "progress") { ProgressCard(plan.stats, plan.isRestDay) }
            prayers?.let { p ->
                item(key = "prayers") { PrayerCard(p, onPreviousDay = vm::prayerPreviousDay, onNextDay = vm::prayerNextDay, onSet = vm::setPrayer, onOpenHistory = onOpenPrayerHistory) }
            }
            state.cycle?.takeUnless { hidePrivate }?.let { cycle ->
                item(key = "cycle") { CycleCard(cycle, onOpenCycle) }
            }
            item(key = "reflection") {
                ReflectionCard(state.reflection?.takeUnless { hidePrivate }, vm::photoFile, onOpen = { onOpenReflection(today) }, onSeeAll = onOpenReflections)
            }

            if (plan.focusTasks.isNotEmpty()) {
                item(key = "focus-header") { SectionHeader("Your priorities") }
                items(plan.focusTasks, key = { "focus-${it.id}" }) { TaskRow(it, state.settings.use24h, today, actions) }
            }
            if (state.overdue.isNotEmpty()) {
                item(key = "overdue-header") { SectionHeader("Unfinished from earlier · ${state.overdue.size}") }
                item(key = "overdue-hint") {
                    Text(
                        "Use ⋮ to move each one to a day that fits. Nothing is moved automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.overdue, key = { "overdue-${it.id}" }) { TaskRow(it, state.settings.use24h, today, actions, showDate = true) }
            }
            dayPlanItems(plan, today, state.settings.use24h, state.streaks, actions, skipFocus = true)

            if (!state.loading && plan.tasks.isEmpty() && plan.habits.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "Nothing planned yet",
                        "Add a task for today or create a habit. Star up to three tasks to make them today's priorities.",
                    )
                }
            }
        }
    }

    logging?.let { item ->
        HabitLogDialog(
            item = item,
            date = today,
            onDismiss = { logging = null },
            onSetValue = { vm.setHabitValue(item.habit.id, today, it) },
            onSkip = { vm.setSkipped(item.habit.id, today, it) },
        )
    }
}

@Composable
private fun Header(date: LocalDate, greeting: String, isRestDay: Boolean, onToggleRest: () -> Unit, onOpenSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 8.dp)) {
            Text(Fmt.dayHeader(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (isRestDay) "Rest day. Habits aren't expected today." else "A little progress goes a long way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Day options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isRestDay) "Undo rest day" else "Make today a rest day") },
                    onClick = { menu = false; onToggleRest() },
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(stats: DayStats, isRestDay: Boolean) {
    // Ease the ring/bar to the current value so completing something feels responsive.
    val animated by animateFloatAsState(targetValue = stats.fraction, animationSpec = tween(600), label = "dailyProgress")
    Card(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(animated, size = 76.dp, track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                Text(
                    if (stats.isEmpty) "–" else Fmt.percent(animated),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Daily progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "${stats.completed}/${stats.planned}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    when {
                        stats.isEmpty && isRestDay -> "Enjoy your rest day"
                        stats.isEmpty -> "No planned activities"
                        stats.completed == stats.planned -> "Everything planned is done"
                        else -> "${Fmt.percent(stats.fraction)} of planned activities completed"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun CycleCard(cycle: CycleSummary, onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cycle.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(cycle.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReflectionCard(reflection: ReflectionEntity?, photoFile: (String) -> File, onOpen: () -> Unit, onSeeAll: () -> Unit) {
    SectionHeader("Morning reflection") {
        TextButton(onClick = onSeeAll) { Text("See all") }
    }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (reflection == null) {
            Image(
                painterResource(R.drawable.illustration_sunrise),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = reflection?.photoPath
            when {
                photo != null -> PhotoImage(
                    photoFile(photo), contentDescription = null, maxSide = 300,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
                reflection != null -> Text(reflection.mood?.emoji ?: "☀️", style = MaterialTheme.typography.headlineMedium)
            }
            if (reflection != null) Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (reflection == null) {
                    Text("Let's start your day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "How do you feel, and what's your main intention today?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        listOfNotNull(reflection.mood?.let { "${it.emoji} ${it.label}" }, reflection.energy?.let { "Energy $it/5" })
                            .joinToString(" · ").ifEmpty { "Reflection saved" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (reflection.intention.isNotBlank()) {
                        Text("Intention: ${reflection.intention}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
            Icon(if (reflection == null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AddButton(onAddTask: () -> Unit, onAddHabit: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { menu = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Add") },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Task") }, onClick = { menu = false; onAddTask() })
            DropdownMenuItem(text = { Text("Habit") }, onClick = { menu = false; onAddHabit() })
        }
    }
}
