package com.dayflow.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.dayflow.domain.Prayer
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND remind = 1 AND timeMinutes IS NOT NULL AND date >= :today")
    suspend fun pendingWithReminders(today: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE date = :day AND status != 'CANCELLED'")
    suspend fun countOn(day: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE date = :day AND status = 'DONE'")
    suspend fun countDoneOn(day: Long): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Insert
    suspend fun insertReschedule(r: TaskRescheduleEntity)

    @Query("SELECT * FROM task_reschedules")
    fun observeReschedules(): Flow<List<TaskRescheduleEntity>>

    @Query("SELECT * FROM task_reschedules")
    suspend fun allReschedules(): List<TaskRescheduleEntity>
}

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<HabitWithSchedules>>

    @Transaction
    @Query("SELECT * FROM habits ORDER BY sortOrder, id")
    suspend fun all(): List<HabitWithSchedules>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): HabitWithSchedules?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM habits")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habit_schedules WHERE habitId = :habitId AND effectiveFrom = :day")
    suspend fun scheduleStartingOn(habitId: Long, day: Long): HabitScheduleEntity?

    @Insert
    suspend fun insertSchedule(s: HabitScheduleEntity)

    @Update
    suspend fun updateSchedule(s: HabitScheduleEntity)

    @Query("SELECT * FROM habit_schedules")
    suspend fun allSchedules(): List<HabitScheduleEntity>

    @Query("SELECT * FROM habit_logs")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs")
    suspend fun allLogs(): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :day")
    suspend fun log(habitId: Long, day: Long): HabitLogEntity?

    @Upsert
    suspend fun upsertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :day")
    suspend fun deleteLog(habitId: Long, day: Long)
}

@Dao
interface RestDayDao {
    @Query("SELECT * FROM rest_days")
    fun observeAll(): Flow<List<RestDayEntity>>

    @Query("SELECT * FROM rest_days")
    suspend fun all(): List<RestDayEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM rest_days WHERE date = :day)")
    suspend fun isRestDay(day: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(day: RestDayEntity)

    @Query("DELETE FROM rest_days WHERE date = :day")
    suspend fun delete(day: Long)
}

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM reflections ORDER BY date DESC")
    fun observeAll(): Flow<List<ReflectionEntity>>

    @Query("SELECT * FROM reflections ORDER BY date DESC")
    suspend fun all(): List<ReflectionEntity>

    @Query("SELECT * FROM reflections WHERE date = :day")
    suspend fun get(day: Long): ReflectionEntity?

    @Upsert
    suspend fun upsert(reflection: ReflectionEntity)

    @Query("DELETE FROM reflections WHERE date = :day")
    suspend fun delete(day: Long)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    suspend fun all(): List<FocusSessionEntity>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long
}

@Dao
interface CycleDao {
    @Query("SELECT * FROM period_days ORDER BY date")
    fun observePeriodDays(): Flow<List<PeriodDayEntity>>

    @Query("SELECT * FROM period_days ORDER BY date")
    suspend fun periodDays(): List<PeriodDayEntity>

    @Upsert
    suspend fun upsertPeriodDays(days: List<PeriodDayEntity>)

    @Query("DELETE FROM period_days WHERE date = :day")
    suspend fun deletePeriodDay(day: Long)

    @Query("SELECT * FROM cycle_symptoms ORDER BY date")
    fun observeSymptoms(): Flow<List<CycleSymptomEntity>>

    @Query("SELECT * FROM cycle_symptoms ORDER BY date")
    suspend fun symptoms(): List<CycleSymptomEntity>

    @Upsert
    suspend fun upsertSymptoms(entry: CycleSymptomEntity)

    @Query("DELETE FROM cycle_symptoms WHERE date = :day")
    suspend fun deleteSymptoms(day: Long)

    @Query("DELETE FROM period_days")
    suspend fun deleteAllPeriodDays()

    @Query("DELETE FROM cycle_symptoms")
    suspend fun deleteAllSymptoms()
}

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_logs")
    fun observeAll(): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayer_logs ORDER BY date, prayer")
    suspend fun all(): List<PrayerLogEntity>

    @Upsert
    suspend fun upsert(entry: PrayerLogEntity)

    @Query("DELETE FROM prayer_logs WHERE date = :day AND prayer = :prayer")
    suspend fun delete(day: Long, prayer: Prayer)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY id")
    suspend fun goals(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: Long): GoalEntity?

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM goal_milestones ORDER BY sortOrder, id")
    fun observeMilestones(): Flow<List<GoalMilestoneEntity>>

    @Query("SELECT * FROM goal_milestones ORDER BY sortOrder, id")
    suspend fun milestones(): List<GoalMilestoneEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM goal_milestones WHERE goalId = :goalId")
    suspend fun maxMilestoneOrder(goalId: Long): Int

    @Insert
    suspend fun insertMilestone(milestone: GoalMilestoneEntity): Long

    @Update
    suspend fun updateMilestone(milestone: GoalMilestoneEntity)

    @Query("DELETE FROM goal_milestones WHERE id = :id")
    suspend fun deleteMilestone(id: Long)

    @Query("SELECT * FROM goal_tasks")
    fun observeTaskLinks(): Flow<List<GoalTaskEntity>>

    @Query("SELECT * FROM goal_tasks ORDER BY taskId")
    suspend fun taskLinks(): List<GoalTaskEntity>

    /** A task belongs to one goal, so linking it again moves it. */
    @Upsert
    suspend fun linkTask(link: GoalTaskEntity)

    @Query("DELETE FROM goal_tasks WHERE taskId = :taskId")
    suspend fun unlinkTask(taskId: Long)

    @Query("SELECT * FROM goal_habits")
    fun observeHabitLinks(): Flow<List<GoalHabitEntity>>

    @Query("SELECT * FROM goal_habits ORDER BY habitId")
    suspend fun habitLinks(): List<GoalHabitEntity>

    @Upsert
    suspend fun linkHabit(link: GoalHabitEntity)

    @Query("DELETE FROM goal_habits WHERE habitId = :habitId")
    suspend fun unlinkHabit(habitId: Long)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_logs ORDER BY date")
    fun observeAll(): Flow<List<SleepLogEntity>>

    @Query("SELECT * FROM sleep_logs ORDER BY date")
    suspend fun all(): List<SleepLogEntity>

    @Query("SELECT * FROM sleep_logs ORDER BY date DESC LIMIT 1")
    suspend fun latest(): SleepLogEntity?

    @Upsert
    suspend fun upsert(entry: SleepLogEntity)

    @Query("DELETE FROM sleep_logs WHERE date = :day")
    suspend fun delete(day: Long)
}

