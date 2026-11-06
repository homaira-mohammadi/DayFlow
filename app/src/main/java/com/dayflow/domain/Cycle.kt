package com.dayflow.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Optional profile information. It never turns features on or off; "prefer not to say" is simply not storing one. */
enum class Gender(val label: String) {
    FEMALE("Female"),
    MALE("Male"),
    NON_BINARY("Non-binary"),
    SELF_DESCRIBED("Another identity"),
}

enum class PeriodFlow(val label: String) { SPOTTING("Spotting"), LIGHT("Light"), MEDIUM("Medium"), HEAVY("Heavy") }

enum class Symptom(val label: String) {
    CRAMPS("Cramps"),
    HEADACHE("Headache"),
    BLOATING("Bloating"),
    BACKACHE("Backache"),
    TENDER_BREASTS("Tender breasts"),
    ACNE("Acne"),
    FATIGUE("Fatigue"),
    CRAVINGS("Cravings"),
    MOOD_SWINGS("Mood swings"),
    IRRITABILITY("Irritability"),
    ANXIETY("Anxiety"),
    INSOMNIA("Trouble sleeping"),
    NAUSEA("Nausea"),
}

/** What a calendar day is in the cycle. Earlier entries win when several apply. */
enum class CycleDayKind { PERIOD, PREDICTED_PERIOD, OVULATION, FERTILE, PMS, NONE }

data class PeriodSpan(val start: LocalDate, val end: LocalDate) {
    val length: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    operator fun contains(date: LocalDate) = date in start..end
}

/** A completed cycle: from one period start to the day before the next. */
data class CycleInfo(val start: LocalDate, val length: Int, val periodLength: Int) {
    val end: LocalDate get() = start.plusDays(length - 1L)
}

sealed interface CycleStatus {
    data object NoData : CycleStatus
    data class InPeriod(val day: Int) : CycleStatus
    data class Upcoming(val daysUntil: Int, val cycleDay: Int) : CycleStatus
    data class Late(val days: Int, val cycleDay: Int) : CycleStatus
}

data class CycleConfig(
    /** Used until at least one full cycle has been logged. */
    val defaultCycleLength: Int = 28,
    val defaultPeriodLength: Int = 5,
    val pmsDays: Int = 5,
    /** Ovulation is estimated this many days before the next period. */
    val lutealDays: Int = 14,
)

/**
 * Cycle calculations from logged period days. Everything here is an estimate based on the
 * user's own history; the UI labels it that way.
 */
class CycleEngine(periodDays: Collection<LocalDate>, private val config: CycleConfig, private val today: LocalDate) {
    val days: Set<LocalDate> = periodDays.toSet()

    /** Logged days grouped into periods; a single missing day inside a period doesn't split it. */
    val periods: List<PeriodSpan> = buildList {
        var start: LocalDate? = null
        var end: LocalDate? = null
        for (d in days.sorted()) {
            if (start != null && ChronoUnit.DAYS.between(end, d) <= 2) {
                end = d
            } else {
                if (start != null) add(PeriodSpan(start, end!!))
                start = d
                end = d
            }
        }
        if (start != null) add(PeriodSpan(start, end!!))
    }

    val cycles: List<CycleInfo> = periods.zipWithNext { a, b ->
        CycleInfo(a.start, ChronoUnit.DAYS.between(a.start, b.start).toInt(), a.length)
    }

    /** Recent cycles that look like real cycles (not a missed log or a doubled entry). */
    private val recentCycles = cycles.filter { it.length in 15..60 }.takeLast(6)

    val averageCycleLength: Int =
        if (recentCycles.isEmpty()) config.defaultCycleLength else recentCycles.map { it.length }.average().roundToInt()

    val averagePeriodLength: Int = periods.filter { it.end < today }.takeLast(6).let { done ->
        if (done.isEmpty()) config.defaultPeriodLength else done.map { it.length }.average().roundToInt().coerceIn(1, 10)
    }

    /** The latest period that has started by today. */
    val lastPeriod: PeriodSpan? = periods.lastOrNull { it.start <= today }

    val hasData: Boolean get() = lastPeriod != null

    val nextPredictedStart: LocalDate? = lastPeriod?.start?.plusDays(averageCycleLength.toLong())

