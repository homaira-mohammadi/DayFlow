package com.dayflow.domain

import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import java.time.LocalDate

/** What a single day means for a single habit. */
enum class DayOutcome {
    /** The habit was not scheduled (wrong weekday, paused, or not created yet). */
    NOT_SCHEDULED,
    /** Scheduled, but excused: a rest day or an explicit skip. Neither helps nor breaks streaks. */
    EXCUSED,
    DONE,
    MISSED,
    /** Today or a future date that has not been completed yet. */
    PENDING,
}

data class Rate(val done: Int, val eligible: Int) {
    val fraction: Float get() = if (eligible == 0) 0f else done.toFloat() / eligible
    operator fun plus(other: Rate) = Rate(done + other.done, eligible + other.eligible)

    companion object {
        val ZERO = Rate(0, 0)
    }
}

/**
 * Habit calculations. Schedules are versioned (see [HabitScheduleEntity]) so editing a habit
 * changes only today and the future; past days are always judged by the version that applied then.
 */
object HabitRules {

    fun scheduleOn(schedules: List<HabitScheduleEntity>, day: Long): HabitScheduleEntity? {
        var best: HabitScheduleEntity? = null
        for (s in schedules) {
            if (s.effectiveFrom <= day && (best == null || s.effectiveFrom > best.effectiveFrom)) best = s
        }
        return best
    }

    /** The schedule version in effect on [date], or null when the habit isn't due that day. */
    fun dueOn(schedules: List<HabitScheduleEntity>, date: LocalDate): HabitScheduleEntity? {
        val s = scheduleOn(schedules, date.toEpochDay()) ?: return null
        return if (Days.contains(s.daysMask, date.dayOfWeek)) s else null
    }

    fun achievement(value: Double, target: Double, minimum: Double?): Achievement = when {
        target > 0 && value >= target -> Achievement.FULL
        minimum != null && minimum > 0 && value >= minimum -> Achievement.MINIMUM
        value > 0 -> Achievement.PARTIAL
        else -> Achievement.NONE
    }

    fun outcome(
        schedules: List<HabitScheduleEntity>,
        logs: Map<Long, HabitLogEntity>,
        restDays: Set<Long>,
        date: LocalDate,
        today: LocalDate,
        minimumCounts: Boolean,
    ): DayOutcome {
        val s = dueOn(schedules, date) ?: return DayOutcome.NOT_SCHEDULED
        val day = date.toEpochDay()
        val log = logs[day]
        if (day in restDays || log?.skipped == true) return DayOutcome.EXCUSED
        val a = achievement(log?.value ?: 0.0, s.target, s.minimumTarget)
        return when {
            a.counts(minimumCounts) -> DayOutcome.DONE
            date >= today -> DayOutcome.PENDING
            else -> DayOutcome.MISSED
        }
    }

    /**
     * Consecutive completed scheduled days ending today. An unfinished today does not break the
     * streak (the day isn't over); excused and unscheduled days are passed over.
     */
    fun currentStreak(
        schedules: List<HabitScheduleEntity>,
        logs: Map<Long, HabitLogEntity>,
        restDays: Set<Long>,
        today: LocalDate,
        minimumCounts: Boolean,
    ): Int {
        val first = schedules.minOfOrNull { it.effectiveFrom } ?: return 0
        var streak = 0
        var d = today
        while (d.toEpochDay() >= first) {
            when (outcome(schedules, logs, restDays, d, today, minimumCounts)) {
                DayOutcome.DONE -> streak++
                DayOutcome.MISSED -> return streak
                else -> Unit
            }
            d = d.minusDays(1)
        }
        return streak
    }

    fun bestStreak(
        schedules: List<HabitScheduleEntity>,
        logs: Map<Long, HabitLogEntity>,
        restDays: Set<Long>,
        today: LocalDate,
        minimumCounts: Boolean,
    ): Int {
        val first = schedules.minOfOrNull { it.effectiveFrom } ?: return 0
        var best = 0
        var run = 0
        var d = LocalDate.ofEpochDay(first)
        while (d <= today) {
            when (outcome(schedules, logs, restDays, d, today, minimumCounts)) {
                DayOutcome.DONE -> { run++; if (run > best) best = run }
                DayOutcome.MISSED -> run = 0
                else -> Unit
            }
            d = d.plusDays(1)
        }
        return best
    }

    /** Completed scheduled days over days that could have been completed (excludes excused and pending). */
    fun consistency(
        schedules: List<HabitScheduleEntity>,
        logs: Map<Long, HabitLogEntity>,
        restDays: Set<Long>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        minimumCounts: Boolean,
    ): Rate {
        var done = 0
        var eligible = 0
        var d = from
        while (d <= to) {
            when (outcome(schedules, logs, restDays, d, today, minimumCounts)) {
                DayOutcome.DONE -> { done++; eligible++ }
                DayOutcome.MISSED -> eligible++
                else -> Unit
            }
            d = d.plusDays(1)
        }
        return Rate(done, eligible)
    }
}
