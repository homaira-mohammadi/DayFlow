package com.dayflow.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.GoalMilestoneEntity
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitWithSchedules
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.GoalStatus
import com.dayflow.domain.HabitStatus
import com.dayflow.domain.Rate
import com.dayflow.domain.TaskStatus
import com.dayflow.ui.PlanViewModel
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalHabitRow(val habit: HabitWithSchedules, val streak: Int, val last30: Rate)

data class GoalDetailState(
    val summary: GoalSummary? = null,
    val settings: UserSettings = UserSettings(),
    val milestones: List<GoalMilestoneEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val habits: List<GoalHabitRow> = emptyList(),
    /** Tasks that can be ticked in the link dialog: this goal's, plus open tasks with no goal. */
    val linkableTasks: List<TaskEntity> = emptyList(),
    /** Habits that can be ticked in the link dialog: this goal's, plus current habits with no goal. */
    val linkableHabits: List<HabitEntity> = emptyList(),
    val loading: Boolean = true,
)

class GoalDetailViewModel(c: AppContainer, handle: SavedStateHandle) : PlanViewModel(c) {
    val goalId: Long = checkNotNull(handle.get<Long>("id"))

    val state: StateFlow<GoalDetailState> = combine(c.goals.goals, c.plans.history, c.settings.settings) { goals, h, s ->
        val today = LocalDate.now()
        val parts = goals.firstOrNull { it.goal.id == goalId } ?: return@combine GoalDetailState(loading = false)
        val taskIdsElsewhere = goals.filter { it.goal.id != goalId }.flatMap { it.taskIds }.toSet()
        val habitIdsElsewhere = goals.filter { it.goal.id != goalId }.flatMap { it.habitIds }.toSet()
        // Open tasks first, in date order, then finished ones.
        val taskOrder = compareBy<TaskEntity>({ it.status != TaskStatus.PENDING }, { it.date }, { it.id })
        GoalDetailState(
            summary = parts.summarize(h, today),
            settings = s,
            milestones = parts.milestones,
            tasks = h.tasks.filter { it.id in parts.taskIds }.sortedWith(taskOrder),
            habits = h.habits.filter { it.habit.id in parts.habitIds }.map { hw ->
                GoalHabitRow(
                    habit = hw,
                    streak = h.currentStreak(hw, today, s.minimumCounts),
                    last30 = h.consistency(hw, today.minusDays(29), today, today, s.minimumCounts),
                )
            },
            linkableTasks = h.tasks.filter {
                it.id in parts.taskIds || (it.status == TaskStatus.PENDING && it.id !in taskIdsElsewhere)
            }.sortedWith(taskOrder),
            linkableHabits = h.habits.map { it.habit }.filter {
                it.id in parts.habitIds || (it.status != HabitStatus.ARCHIVED && it.id !in habitIdsElsewhere)
            },
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailState())

    fun addMilestone(title: String) {
        if (title.isNotBlank()) viewModelScope.launch { c.goals.addMilestone(goalId, title.trim()) }
    }

    fun setMilestoneDone(milestone: GoalMilestoneEntity, done: Boolean) = viewModelScope.launch { c.goals.setMilestoneDone(milestone, done) }

    fun deleteMilestone(id: Long) = viewModelScope.launch { c.goals.deleteMilestone(id) }

    fun setTasks(ids: Set<Long>) = viewModelScope.launch { c.goals.setTasks(goalId, ids) }

    fun setHabits(ids: Set<Long>) = viewModelScope.launch { c.goals.setHabits(goalId, ids) }

    fun unlinkTask(id: Long) = viewModelScope.launch { c.goals.unlinkTask(id) }

    fun unlinkHabit(id: Long) = viewModelScope.launch { c.goals.unlinkHabit(id) }

    fun setStatus(status: GoalStatus) = viewModelScope.launch { c.goals.setStatus(goalId, status) }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        c.goals.delete(goalId)
        onDeleted()
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> GoalDetailViewModel(c, h) }
    }
}