    /** Upcoming period starts. When the period is late, later predictions count from today. */
    val predictedStarts: List<LocalDate> = nextPredictedStart?.let { first ->
        val base = maxOf(first, today)
        listOf(first) + (1..5).map { base.plusDays(it.toLong() * averageCycleLength) }
    }.orEmpty()

    private val actualStarts = periods.map { it.start }
    private val anchors: List<Pair<LocalDate, Boolean>> =
        (actualStarts.map { it to true } + predictedStarts.filter { p -> actualStarts.none { it >= p } }.map { it to false })
            .sortedBy { it.first }

    val status: CycleStatus
        get() {
            val lp = lastPeriod ?: return CycleStatus.NoData
            val cycleDay = ChronoUnit.DAYS.between(lp.start, today).toInt() + 1
            if (today in lp) return CycleStatus.InPeriod(cycleDay)
            val until = ChronoUnit.DAYS.between(today, nextPredictedStart!!).toInt()
            return if (until >= 0) CycleStatus.Upcoming(until, cycleDay) else CycleStatus.Late(-until, cycleDay)
        }

    fun kindOn(date: LocalDate): CycleDayKind {
        if (date in days) return CycleDayKind.PERIOD
        if (actualStarts.isEmpty() || date < actualStarts.first()) return CycleDayKind.NONE
        if (date >= today) {
            for ((start, actual) in anchors) {
                if (!actual && date in start..start.plusDays(averagePeriodLength - 1L)) return CycleDayKind.PREDICTED_PERIOD
            }
        }
        val next = anchors.firstOrNull { it.first > date }?.first ?: return CycleDayKind.NONE
        val ovulation = next.minusDays(config.lutealDays.toLong())
        return when {
            date == ovulation -> CycleDayKind.OVULATION
            date in ovulation.minusDays(5)..ovulation.plusDays(1) -> CycleDayKind.FERTILE
            date in next.minusDays(config.pmsDays.toLong())..next.minusDays(1) -> CycleDayKind.PMS
            else -> CycleDayKind.NONE
        }
    }

    /** A plain-language phase name for [date]. */
    fun phaseOn(date: LocalDate): String = when (kindOn(date)) {
        CycleDayKind.PERIOD -> "Period"
        CycleDayKind.PREDICTED_PERIOD -> "Period expected"
        CycleDayKind.OVULATION -> "Ovulation (estimate)"
        CycleDayKind.FERTILE -> "Fertile window (estimate)"
        CycleDayKind.PMS -> "PMS days"
        CycleDayKind.NONE -> {
            val next = anchors.firstOrNull { it.first > date }?.first
            when {
                next == null || actualStarts.isEmpty() || date < actualStarts.first() -> "No cycle data"
                date < next.minusDays(config.lutealDays.toLong()) -> "Follicular phase"
                else -> "Luteal phase"
            }
        }
    }

    /** True on days when energy is often lower: period and PMS days. */
    fun isSensitive(date: LocalDate): Boolean = kindOn(date).let { it == CycleDayKind.PERIOD || it == CycleDayKind.PMS }

    companion object {
        val TYPICAL_CYCLE = 21..35
        val TYPICAL_PERIOD = 2..7
        /** Cycles varying by more than this many days are commonly described as irregular. */
        const val REGULAR_VARIATION = 7
    }
}

/** Planned-vs-completed on period and PMS days compared with all other days. */
data class CycleProductivity(val sensitive: Rate, val other: Rate)

/**
 * Compares completion on period/PMS days with other days between [from] and [to]. Returns null
 * unless both groups have at least [minDays] days with something planned, so it never generalises
 * from a handful of days.
 */
fun cycleProductivity(
    history: History,
    engine: CycleEngine,
    from: LocalDate,
    to: LocalDate,
    minimumCounts: Boolean,
    minDays: Int = 5,
): CycleProductivity? {
    var sensitive = Rate.ZERO
    var other = Rate.ZERO
    var sensitiveDays = 0
    var otherDays = 0
    var d = from
    while (d <= to) {
        val stats = history.dayPlan(d, minimumCounts).stats
        if (!stats.isEmpty) {
            val r = Rate(stats.completed, stats.planned)
            if (engine.isSensitive(d)) { sensitive += r; sensitiveDays++ } else { other += r; otherDays++ }
        }
        d = d.plusDays(1)
    }
    return if (sensitiveDays >= minDays && otherDays >= minDays) CycleProductivity(sensitive, other) else null
}
