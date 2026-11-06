package com.dayflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerRulesTest {
    private val today = LocalDate.of(2026, 11, 6)

    private fun allFive(status: PrayerStatus = PrayerStatus.ON_TIME) = Prayer.entries.associateWith { status }

    @Test
    fun summaryCountsEachStatus() {
        val s = PrayerRules.summary(
            mapOf(Prayer.FAJR to PrayerStatus.ON_TIME, Prayer.DHUHR to PrayerStatus.LATE, Prayer.ASR to PrayerStatus.MISSED),
        )
        assertEquals(1, s.onTime)
        assertEquals(1, s.late)
        assertEquals(1, s.missed)
        assertEquals(2, s.prayed)
        assertEquals(2, s.unmarked)
        assertFalse(s.allPrayed)
    }

    @Test
    fun latePrayersStillCompleteTheDay() {
        assertTrue(PrayerRules.summary(allFive(PrayerStatus.LATE)).allPrayed)
    }

    @Test
    fun unfinishedTodayKeepsYesterdaysStreak() {
        val logs = mapOf(
            today to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME),
            today.minusDays(1) to allFive(),
            today.minusDays(2) to allFive(PrayerStatus.LATE),
        )
        assertEquals(2, PrayerRules.streak(logs, today))
    }

    @Test
    fun completeTodayAddsToStreak() {
        val logs = mapOf(today to allFive(), today.minusDays(1) to allFive())
        assertEquals(2, PrayerRules.streak(logs, today))
    }

    @Test
    fun missedPrayerBreaksStreak() {
        val logs = mapOf(
            today.minusDays(1) to allFive(),
            today.minusDays(2) to allFive() + (Prayer.ISHA to PrayerStatus.MISSED),
            today.minusDays(3) to allFive(),
        )
        assertEquals(1, PrayerRules.streak(logs, today))
    }

    @Test
    fun periodDaysAreSkippedNotCountedOrBroken() {
        val logs = mapOf(
            today.minusDays(1) to allFive(),
            today.minusDays(4) to allFive(),
        )
        val period = setOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(2, PrayerRules.streak(logs, today, period))
    }

    @Test
    fun noRecordsMeansNoStreak() {
        assertEquals(0, PrayerRules.streak(emptyMap(), today))
    }

    @Test
    fun historyCoversEveryDayOldestFirst() {
        val logs = mapOf(today to mapOf(Prayer.FAJR to PrayerStatus.ON_TIME))
        val days = PrayerRules.history(logs, today.minusDays(6), today, setOf(today.minusDays(3)))
        assertEquals(7, days.size)
        assertEquals(today.minusDays(6), days.first().date)
        assertEquals(1, days.last().summary.onTime)
        assertTrue(days[3].excused)
    }

    @Test
    fun bestStreakFindsLongestRunAndSkipsExcusedDays() {
        val start = today.minusDays(9)
        val logs = buildMap {
            // Days 0-1 complete, day 2 incomplete, days 3-4 complete, day 5 excused, days 6-7 complete.
            listOf(0, 1, 3, 4, 6, 7).forEach { put(start.plusDays(it.toLong()), allFive()) }
        }
        val days = PrayerRules.history(logs, start, start.plusDays(9), setOf(start.plusDays(5)))
        assertEquals(4, PrayerRules.bestStreak(days))
    }
}
