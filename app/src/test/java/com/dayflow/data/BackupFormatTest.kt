package com.dayflow.data

import com.dayflow.data.db.BackupData
import com.dayflow.data.db.CycleSymptomEntity
import com.dayflow.data.db.FocusSessionEntity
import com.dayflow.data.db.GoalEntity
import com.dayflow.data.db.GoalHabitEntity
import com.dayflow.data.db.GoalMilestoneEntity
import com.dayflow.data.db.GoalTaskEntity
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import com.dayflow.data.db.PeriodDayEntity
import com.dayflow.data.db.PrayerLogEntity
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.data.db.RestDayEntity
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.db.TaskRescheduleEntity
import com.dayflow.data.repo.BackupFormat
import com.dayflow.data.repo.BackupFormatException
import com.dayflow.domain.DaySection
import com.dayflow.domain.GoalStatus
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.HabitStatus
import com.dayflow.domain.Mood
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerStatus
import com.dayflow.domain.TaskStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private val sample = BackupData(
        habits = listOf(
            HabitEntity(
                id = 3, name = "Read", icon = "book", color = -16711936, category = "Mind", section = DaySection.EVENING,
                goalType = HabitGoalType.DURATION, unit = "min", daysMask = 62, target = 20.0, minimumTarget = 5.0,
                reminderMinutes = 1260, status = HabitStatus.PAUSED, sortOrder = 4, createdAt = 1_700_000_000_000,
            ),
            HabitEntity(id = 7, name = "Water", color = 1, createdAt = 1_700_000_000_001),
        ),
        habitSchedules = listOf(HabitScheduleEntity(habitId = 3, effectiveFrom = 19_700, daysMask = 62, target = 20.0, minimumTarget = null)),
        habitLogs = listOf(HabitLogEntity(habitId = 3, date = 19_701, value = 12.5, skipped = true, updatedAt = 5)),
        tasks = listOf(
            TaskEntity(
                id = 9, title = "Call \"Sam\"", notes = "line1\nline2", date = 19_702, timeMinutes = 540, priority = 2,
                category = "Home", estimatedMinutes = 15, isFocus = true, remind = true, status = TaskStatus.DONE,
                completedAt = 77, originalDate = 19_700, rescheduleCount = 1, createdAt = 66,
            ),
        ),
        taskReschedules = listOf(TaskRescheduleEntity(taskId = 9, fromDate = 19_700, toDate = 19_702, changedAt = 70)),
        restDays = listOf(RestDayEntity(date = 19_710, note = "Travel")),
        focusSessions = listOf(
            FocusSessionEntity(
                startedAt = 100, endedAt = 200, plannedSeconds = 1500, actualSeconds = 1400, completed = false,
                interruptions = 2, taskId = 9, habitId = null, label = "Deep work",
            ),
        ),
        reflections = listOf(
            ReflectionEntity(date = 19_703, mood = Mood.CALM, energy = 4, gratitude = "g", intention = "i", notes = "n", photoPath = "p.jpg", updatedAt = 8),
            ReflectionEntity(date = 19_704, updatedAt = 9),
        ),
        prayerLogs = listOf(PrayerLogEntity(date = 19_703, prayer = Prayer.FAJR, status = PrayerStatus.LATE, updatedAt = 3)),
        goals = listOf(
            GoalEntity(id = 2, title = "Improve programming skills", description = "For the new job", targetDate = 20_800, status = GoalStatus.ACHIEVED, createdAt = 11),
            GoalEntity(id = 5, title = "Get fit", createdAt = 12),
        ),
        goalMilestones = listOf(
            GoalMilestoneEntity(goalId = 2, title = "Build one project", done = true, sortOrder = 1),
            GoalMilestoneEntity(goalId = 2, title = "Solve 50 problems", sortOrder = 2),
        ),
        goalTasks = listOf(GoalTaskEntity(taskId = 9, goalId = 2)),
        goalHabits = listOf(GoalHabitEntity(habitId = 3, goalId = 2)),
        periodDays = listOf(PeriodDayEntity(date = 19_690, flow = PeriodFlow.HEAVY)),
        cycleSymptoms = listOf(CycleSymptomEntity(date = 19_690, symptoms = "CRAMPS,FATIGUE", note = "rest")),
    )

    private fun export(includeCycle: Boolean) = BackupFormat.toJson(sample, includeCycle).toString(2)

    private fun failure(text: String) = assertThrows(BackupFormatException::class.java) { BackupFormat.parse(text) }.message!!

    @Test fun `an exported backup reads back unchanged`() {
        assertEquals(sample, BackupFormat.parse(export(includeCycle = true)))
    }

    @Test fun `period data is left out unless asked for`() {
        val text = export(includeCycle = false)
        assertTrue("periodDays" !in text && "cycleSymptoms" !in text)
        val parsed = BackupFormat.parse(text)
        assertNull(parsed.periodDays)
        assertNull(parsed.cycleSymptoms)
        assertEquals(sample.copy(periodDays = null, cycleSymptoms = null), parsed)
    }

    @Test fun `a version 1 backup is still read`() {
        // What the first release wrote: plain rest-day dates, and no remind, sortOrder or prayerLogs.
        val v1 = JSONObject(export(includeCycle = false)).apply {
            put("version", 1)
            remove("prayerLogs")
            getJSONArray("habits").getJSONObject(0).remove("sortOrder")
            getJSONArray("tasks").getJSONObject(0).remove("remind")
            put("restDays", org.json.JSONArray(listOf(19_710L)))
        }
        val parsed = BackupFormat.parse(v1.toString())
        assertEquals(0, parsed.habits[0].sortOrder)
        assertEquals(false, parsed.tasks[0].remind)
        assertEquals(listOf(RestDayEntity(date = 19_710)), parsed.restDays)
        assertEquals(emptyList<PrayerLogEntity>(), parsed.prayerLogs)
    }

    @Test fun `a version 2 backup from before goals is still read`() {
        val v2 = JSONObject(export(includeCycle = false)).apply {
            put("version", 2)
            listOf("goals", "goalMilestones", "goalTasks", "goalHabits").forEach { remove(it) }
        }
        val parsed = BackupFormat.parse(v2.toString())
        assertEquals(sample.tasks, parsed.tasks)
        assertTrue(parsed.goals.isEmpty() && parsed.goalMilestones.isEmpty() && parsed.goalTasks.isEmpty() && parsed.goalHabits.isEmpty())
    }

    @Test fun `files that are not backups are refused`() {
        assertEquals("This isn't a Dayflow backup file.", failure("hello"))
        assertEquals("This isn't a Dayflow backup file.", failure("{\"habits\": []}"))
        assertEquals("This isn't a Dayflow backup file.", failure("[1, 2]"))
    }

    @Test fun `a backup from a newer app version is refused`() {
        val newer = JSONObject(export(includeCycle = false)).put("version", BackupFormat.VERSION + 1)
        assertTrue("newer version" in failure(newer.toString()))
    }

    @Test fun `a damaged backup is refused`() {
        val missingField = JSONObject(export(includeCycle = false)).apply { getJSONArray("tasks").getJSONObject(0).remove("title") }
        val unknownEnum = JSONObject(export(includeCycle = false)).apply { getJSONArray("habits").getJSONObject(0).put("status", "GONE") }
        val zeroId = JSONObject(export(includeCycle = false)).apply { getJSONArray("habits").getJSONObject(0).put("id", 0) }
        val wrongShape = JSONObject(export(includeCycle = false)).put("tasks", "none")
        listOf(missingField, unknownEnum, zeroId, wrongShape).forEach {
            assertTrue("damaged" in failure(it.toString()))
        }
    }
}
