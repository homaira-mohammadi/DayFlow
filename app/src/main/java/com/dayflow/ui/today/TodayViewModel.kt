package com.dayflow.ui.today

import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.CycleEngine
import com.dayflow.domain.CycleStatus
import com.dayflow.domain.DayPlan
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerRules
import com.dayflow.domain.PrayerStatus
import com.dayflow.ui.PlanViewModel
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayState(
    val today: LocalDate = LocalDate.now(),
    val settings: UserSettings = UserSettings(),
    val plan: DayPlan = DayPlan(today, emptyList(), emptyList(), false, emptyList()),
    val overdue: List<TaskEntity> = emptyList(),
    val streaks: Map<Long, Int> = emptyMap(),
    val reflection: ReflectionEntity? = null,
    /** Null when cycle tracking is off or nothing is logged yet. */
    val cycle: CycleSummary? = null,
    val loading: Boolean = true,
)

data class CycleSummary(val title: String, val subtitle: String, val kind: CycleDayKind)

private fun summarize(engine: CycleEngine, today: LocalDate, showFertility: Boolean): CycleSummary? {
    val kind = engine.kindOn(today).let {
        if (!showFertility && (it == CycleDayKind.FERTILE || it == CycleDayKind.OVULATION)) CycleDayKind.NONE else it
    }
    val title = when (val st = engine.status) {
        CycleStatus.NoData -> return null
        is CycleStatus.InPeriod -> "Period · day ${st.day}"
        is CycleStatus.Upcoming -> when (st.daysUntil) { 0 -> "Period expected today"; 1 -> "Period in 1 day"; else -> "Period in ${st.daysUntil} days" }
        is CycleStatus.Late -> "Period is ${st.days} day${if (st.days == 1) "" else "s"} late"
    }
    val subtitle = when (kind) {
        CycleDayKind.PERIOD, CycleDayKind.PREDICTED_PERIOD -> "Go easy today: keep one or two priorities."
        CycleDayKind.PMS -> "PMS days: a lighter plan and minimum habit versions can help."
        CycleDayKind.FERTILE, CycleDayKind.OVULATION -> "Energy is often high now. Good day for focus sessions."
        CycleDayKind.NONE -> if (showFertility) engine.phaseOn(today) else "Tap to log symptoms or your period."
    }
    return CycleSummary(title, subtitle, kind)
}

class TodayViewModel(c: AppContainer) : PlanViewModel(c) {
    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<TodayState> = combine(c.plans.history, c.settings.settings, today, c.reflections.all, c.cycle.periodDays) { h, s, d, reflections, periodDays ->
        TodayState(
            today = d,
            settings = s,
            plan = h.dayPlan(d, s.minimumCounts),
            overdue = h.overdueTasks(d),
            streaks = h.habits.associate { it.habit.id to h.currentStreak(it, d, s.minimumCounts) },
            reflection = reflections.firstOrNull { it.date == d.toEpochDay() },
            cycle = if (s.cycleTracking && s.cycleHints) summarize(CycleEngine(periodDays.keys, s.cycleConfig, d), d, s.showFertility) else null,
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    /** How many days before today the prayers card is showing; 0 is today. */
    private val prayerOffset = MutableStateFlow(0)

    val prayers: StateFlow<PrayerCardState?> = combine(c.prayers.logs, c.settings.settings, today, prayerOffset, c.cycle.periodDays) { logs, s, d, offset, periodDays ->
        if (!s.prayerTracking) return@combine null
        val date = d.minusDays(offset.toLong())
        val day = logs[date].orEmpty()
        // Logged period days are excused from prayer; predicted ones are not, since they may be wrong.
        val excused = if (s.cycleTracking) periodDays.keys else emptySet()
        PrayerCardState(
            date = date,
            isToday = offset == 0,
            canGoBack = offset < MAX_PRAYER_DAYS_BACK,
            excused = date in excused,
            day = day,
            summary = PrayerRules.summary(day),
            streak = PrayerRules.streak(logs, d, excused),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sleep: StateFlow<SleepCardState?> = combine(c.sleep.nights, c.settings.settings, today) { nights, s, d ->
        if (!s.sleepTracking) return@combine null
        val latest = nights.values.maxByOrNull { it.date }
        SleepCardState(
            lastNight = nights[d],
            goalMinutes = s.sleepGoalMinutes,
            use24h = s.use24h,
            defaultBed = latest?.bedMinutes ?: (23 * 60),
            defaultWake = latest?.wakeMinutes ?: (7 * 60),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveSleep(bed: Int, wake: Int) = viewModelScope.launch { c.sleep.save(today.value, bed, wake) }

    fun deleteSleep() = viewModelScope.launch { c.sleep.delete(today.value) }

    fun prayerPreviousDay() = prayerOffset.update { (it + 1).coerceAtMost(MAX_PRAYER_DAYS_BACK) }

    fun prayerNextDay() = prayerOffset.update { (it - 1).coerceAtLeast(0) }

    fun setPrayer(date: LocalDate, prayer: Prayer, status: PrayerStatus?) = viewModelScope.launch { c.prayers.set(date, prayer, status) }

    fun photoFile(name: String) = c.photos.file(name)

    /** Called on resume so the screen rolls over after midnight. */
    fun refreshDate() {
        if (today.value != LocalDate.now()) prayerOffset.value = 0
        today.value = LocalDate.now()
    }

    companion object {
        private const val MAX_PRAYER_DAYS_BACK = 60
        val Factory = appViewModelFactory { c, _ -> TodayViewModel(c) }
    }
}
