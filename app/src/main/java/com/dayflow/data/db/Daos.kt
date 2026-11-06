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
