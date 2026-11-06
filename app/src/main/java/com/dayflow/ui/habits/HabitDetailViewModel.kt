package com.dayflow.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.HabitWithSchedules
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.DayOutcome
import com.dayflow.domain.HabitDayItem
import com.dayflow.domain.HabitRules
import com.dayflow.domain.Rate
import com.dayflow.ui.PlanViewModel
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class DetailCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val outcome: DayOutcome,
    /** Non-null when the day can be logged or corrected (scheduled, not in the future). */
    val item: HabitDayItem?,
)

data class HabitDetailState(
    val habit: HabitWithSchedules? = null,
    val settings: UserSettings = UserSettings(),
    val today: LocalDate = LocalDate.now(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val last30: Rate = Rate.ZERO,
    val allTime: Rate = Rate.ZERO,
    val total: Double = 0.0,
    val month: YearMonth = YearMonth.now(),
    val cells: List<DetailCell> = emptyList(),
    val loading: Boolean = true,
)

class HabitDetailViewModel(c: AppContainer, handle: SavedStateHandle) : PlanViewModel(c) {
    val habitId: Long = checkNotNull(handle.get<Long>("id"))
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HabitDetailState> = combine(c.plans.history, c.settings.settings, month) { h, s, m ->
        val today = LocalDate.now()
        val hw = h.habits.firstOrNull { it.habit.id == habitId } ?: return@combine HabitDetailState(loading = false)
        val logs = h.logsFor(habitId)
        val first = hw.schedules.minOfOrNull { it.effectiveFrom }?.let(LocalDate::ofEpochDay) ?: today
        val gridStart = m.atDay(1).with(TemporalAdjusters.previousOrSame(s.weekStart))
        val gridEnd = m.atEndOfMonth().with(TemporalAdjusters.nextOrSame(s.weekStart.minus(1)))
        val cells = generateSequence(gridStart) { it.plusDays(1) }.takeWhile { it <= gridEnd }.map { d ->
            val due = HabitRules.dueOn(hw.schedules, d)
            val item = if (due != null && d <= today) HabitDayItem(hw.habit, due, logs[d.toEpochDay()], s.minimumCounts) else null
            DetailCell(d, YearMonth.from(d) == m, h.outcome(hw, d, today, s.minimumCounts), item)
        }.toList()
        HabitDetailState(
            habit = hw,
            settings = s,
            today = today,
            currentStreak = h.currentStreak(hw, today, s.minimumCounts),
            bestStreak = h.bestStreak(hw, today, s.minimumCounts),
            last30 = h.consistency(hw, today.minusDays(29), today, today, s.minimumCounts),
            allTime = h.consistency(hw, first, today, today, s.minimumCounts),
            total = logs.values.filter { !it.skipped }.sumOf { it.value },
            month = m,
            cells = cells,
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    companion object {
        val Factory = appViewModelFactory { c, h -> HabitDetailViewModel(c, h) }
    }
}
