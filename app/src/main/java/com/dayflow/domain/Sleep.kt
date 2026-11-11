package com.dayflow.domain

import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One night of sleep, filed under the morning you woke up. Times are minutes after midnight;
 * a bedtime later than the wake-up time means you went to bed the evening before.
 */
data class SleepNight(val date: LocalDate, val bedMinutes: Int, val wakeMinutes: Int) {
    /** Minutes asleep, from 0 to just under 24 hours. */
    val durationMinutes: Int get() = SleepRules.duration(bedMinutes, wakeMinutes)
    val hours: Float get() = durationMinutes / 60f
}

data class SleepSummary(
    val nights: Int,
    val averageMinutes: Int?,
    /** Average bedtime as minutes after midnight (0–1439), or null with no nights. */
    val averageBedtime: Int?,
    val averageWake: Int?,
    val longest: SleepNight?,
    val shortest: SleepNight?,
    /** Nights that reached the sleep goal. */
    val metGoal: Int,
    /** Typical spread of wake-up times in minutes (standard deviation); lower is steadier. */
    val wakeSpread: Int?,
)

object SleepRules {
    private const val DAY = 24 * 60

    fun duration(bedMinutes: Int, wakeMinutes: Int): Int = ((wakeMinutes - bedMinutes) % DAY + DAY) % DAY

    /**
     * Bedtimes sit on both sides of midnight (23:30, 00:15...), so they are averaged on a scale
     * that runs from noon to noon: 23:30 → -30 and 00:15 → +15, then mapped back to a clock time.
     */
    fun averageBedtime(bedtimes: List<Int>): Int? {
        if (bedtimes.isEmpty()) return null
        val avg = bedtimes.map { if (it >= DAY / 2) it - DAY else it }.average().roundToInt()
        return (avg + DAY) % DAY
    }

    /** Wake-up times are averaged as they are (mornings rarely cross midnight). */
    fun averageWake(wakes: List<Int>): Int? = if (wakes.isEmpty()) null else wakes.average().roundToInt()

    fun summary(nights: List<SleepNight>, goalMinutes: Int): SleepSummary {
        val wakes = nights.map { it.wakeMinutes }
        val avgWake = averageWake(wakes)
        return SleepSummary(
            nights = nights.size,
            averageMinutes = if (nights.isEmpty()) null else nights.map { it.durationMinutes }.average().roundToInt(),
            averageBedtime = averageBedtime(nights.map { it.bedMinutes }),
            averageWake = avgWake,
            longest = nights.maxByOrNull { it.durationMinutes },
            shortest = nights.minByOrNull { it.durationMinutes },
            metGoal = nights.count { it.durationMinutes >= goalMinutes },
            wakeSpread = if (nights.size < 2 || avgWake == null) null
            else sqrt(wakes.map { (it - avgWake).toDouble().let { d -> d * d } }.average()).roundToInt(),
        )
    }

    /** One entry per day from [from] to [to], null where nothing was logged. */
    fun history(nights: Map<LocalDate, SleepNight>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, SleepNight?>> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it to nights[it] }.toList()

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }
}
