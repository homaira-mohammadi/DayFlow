package com.dayflow.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.GoalEntity
import com.dayflow.data.db.GoalHabitEntity
import com.dayflow.data.db.GoalMilestoneEntity
import com.dayflow.data.db.GoalTaskEntity
import com.dayflow.data.db.SleepLogEntity
import com.dayflow.domain.Mood
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerStatus
import com.dayflow.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Checks that updating the app never loses data. Each test builds a database the way an older
 * version of the app left it, lets Room upgrade it with the real migrations, and reads it back.
 * Room refuses to open a database whose upgraded layout differs from what the app expects, so
 * simply opening it also proves each migration produced the right tables.
 *
 * The old layouts come from the files Room saves in `app/schemas` on every build. Commit the new
 * file whenever the database version goes up, and that version is covered here automatically.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var open: DayFlowDatabase? = null

    @After fun tearDown() {
        open?.close()
        context.deleteDatabase(DB)
    }

    @Test fun `every step from version 1 to the current version has a migration`() {
        val steps = DayFlowDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until currentVersion()).map { it to it + 1 }, steps)
    }

    @Test fun `the current database layout is saved for future migration tests`() {
        assertTrue("Commit app/schemas/.../${currentVersion()}.json", schemaFile(currentVersion()).isFile)
    }

    @Test fun `every saved version upgrades to the current layout`() {
        val saved = savedVersions().filter { it < currentVersion() }
        assertTrue(saved.isNotEmpty())
        saved.forEach { version ->
            createOldDatabase(version, schemaStatements(version))
            assertEquals(currentVersion(), upgrade().openHelper.readableDatabase.version)
            tearDown()
        }
    }

    @Test fun `a version 1 database keeps its tasks and habits through every migration`() = runBlocking {
        createOldDatabase(1, versionOneStatements()) { seedTasksAndHabits() }
        val db = upgrade()
        assertTasksAndHabitsKept(db)
        // Tables added by later versions start empty.
        assertTrue(db.reflectionDao().all().isEmpty())
        assertTrue(db.focusDao().all().isEmpty())
        assertTrue(db.cycleDao().periodDays().isEmpty())
        assertTrue(db.prayerDao().all().isEmpty())
    }

    @Test fun `a version 5 database keeps everything`() = runBlocking {
        createOldDatabase(5, schemaStatements(5)) {
            seedTasksAndHabits()
            execSQL("INSERT INTO reflections (date, mood, energy, gratitude, intention, notes, photoPath, updatedAt) VALUES (19701, 'CALM', 4, 'tea', 'focus', 'ok', 'a.jpg', 9)")
            execSQL("INSERT INTO focus_sessions (startedAt, endedAt, plannedSeconds, actualSeconds, completed, interruptions, taskId, habitId, label) VALUES (100, 200, 1500, 1400, 1, 2, 1, NULL, 'Deep work')")
            execSQL("INSERT INTO period_days (date, flow) VALUES (19690, 'HEAVY')")
            execSQL("INSERT INTO cycle_symptoms (date, symptoms, note) VALUES (19690, 'CRAMPS', 'rest')")
        }
        val db = upgrade()
        assertTasksAndHabitsKept(db)
        val reflection = db.reflectionDao().all().single()
        assertEquals(Mood.CALM, reflection.mood)
        assertEquals("a.jpg", reflection.photoPath)
        val focus = db.focusDao().all().single()
        assertEquals(1400, focus.actualSeconds)
        assertEquals("Deep work", focus.label)
        assertEquals(PeriodFlow.HEAVY, db.cycleDao().periodDays().single().flow)
        assertEquals("CRAMPS", db.cycleDao().symptoms().single().symptoms)
    }

    @Test fun `a version 6 database keeps its prayer records`() = runBlocking {
        createOldDatabase(6, schemaStatements(6)) {
            seedTasksAndHabits()
            execSQL("INSERT INTO prayer_logs (date, prayer, status, updatedAt) VALUES (19701, 'FAJR', 'LATE', 3)")
        }
        val db = upgrade()
        assertTasksAndHabitsKept(db)
        val prayer = db.prayerDao().all().single()
        assertEquals(Prayer.FAJR, prayer.prayer)
        assertEquals(PrayerStatus.LATE, prayer.status)
    }

    @Test fun `goals can be added to tasks and habits that existed before version 7`() = runBlocking {
        createOldDatabase(6, schemaStatements(6)) { seedTasksAndHabits() }
        val db = upgrade()
        assertTrue(db.goalDao().goals().isEmpty())

        val goal = db.goalDao().insert(GoalEntity(title = "Read more"))
        db.goalDao().insertMilestone(GoalMilestoneEntity(goalId = goal, title = "Finish the first book"))
        db.goalDao().linkTask(GoalTaskEntity(taskId = 1, goalId = goal))
        db.goalDao().linkHabit(GoalHabitEntity(habitId = 1, goalId = goal))
        assertEquals(1L, db.goalDao().taskLinks().single().taskId)
        assertEquals(1L, db.goalDao().habitLinks().single().habitId)
        assertTasksAndHabitsKept(db)
    }

    @Test fun `a version 7 database keeps its goals and gains the sleep tracker`() = runBlocking {
        createOldDatabase(7, schemaStatements(7)) {
            seedTasksAndHabits()
            execSQL("INSERT INTO goals (id, title, description, targetDate, status, createdAt) VALUES (1, 'Read more', 'One book a month', 20800, 'ACTIVE', 9)")
            execSQL("INSERT INTO goal_milestones (goalId, title, done, sortOrder) VALUES (1, 'Finish the first book', 1, 1)")
            execSQL("INSERT INTO goal_tasks (taskId, goalId) VALUES (1, 1)")
            execSQL("INSERT INTO goal_habits (habitId, goalId) VALUES (1, 1)")
        }
        val db = upgrade()
        assertTasksAndHabitsKept(db)
        assertEquals("Read more", db.goalDao().goals().single().title)
        assertEquals(true, db.goalDao().milestones().single().done)
        assertEquals(1L, db.goalDao().taskLinks().single().goalId)
        assertEquals(1L, db.goalDao().habitLinks().single().goalId)

        assertTrue(db.sleepDao().all().isEmpty())
        db.sleepDao().upsert(SleepLogEntity(date = 19701, bedMinutes = 23 * 60, wakeMinutes = 7 * 60))
        assertEquals(7 * 60, db.sleepDao().latest()?.wakeMinutes)
    }

    @Test fun `a version 7 test build that had sleep but no goals upgrades and keeps its sleep records`() = runBlocking {
        val sleepTable = "CREATE TABLE IF NOT EXISTS `sleep_logs` (`date` INTEGER NOT NULL, `bedMinutes` INTEGER NOT NULL, " +
            "`wakeMinutes` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
        val sleepOnlyVersion7 = schemaStatements(6, onlyTables = allTables(6)) + sleepTable
        createOldDatabase(7, sleepOnlyVersion7) {
            seedTasksAndHabits()
            execSQL("INSERT INTO sleep_logs (date, bedMinutes, wakeMinutes, updatedAt) VALUES (19701, 1380, 420, 3)")
        }
        val db = upgrade()
        assertTasksAndHabitsKept(db)
        assertEquals(420, db.sleepDao().all().single().wakeMinutes)
        assertTrue(db.goalDao().goals().isEmpty())
        db.goalDao().linkTask(GoalTaskEntity(taskId = 1, goalId = db.goalDao().insert(GoalEntity(title = "Read more"))))
    }

    // ---- Building old databases ----

    private fun createOldDatabase(version: Int, statements: List<String>, seed: SQLiteDatabase.() -> Unit = {}) {
        context.deleteDatabase(DB)
        context.openOrCreateDatabase(DB, Context.MODE_PRIVATE, null).use { db ->
            statements.forEach(db::execSQL)
            db.seed()
            db.version = version
        }
    }

    /** Opens the old database with the app's real migrations, which upgrades and checks it. */
    private fun upgrade(): DayFlowDatabase =
        Room.databaseBuilder(context, DayFlowDatabase::class.java, DB)
            .addMigrations(*DayFlowDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { open = it; it.openHelper.writableDatabase }

    private fun currentVersion(): Int =
        Room.inMemoryDatabaseBuilder(context, DayFlowDatabase::class.java).build().run {
            openHelper.readableDatabase.version.also { close() }
        }

    private fun schemaFile(version: Int) = File(SCHEMAS, "$version.json")

    private fun savedVersions(): List<Int> =
        File(SCHEMAS).listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()

    private fun allTables(version: Int): Set<String> {
        val entities = JSONObject(schemaFile(version).readText()).getJSONObject("database").getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }.toSet()
    }

    /** The statements that create a saved version's tables, optionally only some of them. */
    private fun schemaStatements(version: Int, onlyTables: Set<String>? = null): List<String> {
        val database = JSONObject(schemaFile(version).readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val statements = mutableListOf<String>()
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            if (onlyTables != null && table !in onlyTables) continue
            statements += entity.getString("createSql").replace("\${TABLE_NAME}", table)
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                statements += indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)
            }
        }
        if (onlyTables == null) {
            val setup = database.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) statements += setup.getString(i)
        }
        return statements
    }

    /**
     * Version 1 was released before layouts were saved. It had only the tables below, and no
     * later migration has changed them, so their version 5 definitions are the version 1 ones.
     */
    private fun versionOneStatements() = schemaStatements(
        5, onlyTables = setOf("tasks", "task_reschedules", "habits", "habit_schedules", "habit_logs", "rest_days"),
    )

    private fun SQLiteDatabase.seedTasksAndHabits() {
        execSQL(
            "INSERT INTO tasks (id, title, notes, date, timeMinutes, priority, category, estimatedMinutes, isFocus, remind, status, completedAt, originalDate, rescheduleCount, createdAt) " +
                "VALUES (1, 'Call Sam', 'about Friday', 19702, 540, 2, 'Home', 15, 1, 1, 'DONE', 77, 19700, 1, 66)",
        )
        execSQL("INSERT INTO task_reschedules (taskId, fromDate, toDate, changedAt) VALUES (1, 19700, 19702, 70)")
        execSQL(
            "INSERT INTO habits (id, name, icon, color, category, section, goalType, unit, daysMask, target, minimumTarget, reminderMinutes, status, sortOrder, createdAt) " +
                "VALUES (1, 'Read', 'book', -16711936, 'Mind', 'EVENING', 'DURATION', 'min', 62, 20.0, 5.0, 1260, 'ACTIVE', 4, 55)",
        )
        execSQL("INSERT INTO habit_schedules (habitId, effectiveFrom, daysMask, target, minimumTarget) VALUES (1, 19700, 62, 20.0, 5.0)")
        execSQL("INSERT INTO habit_logs (habitId, date, value, skipped, updatedAt) VALUES (1, 19701, 12.5, 0, 5)")
        execSQL("INSERT INTO rest_days (date, note) VALUES (19710, 'Travel')")
    }

    private suspend fun assertTasksAndHabitsKept(db: DayFlowDatabase) {
        val task = db.taskDao().all().single()
        assertEquals("Call Sam", task.title)
        assertEquals("about Friday", task.notes)
        assertEquals(TaskStatus.DONE, task.status)
        assertEquals(540, task.timeMinutes)
        assertEquals(true, task.remind)
        assertEquals(19702L, db.taskDao().allReschedules().single().toDate)

        val habit = db.habitDao().all().single()
        assertEquals("Read", habit.habit.name)
        assertEquals(20.0, habit.habit.target, 0.0)
        assertEquals(5.0, habit.habit.minimumTarget)
        assertEquals(19700L, habit.schedules.single().effectiveFrom)
        assertEquals(12.5, db.habitDao().allLogs().single().value, 0.0)
        assertEquals("Travel", db.restDayDao().all().single().note)
    }

    private companion object {
        const val DB = "migration-test.db"
        const val SCHEMAS = "schemas/com.dayflow.data.db.DayFlowDatabase"
    }
}
