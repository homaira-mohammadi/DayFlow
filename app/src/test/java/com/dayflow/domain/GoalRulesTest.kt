package com.dayflow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalRulesTest {

    @Test fun `progress counts finished milestones and tasks together`() {
        val p = GoalRules.progress(
            milestonesDone = listOf(true, false),
            tasks = listOf(TaskStatus.DONE, TaskStatus.DONE, TaskStatus.PENDING),
        )
        assertEquals(GoalProgress(done = 3, total = 5), p)
        assertEquals(0.6f, p.fraction, 0.0001f)
    }

    @Test fun `cancelled tasks are not steps`() {
        val p = GoalRules.progress(emptyList(), listOf(TaskStatus.DONE, TaskStatus.CANCELLED, TaskStatus.CANCELLED))
        assertEquals(GoalProgress(done = 1, total = 1), p)
        assertTrue(p.isComplete)
    }

    @Test fun `a goal with no steps has no progress and is not complete`() {
        val p = GoalRules.progress(emptyList(), emptyList())
        assertTrue(p.isEmpty)
        assertFalse(p.isComplete)
        assertEquals(0f, p.fraction, 0f)
        // Only cancelled tasks: still nothing to count.
        assertTrue(GoalRules.progress(emptyList(), listOf(TaskStatus.CANCELLED)).isEmpty)
    }

    @Test fun `an achieved goal shows a full bar whatever its steps say`() {
        val half = GoalProgress(1, 2)
        assertEquals(0.5f, GoalRules.shownFraction(GoalStatus.ACTIVE, half), 0f)
        assertEquals(0.5f, GoalRules.shownFraction(GoalStatus.ARCHIVED, half), 0f)
        assertEquals(1f, GoalRules.shownFraction(GoalStatus.ACHIEVED, half), 0f)
        assertEquals(1f, GoalRules.shownFraction(GoalStatus.ACHIEVED, GoalProgress.EMPTY), 0f)
    }

    @Test fun `days left counts down to the target date and goes negative after it`() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals(30L, GoalRules.daysLeft(today.plusDays(30).toEpochDay(), today))
        assertEquals(0L, GoalRules.daysLeft(today.toEpochDay(), today))
        assertEquals(-2L, GoalRules.daysLeft(today.minusDays(2).toEpochDay(), today))
        assertNull(GoalRules.daysLeft(null, today))
    }
}
