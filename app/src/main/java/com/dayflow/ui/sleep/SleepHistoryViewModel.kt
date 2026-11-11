package com.dayflow.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.domain.SleepNight
import com.dayflow.domain.SleepRules
import com.dayflow.domain.SleepSummary
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SleepRange(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "90 days"),
}

data class SleepHistoryState(
    val range: SleepRange = SleepRange.WEEK,
    val days: List<Pair<LocalDate, SleepNight?>> = emptyList(),
    val summary: SleepSummary = SleepRules.summary(emptyList(), 480),
    val goalMinutes: Int = 480,
    val use24h: Boolean = false,
    val loading: Boolean = true,
) {
    val logged: List<SleepNight> get() = days.mapNotNull { it.second }
}

class SleepHistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(SleepRange.WEEK)

    val state: StateFlow<SleepHistoryState> = combine(c.sleep.nights, c.settings.settings, range) { nights, s, r ->
        val today = LocalDate.now()
        val days = SleepRules.history(nights, today.minusDays(r.days - 1L), today)
        SleepHistoryState(
            range = r,
            days = days,
            summary = SleepRules.summary(days.mapNotNull { it.second }, s.sleepGoalMinutes),
            goalMinutes = s.sleepGoalMinutes,
            use24h = s.use24h,
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepHistoryState())

    fun setRange(r: SleepRange) {
        range.value = r
    }

    fun save(date: LocalDate, bed: Int, wake: Int) = viewModelScope.launch { c.sleep.save(date, bed, wake) }

    fun delete(date: LocalDate) = viewModelScope.launch { c.sleep.delete(date) }

    companion object {
        val Factory = appViewModelFactory { c, _ -> SleepHistoryViewModel(c) }
    }
}
