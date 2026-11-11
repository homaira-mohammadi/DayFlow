package com.dayflow.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.data.db.TaskEntity
import com.dayflow.domain.GoalStatus
import com.dayflow.domain.TaskStatus
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ConfirmDialog
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.HabitBadge
import com.dayflow.ui.components.HabitIcons
import com.dayflow.ui.components.SectionHeader
import java.time.LocalDate

private enum class GoalDialog { LINK_TASKS, LINK_HABITS, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNewTask: (goalId: Long) -> Unit,
    onNewHabit: (goalId: Long) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenHabit: (Long) -> Unit,
    vm: GoalDetailViewModel = viewModel(factory = GoalDetailViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val summary = state.summary
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<GoalDialog?>(null) }
    var newMilestone by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goal") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (summary != null) {
                        IconButton(onClick = { onEdit(vm.goalId) }) { Icon(Icons.Filled.Edit, "Edit goal") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                GoalStatusMenuItems(summary.goal.status) { menu = false; vm.setStatus(it) }
                                DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; dialog = GoalDialog.DELETE })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (summary == null) {
            if (!state.loading) EmptyState("Goal not found", "It may have been deleted.", Modifier.padding(padding))
            return@Scaffold
        }
        val goal = summary.goal
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // The title can be long, so it gets the full width here instead of one line in the top bar.
            Text(goal.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
            if (goal.description.isNotBlank()) {
                Text(goal.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(12.dp))
            GoalProgressLine(summary)
            val steps = when {
                summary.progress.isEmpty -> "Add milestones or link tasks to measure progress."
                else -> "${summary.progress.done} of ${summary.progress.total} steps done"
            }
            listOfNotNull(steps, targetText(goal, summary.daysLeft)).forEach {
                Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (goal.status == GoalStatus.ACTIVE && summary.progress.isComplete) {
                Button(onClick = { vm.setStatus(GoalStatus.ACHIEVED) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Every step is done. Mark as achieved")
                }
            }

            SectionHeader("Milestones")
            state.milestones.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = m.done, onCheckedChange = { vm.setMilestoneDone(m, it) })
                    Text(
                        m.title,
                        Modifier.weight(1f),
                        textDecoration = if (m.done) TextDecoration.LineThrough else null,
                        color = if (m.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    )
                    IconButton(onClick = { vm.deleteMilestone(m.id) }) { Icon(Icons.Filled.Close, "Remove milestone ${m.title}") }
                }
            }
            val addMilestone = {
                vm.addMilestone(newMilestone)
                newMilestone = ""
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMilestone,
                    onValueChange = { newMilestone = it },
                    label = { Text("Add a milestone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addMilestone() }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = addMilestone, enabled = newMilestone.isNotBlank()) { Text("Add") }
            }

            SectionHeader("Tasks") {
                TextButton(onClick = { dialog = GoalDialog.LINK_TASKS }) { Text("Link") }
                TextButton(onClick = { onNewTask(vm.goalId) }) { Text("New") }
            }
            if (state.tasks.isEmpty()) Hint("One-off things to do for this goal. Link tasks you already have, or create a new one.")
            state.tasks.forEach { t -> TaskRow(t, vm::toggleTask, onOpenTask, vm::unlinkTask) }

            SectionHeader("Habits") {
                TextButton(onClick = { dialog = GoalDialog.LINK_HABITS }) { Text("Link") }
                TextButton(onClick = { onNewHabit(vm.goalId) }) { Text("New") }
            }
            if (state.habits.isEmpty()) Hint("The routines that keep this goal moving. Habits are ongoing, so they show their streak here and don't count as steps.")
            state.habits.forEach { row ->
                val h = row.habit.habit
                Row(Modifier.fillMaxWidth().clickable { onOpenHabit(h.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    HabitBadge(HabitIcons.get(h.icon), Color(h.color), 1f, done = true, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${row.streak} day streak · ${if (row.last30.eligible == 0) "–" else Fmt.percent(row.last30.fraction)} last 30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.unlinkHabit(h.id) }) { Icon(Icons.Filled.LinkOff, "Unlink ${h.name}") }
                }
            }
            Spacer(Modifier.size(40.dp))
        }
    }

    when (dialog) {
        GoalDialog.LINK_TASKS -> LinkDialog(
            title = "Tasks for this goal",
            empty = "There are no open tasks to link. Create one with New.",
            items = state.linkableTasks.map { Triple(it.id, it.title, Fmt.relative(LocalDate.ofEpochDay(it.date))) },
            selected = state.tasks.map { it.id }.toSet(),
            onDismiss = { dialog = null },
            onSave = vm::setTasks,
        )
        GoalDialog.LINK_HABITS -> LinkDialog(
            title = "Habits for this goal",
            empty = "There are no habits to link. Create one with New.",
            items = state.linkableHabits.map { Triple(it.id, it.name, it.category) },
            selected = state.habits.map { it.habit.habit.id }.toSet(),
            onDismiss = { dialog = null },
            onSave = vm::setHabits,
        )
        GoalDialog.DELETE -> ConfirmDialog(
            title = "Delete this goal?",
            body = "This removes the goal and its milestones. The tasks and habits linked to it are kept.",
            confirm = "Delete",
            onDismiss = { dialog = null },
            onConfirm = { vm.delete(onBack) },
        )
        null -> Unit
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: (TaskEntity) -> Unit, onOpen: (Long) -> Unit, onUnlink: (Long) -> Unit) {
    val cancelled = task.status == TaskStatus.CANCELLED
    val struck = task.status != TaskStatus.PENDING
    Row(Modifier.fillMaxWidth().clickable { onOpen(task.id) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.status == TaskStatus.DONE, onCheckedChange = { onToggle(task) }, enabled = !cancelled)
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            )
            Text(
                Fmt.relative(LocalDate.ofEpochDay(task.date)) + if (cancelled) " · cancelled, not counted" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onUnlink(task.id) }) { Icon(Icons.Filled.LinkOff, "Unlink ${task.title}") }
    }
}

/** Tick the items that belong to the goal. Each item is (id, title, detail). */
@Composable
private fun LinkDialog(
    title: String,
    empty: String,
    items: List<Triple<Long, String, String>>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (items.isEmpty()) {
                Text(empty)
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    items.forEach { (id, name, detail) ->
                        val toggle = { picked = if (id in picked) picked - id else picked + id }
                        Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = id in picked, onCheckedChange = { toggle() })
                            Column {
                                Text(name)
                                if (detail.isNotBlank()) {
                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (items.isNotEmpty()) TextButton(onClick = { onSave(picked); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (items.isEmpty()) "Close" else "Cancel") } },
    )
}
