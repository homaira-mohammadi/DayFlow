package com.dayflow.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.GoalEntity
import com.dayflow.data.repo.GoalParts
import com.dayflow.domain.GoalProgress
import com.dayflow.domain.GoalRules
import com.dayflow.domain.GoalStatus
import com.dayflow.domain.History
import com.dayflow.domain.TaskStatus
import com.dayflow.ui.Fmt
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalSummary(
    val goal: GoalEntity,
    val progress: GoalProgress,
    val milestones: Int,
    val tasks: Int,
    val habits: Int,
    val daysLeft: Long?,
) {
    val shownFraction: Float get() = GoalRules.shownFraction(goal.status, progress)

    /** "3 tasks · 2 habits · 1 milestone", leaving out what the goal doesn't have. */
    val contents: String
        get() = listOfNotNull(
            count(tasks, "task"), count(habits, "habit"), count(milestones, "milestone"),
        ).joinToString(" · ")

    private fun count(n: Int, noun: String) = when (n) {
        0 -> null
        1 -> "1 $noun"
        else -> "$n ${noun}s"
    }
}

data class GoalsState(
    val active: List<GoalSummary> = emptyList(),
    val achieved: List<GoalSummary> = emptyList(),
    val archived: List<GoalSummary> = emptyList(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = active.isEmpty() && achieved.isEmpty() && archived.isEmpty()
}

/** Counts a goal's steps from the tasks that still exist; cancelled tasks are not steps. */
fun GoalParts.summarize(history: History, today: LocalDate): GoalSummary {
    val statuses = history.tasks.filter { it.id in taskIds }.map { it.status }
    return GoalSummary(
        goal = goal,
        progress = GoalRules.progress(milestones.map { it.done }, statuses),
        milestones = milestones.size,
        tasks = statuses.count { it != TaskStatus.CANCELLED },
        habits = history.habits.count { it.habit.id in habitIds },
        daysLeft = GoalRules.daysLeft(goal.targetDate, today),
    )
}

/** "Target Dec 12, 2026 · 30 days left", or null when the goal has no target date. */
fun targetText(goal: GoalEntity, daysLeft: Long?): String? {
    val date = goal.targetDate?.let(LocalDate::ofEpochDay) ?: return null
    val base = "Target ${Fmt.fullDate(date)}"
    if (goal.status != GoalStatus.ACTIVE || daysLeft == null) return base
    return base + " · " + when {
        daysLeft == 0L -> "today"
        daysLeft == 1L -> "1 day left"
        daysLeft > 1 -> "$daysLeft days left"
        daysLeft == -1L -> "1 day ago"
        else -> "${-daysLeft} days ago"
    }
}

class GoalsViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<GoalsState> = combine(c.goals.goals, c.plans.history) { goals, history ->
        val today = LocalDate.now()
        val summaries = goals.map { it.summarize(history, today) }
        GoalsState(
            // Goals with a target date come first, soonest first.
            active = summaries.filter { it.goal.status == GoalStatus.ACTIVE }
                .sortedWith(compareBy({ it.goal.targetDate ?: Long.MAX_VALUE }, { it.goal.id })),
            achieved = summaries.filter { it.goal.status == GoalStatus.ACHIEVED },
            archived = summaries.filter { it.goal.status == GoalStatus.ARCHIVED },
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

    fun setStatus(id: Long, status: GoalStatus) = viewModelScope.launch { c.goals.setStatus(id, status) }

    fun delete(id: Long) = viewModelScope.launch { c.goals.delete(id) }

    companion object {
        val Factory = appViewModelFactory { c, _ -> GoalsViewModel(c) }
    }
}
