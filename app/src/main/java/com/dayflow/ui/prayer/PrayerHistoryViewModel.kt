package com.dayflow.ui.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerDay
import com.dayflow.domain.PrayerRules
import com.dayflow.domain.PrayerStatus
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class PrayerRange(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "90 days"),
}

data class PrayerBreakdown(val prayer: Prayer, val onTime: Int, val late: Int, val missed: Int, val expected: Int)

data class PrayerHistoryState(
    val range: PrayerRange = PrayerRange.WEEK,
    val days: List<PrayerDay> = emptyList(),
    /** Prayers asked for in the range: five for every day that isn't excused. */
    val expected: Int = 0,
    val prayed: Int = 0,
    val onTime: Int = 0,
    val late: Int = 0,
    val missed: Int = 0,
    val excusedDays: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val perPrayer: List<PrayerBreakdown> = emptyList(),
    val loading: Boolean = true,
) {
    val hasData: Boolean get() = prayed + missed > 0
    fun rate(n: Int): Float = if (expected == 0) 0f else n.toFloat() / expected
}

class PrayerHistoryViewModel(c: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(PrayerRange.WEEK)

    val state: StateFlow<PrayerHistoryState> = combine(c.prayers.logs, c.cycle.periodDays, c.settings.settings, range) { logs, periodDays, s, r ->
        val today = LocalDate.now()
        val excused = if (s.cycleTracking) periodDays.keys else emptySet()
        val days = PrayerRules.history(logs, today.minusDays(r.days - 1L), today, excused)
        val counted = days.filterNot { it.excused }
        val current = PrayerRules.streak(logs, today, excused)
        PrayerHistoryState(
            range = r,
            days = days,
            expected = counted.size * Prayer.entries.size,
            prayed = counted.sumOf { it.summary.prayed },
            onTime = counted.sumOf { it.summary.onTime },
            late = counted.sumOf { it.summary.late },
            missed = counted.sumOf { it.summary.missed },
            excusedDays = days.size - counted.size,
            currentStreak = current,
            // The current streak may start before the range, so the best is never lower than it.
            bestStreak = maxOf(PrayerRules.bestStreak(days), current),
            perPrayer = Prayer.entries.map { p ->
                val statuses = counted.mapNotNull { logs[it.date]?.get(p) }
                PrayerBreakdown(
                    prayer = p,
                    onTime = statuses.count { it == PrayerStatus.ON_TIME },
                    late = statuses.count { it == PrayerStatus.LATE },
                    missed = statuses.count { it == PrayerStatus.MISSED },
                    expected = counted.size,
                )
            },
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrayerHistoryState())

    fun setRange(r: PrayerRange) {
        range.value = r
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> PrayerHistoryViewModel(c) }
    }
}
