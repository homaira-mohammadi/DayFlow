package com.dayflow.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.Achievement
import com.dayflow.domain.DayStats
import com.dayflow.domain.History
import com.dayflow.domain.Rate
import com.dayflow.domain.TaskStatus
import com.dayflow.ui.Fmt
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Period { WEEK, MONTH }

data class HabitRate(val habit: HabitEntity, val rate: Rate, val streak: Int)

data class PeriodSummary(
    val overall: DayStats,
    val tasks: Rate,
    val habits: Rate,
    val minimumDays: Int,
    val postponed: Int,
)

data class ProgressState(
    val period: Period = Period.WEEK,
    val offset: Int = 0,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val settings: UserSettings = UserSettings(),
    val summary: PeriodSummary = PeriodSummary(DayStats.EMPTY, Rate.ZERO, Rate.ZERO, 0, 0),
    val previous: PeriodSummary = summary,
    /** One entry per day of the period; null stats for days that haven't happened yet. */
    val days: List<Pair<LocalDate, DayStats?>> = emptyList(),
    val habitRates: List<HabitRate> = emptyList(),
    val categories: List<Pair<String, Rate>> = emptyList(),
    /** Columns of weeks, each holding 7 days ordered from the week start; null = no data. */
    val heatmap: List<List<Pair<LocalDate, Float?>>> = emptyList(),
    val insights: List<String> = emptyList(),
)

