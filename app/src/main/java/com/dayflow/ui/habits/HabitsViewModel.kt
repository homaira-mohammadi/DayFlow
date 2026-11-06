package com.dayflow.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.HabitWithSchedules
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.DayOutcome
import com.dayflow.domain.HabitStatus
import com.dayflow.domain.Rate
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitSummary(
    val habit: HabitWithSchedules,
    val currentStreak: Int,
    val bestStreak: Int,
    val last30: Rate,
    /** Oldest first, ending today. */
    val lastWeek: List<Pair<LocalDate, DayOutcome>>,
)

data class HabitsState(
    val settings: UserSettings = UserSettings(),
    val active: List<HabitSummary> = emptyList(),
    val paused: List<HabitSummary> = emptyList(),
    val archived: List<HabitSummary> = emptyList(),
    val loading: Boolean = true,
)

class HabitsViewModel(private val c: AppContainer) : ViewModel() {

    val state: StateFlow<HabitsState> = combine(c.plans.history, c.settings.settings) { h, s ->
        val today = LocalDate.now()
        val summaries = h.habits.map { hw ->
            HabitSummary(
                habit = hw,
                currentStreak = h.currentStreak(hw, today, s.minimumCounts),
                bestStreak = h.bestStreak(hw, today, s.minimumCounts),
                last30 = h.consistency(hw, today.minusDays(29), today, today, s.minimumCounts),
                lastWeek = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to h.outcome(hw, it, today, s.minimumCounts) },
            )
        }
        HabitsState(
            settings = s,
            active = summaries.filter { it.habit.habit.status == HabitStatus.ACTIVE },
            paused = summaries.filter { it.habit.habit.status == HabitStatus.PAUSED },
            archived = summaries.filter { it.habit.habit.status == HabitStatus.ARCHIVED },
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsState())

    fun setStatus(id: Long, status: HabitStatus) = viewModelScope.launch { c.habits.setStatus(id, status) }

    fun delete(id: Long) = viewModelScope.launch { c.habits.delete(id) }

    companion object {
        val Factory = appViewModelFactory { c, _ -> HabitsViewModel(c) }
    }
}
