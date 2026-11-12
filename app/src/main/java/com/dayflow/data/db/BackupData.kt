package com.dayflow.data.db

/** Every record a backup file carries, ready to be written to the database in one step. */
data class BackupData(
    val habits: List<HabitEntity>,
    val habitSchedules: List<HabitScheduleEntity>,
    val habitLogs: List<HabitLogEntity>,
    val tasks: List<TaskEntity>,
    val taskReschedules: List<TaskRescheduleEntity>,
    val restDays: List<RestDayEntity>,
    val focusSessions: List<FocusSessionEntity>,
    val reflections: List<ReflectionEntity>,
    val prayerLogs: List<PrayerLogEntity>,
    val sleepLogs: List<SleepLogEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val goalMilestones: List<GoalMilestoneEntity> = emptyList(),
    val goalTasks: List<GoalTaskEntity> = emptyList(),
    val goalHabits: List<GoalHabitEntity> = emptyList(),
    /** Null when the backup was made without period data. */
    val periodDays: List<PeriodDayEntity>? = null,
    /** Null when the backup was made without period data. */
    val cycleSymptoms: List<CycleSymptomEntity>? = null,
)