class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(Period.WEEK)
    private val offset = MutableStateFlow(0)

    val state: StateFlow<ProgressState> = combine(c.plans.history, c.settings.settings, period, offset) { h, s, p, o ->
        build(h, s, p, o, LocalDate.now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun setPeriod(p: Period) {
        period.value = p
        offset.value = 0
    }

    fun shift(by: Int) {
        offset.value = (offset.value + by).coerceAtMost(0)
    }

    private fun range(p: Period, offset: Int, today: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> = when (p) {
        Period.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(weekStart)).plusWeeks(offset.toLong())
            start to start.plusDays(6)
        }
        Period.MONTH -> {
            val start = today.withDayOfMonth(1).plusMonths(offset.toLong())
            start to start.with(TemporalAdjusters.lastDayOfMonth())
        }
    }

    private fun summarize(h: History, s: UserSettings, from: LocalDate, to: LocalDate, today: LocalDate): PeriodSummary {
        var planned = 0
        var completed = 0
        var tasks = Rate.ZERO
        var minimumDays = 0
        var postponed = 0
        var d = from
        val end = minOf(to, today)
        while (d <= end) {
            val plan = h.dayPlan(d, s.minimumCounts)
            val st = plan.stats
            planned += st.planned
            completed += st.completed
            val eligible = plan.tasks.filter { it.status != TaskStatus.CANCELLED }
            tasks += Rate(eligible.count { it.status == TaskStatus.DONE }, eligible.size)
            if (!plan.isRestDay) minimumDays += plan.habits.count { !it.skipped && it.achievement == Achievement.MINIMUM }
            postponed += plan.postponed.size
            d = d.plusDays(1)
        }
        val habits = h.habits.fold(Rate.ZERO) { acc, hw -> acc + h.consistency(hw, from, end, today, s.minimumCounts) }
        return PeriodSummary(DayStats(planned, completed), tasks, habits, minimumDays, postponed)
    }

    private fun build(h: History, s: UserSettings, p: Period, o: Int, today: LocalDate): ProgressState {
        val (from, to) = range(p, o, today, s.weekStart)
        val (pFrom, pTo) = range(p, o - 1, today, s.weekStart)
        val summary = summarize(h, s, from, to, today)
        val previous = summarize(h, s, pFrom, pTo, today)
        val end = minOf(to, today)

        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }
            .map { d -> d to (if (d <= today) h.dayPlan(d, s.minimumCounts).stats else null) }.toList()

        val habitRates = h.habits
            .map { HabitRate(it.habit, h.consistency(it, from, end, today, s.minimumCounts), h.currentStreak(it, today, s.minimumCounts)) }
            .filter { it.rate.eligible > 0 }
            .sortedByDescending { it.rate.fraction }

        val categories = mutableMapOf<String, Rate>()
        h.tasks.filter { LocalDate.ofEpochDay(it.date) in from..end && it.status != TaskStatus.CANCELLED }.forEach { t ->
            val key = t.category.ifBlank { "Uncategorized" }
            categories[key] = (categories[key] ?: Rate.ZERO) + Rate(if (t.status == TaskStatus.DONE) 1 else 0, 1)
        }
        h.habits.forEach { hw ->
            val key = hw.habit.category.ifBlank { "Uncategorized" }
            categories[key] = (categories[key] ?: Rate.ZERO) + h.consistency(hw, from, end, today, s.minimumCounts)
        }
        val categoryList = categories.filter { it.value.eligible > 0 }.toList().sortedByDescending { it.second.fraction }

        val heatStart = today.with(TemporalAdjusters.previousOrSame(s.weekStart)).minusWeeks(17)
        val heatmap = (0 until 18).map { w ->
            (0 until 7).map { i ->
                val d = heatStart.plusWeeks(w.toLong()).plusDays(i.toLong())
                val st = if (d <= today) h.dayPlan(d, s.minimumCounts).stats else null
                d to st?.takeIf { !it.isEmpty }?.fraction
            }
        }

        return ProgressState(
            period = p, offset = o, from = from, to = to, today = today, settings = s,
            summary = summary, previous = previous, days = days, habitRates = habitRates,
            categories = categoryList, heatmap = heatmap,
            insights = insights(h, s, summary, previous, categoryList, p, today),
        )
    }

    /** Plain, factual observations from the user's own records. Nothing is claimed without enough data. */
    private fun insights(
        h: History, s: UserSettings, now: PeriodSummary, prev: PeriodSummary,
        categories: List<Pair<String, Rate>>, p: Period, today: LocalDate,
    ): List<String> {
        val out = mutableListOf<String>()
        val label = if (p == Period.WEEK) "week" else "month"
        if (!now.overall.isEmpty) {
            out += "You completed ${Fmt.percent(now.overall.fraction)} of your planned activities this $label (${now.overall.completed} of ${now.overall.planned})."
            if (!prev.overall.isEmpty) {
                val diff = ((now.overall.fraction - prev.overall.fraction) * 100).toInt()
                out += when {
                    diff > 2 -> "That's $diff points higher than the previous $label."
                    diff < -2 -> "That's ${-diff} points lower than the previous $label."
                    else -> "About the same as the previous $label."
                }
            }
        }
        val strong = categories.filter { it.second.eligible >= 3 }
        if (strong.size >= 2) out += "Your most consistent category was ${strong.first().first} (${Fmt.percent(strong.first().second.fraction)})."
        if (now.minimumDays > 0) out += "On ${now.minimumDays} occasions you kept a habit going with its minimum version."
        if (now.postponed >= 3) out += "${now.postponed} tasks were moved to another day. If that happens often, consider planning fewer tasks per day."

        // Weekday pattern over the last 8 weeks, only when each weekday has some data.
        val byWeekday = mutableMapOf<DayOfWeek, Pair<Int, Int>>()
        var d = today.minusWeeks(8)
        while (d < today) {
            val st = h.dayPlan(d, s.minimumCounts).stats
            if (!st.isEmpty) {
                val (c, pl) = byWeekday[d.dayOfWeek] ?: (0 to 0)
                byWeekday[d.dayOfWeek] = (c + st.completed) to (pl + st.planned)
            }
            d = d.plusDays(1)
        }
        if (byWeekday.size >= 5 && byWeekday.values.all { it.second >= 6 }) {
            val rates = byWeekday.mapValues { it.value.first.toFloat() / it.value.second }
            val low = rates.minBy { it.value }
            val high = rates.maxBy { it.value }
            if (high.value - low.value >= 0.2f) {
                out += "Over the last 8 weeks you completed the least on ${low.key.getDisplayName(TextStyle.FULL, Locale.getDefault())}s " +
                    "(${Fmt.percent(low.value)}) and the most on ${high.key.getDisplayName(TextStyle.FULL, Locale.getDefault())}s (${Fmt.percent(high.value)})."
            }
        }
        return out
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> ProgressViewModel(c) }
    }
}
