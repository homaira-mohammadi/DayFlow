package com.dayflow.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.prefs.UserSettings
import com.dayflow.data.repo.SymptomDay
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.CycleEngine
import com.dayflow.domain.CycleProductivity
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Symptom
import com.dayflow.domain.cycleProductivity
import com.dayflow.ui.Fmt
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
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class CycleHistoryRow(
    val title: String,
    val subtitle: String,
    val kinds: List<CycleDayKind>,
    val current: Boolean,
)

data class SymptomPattern(val symptom: Symptom, val days: Int, val whenText: String)

data class CycleUiState(
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = today,
    val month: YearMonth = YearMonth.now(),
    val settings: UserSettings = UserSettings(),
    val engine: CycleEngine = CycleEngine(emptyList(), settings.cycleConfig, today),
    val flows: Map<LocalDate, PeriodFlow> = emptyMap(),
    val symptoms: Map<LocalDate, SymptomDay> = emptyMap(),
    val history: List<CycleHistoryRow> = emptyList(),
    val patterns: List<SymptomPattern> = emptyList(),
    val productivity: CycleProductivity? = null,
    val loading: Boolean = true,
) {
    /** Fertile and ovulation days are hidden when the user turned that display off. */
    fun kind(date: LocalDate): CycleDayKind = engine.kindOn(date).let {
        if (!settings.showFertility && (it == CycleDayKind.FERTILE || it == CycleDayKind.OVULATION)) CycleDayKind.NONE else it
    }

    fun phase(date: LocalDate): String {
        val k = engine.kindOn(date)
        return if (!settings.showFertility && (k == CycleDayKind.FERTILE || k == CycleDayKind.OVULATION)) "Mid-cycle" else engine.phaseOn(date)
    }
}

class CycleViewModel(private val c: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow(LocalDate.now())
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<CycleUiState> = combine(
        combine(c.cycle.periodDays, c.cycle.symptoms) { a, b -> a to b },
        c.settings.settings,
        c.plans.history,
        selected,
        month,
    ) { (flows, symptoms), s, history, sel, m ->
        val today = LocalDate.now()
        val engine = CycleEngine(flows.keys, s.cycleConfig, today)
        CycleUiState(
            today = today,
            selected = sel,
            month = m,
            settings = s,
            engine = engine,
            flows = flows,
            symptoms = symptoms,
            history = historyRows(engine, today),
            patterns = patterns(engine, symptoms, today),
            productivity = if (engine.hasData) cycleProductivity(history, engine, today.minusDays(90), today.minusDays(1), s.minimumCounts) else null,
            loading = false,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState())

    fun select(date: LocalDate) {
        selected.value = date
        month.value = YearMonth.from(date)
    }

    fun showMonth(m: YearMonth) {
        month.value = m
    }

    /** "Log period" starts a period today, filled in for the usual length; "End period" trims the rest. */
    fun togglePeriodToday() = viewModelScope.launch {
        val s = state.value
        val today = s.today
        val current = s.engine.lastPeriod?.takeIf { today in it }
        if (current == null) {
            c.cycle.logPeriod(today, s.engine.averagePeriodLength)
        } else {
            var d = today.plusDays(1)
            while (d <= current.end) {
                c.cycle.setPeriodDay(d, null)
                d = d.plusDays(1)
            }
        }
    }

    fun setPeriodDay(date: LocalDate, flow: PeriodFlow?) = viewModelScope.launch { c.cycle.setPeriodDay(date, flow) }

    fun logPastPeriod(start: LocalDate, days: Int) = viewModelScope.launch { c.cycle.logPeriod(start, days) }

    fun setSymptoms(date: LocalDate, symptoms: Set<Symptom>, note: String) = viewModelScope.launch {
        c.cycle.setSymptoms(date, symptoms, note)
    }

    private fun historyRows(engine: CycleEngine, today: LocalDate): List<CycleHistoryRow> {
        val rows = mutableListOf<CycleHistoryRow>()
        engine.lastPeriod?.let { lp ->
            val day = ChronoUnit.DAYS.between(lp.start, today).toInt() + 1
            val span = maxOf(day, engine.averageCycleLength)
            rows += CycleHistoryRow(
                "Current cycle: $day days",
                "Started ${Fmt.shortDate(lp.start)}",
                (0 until span).map { engine.kindOn(lp.start.plusDays(it.toLong())) },
                current = true,
            )
        }
        engine.cycles.reversed().take(6).forEach { cy ->
            rows += CycleHistoryRow(
                "${cy.length} days",
                "${Fmt.shortDate(cy.start)} – ${Fmt.shortDate(cy.end)}",
                (0 until cy.length).map { engine.kindOn(cy.start.plusDays(it.toLong())) },
                current = false,
            )
        }
        return rows
    }

    private fun patterns(engine: CycleEngine, symptoms: Map<LocalDate, SymptomDay>, today: LocalDate): List<SymptomPattern> {
        val since = engine.periods.takeLast(3).firstOrNull()?.start ?: today.minusDays(90)
        val byPhase = mutableMapOf<Symptom, MutableMap<String, Int>>()
        symptoms.values.filter { it.date in since..today }.forEach { day ->
            val phase = when (engine.kindOn(day.date)) {
                CycleDayKind.PERIOD -> "during your period"
                CycleDayKind.PMS -> "on PMS days"
                CycleDayKind.FERTILE, CycleDayKind.OVULATION -> "mid-cycle"
                else -> "on other days"
            }
            day.symptoms.forEach { sym -> byPhase.getOrPut(sym) { mutableMapOf() }.merge(phase, 1, Int::plus) }
        }
        return byPhase.map { (sym, phases) ->
            SymptomPattern(sym, phases.values.sum(), "Mostly " + phases.maxBy { it.value }.key)
        }.sortedByDescending { it.days }.take(5)
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> CycleViewModel(c) }
    }
}

/** First day of the week containing [date]. */
fun weekOf(date: LocalDate, weekStart: java.time.DayOfWeek): List<LocalDate> {
    val start = date.with(TemporalAdjusters.previousOrSame(weekStart))
    return (0L..6L).map { start.plusDays(it) }
}
