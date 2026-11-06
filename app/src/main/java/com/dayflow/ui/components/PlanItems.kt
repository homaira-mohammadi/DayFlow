package com.dayflow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dayflow.data.db.TaskEntity
import com.dayflow.domain.Achievement
import com.dayflow.domain.DayPlan
import com.dayflow.domain.DaySection
import com.dayflow.domain.HabitDayItem
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.TaskStatus
import com.dayflow.ui.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Callbacks shared by every list that shows a day's tasks and habits. */
class PlanActions(
    val onToggleTask: (TaskEntity) -> Unit,
    val onOpenTask: (TaskEntity) -> Unit,
    val onReschedule: (TaskEntity, LocalDate) -> Unit,
    val onCancelTask: (TaskEntity, Boolean) -> Unit,
    val onQuickHabit: (HabitDayItem, LocalDate) -> Unit,
    val onLogHabit: (HabitDayItem) -> Unit,
    val onFocusTask: ((TaskEntity) -> Unit)? = null,
)

/**
 * Renders a day's plan grouped by Morning / Afternoon / Evening / Anytime, mixing habits and tasks.
 * Focus tasks are shown separately by the caller when [skipFocus] is true.
 */
fun LazyListScope.dayPlanItems(
    plan: DayPlan,
    today: LocalDate,
    use24h: Boolean,
    streaks: Map<Long, Int>,
    actions: PlanActions,
    skipFocus: Boolean = false,
) {
    val canLogHabits = plan.date <= today
    DaySection.entries.forEach { section ->
        val habits = plan.habits.filter { it.habit.section == section }
        val tasks = plan.tasks.filter { DaySection.forTime(it.timeMinutes) == section && !(skipFocus && it.isFocus) }
        if (habits.isEmpty() && tasks.isEmpty()) return@forEach
        item(key = "section-${section.name}") { SectionHeader(section.label) }
        items(habits, key = { "habit-${it.habit.id}" }) { item ->
            HabitRow(item, plan.date, streaks[item.habit.id], plan.isRestDay, canLogHabits, actions)
        }
        items(tasks, key = { "task-${it.id}" }) { task ->
            TaskRow(task, use24h, today, actions)
        }
    }
    if (plan.postponed.isNotEmpty()) {
        item(key = "postponed") {
            SectionHeader("Moved from this day")
            plan.postponed.forEach { (r, task) ->
                Text(
                    "${task?.title ?: "Deleted task"} → ${Fmt.relative(LocalDate.ofEpochDay(r.toDate), today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun HabitRow(
    item: HabitDayItem,
    date: LocalDate,
    streak: Int?,
    restDay: Boolean,
    canLog: Boolean,
    actions: PlanActions,
) {
    val color = Color(item.habit.color)
    val done = item.isDone
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = canLog) { actions.onLogHabit(item) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HabitBadge(HabitIcons.get(item.habit.icon), color, item.fraction, done)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.habit.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                if (item.habit.goalType != HabitGoalType.CHECK) {
                    add("${Fmt.number(item.value)} / ${Fmt.amount(item.schedule.target, item.habit.goalType, item.habit.unit)}")
                }
                when {
                    item.skipped -> add("Skipped")
                    restDay -> add("Rest day")
                    item.achievement == Achievement.MINIMUM -> add("Minimum reached")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (streak != null && streak > 0) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("$streak", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    if (parts.isNotEmpty()) Spacer(Modifier.width(6.dp))
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val haptics = LocalHapticFeedback.current
        IconButton(
            enabled = canLog,
            onClick = {
                if (!done) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onQuickHabit(item, date)
            },
            modifier = Modifier.semantics { contentDescription = if (done) "Mark ${item.habit.name} not done" else "Complete ${item.habit.name}" },
        ) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) color else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun TaskRow(task: TaskEntity, use24h: Boolean, today: LocalDate, actions: PlanActions, showDate: Boolean = false) {
    val done = task.status == TaskStatus.DONE
    val cancelled = task.status == TaskStatus.CANCELLED
    var menu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { actions.onOpenTask(task) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val haptics = LocalHapticFeedback.current
        IconButton(
            onClick = {
                if (!done) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onToggleTask(task)
            },
            enabled = !cancelled,
            modifier = Modifier.semantics { contentDescription = if (done) "Mark ${task.title} not done" else "Complete ${task.title}" },
        ) {
            Icon(
                when {
                    cancelled -> Icons.Filled.Block
                    done -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isFocus) {
                    Icon(Icons.Filled.Star, "Priority", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done || cancelled) TextDecoration.LineThrough else null,
                    color = if (done || cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.priority == 2) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Flag, "High priority", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            val parts = buildList {
                if (showDate) add(Fmt.relative(LocalDate.ofEpochDay(task.date), today))
                task.timeMinutes?.let { add(Fmt.time(it, use24h)) }
                if (task.category.isNotBlank()) add(task.category)
                task.estimatedMinutes?.let { add("~$it min") }
                if (task.rescheduleCount > 0) add("moved ${task.rescheduleCount}×")
                if (cancelled) add("Cancelled")
            }
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options for ${task.title}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (task.status == TaskStatus.PENDING) {
                    actions.onFocusTask?.let { focus ->
                        DropdownMenuItem(text = { Text("Start focus session") }, onClick = { menu = false; focus(task) })
                        HorizontalDivider()
                    }
                    val date = LocalDate.ofEpochDay(task.date)
                    if (date != today) DropdownMenuItem(text = { Text("Move to today") }, onClick = { menu = false; actions.onReschedule(task, today) })
                    DropdownMenuItem(text = { Text("Move to tomorrow") }, onClick = { menu = false; actions.onReschedule(task, maxOf(today, date).plusDays(1)) })
                    DropdownMenuItem(
                        text = { Text("Move to next week") },
                        onClick = { menu = false; actions.onReschedule(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))) },
                    )
                    DropdownMenuItem(text = { Text("Pick a date…") }, onClick = { menu = false; picking = true })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Cancel task") }, onClick = { menu = false; actions.onCancelTask(task, true) })
                } else if (cancelled) {
                    DropdownMenuItem(text = { Text("Restore task") }, onClick = { menu = false; actions.onCancelTask(task, false) })
                }
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; actions.onOpenTask(task) })
            }
        }
    }
    if (picking) {
        DatePickerDialogFor(maxOf(today, LocalDate.ofEpochDay(task.date)), onDismiss = { picking = false }) {
            actions.onReschedule(task, it)
        }
    }
}

/** Record progress for any habit type, mark the minimum version, skip the day, or correct a past record. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitLogDialog(
    item: HabitDayItem,
    date: LocalDate,
    onDismiss: () -> Unit,
    onSetValue: (Double) -> Unit,
    onSkip: (Boolean) -> Unit,
) {
    val habit = item.habit
    val target = item.schedule.target
    val minimum = item.schedule.minimumTarget
    var text by remember { mutableStateOf(if (item.value > 0) Fmt.number(item.value) else "") }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    Fmt.relative(date) + when (habit.goalType) {
                        HabitGoalType.CHECK -> ""
                        else -> " · target ${Fmt.amount(target, habit.goalType, habit.unit)}" +
                            (minimum?.let { " · minimum ${Fmt.amount(it, habit.goalType, habit.unit)}" } ?: "")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (habit.goalType == HabitGoalType.CHECK) {
                    Text(if (item.value > 0) "Completed" else "Not completed yet", fontWeight = FontWeight.Medium)
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(if (habit.goalType == HabitGoalType.DURATION) "Minutes" else habit.unit.ifBlank { "Amount" }) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val steps = if (habit.goalType == HabitGoalType.DURATION) listOf(5.0, 15.0) else listOf(1.0, 5.0)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        steps.forEach { step ->
                            AssistChip(onClick = { text = Fmt.number((parsed ?: 0.0) + step) }, label = { Text("+${Fmt.number(step)}") })
                        }
                        if (minimum != null) AssistChip(onClick = { text = Fmt.number(minimum) }, label = { Text("Minimum") })
                        AssistChip(onClick = { text = Fmt.number(target) }, label = { Text("Full target") })
                    }
                }
                Row {
                    TextButton(onClick = { onSkip(!item.skipped); onDismiss() }) {
                        Text(if (item.skipped) "Un-skip this day" else "Skip this day")
                    }
                }
            }
        },
        confirmButton = {
            if (habit.goalType == HabitGoalType.CHECK) {
                TextButton(onClick = { onSetValue(if (item.value > 0) 0.0 else 1.0); onDismiss() }) {
                    Text(if (item.value > 0) "Mark not done" else "Mark done")
                }
            } else {
                TextButton(enabled = text.isBlank() || parsed != null, onClick = { onSetValue(parsed ?: 0.0); onDismiss() }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
