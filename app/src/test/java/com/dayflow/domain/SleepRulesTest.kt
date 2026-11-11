package com.dayflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SleepRulesTest {
    private val day = LocalDate.of(2026, 11, 6)
    private fun t(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun durationCrossesMidnight() {
        assertEquals(t(7, 30), SleepRules.duration(t(23), t(6, 30)))
        assertEquals(t(6), SleepRules.duration(t(1), t(7)))
        assertEquals(t(2), SleepRules.duration(t(13), t(15))) // a nap on the same day
    }

    @Test
    fun averageBedtimeAroundMidnight() {
        // 23:30 and 00:30 average to midnight, not to noon.
        assertEquals(0, SleepRules.averageBedtime(listOf(t(23, 30), t(0, 30))))
        assertEquals(t(23, 15), SleepRules.averageBedtime(listOf(t(23), t(23, 30))))
        assertNull(SleepRules.averageBedtime(emptyList()))
    }

    @Test
    fun summaryCountsGoalAndExtremes() {
        val nights = listOf(
            SleepNight(day, t(23), t(7)), // 8h
            SleepNight(day.minusDays(1), t(0), t(6)), // 6h
            SleepNight(day.minusDays(2), t(22, 30), t(7)), // 8h30
        )
        val s = SleepRules.summary(nights, goalMinutes = t(8))
        assertEquals(3, s.nights)
        assertEquals(t(7, 30), s.averageMinutes)
        assertEquals(2, s.metGoal)
        assertEquals(day.minusDays(2), s.longest!!.date)
        assertEquals(day.minusDays(1), s.shortest!!.date)
        assertEquals(t(6, 40), s.averageWake)
    }

    @Test
    fun historyFillsGapsWithNull() {
        val n = SleepNight(day, t(23), t(7))
        val h = SleepRules.history(mapOf(day to n), day.minusDays(2), day)
        assertEquals(3, h.size)
        assertNull(h[0].second)
        assertEquals(n, h[2].second)
    }

    @Test
    fun formatsDurations() {
        assertEquals("7h 30m", SleepRules.formatDuration(t(7, 30)))
        assertEquals("8h", SleepRules.formatDuration(t(8)))
        assertEquals("45m", SleepRules.formatDuration(45))
    }
}
