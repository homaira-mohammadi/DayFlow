package com.dayflow.data.repo

import android.database.sqlite.SQLiteConstraintException
import com.dayflow.data.db.BackupData
import com.dayflow.data.db.DayFlowDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** What a restore brought back, for the confirmation message. */
data class RestoreSummary(val habits: Int, val tasks: Int)

class BackupRepository(private val db: DayFlowDatabase, private val photos: PhotoStore) {

    /**
     * Writes the data as JSON so it can be inspected, or restored with [importJson]. Period data is
     * sensitive, so it is only included when [includeCycle] is explicitly requested.
     */
    suspend fun exportJson(out: OutputStream, includeCycle: Boolean) = withContext(Dispatchers.IO) {
        val data = BackupData(
            habits = db.habitDao().all().map { it.habit },
            habitSchedules = db.habitDao().allSchedules(),
            habitLogs = db.habitDao().allLogs(),
            tasks = db.taskDao().all(),
            taskReschedules = db.taskDao().allReschedules(),
            restDays = db.restDayDao().all(),
            focusSessions = db.focusDao().all(),
            reflections = db.reflectionDao().all(),
            prayerLogs = db.prayerDao().all(),
            periodDays = if (includeCycle) db.cycleDao().periodDays() else null,
            cycleSymptoms = if (includeCycle) db.cycleDao().symptoms() else null,
        )
        out.bufferedWriter().use { it.write(BackupFormat.toJson(data, includeCycle).toString(2)) }
    }

    /**
     * Replaces the stored records with the ones in a backup file. The whole file is read and
     * checked first, and the swap is a single transaction, so a bad file changes nothing.
     * Period data is only replaced when the file carries it. Photos are not part of a backup:
     * a reflection keeps its photo only if that photo is already on this phone.
     */
    suspend fun importJson(input: InputStream): RestoreSummary = withContext(Dispatchers.IO) {
        val parsed = BackupFormat.parse(readText(input))
        val data = parsed.copy(
            reflections = parsed.reflections.map { r ->
                if (r.photoPath != null && !photos.has(r.photoPath)) r.copy(photoPath = null) else r
            },
        )
        try {
            db.backupDao().replaceAll(data)
        } catch (e: SQLiteConstraintException) {
            // Duplicate rows, or history pointing at a habit or task that isn't in the file.
            throw BackupFormatException("This backup file is damaged or incomplete, so nothing was changed.")
        }
        photos.retainOnly(data.reflections.mapNotNull { it.photoPath }.toSet())
        RestoreSummary(habits = data.habits.size, tasks = data.tasks.size)
    }

    private fun readText(input: InputStream): String {
        val reader = input.bufferedReader()
        val text = StringBuilder()
        val buffer = CharArray(64 * 1024)
        while (true) {
            val n = reader.read(buffer)
            if (n < 0) break
            text.append(buffer, 0, n)
            if (text.length > MAX_CHARS) throw BackupFormatException("This file is too large to be a Dayflow backup.")
        }
        return text.toString()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        photos.deleteAll()
    }

    private companion object {
        /** Far above any real backup (years of daily use is a few MB), low enough to stay in memory. */
        const val MAX_CHARS = 50_000_000
    }
}
