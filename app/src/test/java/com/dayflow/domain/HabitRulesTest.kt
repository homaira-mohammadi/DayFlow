package com.dayflow.domain

import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import com.dayflow.data.db.HabitWithSchedules
import com.dayflow.data.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitRulesTest {
    // 2026-10-12 is a Monday.
    private val monday = LocalDate.of(2026, 10, 12)

    private fun version(from: LocalDate, mask: Int, target: Double = 1.0, min: Double? = null) =
        HabitScheduleEntity(habitId = 1, effectiveFrom = from.toEpochDay(), daysMask = mask, target = target, minimumTarget = min)

    private fun logs(vararg entries: Pair<LocalDate, Double>) =
        entries.associate { (d, v) -> d.toEpochDay() to HabitLogEntity(1, d.toEpochDay(), v) }

    @Test
    fun weekdayHabitIsNotDueOnWeekend() {
        val s = listOf(version(monday, Days.WEEKDAYS))
        assertTrue(HabitRules.dueOn(s, monday) != null)
        assertNull(HabitRules.dueOn(s, monday.plusDays(5))) // Saturday
    }

    @Test
    fun missingWeekendDoesNotBreakWeekdayStreak() {
        val s = listOf(version(monday, Days.WEEKDAYS))
        val l = logs(*(0L..4L).map { monday.plusDays(it) to 1.0 }.toTypedArray(), monday.plusDays(7) to 1.0)
        // Next Monday; Saturday and Sunday unlogged but unscheduled.
        assertEquals(6, HabitRules.currentStreak(s, l, emptySet(), monday.plusDays(7), minimumCounts = true))
    }

    @Test
    fun unfinishedTodayDoesNotBreakStreak() {
        val s = listOf(version(monday, Days.ALL))
        val l = logs(monday to 1.0, monday.plusDays(1) to 1.0)
        assertEquals(2, HabitRules.currentStreak(s, l, emptySet(), monday.plusDays(2), minimumCounts = true))
    }

    @Test
    fun missedDayBreaksStreakButBestIsKept() {
        val s = listOf(version(monday, Days.ALL))
        val l = logs(monday to 1.0, monday.plusDays(1) to 1.0, monday.plusDays(2) to 1.0, monday.plusDays(4) to 1.0)
        val today = monday.plusDays(4)
        assertEquals(1, HabitRules.currentStreak(s, l, emptySet(), today, true))
        assertEquals(3, HabitRules.bestStreak(s, l, emptySet(), today, true))
    }

    @Test
    fun restDayAndSkipAreNeutral() {
        val s = listOf(version(monday, Days.ALL))
        val l = logs(monday to 1.0, monday.plusDays(2) to 1.0) +
            (monday.plusDays(3).toEpochDay() to HabitLogEntity(1, monday.plusDays(3).toEpochDay(), 0.0, skipped = true))
        val rest = setOf(monday.plusDays(1).toEpochDay())
        assertEquals(2, HabitRules.currentStreak(s, l, rest, monday.plusDays(3), true))
        val rate = HabitRules.consistency(s, l, rest, monday, monday.plusDays(3), monday.plusDays(3), true)
        assertEquals(Rate(2, 2), rate)
    }

    @Test
    fun partialProgressIsNotCompletion() {
        assertEquals(Achievement.PARTIAL, HabitRules.achievement(15.0, 30.0, null))
        assertEquals(Achievement.MINIMUM, HabitRules.achievement(5.0, 30.0, 5.0))
        assertEquals(Achievement.FULL, HabitRules.achievement(30.0, 30.0, 5.0))
        assertFalse(Achievement.MINIMUM.counts(minimumCounts = false))
        assertTrue(Achievement.MINIMUM.counts(minimumCounts = true))
    }

    @Test
    fun editingScheduleDoesNotRewriteHistory() {
        // Daily until Thursday, then weekends only.
        val thursday = monday.plusDays(3)
        val s = listOf(version(monday, Days.ALL, target = 20.0), version(thursday, Days.WEEKENDS, target = 30.0))
        assertEquals(20.0, HabitRules.dueOn(s, monday.plusDays(1))!!.target, 0.0)
        assertNull(HabitRules.dueOn(s, thursday))
        assertEquals(30.0, HabitRules.dueOn(s, monday.plusDays(5))!!.target, 0.0)
        // Tuesday was missed under the old schedule and stays missed.
        assertEquals(DayOutcome.MISSED, HabitRules.outcome(s, emptyMap(), emptySet(), monday.plusDays(1), monday.plusDays(6), true))
    }

    @Test
    fun pausedVersionIsNotDue() {
        val s = listOf(version(monday, Days.ALL), version(monday.plusDays(2), 0))
        assertTrue(HabitRules.dueOn(s, monday.plusDays(1)) != null)
        assertNull(HabitRules.dueOn(s, monday.plusDays(3)))
    }

    @Test
    fun dayStatsExcludeCancelledAndSkipped() {
        val habit = HabitEntity(id = 1, name = "Read", color = 0)
        val history = History(
            habits = listOf(HabitWithSchedules(habit, listOf(version(monday, Days.ALL)))),
            tasks = listOf(
                TaskEntity(id = 1, title = "a", date = monday.toEpochDay(), status = TaskStatus.DONE),
                TaskEntity(id = 2, title = "b", date = monday.toEpochDay()),
                TaskEntity(id = 3, title = "c", date = monday.toEpochDay(), status = TaskStatus.CANCELLED),
            ),
            logs = emptyList(),
            restDays = emptySet(),
            reschedules = emptyList(),
        )
        val stats = history.dayPlan(monday, true).stats
        assertEquals(DayStats(planned = 3, completed = 1), stats)

        val rest = History(history.habits, history.tasks, emptyList(), setOf(monday.toEpochDay()), emptyList())
        assertEquals(DayStats(planned = 2, completed = 1), rest.dayPlan(monday, true).stats)
    }
}
