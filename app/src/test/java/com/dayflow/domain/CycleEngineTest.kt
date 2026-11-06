package com.dayflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CycleEngineTest {
    private val config = CycleConfig(defaultCycleLength = 28, defaultPeriodLength = 5, pmsDays = 5, lutealDays = 14)

    private fun period(start: LocalDate, length: Int) = (0 until length).map { start.plusDays(it.toLong()) }

    private val jul30 = LocalDate.of(2026, 7, 30)
    private val aug26 = LocalDate.of(2026, 8, 26)
    private val sep23 = LocalDate.of(2026, 9, 23)
    private val logged = period(jul30, 5) + period(aug26, 5) + period(sep23, 5)

    @Test
    fun groupsDaysIntoPeriodsAndCycles() {
        val e = CycleEngine(logged, config, LocalDate.of(2026, 10, 19))
        assertEquals(3, e.periods.size)
        assertEquals(listOf(27, 28), e.cycles.map { it.length })
        assertEquals(28, e.averageCycleLength) // 27.5 rounds to 28
        assertEquals(5, e.averagePeriodLength)
    }

    @Test
    fun oneMissingDayDoesNotSplitAPeriod() {
        val days = listOf(sep23, sep23.plusDays(1), sep23.plusDays(3))
        assertEquals(1, CycleEngine(days, config, sep23.plusDays(10)).periods.size)
    }

    @Test
    fun predictsNextPeriodAndCountsDown() {
        val e = CycleEngine(logged, config, LocalDate.of(2026, 10, 19))
        assertEquals(LocalDate.of(2026, 10, 21), e.nextPredictedStart)
        assertEquals(CycleStatus.Upcoming(daysUntil = 2, cycleDay = 27), e.status)
        assertEquals(CycleDayKind.PREDICTED_PERIOD, e.kindOn(LocalDate.of(2026, 10, 22)))
    }

    @Test
    fun reportsLatePeriod() {
        val e = CycleEngine(logged, config, LocalDate.of(2026, 10, 24))
        assertEquals(CycleStatus.Late(days = 3, cycleDay = 32), e.status)
    }

    @Test
    fun inPeriodShowsDayNumber() {
        val e = CycleEngine(logged, config, sep23.plusDays(1))
        assertEquals(CycleStatus.InPeriod(2), e.status)
    }

    @Test
    fun ovulationFertileAndPmsAreBeforeTheNextPeriod() {
        val e = CycleEngine(logged, config, LocalDate.of(2026, 10, 1))
        // Next actual start after Aug 26 is Sep 23: ovulation Sep 9, fertile Sep 4–10, PMS Sep 18–22.
        assertEquals(CycleDayKind.OVULATION, e.kindOn(LocalDate.of(2026, 9, 9)))
        assertEquals(CycleDayKind.FERTILE, e.kindOn(LocalDate.of(2026, 9, 4)))
        assertEquals(CycleDayKind.PMS, e.kindOn(LocalDate.of(2026, 9, 20)))
        assertEquals(CycleDayKind.NONE, e.kindOn(LocalDate.of(2026, 9, 14)))
        assertTrue(e.isSensitive(LocalDate.of(2026, 9, 22)))
    }

    @Test
    fun noDataUsesDefaults() {
        val e = CycleEngine(emptyList(), config, sep23)
        assertEquals(CycleStatus.NoData, e.status)
        assertEquals(28, e.averageCycleLength)
        assertEquals(CycleDayKind.NONE, e.kindOn(sep23))
    }
}