/** Restores a backup: swaps the stored records for the ones in a backup file. */
@Dao
abstract class BackupDao {
    @Query("DELETE FROM goal_tasks") protected abstract suspend fun clearGoalTasks()
    @Query("DELETE FROM goal_habits") protected abstract suspend fun clearGoalHabits()
    @Query("DELETE FROM goal_milestones") protected abstract suspend fun clearGoalMilestones()
    @Query("DELETE FROM goals") protected abstract suspend fun clearGoals()
    @Query("DELETE FROM habit_logs") protected abstract suspend fun clearHabitLogs()
    @Query("DELETE FROM habit_schedules") protected abstract suspend fun clearHabitSchedules()
    @Query("DELETE FROM habits") protected abstract suspend fun clearHabits()
    @Query("DELETE FROM task_reschedules") protected abstract suspend fun clearTaskReschedules()
    @Query("DELETE FROM tasks") protected abstract suspend fun clearTasks()
    @Query("DELETE FROM rest_days") protected abstract suspend fun clearRestDays()
    @Query("DELETE FROM focus_sessions") protected abstract suspend fun clearFocusSessions()
    @Query("DELETE FROM reflections") protected abstract suspend fun clearReflections()
    @Query("DELETE FROM prayer_logs") protected abstract suspend fun clearPrayerLogs()
    @Query("DELETE FROM sleep_logs") protected abstract suspend fun clearSleepLogs()
    @Query("DELETE FROM period_days") protected abstract suspend fun clearPeriodDays()
    @Query("DELETE FROM cycle_symptoms") protected abstract suspend fun clearCycleSymptoms()

    @Insert protected abstract suspend fun insertHabits(rows: List<HabitEntity>)
    @Insert protected abstract suspend fun insertHabitSchedules(rows: List<HabitScheduleEntity>)
    @Insert protected abstract suspend fun insertHabitLogs(rows: List<HabitLogEntity>)
    @Insert protected abstract suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert protected abstract suspend fun insertTaskReschedules(rows: List<TaskRescheduleEntity>)
    @Insert protected abstract suspend fun insertRestDays(rows: List<RestDayEntity>)
    @Insert protected abstract suspend fun insertFocusSessions(rows: List<FocusSessionEntity>)
    @Insert protected abstract suspend fun insertReflections(rows: List<ReflectionEntity>)
    @Insert protected abstract suspend fun insertPrayerLogs(rows: List<PrayerLogEntity>)
    @Insert protected abstract suspend fun insertSleepLogs(rows: List<SleepLogEntity>)
    @Insert protected abstract suspend fun insertPeriodDays(rows: List<PeriodDayEntity>)
    @Insert protected abstract suspend fun insertCycleSymptoms(rows: List<CycleSymptomEntity>)
    @Insert protected abstract suspend fun insertGoals(rows: List<GoalEntity>)
    @Insert protected abstract suspend fun insertGoalMilestones(rows: List<GoalMilestoneEntity>)
    @Insert protected abstract suspend fun insertGoalTasks(rows: List<GoalTaskEntity>)
    @Insert protected abstract suspend fun insertGoalHabits(rows: List<GoalHabitEntity>)

    /**
     * Runs as one transaction: if any row is rejected, nothing changes and the old records stay.
     * Period data is only replaced when the backup carries it.
     */
    @Transaction
    open suspend fun replaceAll(data: BackupData) {
        clearGoalTasks()
        clearGoalHabits()
        clearGoalMilestones()
        clearGoals()
        clearHabitLogs()
        clearHabitSchedules()
        clearHabits()
        clearTaskReschedules()
        clearTasks()
        clearRestDays()
        clearFocusSessions()
        clearReflections()
        clearPrayerLogs()
        clearSleepLogs()

        insertHabits(data.habits)
        insertHabitSchedules(data.habitSchedules)
        insertHabitLogs(data.habitLogs)
        insertTasks(data.tasks)
        insertTaskReschedules(data.taskReschedules)
        insertRestDays(data.restDays)
        insertFocusSessions(data.focusSessions)
        insertReflections(data.reflections)
        insertPrayerLogs(data.prayerLogs)
        insertSleepLogs(data.sleepLogs)
        insertGoals(data.goals)
        insertGoalMilestones(data.goalMilestones)
        insertGoalTasks(data.goalTasks)
        insertGoalHabits(data.goalHabits)

        data.periodDays?.let { clearPeriodDays(); insertPeriodDays(it) }
        data.cycleSymptoms?.let { clearCycleSymptoms(); insertCycleSymptoms(it) }
    }
}
