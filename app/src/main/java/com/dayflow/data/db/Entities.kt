package com.dayflow.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.dayflow.domain.DaySection
import com.dayflow.domain.Days
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.HabitStatus
import com.dayflow.domain.Mood
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerStatus
import com.dayflow.domain.TaskStatus

// Dates are stored as epoch days (LocalDate.toEpochDay) and times of day as minutes after midnight.

@Entity(tableName = "tasks", indices = [Index("date"), Index("status")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val date: Long,
    val timeMinutes: Int? = null,
    /** 0 = low, 1 = normal, 2 = high. */
    val priority: Int = 1,
    val category: String = "",
    val estimatedMinutes: Int? = null,
    /** One of the day's top priorities. */
    val isFocus: Boolean = false,
    val remind: Boolean = false,
    val status: TaskStatus = TaskStatus.PENDING,
    val completedAt: Long? = null,
    /** The date the task was first planned for; kept when the task is rescheduled. */
    val originalDate: Long = date,
    val rescheduleCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_reschedules",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index("fromDate")],
)
data class TaskRescheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val fromDate: Long,
    val toDate: Long,
    val changedAt: Long = System.currentTimeMillis(),
)

/**
 * A habit's identity and its *current* configuration (what the editor shows). The configuration
 * that actually applied on any given day lives in [HabitScheduleEntity].
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "check",
    val color: Int,
    val category: String = "",
    val section: DaySection = DaySection.ANYTIME,
    val goalType: HabitGoalType = HabitGoalType.CHECK,
    val unit: String = "",
    val daysMask: Int = Days.ALL,
    val target: Double = 1.0,
    /** Optional "busy day" version of the habit, e.g. 5 minutes instead of 20. */
    val minimumTarget: Double? = null,
    val reminderMinutes: Int? = null,
    val status: HabitStatus = HabitStatus.ACTIVE,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A version of a habit's schedule and target, effective from [effectiveFrom] until the next version. */
@Entity(
    tableName = "habit_schedules",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")],
)
data class HabitScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val effectiveFrom: Long,
    /** 0 while the habit is paused or archived. */
    val daysMask: Int,
    val target: Double,
    val minimumTarget: Double?,
)

@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class HabitLogEntity(
    val habitId: Long,
    val date: Long,
    /** 1.0 for a checked yes/no habit, otherwise the recorded quantity or minutes. */
    val value: Double,
    val skipped: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A planned break: habits are not expected on rest days. */
@Entity(tableName = "rest_days")
data class RestDayEntity(
    @PrimaryKey val date: Long,
    val note: String = "",
)

/** One morning reflection per day. */
@Entity(tableName = "reflections")
data class ReflectionEntity(
    @PrimaryKey val date: Long,
    val mood: Mood? = null,
    /** 1 (drained) to 5 (full of energy). */
    val energy: Int? = null,
    val gratitude: String = "",
    val intention: String = "",
    val notes: String = "",
    /** File name of an attached photo inside the app's private storage. */
    val photoPath: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A recorded focus session. Breaks are not stored. */
@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    /** Time actually spent focusing, excluding pauses. */
    val actualSeconds: Int,
    /** False when the session was reset before the timer ran out. */
    val completed: Boolean,
    val interruptions: Int,
    val taskId: Long? = null,
    val habitId: Long? = null,
    val label: String = "",
)

/** A day with menstrual bleeding. Consecutive days form a period. */
@Entity(tableName = "period_days")
data class PeriodDayEntity(
    @PrimaryKey val date: Long,
    val flow: PeriodFlow = PeriodFlow.MEDIUM,
)

/** Symptoms logged for a day, stored as comma-separated [com.dayflow.domain.Symptom] names. */
@Entity(tableName = "cycle_symptoms")
data class CycleSymptomEntity(
    @PrimaryKey val date: Long,
    val symptoms: String = "",
    val note: String = "",
)

/** How one of the five daily prayers was kept on a day. No row means it isn't marked yet. */
@Entity(tableName = "prayer_logs", primaryKeys = ["date", "prayer"])
data class PrayerLogEntity(
    val date: Long,
    val prayer: Prayer,
    val status: PrayerStatus,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class HabitWithSchedules(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habitId")
    val schedules: List<HabitScheduleEntity>,
)
