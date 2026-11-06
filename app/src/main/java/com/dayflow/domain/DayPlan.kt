package com.dayflow.domain

import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import com.dayflow.data.db.HabitWithSchedules
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.db.TaskRescheduleEntity
import java.time.LocalDate

data class HabitDayItem(
    val habit: HabitEntity,
    val schedule: HabitScheduleEntity,
    val log: HabitLogEntity?,
    val minimumCounts: Boolean,
) {
    val value: Double get() = log?.value ?: 0.0
    val skipped: Boolean get() = log?.skipped == true
    val achievement: Achievement get() = HabitRules.achievement(value, schedule.target, schedule.minimumTarget)
    val isDone: Boolean get() = achievement.counts(minimumCounts)
    val fraction: Float get() = if (schedule.target <= 0) 0f else (value / schedule.target).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Completion for a day. Cancelled tasks, skipped habits and habits on a rest day are not
 * eligible, so they never count as incomplete.
 */
data class DayStats(val planned: Int, val completed: Int) {
    val fraction: Float get() = if (planned == 0) 0f else completed.toFloat() / planned
    val isEmpty: Boolean get() = planned == 0

    companion object {
        val EMPTY = DayStats(0, 0)
    }
}

data class DayPlan(
    val date: LocalDate,
    val tasks: List<TaskEntity>,
    val habits: List<HabitDayItem>,
    val isRestDay: Boolean,
    /** Tasks that were planned for this day and moved elsewhere (plan vs. reality). */
    val postponed: List<Pair<TaskRescheduleEntity, TaskEntity?>>,
) {
    val stats: DayStats
        get() {
            val eligibleTasks = tasks.filter { it.status != TaskStatus.CANCELLED }
            val eligibleHabits = if (isRestDay) emptyList() else habits.filter { !it.skipped }
            return DayStats(
                planned = eligibleTasks.size + eligibleHabits.size,
                completed = eligibleTasks.count { it.status == TaskStatus.DONE } + eligibleHabits.count { it.isDone },
            )
        }

    val focusTasks: List<TaskEntity> get() = tasks.filter { it.isFocus && it.status != TaskStatus.CANCELLED }
}

/** Snapshot of everything recorded, indexed for the screens' calculations. */
class History(
    val habits: List<HabitWithSchedules>,
    val tasks: List<TaskEntity>,
    logs: List<HabitLogEntity>,
    val restDays: Set<Long>,
    reschedules: List<TaskRescheduleEntity>,
) {
    private val tasksByDay: Map<Long, List<TaskEntity>> = tasks.groupBy { it.date }
    private val tasksById: Map<Long, TaskEntity> = tasks.associateBy { it.id }
    val logsByHabit: Map<Long, Map<Long, HabitLogEntity>> =
        logs.groupBy { it.habitId }.mapValues { (_, l) -> l.associateBy { it.date } }
    private val reschedulesByDay = reschedules.groupBy { it.fromDate }

    fun logsFor(habitId: Long): Map<Long, HabitLogEntity> = logsByHabit[habitId].orEmpty()

    fun dayPlan(date: LocalDate, minimumCounts: Boolean): DayPlan {
        val day = date.toEpochDay()
        val habitItems = habits.mapNotNull { h ->
            val s = HabitRules.dueOn(h.schedules, date) ?: return@mapNotNull null
            HabitDayItem(h.habit, s, logsByHabit[h.habit.id]?.get(day), minimumCounts)
        }.sortedWith(compareBy({ it.habit.section.ordinal }, { it.habit.sortOrder }, { it.habit.id }))
        val dayTasks = tasksByDay[day].orEmpty().sortedWith(
            compareBy<TaskEntity>({ it.timeMinutes ?: Int.MAX_VALUE }, { -it.priority }, { it.id }),
        )
        val moved = reschedulesByDay[day].orEmpty()
            .filter { it.toDate != day }
            .map { it to tasksById[it.taskId] }
        return DayPlan(date, dayTasks, habitItems, day in restDays, moved)
    }

    fun overdueTasks(today: LocalDate): List<TaskEntity> {
        val t = today.toEpochDay()
        return tasks.filter { it.status == TaskStatus.PENDING && it.date < t }.sortedBy { it.date }
    }

    fun currentStreak(h: HabitWithSchedules, today: LocalDate, minimumCounts: Boolean) =
        HabitRules.currentStreak(h.schedules, logsFor(h.habit.id), restDays, today, minimumCounts)

    fun bestStreak(h: HabitWithSchedules, today: LocalDate, minimumCounts: Boolean) =
        HabitRules.bestStreak(h.schedules, logsFor(h.habit.id), restDays, today, minimumCounts)

    fun consistency(h: HabitWithSchedules, from: LocalDate, to: LocalDate, today: LocalDate, minimumCounts: Boolean) =
        HabitRules.consistency(h.schedules, logsFor(h.habit.id), restDays, from, to, today, minimumCounts)

    fun outcome(h: HabitWithSchedules, date: LocalDate, today: LocalDate, minimumCounts: Boolean) =
        HabitRules.outcome(h.schedules, logsFor(h.habit.id), restDays, date, today, minimumCounts)

    val categories: List<String>
        get() = (tasks.map { it.category } + habits.map { it.habit.category })
            .filter { it.isNotBlank() }.distinct().sorted()

    companion object {
        val EMPTY = History(emptyList(), emptyList(), emptyList(), emptySet(), emptyList())
    }
}
