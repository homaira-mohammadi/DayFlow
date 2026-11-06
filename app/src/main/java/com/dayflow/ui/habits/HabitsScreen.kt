package com.dayflow.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.DayOutcome
import com.dayflow.domain.Days
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.HabitStatus
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ConfirmDialog
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.HabitBadge
import com.dayflow.ui.components.HabitIcons
import com.dayflow.ui.components.SectionHeader
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    onAddHabit: () -> Unit,
    onOpenHabit: (Long) -> Unit,
    onEditHabit: (Long) -> Unit,
    vm: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HabitSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Habits") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddHabit, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New habit") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.loading && state.active.isEmpty() && state.paused.isEmpty() && state.archived.isEmpty()) {
                item { EmptyState("No habits yet", "Create a habit with a schedule, a target, and an optional minimum version for busy days.") }
            }
            items(state.active, key = { it.habit.habit.id }) { s ->
                HabitCard(s, state.settings.weekStart, onOpenHabit, onEditHabit, vm::setStatus) { deleting = s }
            }
            if (state.paused.isNotEmpty()) {
                item { SectionHeader("Paused") }
                items(state.paused, key = { it.habit.habit.id }) { s ->
                    HabitCard(s, state.settings.weekStart, onOpenHabit, onEditHabit, vm::setStatus) { deleting = s }
                }
            }
            if (state.archived.isNotEmpty()) {
                item {
                    SectionHeader("Archived · ${state.archived.size}") {
                        TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Hide" else "Show") }
                    }
                }
                if (showArchived) {
                    items(state.archived, key = { it.habit.habit.id }) { s ->
                        HabitCard(s, state.settings.weekStart, onOpenHabit, onEditHabit, vm::setStatus) { deleting = s }
                    }
                }
            }
        }
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete ${s.habit.habit.name}?",
            body = "This permanently removes the habit and all of its history. Archive it instead to keep your records.",
            confirm = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(s.habit.habit.id) },
        )
    }
}

@Composable
private fun HabitCard(
    s: HabitSummary,
    weekStart: DayOfWeek,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onStatus: (Long, HabitStatus) -> Unit,
    onDelete: () -> Unit,
) {
    val h = s.habit.habit
    val color = Color(h.color)
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable { onOpen(h.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 14.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitBadge(HabitIcons.get(h.icon), color, 1f, done = h.status == HabitStatus.ACTIVE)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    val target = when (h.goalType) {
                        HabitGoalType.CHECK -> null
                        else -> Fmt.amount(h.target, h.goalType, h.unit) + (h.minimumTarget?.let { " (min ${Fmt.number(it)})" } ?: "")
                    }
                    Text(
                        listOfNotNull(Days.describe(h.daysMask, weekStart), target, h.category.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for ${h.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(h.id) })
                        when (h.status) {
                            HabitStatus.ACTIVE -> {
                                DropdownMenuItem(text = { Text("Pause") }, onClick = { menu = false; onStatus(h.id, HabitStatus.PAUSED) })
                                DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onStatus(h.id, HabitStatus.ARCHIVED) })
                            }
                            HabitStatus.PAUSED -> {
                                DropdownMenuItem(text = { Text("Resume") }, onClick = { menu = false; onStatus(h.id, HabitStatus.ACTIVE) })
                                DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onStatus(h.id, HabitStatus.ARCHIVED) })
                            }
                            HabitStatus.ARCHIVED ->
                                DropdownMenuItem(text = { Text("Restore") }, onClick = { menu = false; onStatus(h.id, HabitStatus.ACTIVE) })
                        }
                        DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    " ${s.currentStreak} streak · best ${s.bestStreak} · ${if (s.last30.eligible == 0) "–" else Fmt.percent(s.last30.fraction)} last 30 days",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.padding(top = 10.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.lastWeek.forEach { (date, outcome) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutcomeDot(outcome, color, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OutcomeDot(outcome: DayOutcome, color: Color, modifier: Modifier = Modifier) {
    val label = when (outcome) {
        DayOutcome.DONE -> "done"
        DayOutcome.MISSED -> "missed"
        DayOutcome.EXCUSED -> "skipped or rest"
        DayOutcome.PENDING -> "pending"
        DayOutcome.NOT_SCHEDULED -> "not scheduled"
    }
    val base = modifier.size(18.dp).clip(CircleShape).semantics { contentDescription = label }
    when (outcome) {
        DayOutcome.DONE -> Box(base.background(color))
        DayOutcome.MISSED -> Box(base.border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f), CircleShape))
        DayOutcome.PENDING -> Box(base.border(1.5.dp, color.copy(alpha = 0.6f), CircleShape))
        DayOutcome.EXCUSED -> Box(base.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Text("–", style = MaterialTheme.typography.labelSmall)
        }
        DayOutcome.NOT_SCHEDULED -> Box(base.background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)))
    }
}
