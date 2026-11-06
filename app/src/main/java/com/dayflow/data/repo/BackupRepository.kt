package com.dayflow.data.repo

import com.dayflow.data.db.DayFlowDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

class BackupRepository(private val db: DayFlowDatabase, private val photos: PhotoStore) {

    /**
     * Writes the data as JSON so it can be inspected or imported elsewhere. Period data is
     * sensitive, so it is only included when [includeCycle] is explicitly requested.
     */
    suspend fun exportJson(out: OutputStream, includeCycle: Boolean) = withContext(Dispatchers.IO) {
        val habitDao = db.habitDao()
        val taskDao = db.taskDao()
        val root = JSONObject()
            .put("format", "dayflow-export")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("habits", JSONArray(habitDao.all().map { h ->
                val e = h.habit
                JSONObject()
                    .put("id", e.id).put("name", e.name).put("icon", e.icon).put("color", e.color)
                    .put("category", e.category).put("section", e.section.name).put("goalType", e.goalType.name)
                    .put("unit", e.unit).put("daysMask", e.daysMask).put("target", e.target)
                    .put("minimumTarget", e.minimumTarget ?: JSONObject.NULL)
                    .put("reminderMinutes", e.reminderMinutes ?: JSONObject.NULL)
                    .put("status", e.status.name).put("createdAt", e.createdAt)
            }))
            .put("habitSchedules", JSONArray(habitDao.allSchedules().map { s ->
                JSONObject().put("habitId", s.habitId).put("effectiveFrom", s.effectiveFrom)
                    .put("daysMask", s.daysMask).put("target", s.target)
                    .put("minimumTarget", s.minimumTarget ?: JSONObject.NULL)
            }))
            .put("habitLogs", JSONArray(habitDao.allLogs().map { l ->
                JSONObject().put("habitId", l.habitId).put("date", l.date).put("value", l.value)
                    .put("skipped", l.skipped).put("updatedAt", l.updatedAt)
            }))
            .put("tasks", JSONArray(taskDao.all().map { t ->
                JSONObject()
                    .put("id", t.id).put("title", t.title).put("notes", t.notes).put("date", t.date)
                    .put("timeMinutes", t.timeMinutes ?: JSONObject.NULL).put("priority", t.priority)
                    .put("category", t.category).put("estimatedMinutes", t.estimatedMinutes ?: JSONObject.NULL)
                    .put("isFocus", t.isFocus).put("status", t.status.name)
                    .put("completedAt", t.completedAt ?: JSONObject.NULL).put("originalDate", t.originalDate)
                    .put("rescheduleCount", t.rescheduleCount).put("createdAt", t.createdAt)
            }))
            .put("taskReschedules", JSONArray(taskDao.allReschedules().map { r ->
                JSONObject().put("taskId", r.taskId).put("fromDate", r.fromDate).put("toDate", r.toDate)
                    .put("changedAt", r.changedAt)
            }))
            .put("restDays", JSONArray(db.restDayDao().all().map { it.date }))
            .put("focusSessions", JSONArray(db.focusDao().all().map { f ->
                JSONObject().put("startedAt", f.startedAt).put("endedAt", f.endedAt)
                    .put("plannedSeconds", f.plannedSeconds).put("actualSeconds", f.actualSeconds)
                    .put("completed", f.completed).put("interruptions", f.interruptions)
                    .put("taskId", f.taskId ?: JSONObject.NULL).put("habitId", f.habitId ?: JSONObject.NULL)
                    .put("label", f.label)
            }))
            .put("reflections", JSONArray(db.reflectionDao().all().map { r ->
                JSONObject().put("date", r.date).put("mood", r.mood?.name ?: JSONObject.NULL)
                    .put("energy", r.energy ?: JSONObject.NULL).put("gratitude", r.gratitude)
                    .put("intention", r.intention).put("notes", r.notes).put("updatedAt", r.updatedAt)
                    .put("photo", r.photoPath ?: JSONObject.NULL)
            }))
            .put("prayerLogs", JSONArray(db.prayerDao().all().map { p ->
                JSONObject().put("date", p.date).put("prayer", p.prayer.name).put("status", p.status.name)
                    .put("updatedAt", p.updatedAt)
            }))
        if (includeCycle) {
            root.put("periodDays", JSONArray(db.cycleDao().periodDays().map { JSONObject().put("date", it.date).put("flow", it.flow.name) }))
            root.put("cycleSymptoms", JSONArray(db.cycleDao().symptoms().map {
                JSONObject().put("date", it.date).put("symptoms", it.symptoms).put("note", it.note)
            }))
        }
        out.bufferedWriter().use { it.write(root.toString(2)) }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        photos.deleteAll()
    }
}
