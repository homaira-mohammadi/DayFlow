package com.dayflow.domain

import java.time.LocalDate

enum class GoalStatus { ACTIVE, ACHIEVED, ARCHIVED }

/** How far along a goal is: finished steps out of all steps. */
data class GoalProgress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    val isEmpty: Boolean get() = total == 0
    val isComplete: Boolean get() = total > 0 && done == total

    companion object {
        val EMPTY = GoalProgress(0, 0)
    }
}

object GoalRules {
    /**
     * A step is a milestone or a linked task. Cancelled tasks are left out, so dropping a task
     * never counts against the goal. Habits are ongoing rather than finished, so they are shown
     * beside the goal and are not steps.
     */
    fun progress(milestonesDone: List<Boolean>, tasks: List<TaskStatus>): GoalProgress {
        val counted = tasks.filter { it != TaskStatus.CANCELLED }
        return GoalProgress(
            done = milestonesDone.count { it } + counted.count { it == TaskStatus.DONE },
            total = milestonesDone.size + counted.size,
        )
    }

    /** What the progress bar shows: an achieved goal is full, whatever its steps say. */
    fun shownFraction(status: GoalStatus, progress: GoalProgress): Float =
        if (status == GoalStatus.ACHIEVED) 1f else progress.fraction

    /** Days until the target date: 0 on the day, negative once it has passed, null without a target. */
    fun daysLeft(targetDate: Long?, today: LocalDate): Long? = targetDate?.let { it - today.toEpochDay() }
}
