package com.dayflow.data.repo

import androidx.room.withTransaction
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.GoalEntity
import com.dayflow.data.db.GoalHabitEntity
import com.dayflow.data.db.GoalMilestoneEntity
import com.dayflow.data.db.GoalTaskEntity
import com.dayflow.domain.GoalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** A goal with everything attached to it. Tasks and habits are given by id; their details come from the plan history. */
data class GoalParts(
    val goal: GoalEntity,
    val milestones: List<GoalMilestoneEntity>,
    val taskIds: Set<Long>,
    val habitIds: Set<Long>,
)

class GoalRepository(private val db: DayFlowDatabase) {
    private val dao = db.goalDao()

    val goals: Flow<List<GoalParts>> = combine(
        dao.observeGoals(),
        dao.observeMilestones(),
        dao.observeTaskLinks(),
        dao.observeHabitLinks(),
    ) { goals, milestones, taskLinks, habitLinks ->
        val milestonesByGoal = milestones.groupBy { it.goalId }
        val tasksByGoal = taskLinks.groupBy({ it.goalId }, { it.taskId })
        val habitsByGoal = habitLinks.groupBy({ it.goalId }, { it.habitId })
        goals.map { g ->
            GoalParts(g, milestonesByGoal[g.id].orEmpty(), tasksByGoal[g.id].orEmpty().toSet(), habitsByGoal[g.id].orEmpty().toSet())
        }
    }.flowOn(Dispatchers.Default)

    suspend fun get(id: Long): GoalEntity? = dao.get(id)

    suspend fun save(goal: GoalEntity): Long =
        if (goal.id == 0L) dao.insert(goal) else goal.id.also { dao.update(goal) }

    suspend fun setStatus(id: Long, status: GoalStatus) {
        db.withTransaction { dao.get(id)?.let { dao.update(it.copy(status = status)) } }
    }

    /** Removes the goal with its milestones and links. The tasks and habits themselves are kept. */
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun addMilestone(goalId: Long, title: String) {
        db.withTransaction {
            dao.insertMilestone(GoalMilestoneEntity(goalId = goalId, title = title, sortOrder = dao.maxMilestoneOrder(goalId) + 1))
        }
    }

    suspend fun setMilestoneDone(milestone: GoalMilestoneEntity, done: Boolean) = dao.updateMilestone(milestone.copy(done = done))

    suspend fun deleteMilestone(id: Long) = dao.deleteMilestone(id)

    suspend fun linkTask(goalId: Long, taskId: Long) = dao.linkTask(GoalTaskEntity(taskId, goalId))

    suspend fun unlinkTask(taskId: Long) = dao.unlinkTask(taskId)

    suspend fun linkHabit(goalId: Long, habitId: Long) = dao.linkHabit(GoalHabitEntity(habitId, goalId))

    suspend fun unlinkHabit(habitId: Long) = dao.unlinkHabit(habitId)

    /** Makes [taskIds] exactly the tasks linked to the goal. */
    suspend fun setTasks(goalId: Long, taskIds: Set<Long>) = db.withTransaction {
        dao.taskLinks().filter { it.goalId == goalId && it.taskId !in taskIds }.forEach { dao.unlinkTask(it.taskId) }
        taskIds.forEach { dao.linkTask(GoalTaskEntity(it, goalId)) }
    }

    /** Makes [habitIds] exactly the habits linked to the goal. */
    suspend fun setHabits(goalId: Long, habitIds: Set<Long>) = db.withTransaction {
        dao.habitLinks().filter { it.goalId == goalId && it.habitId !in habitIds }.forEach { dao.unlinkHabit(it.habitId) }
        habitIds.forEach { dao.linkHabit(GoalHabitEntity(it, goalId)) }
    }
}
