package com.dayflow.data.repo

import androidx.room.withTransaction
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import com.dayflow.data.db.RestDayEntity
import com.dayflow.domain.HabitRules
import com.dayflow.domain.HabitStatus
import com.dayflow.notifications.ReminderScheduler
import java.time.LocalDate

class HabitRepository(
    private val db: DayFlowDatabase,
    private val reminders: ReminderScheduler,
) {
    private val dao = db.habitDao()
    private val restDays = db.restDayDao()

    suspend fun get(id: Long) = dao.get(id)

    /** Inserts or updates a habit. Schedule/target changes take effect from [today]; history is untouched. */
    suspend fun save(habit: HabitEntity, today: LocalDate = LocalDate.now()): Long {
        val id = db.withTransaction {
            val id = if (habit.id == 0L) {
                dao.insert(habit.copy(sortOrder = dao.maxSortOrder() + 1))
            } else {
                dao.update(habit)
                habit.id
            }
            if (habit.status == HabitStatus.ACTIVE) {
                writeVersion(id, today, habit.daysMask, habit.target, habit.minimumTarget)
            }
            id
        }
        reminders.refreshHabit(id)
        return id
    }

    /** Pausing or archiving stops the habit from being expected from today on, without touching the past. */
    suspend fun setStatus(id: Long, status: HabitStatus, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val h = dao.get(id)?.habit ?: return@withTransaction
            dao.update(h.copy(status = status))
            val mask = if (status == HabitStatus.ACTIVE) h.daysMask else 0
            writeVersion(id, today, mask, h.target, h.minimumTarget)
        }
        reminders.refreshHabit(id)
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        reminders.cancelHabitNotification(id)
    }

    private suspend fun writeVersion(id: Long, today: LocalDate, mask: Int, target: Double, minimum: Double?) {
        val day = today.toEpochDay()
        val existingToday = dao.scheduleStartingOn(id, day)
        if (existingToday != null) {
            dao.updateSchedule(existingToday.copy(daysMask = mask, target = target, minimumTarget = minimum))
            return
        }
        val current = HabitRules.scheduleOn(dao.get(id)?.schedules.orEmpty(), day)
        if (current != null && current.daysMask == mask && current.target == target && current.minimumTarget == minimum) return
        dao.insertSchedule(HabitScheduleEntity(habitId = id, effectiveFrom = day, daysMask = mask, target = target, minimumTarget = minimum))
    }

    suspend fun setValue(habitId: Long, date: LocalDate, value: Double) {
        val day = date.toEpochDay()
        if (value <= 0.0) dao.deleteLog(habitId, day)
        else dao.upsertLog(HabitLogEntity(habitId, day, value, skipped = false))
        if (date == LocalDate.now()) reminders.cancelHabitNotification(habitId)
    }

    /** Adds to the day's recorded amount, e.g. minutes from a focus session. */
    suspend fun addValue(habitId: Long, date: LocalDate, delta: Double) {
        val current = dao.log(habitId, date.toEpochDay())?.takeIf { !it.skipped }?.value ?: 0.0
        setValue(habitId, date, current + delta)
    }

    suspend fun setSkipped(habitId: Long, date: LocalDate, skipped: Boolean) {
        val day = date.toEpochDay()
        if (skipped) {
            dao.upsertLog(HabitLogEntity(habitId, day, value = 0.0, skipped = true))
            if (date == LocalDate.now()) reminders.cancelHabitNotification(habitId)
        } else {
            dao.deleteLog(habitId, day)
        }
    }

    suspend fun setRestDay(date: LocalDate, rest: Boolean) {
        if (rest) restDays.insert(RestDayEntity(date.toEpochDay())) else restDays.delete(date.toEpochDay())
    }
}
