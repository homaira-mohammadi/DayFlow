package com.dayflow.data.repo

import androidx.room.withTransaction
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.db.TaskRescheduleEntity
import com.dayflow.domain.TaskStatus
import com.dayflow.notifications.ReminderScheduler
import java.time.LocalDate

class TaskRepository(
    private val db: DayFlowDatabase,
    private val reminders: ReminderScheduler,
) {
    private val dao = db.taskDao()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(task: TaskEntity): Long {
        val id = db.withTransaction {
            if (task.id == 0L) return@withTransaction dao.insert(task.copy(originalDate = task.date))
            val old = dao.get(task.id)
            if (old != null && old.date != task.date) {
                // Changing the date in the editor is a reschedule: keep the evidence.
                dao.insertReschedule(TaskRescheduleEntity(taskId = task.id, fromDate = old.date, toDate = task.date))
                dao.update(task.copy(rescheduleCount = old.rescheduleCount + 1))
            } else {
                dao.update(task)
            }
            task.id
        }
        reminders.refreshTask(id)
        return id
    }

    suspend fun setStatus(task: TaskEntity, status: TaskStatus) {
        dao.update(task.copy(status = status, completedAt = if (status == TaskStatus.DONE) System.currentTimeMillis() else null))
        if (status != TaskStatus.PENDING) reminders.cancelTaskNotification(task.id)
    }

    /** Moves a task to another date, recording where it came from so postponements stay visible. */
    suspend fun reschedule(task: TaskEntity, to: LocalDate) {
        val toDay = to.toEpochDay()
        if (toDay == task.date) return
        db.withTransaction {
            dao.insertReschedule(TaskRescheduleEntity(taskId = task.id, fromDate = task.date, toDate = toDay))
            dao.update(task.copy(date = toDay, rescheduleCount = task.rescheduleCount + 1))
        }
        reminders.refreshTask(task.id)
    }

    suspend fun delete(task: TaskEntity) {
        dao.delete(task)
        reminders.cancelTaskNotification(task.id)
    }
}
