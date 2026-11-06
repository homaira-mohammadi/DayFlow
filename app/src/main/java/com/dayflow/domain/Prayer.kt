package com.dayflow.domain

import java.time.LocalDate

/** The five daily prayers, in the order they are prayed. */
enum class Prayer(val label: String, val arabic: String) {
    FAJR("Fajr", "الفجر"),
    DHUHR("Dhuhr", "الظهر"),
    ASR("Asr", "العصر"),
    MAGHRIB("Maghrib", "المغرب"),
    ISHA("Isha", "العشاء"),
}

/** How a prayer was kept. A prayer with no record yet is simply unmarked. */
enum class PrayerStatus(val label: String) {
    ON_TIME("On time"),
    LATE("Late"),
    MISSED("Missed"),
}

data class PrayerSummary(val onTime: Int, val late: Int, val missed: Int) {
    /** Prayed at all, on time or late (qada). */
    val prayed: Int get() = onTime + late
    val unmarked: Int get() = Prayer.entries.size - onTime - late - missed
    val allPrayed: Boolean get() = prayed == Prayer.entries.size
}

/** One day of prayer records, for charts and history. */
data class PrayerDay(val date: LocalDate, val summary: PrayerSummary, val excused: Boolean)

object PrayerRules {
    fun summary(day: Map<Prayer, PrayerStatus>): PrayerSummary = PrayerSummary(
        onTime = day.values.count { it == PrayerStatus.ON_TIME },
        late = day.values.count { it == PrayerStatus.LATE },
        missed = day.values.count { it == PrayerStatus.MISSED },
    )

    /**
     * Consecutive days on which all five prayers were prayed (on time or late). Today only adds to
     * the streak once it is complete, so an unfinished today never breaks yesterday's streak.
     * [excused] days (period days) are skipped: they neither add to nor break the streak.
     */
    fun streak(
        logs: Map<LocalDate, Map<Prayer, PrayerStatus>>,
        today: LocalDate,
        excused: Set<LocalDate> = emptySet(),
    ): Int {
        fun complete(d: LocalDate) = summary(logs[d].orEmpty()).allPrayed
        var day = if (complete(today) || today in excused) today else today.minusDays(1)
        var count = 0
        while (true) {
            when {
                day in excused -> {}
                complete(day) -> count++
                else -> return count
            }
            day = day.minusDays(1)
        }
    }

    /** Every day from [from] to [to] (inclusive), oldest first. */
    fun history(
        logs: Map<LocalDate, Map<Prayer, PrayerStatus>>,
        from: LocalDate,
        to: LocalDate,
        excused: Set<LocalDate> = emptySet(),
    ): List<PrayerDay> = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
        .map { PrayerDay(it, summary(logs[it].orEmpty()), it in excused) }
        .toList()

    /** The longest run of complete days within [days]; excused days neither add to nor break a run. */
    fun bestStreak(days: List<PrayerDay>): Int {
        var best = 0
        var run = 0
        for (d in days) {
            when {
                d.excused -> {}
                d.summary.allPrayed -> { run++; best = maxOf(best, run) }
                else -> run = 0
            }
        }
        return best
    }
}
