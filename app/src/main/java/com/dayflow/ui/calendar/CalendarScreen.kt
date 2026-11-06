package com.dayflow.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.Days
import com.dayflow.domain.HabitDayItem
import com.dayflow.domain.HabitGoalType
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.HabitLogDialog
import com.dayflow.ui.components.LegendItem
import com.dayflow.ui.components.PlanActions
import com.dayflow.ui.components.Swatch
import com.dayflow.ui.components.dayPlanItems
import com.dayflow.ui.cycle.CycleColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onAddTask: (LocalDate) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenFocus: () -> Unit,
    vm: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf<HabitDayItem?>(null) }
    val actions = remember(vm) {
        PlanActions(
            onToggleTask = vm::toggleTask,
            onOpenTask = { onOpenTask(it.id) },
            onReschedule = vm::reschedule,
            onCancelTask = vm::cancelTask,
            onQuickHabit = { item, date ->
                if (item.habit.goalType != HabitGoalType.CHECK && item.value > 0) logging = item else vm.quickHabit(item, date)
            },
            onLogHabit = { logging = it },
            onFocusTask = { vm.focusOn(it); onOpenFocus() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Fmt.month(state.selected)) },
                actions = {
                    IconButton(onClick = vm::previous) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
                    IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
                    IconButton(onClick = vm::jumpToToday) { Icon(Icons.Filled.Today, "Jump to today") }
                },
            )
        },
    ) { padding ->
        val plan = state.plan
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item(key = "mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CalendarMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = state.mode == m,
                            onClick = { vm.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size),
                        ) { Text(if (m == CalendarMode.WEEK) "Week" else "Month") }
                    }
                }
            }
            item(key = "grid") { CalendarGrid(state, vm::select) }
            item(key = "legend") {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem("More done") { Swatch(MaterialTheme.colorScheme.primary) }
                    LegendItem("Less done") { Swatch(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) }
                    LegendItem("Rest day") { Swatch(MaterialTheme.colorScheme.outline, outlined = true) }
                    if (state.settings.cycleTracking && state.settings.cycleHints) LegendItem("Period") { Swatch(CycleColors.Period) }
                }
            }
            item(key = "day-header") {
                val stats = plan.stats
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.relative(plan.date, state.today).let { if (it == Fmt.shortDate(plan.date)) it else "$it · ${Fmt.shortDate(plan.date)}" },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                stats.isEmpty -> if (plan.isRestDay) "Rest day" else "Nothing planned"
                                plan.date > state.today -> "${stats.planned} planned"
                                else -> "${stats.completed}/${stats.planned} done · ${Fmt.percent(stats.fraction)}" + if (plan.isRestDay) " · rest day" else ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { vm.setRestDay(plan.date, !plan.isRestDay) }) {
                        Text(if (plan.isRestDay) "Undo rest" else "Rest day")
                    }
                    OutlinedButton(onClick = { onAddTask(plan.date) }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text("Task")
                    }
                }
            }
            dayPlanItems(plan, state.today, state.settings.use24h, if (plan.date == state.today) state.streaks else emptyMap(), actions)
            if (plan.tasks.isEmpty() && plan.habits.isEmpty()) {
                item(key = "empty") { EmptyState("A free day", "Add a one-time task to plan something for this date.") }
            }
        }
    }

    logging?.let { item ->
        HabitLogDialog(
            item = item,
            date = state.plan.date,
            onDismiss = { logging = null },
            onSetValue = { vm.setHabitValue(item.habit.id, state.plan.date, it) },
            onSkip = { vm.setSkipped(item.habit.id, state.plan.date, it) },
        )
    }
}

@Composable
private fun CalendarGrid(state: CalendarState, onSelect: (LocalDate) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Days.orderedWeek(state.settings.weekStart).forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        state.cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val past = cell.date <= state.today
                    val stats = cell.stats
                    val selected = cell.date == state.selected
                    val isToday = cell.date == state.today
                    val fill = when {
                        past && !stats.isEmpty -> primary.copy(alpha = 0.12f + 0.68f * stats.fraction)
                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    }
                    val label = buildString {
                        append(Fmt.shortDate(cell.date))
                        if (!stats.isEmpty) append(if (past) ", ${Fmt.percent(stats.fraction)} done" else ", ${stats.planned} planned")
                        if (cell.isRestDay) append(", rest day")
                        if (cell.cycleKind == CycleDayKind.PERIOD) append(", period")
                        if (cell.cycleKind == CycleDayKind.PREDICTED_PERIOD) append(", predicted period")
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (state.mode == CalendarMode.WEEK) 0.8f else 1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(fill)
                            .then(
                                when {
                                    selected -> Modifier.border(2.dp, primary, RoundedCornerShape(10.dp))
                                    cell.isRestDay -> Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                    else -> Modifier
                                },
                            )
                            .clickable { onSelect(cell.date) }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell.cycleKind != CycleDayKind.NONE && cell.inRange) {
                            val dot = Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape)
                            Box(
                                if (cell.cycleKind == CycleDayKind.PERIOD) dot.background(CycleColors.Period)
                                else dot.border(1.5.dp, CycleColors.Period, CircleShape),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val textColor = if (stats.fraction > 0.6f && past) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Text(
                                cell.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    !cell.inRange -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    isToday && stats.fraction <= 0.6f -> primary
                                    else -> textColor
                                },
                            )
                            when {
                                past && !stats.isEmpty && cell.inRange -> Text(
                                    "${(stats.fraction * 100).roundToInt()}%",
                                    fontSize = 9.sp,
                                    color = textColor.copy(alpha = 0.85f),
                                )
                                !past && !stats.isEmpty && cell.inRange -> Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(primary))
                                else -> Spacer(Modifier.size(5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
