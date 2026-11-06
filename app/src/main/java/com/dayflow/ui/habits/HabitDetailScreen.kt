package com.dayflow.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.Days
import com.dayflow.domain.HabitDayItem
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.Rate
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.HabitBadge
import com.dayflow.ui.components.HabitIcons
import com.dayflow.ui.components.HabitLogDialog
import com.dayflow.ui.components.LegendItem
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.StatTile
import com.dayflow.ui.components.Swatch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: HabitDetailViewModel = viewModel(factory = HabitDetailViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf<Pair<HabitDayItem, LocalDate>?>(null) }
    val hw = state.habit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(hw?.habit?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { if (hw != null) IconButton(onClick = { onEdit(vm.habitId) }) { Icon(Icons.Filled.Edit, "Edit habit") } },
            )
        },
    ) { padding ->
        if (hw == null) {
            if (!state.loading) EmptyState("Habit not found", "It may have been deleted.", Modifier.padding(padding))
            return@Scaffold
        }
        val h = hw.habit
        val color = Color(h.color)
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBadge(HabitIcons.get(h.icon), color, 1f, done = true, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(Days.describe(h.daysMask, state.settings.weekStart) + " · " + h.section.label, style = MaterialTheme.typography.bodyMedium)
                    if (h.goalType != HabitGoalType.CHECK) {
                        Text(
                            "Target ${Fmt.amount(h.target, h.goalType, h.unit)}" +
                                (h.minimumTarget?.let { " · minimum ${Fmt.amount(it, h.goalType, h.unit)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (h.reminderMinutes != null) {
                        Text("Reminder at ${Fmt.time(h.reminderMinutes, state.settings.use24h)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Current streak", "${state.currentStreak}", Modifier.weight(1f), caption = "scheduled days")
                StatTile("Best streak", "${state.bestStreak}", Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Last 30 days", rateText(state.last30), Modifier.weight(1f), caption = "${state.last30.done} of ${state.last30.eligible} days")
                StatTile(
                    when (h.goalType) {
                        HabitGoalType.CHECK -> "All time"
                        HabitGoalType.DURATION -> "Total time"
                        HabitGoalType.QUANTITY -> "Total"
                    },
                    when (h.goalType) {
                        HabitGoalType.CHECK -> rateText(state.allTime)
                        HabitGoalType.DURATION -> if (state.total >= 60) "${Fmt.number(Math.round(state.total / 6.0) / 10.0)} h" else "${Fmt.number(state.total)} min"
                        HabitGoalType.QUANTITY -> Fmt.amount(state.total, h.goalType, h.unit)
                    },
                    Modifier.weight(1f),
                    caption = if (h.goalType == HabitGoalType.CHECK) "${state.allTime.done} days done" else "all recorded",
                )
            }

            SectionHeader("History") {
                IconButton(onClick = vm::previousMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text(state.month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = vm::nextMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            Row(Modifier.fillMaxWidth()) {
                Days.orderedWeek(state.settings.weekStart).forEach {
                    Text(
                        it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    week.forEach { cell ->
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clickable(enabled = cell.item != null) { logging = cell.item!! to cell.date },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cell.inMonth) {
                                OutcomeDot(cell.outcome, color)
                                Text(
                                    "${cell.date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (cell.date == state.today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (cell.outcome == com.dayflow.domain.DayOutcome.DONE) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem("Done") { Swatch(color) }
                LegendItem("Missed") { Swatch(MaterialTheme.colorScheme.error, outlined = true) }
                LegendItem("Skipped/rest") { Swatch(MaterialTheme.colorScheme.surfaceContainerHighest) }
            }
            Text(
                "Tap a past day to correct its record.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionHeader("Schedule history")
            hw.schedules.sortedByDescending { it.effectiveFrom }.forEach { v ->
                val since = LocalDate.ofEpochDay(v.effectiveFrom)
                val what = if (v.daysMask == 0) "Paused" else buildString {
                    append(Days.describe(v.daysMask, state.settings.weekStart))
                    if (h.goalType != HabitGoalType.CHECK) {
                        append(" · ${Fmt.amount(v.target, h.goalType, h.unit)}")
                        v.minimumTarget?.let { append(" (min ${Fmt.number(it)})") }
                    }
                }
                Text("From ${Fmt.shortDate(since)}: $what", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    logging?.let { (item, date) ->
        HabitLogDialog(
            item = item,
            date = date,
            onDismiss = { logging = null },
            onSetValue = { vm.setHabitValue(item.habit.id, date, it) },
            onSkip = { vm.setSkipped(item.habit.id, date, it) },
        )
    }
}

private fun rateText(r: Rate) = if (r.eligible == 0) "–" else Fmt.percent(r.fraction)
