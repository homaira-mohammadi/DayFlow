package com.dayflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.GoalEntity
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.repo.GoalParts
import com.dayflow.data.repo.GoalRepository
import com.dayflow.domain.GoalStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, DayFlowDatabase::class.java).allowMainThreadQueries().build()
    private val goals = GoalRepository(db)
    private var goal = 0L
    private var task = 0L
    private var habit = 0L

    @Before fun setUp() = runBlocking {
        goal = goals.save(GoalEntity(title = "Improve programming skills", targetDate = 20_800))
        task = db.taskDao().insert(TaskEntity(title = "Finish Python course", date = 100))
        habit = db.habitDao().insert(HabitEntity(name = "Practice coding", color = 1))
    }

    @After fun tearDown() = db.close()

    private suspend fun parts(id: Long = goal): GoalParts = goals.goals.first().single { it.goal.id == id }

    @Test fun `a goal collects its milestones tasks and habits`() = runBlocking {
        goals.addMilestone(goal, "Build one project")
        goals.addMilestone(goal, "Solve 50 problems")
        goals.linkTask(goal, task)
        goals.linkHabit(goal, habit)

        val p = parts()
        assertEquals(listOf("Build one project", "Solve 50 problems"), p.milestones.map { it.title })
        assertEquals(setOf(task), p.taskIds)
        assertEquals(setOf(habit), p.habitIds)
    }

    @Test fun `milestones can be ticked and removed`() = runBlocking {
        goals.addMilestone(goal, "Build one project")
        val m = parts().milestones.single()
        goals.setMilestoneDone(m, true)
        assertTrue(parts().milestones.single().done)
        goals.deleteMilestone(m.id)
        assertTrue(parts().milestones.isEmpty())
    }

    @Test fun `a task belongs to one goal so linking it again moves it`() = runBlocking {
        val other = goals.save(GoalEntity(title = "Get fit"))
        goals.linkTask(goal, task)
        goals.linkTask(other, task)
        assertTrue(parts(goal).taskIds.isEmpty())
        assertEquals(setOf(task), parts(other).taskIds)
    }

    @Test fun `choosing the linked tasks adds and removes links`() = runBlocking {
        val second = db.taskDao().insert(TaskEntity(title = "Build a project", date = 101))
        goals.setTasks(goal, setOf(task, second))
        assertEquals(setOf(task, second), parts().taskIds)
        goals.setTasks(goal, setOf(second))
        assertEquals(setOf(second), parts().taskIds)
        goals.setHabits(goal, setOf(habit))
        goals.setHabits(goal, emptySet())
        assertTrue(parts().habitIds.isEmpty())
    }

    @Test fun `deleting a goal keeps its tasks and habits`() = runBlocking {
        goals.addMilestone(goal, "Build one project")
        goals.linkTask(goal, task)
        goals.linkHabit(goal, habit)
        goals.delete(goal)

        assertTrue(goals.goals.first().isEmpty())
        assertTrue(db.goalDao().milestones().isEmpty())
        assertTrue(db.goalDao().taskLinks().isEmpty())
        assertEquals("Finish Python course", db.taskDao().get(task)?.title)
        assertEquals("Practice coding", db.habitDao().get(habit)?.habit?.name)
    }

    @Test fun `deleting a task or habit only removes its link`() = runBlocking {
        goals.linkTask(goal, task)
        goals.linkHabit(goal, habit)
        db.taskDao().delete(db.taskDao().get(task)!!)
        db.habitDao().delete(habit)

        val p = parts()
        assertTrue(p.taskIds.isEmpty())
        assertTrue(p.habitIds.isEmpty())
        assertEquals("Improve programming skills", p.goal.title)
    }

    @Test fun `status changes keep everything else`() = runBlocking {
        goals.setStatus(goal, GoalStatus.ACHIEVED)
        val g = parts().goal
        assertEquals(GoalStatus.ACHIEVED, g.status)
        assertEquals(20_800L, g.targetDate)
    }
}
