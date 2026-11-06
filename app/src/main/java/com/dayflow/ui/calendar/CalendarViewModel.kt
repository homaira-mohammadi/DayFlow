package com.dayflow.ui.calendar

import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.CycleEngine
import com.dayflow.domain.DayPlan
import com.dayflow.domain.DayStats
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
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { WEEK, MONTH }

data class DayCell(
    val date: LocalDate,
    val inRange: Boolean,
    val stats: DayStats,
    val isRestDay: Boolean,
    /** Period or predicted period, when cycle tracking is on. */
    val cycleKind: CycleDayKind = CycleDayKind.NONE,
)

data class CalendarState(
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = today,
    val mode: CalendarMode = CalendarMode.MONTH,
    val settings: UserSettings = UserSettings(),
    val cells: List<DayCell> = emptyList(),
    val plan: DayPlan = DayPlan(selected, emptyList(), emptyList(), false, emptyList()),
    val streaks: Map<Long, Int> = emptyMap(),
)

class CalendarViewModel(c: AppContainer) : PlanViewModel(c) {
    private val selected = MutableStateFlow(LocalDate.now())
    private val mode = MutableStateFlow(CalendarMode.MONTH)

    val state: StateFlow<CalendarState> =
        combine(c.plans.history, c.settings.settings, selected, mode, c.cycle.periodDays) { h, s, sel, m, periodDays ->
            val today = LocalDate.now()
            val (from, to) = when (m) {
                CalendarMode.MONTH -> {
                    val first = sel.withDayOfMonth(1)
                    first.with(TemporalAdjusters.previousOrSame(s.weekStart)) to
                        sel.with(TemporalAdjusters.lastDayOfMonth()).with(TemporalAdjusters.nextOrSame(s.weekStart.minus(1)))
                }
                CalendarMode.WEEK -> {
                    val start = sel.with(TemporalAdjusters.previousOrSame(s.weekStart))
                    start to start.plusDays(6)
                }
            }
            val cycle = if (s.cycleTracking && s.cycleHints) CycleEngine(periodDays.keys, s.cycleConfig, today) else null
            val cells = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { d ->
                val plan = h.dayPlan(d, s.minimumCounts)
                val kind = cycle?.kindOn(d)?.takeIf { it == CycleDayKind.PERIOD || it == CycleDayKind.PREDICTED_PERIOD } ?: CycleDayKind.NONE
                DayCell(d, m == CalendarMode.WEEK || d.month == sel.month, plan.stats, plan.isRestDay, kind)
            }.toList()
            CalendarState(
                today = today,
                selected = sel,
                mode = m,
                settings = s,
                cells = cells,
                plan = h.dayPlan(sel, s.minimumCounts),
                streaks = h.habits.associate { it.habit.id to h.currentStreak(it, today, s.minimumCounts) },
            )
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun select(date: LocalDate) {
        selected.value = date
    }

    fun setMode(m: CalendarMode) {
        mode.value = m
    }

    fun previous() {
        selected.value = if (mode.value == CalendarMode.MONTH) selected.value.minusMonths(1) else selected.value.minusWeeks(1)
    }

    fun next() {
        selected.value = if (mode.value == CalendarMode.MONTH) selected.value.plusMonths(1) else selected.value.plusWeeks(1)
    }

    fun jumpToToday() {
        selected.value = LocalDate.now()
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> CalendarViewModel(c) }
    }
}
