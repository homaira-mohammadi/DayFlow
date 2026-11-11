package com.dayflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dayflow.data.db.CycleSymptomEntity
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.FocusSessionEntity
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import com.dayflow.data.db.PeriodDayEntity
import com.dayflow.data.db.PrayerLogEntity
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.data.db.RestDayEntity
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.db.TaskRescheduleEntity
import com.dayflow.data.repo.BackupFormatException
import com.dayflow.data.repo.BackupRepository
import com.dayflow.data.repo.PhotoStore
import com.dayflow.domain.Mood
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File

/** Export on one phone, restore on another: the real database and the real backup file. */
@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val photoDir = File(context.filesDir, "reflection_photos")
    private val photos = PhotoStore(context)
    private val oldPhone = newDatabase()
    private val newPhone = newDatabase()

    private fun newDatabase() =
        Room.inMemoryDatabaseBuilder(context, DayFlowDatabase::class.java).allowMainThreadQueries().build()

    @Before fun setUp() = runBlocking {
        photoDir.mkdirs()
        File(photoDir, "on-this-phone.jpg").writeText("photo")
        seed(oldPhone)
    }

    @After fun tearDown() {
        oldPhone.close()
        newPhone.close()
        photoDir.deleteRecursively()
    }

    @Test fun `a backup restores every record on a new phone`() = runBlocking {
        val summary = restore(newPhone, export(includeCycle = true))
        assertEquals(1, summary.habits)
        assertEquals(1, summary.tasks)
        assertEquals(expectedAfterRestore(), records(newPhone))
    }

    @Test fun `restoring replaces what was there and can be repeated`() = runBlocking {
        val backup = export(includeCycle = true)
        newPhone.taskDao().insert(TaskEntity(title = "Only on the new phone", date = 5))
        restore(newPhone, backup)
        restore(newPhone, backup)
        assertEquals(expectedAfterRestore(), records(newPhone))
    }

    @Test fun `a backup without period data leaves period data alone`() = runBlocking {
        newPhone.cycleDao().upsertPeriodDays(listOf(PeriodDayEntity(50, PeriodFlow.MEDIUM)))
        restore(newPhone, export(includeCycle = false))
        assertEquals(listOf(PeriodDayEntity(50, PeriodFlow.MEDIUM)), newPhone.cycleDao().periodDays())
    }

    @Test fun `a damaged backup changes nothing`() = runBlocking {
        val good = export(includeCycle = true)
        restore(newPhone, good)
        val before = records(newPhone)

        // History that points at a habit the file doesn't contain.
        val orphan = JSONObject(good).apply { getJSONArray("habitLogs").getJSONObject(0).put("habitId", 999) }
        // The same task twice.
        val duplicate = JSONObject(good).apply { getJSONArray("tasks").put(getJSONArray("tasks").getJSONObject(0)) }
        listOf(orphan, duplicate).forEach { broken ->
            val error = assertThrows(BackupFormatException::class.java) { runBlocking { restore(newPhone, broken.toString()) } }
            assertTrue("nothing was changed" in error.message!!)
            assertEquals(before, records(newPhone))
        }
    }

    @Test fun `photos are kept only when they are on this phone`() = runBlocking {
        File(photoDir, "unused.jpg").writeText("photo")
        restore(newPhone, export(includeCycle = true))
        val reflections = newPhone.reflectionDao().all().associate { it.date to it.photoPath }
        assertEquals("on-this-phone.jpg", reflections[101L])
        assertEquals(null, reflections[102L])
        assertTrue(File(photoDir, "on-this-phone.jpg").exists())
        assertFalse(File(photoDir, "unused.jpg").exists())
    }

    @Test fun `a photo name that is really a path is ignored`() = runBlocking {
        val backup = JSONObject(export(includeCycle = true)).apply {
            val reflections = getJSONArray("reflections")
            for (i in 0 until reflections.length()) reflections.getJSONObject(i).put("photo", "../../databases/dayflow.db")
        }
        restore(newPhone, backup.toString())
        assertTrue(newPhone.reflectionDao().all().all { it.photoPath == null })
    }

    @Test fun `new records get fresh ids after a restore`() = runBlocking {
        restore(newPhone, export(includeCycle = true))
        val restoredId = newPhone.taskDao().all().single().id
        assertTrue(newPhone.taskDao().insert(TaskEntity(title = "Next", date = 5)) > restoredId)
    }

    private suspend fun export(includeCycle: Boolean): String =
        ByteArrayOutputStream().also { BackupRepository(oldPhone, photos).exportJson(it, includeCycle) }.toString()

    private suspend fun restore(db: DayFlowDatabase, backup: String) =
        BackupRepository(db, photos).importJson(backup.byteInputStream())

    private suspend fun seed(db: DayFlowDatabase) {
        val habit = db.habitDao().insert(HabitEntity(name = "Read", color = 1, sortOrder = 3))
        db.habitDao().insertSchedule(HabitScheduleEntity(habitId = habit, effectiveFrom = 100, daysMask = 127, target = 1.0, minimumTarget = null))
        db.habitDao().upsertLog(HabitLogEntity(habitId = habit, date = 101, value = 1.0, updatedAt = 5))
        val task = db.taskDao().insert(TaskEntity(title = "Call", date = 101, remind = true, timeMinutes = 600))
        db.taskDao().insertReschedule(TaskRescheduleEntity(taskId = task, fromDate = 100, toDate = 101, changedAt = 4))
        db.restDayDao().insert(RestDayEntity(110, "Travel"))
        db.reflectionDao().upsert(ReflectionEntity(date = 101, mood = Mood.CALM, photoPath = "on-this-phone.jpg", updatedAt = 6))
        db.reflectionDao().upsert(ReflectionEntity(date = 102, photoPath = "left-on-old-phone.jpg", updatedAt = 7))
        db.focusDao().insert(
            FocusSessionEntity(startedAt = 1, endedAt = 2, plannedSeconds = 3, actualSeconds = 3, completed = true, interruptions = 0, taskId = task),
        )
        db.prayerDao().upsert(PrayerLogEntity(101, Prayer.FAJR, PrayerStatus.ON_TIME, 8))
        db.cycleDao().upsertPeriodDays(listOf(PeriodDayEntity(90, PeriodFlow.LIGHT)))
        db.cycleDao().upsertSymptoms(CycleSymptomEntity(90, "CRAMPS", "x"))
    }

    /** Everything stored, with the ids that a restore is free to renumber set to zero. */
    private suspend fun records(db: DayFlowDatabase): List<List<Any>> = listOf(
        db.habitDao().all().map { it.habit },
        db.habitDao().allSchedules().map { it.copy(id = 0) },
        db.habitDao().allLogs(),
        db.taskDao().all(),
        db.taskDao().allReschedules().map { it.copy(id = 0) },
        db.restDayDao().all(),
        db.reflectionDao().all(),
        db.focusDao().all().map { it.copy(id = 0) },
        db.prayerDao().all(),
        db.cycleDao().periodDays(),
        db.cycleDao().symptoms(),
    )

    /** The old phone's records, except that the photo left behind on the old phone is unlinked. */
    private suspend fun expectedAfterRestore(): List<List<Any>> = records(oldPhone).map { rows ->
        rows.map { if (it is ReflectionEntity && it.photoPath == "left-on-old-phone.jpg") it.copy(photoPath = null) else it }
    }
}
