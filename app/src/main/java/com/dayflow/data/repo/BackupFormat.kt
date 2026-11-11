package com.dayflow.data.repo

import com.dayflow.data.db.BackupData
import com.dayflow.data.db.CycleSymptomEntity
import com.dayflow.data.db.FocusSessionEntity
import com.dayflow.data.db.GoalEntity
import com.dayflow.data.db.GoalHabitEntity
import com.dayflow.data.db.GoalMilestoneEntity
import com.dayflow.data.db.GoalTaskEntity
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.HabitLogEntity
import com.dayflow.data.db.HabitScheduleEntity
import com.dayflow.data.db.PeriodDayEntity
import com.dayflow.data.db.PrayerLogEntity
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.data.db.RestDayEntity
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.db.TaskRescheduleEntity
import com.dayflow.domain.Mood
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The backup file can't be used. [message] is written for the person restoring it. */
class BackupFormatException(message: String) : Exception(message)

/**
 * The JSON backup file. Version 2 added task reminders, habit order and rest day notes, and
 * version 3 added goals. Older files are still read: what they lack falls back to its default.
 */
object BackupFormat {
    const val FORMAT = "dayflow-export"
    const val VERSION = 3

    /** Period data is sensitive, so it is only written when [includeCycle] is explicitly requested. */
    fun toJson(data: BackupData, includeCycle: Boolean, exportedAt: Long = System.currentTimeMillis()): JSONObject {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put("habits", JSONArray(data.habits.map { e ->
                JSONObject()
                    .put("id", e.id).put("name", e.name).put("icon", e.icon).put("color", e.color)
                    .put("category", e.category).put("section", e.section.name).put("goalType", e.goalType.name)
                    .put("unit", e.unit).put("daysMask", e.daysMask).put("target", e.target)
                    .put("minimumTarget", e.minimumTarget ?: JSONObject.NULL)
                    .put("reminderMinutes", e.reminderMinutes ?: JSONObject.NULL)
                    .put("status", e.status.name).put("sortOrder", e.sortOrder).put("createdAt", e.createdAt)
            }))
            .put("habitSchedules", JSONArray(data.habitSchedules.map { s ->
                JSONObject().put("habitId", s.habitId).put("effectiveFrom", s.effectiveFrom)
                    .put("daysMask", s.daysMask).put("target", s.target)
                    .put("minimumTarget", s.minimumTarget ?: JSONObject.NULL)
            }))
            .put("habitLogs", JSONArray(data.habitLogs.map { l ->
                JSONObject().put("habitId", l.habitId).put("date", l.date).put("value", l.value)
                    .put("skipped", l.skipped).put("updatedAt", l.updatedAt)
            }))
            .put("tasks", JSONArray(data.tasks.map { t ->
                JSONObject()
                    .put("id", t.id).put("title", t.title).put("notes", t.notes).put("date", t.date)
                    .put("timeMinutes", t.timeMinutes ?: JSONObject.NULL).put("priority", t.priority)
                    .put("category", t.category).put("estimatedMinutes", t.estimatedMinutes ?: JSONObject.NULL)
                    .put("isFocus", t.isFocus).put("remind", t.remind).put("status", t.status.name)
                    .put("completedAt", t.completedAt ?: JSONObject.NULL).put("originalDate", t.originalDate)
                    .put("rescheduleCount", t.rescheduleCount).put("createdAt", t.createdAt)
            }))
            .put("taskReschedules", JSONArray(data.taskReschedules.map { r ->
                JSONObject().put("taskId", r.taskId).put("fromDate", r.fromDate).put("toDate", r.toDate)
                    .put("changedAt", r.changedAt)
            }))
            .put("restDays", JSONArray(data.restDays.map { JSONObject().put("date", it.date).put("note", it.note) }))
            .put("focusSessions", JSONArray(data.focusSessions.map { f ->
                JSONObject().put("startedAt", f.startedAt).put("endedAt", f.endedAt)
                    .put("plannedSeconds", f.plannedSeconds).put("actualSeconds", f.actualSeconds)
                    .put("completed", f.completed).put("interruptions", f.interruptions)
                    .put("taskId", f.taskId ?: JSONObject.NULL).put("habitId", f.habitId ?: JSONObject.NULL)
                    .put("label", f.label)
            }))
            .put("reflections", JSONArray(data.reflections.map { r ->
                JSONObject().put("date", r.date).put("mood", r.mood?.name ?: JSONObject.NULL)
                    .put("energy", r.energy ?: JSONObject.NULL).put("gratitude", r.gratitude)
                    .put("intention", r.intention).put("notes", r.notes).put("updatedAt", r.updatedAt)
                    .put("photo", r.photoPath ?: JSONObject.NULL)
            }))
            .put("prayerLogs", JSONArray(data.prayerLogs.map { p ->
                JSONObject().put("date", p.date).put("prayer", p.prayer.name).put("status", p.status.name)
                    .put("updatedAt", p.updatedAt)
            }))
        root
            .put("goals", JSONArray(data.goals.map { g ->
                JSONObject().put("id", g.id).put("title", g.title).put("description", g.description)
                    .put("targetDate", g.targetDate ?: JSONObject.NULL).put("status", g.status.name)
                    .put("createdAt", g.createdAt)
            }))
            .put("goalMilestones", JSONArray(data.goalMilestones.map { m ->
                JSONObject().put("goalId", m.goalId).put("title", m.title).put("done", m.done).put("sortOrder", m.sortOrder)
            }))
            .put("goalTasks", JSONArray(data.goalTasks.map { JSONObject().put("taskId", it.taskId).put("goalId", it.goalId) }))
            .put("goalHabits", JSONArray(data.goalHabits.map { JSONObject().put("habitId", it.habitId).put("goalId", it.goalId) }))
        if (includeCycle) {
            root.put("periodDays", JSONArray(data.periodDays.orEmpty().map { JSONObject().put("date", it.date).put("flow", it.flow.name) }))
            root.put("cycleSymptoms", JSONArray(data.cycleSymptoms.orEmpty().map {
                JSONObject().put("date", it.date).put("symptoms", it.symptoms).put("note", it.note)
            }))
        }
        return root
    }

    /** Reads and checks a whole backup file. Throws [BackupFormatException] if any part of it can't be used. */
    fun parse(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("This isn't a Dayflow backup file.")
        }
        if (root.optString("format") != FORMAT) throw BackupFormatException("This isn't a Dayflow backup file.")
        val version = root.optInt("version", 0)
        if (version > VERSION) throw BackupFormatException("This backup was made by a newer version of Dayflow. Update the app, then try again.")
        if (version < 1) throw BackupFormatException("This isn't a Dayflow backup file.")

        return try {
            BackupData(
                habits = root.rows("habits") { o ->
                    HabitEntity(
                        id = o.id(), name = o.getString("name"), icon = o.getString("icon"), color = o.getInt("color"),
                        category = o.getString("category"), section = enumValueOf(o.getString("section")),
                        goalType = enumValueOf(o.getString("goalType")), unit = o.getString("unit"),
                        daysMask = o.getInt("daysMask"), target = o.getDouble("target"),
                        minimumTarget = o.doubleOrNull("minimumTarget"), reminderMinutes = o.intOrNull("reminderMinutes"),
                        status = enumValueOf(o.getString("status")), sortOrder = o.optInt("sortOrder", 0),
                        createdAt = o.getLong("createdAt"),
                    )
                },
                habitSchedules = root.rows("habitSchedules") { o ->
                    HabitScheduleEntity(
                        habitId = o.getLong("habitId"), effectiveFrom = o.getLong("effectiveFrom"),
                        daysMask = o.getInt("daysMask"), target = o.getDouble("target"),
                        minimumTarget = o.doubleOrNull("minimumTarget"),
                    )
                },
                habitLogs = root.rows("habitLogs") { o ->
                    HabitLogEntity(
                        habitId = o.getLong("habitId"), date = o.getLong("date"), value = o.getDouble("value"),
                        skipped = o.getBoolean("skipped"), updatedAt = o.getLong("updatedAt"),
                    )
                },
                tasks = root.rows("tasks") { o ->
                    TaskEntity(
                        id = o.id(), title = o.getString("title"), notes = o.getString("notes"), date = o.getLong("date"),
                        timeMinutes = o.intOrNull("timeMinutes"), priority = o.getInt("priority"),
                        category = o.getString("category"), estimatedMinutes = o.intOrNull("estimatedMinutes"),
                        isFocus = o.getBoolean("isFocus"), remind = o.optBoolean("remind", false),
                        status = enumValueOf(o.getString("status")), completedAt = o.longOrNull("completedAt"),
                        originalDate = o.getLong("originalDate"), rescheduleCount = o.getInt("rescheduleCount"),
                        createdAt = o.getLong("createdAt"),
                    )
                },
                taskReschedules = root.rows("taskReschedules") { o ->
                    TaskRescheduleEntity(
                        taskId = o.getLong("taskId"), fromDate = o.getLong("fromDate"), toDate = o.getLong("toDate"),
                        changedAt = o.getLong("changedAt"),
                    )
                },
                restDays = root.restDays(),
                focusSessions = root.rows("focusSessions") { o ->
                    FocusSessionEntity(
                        startedAt = o.getLong("startedAt"), endedAt = o.getLong("endedAt"),
                        plannedSeconds = o.getInt("plannedSeconds"), actualSeconds = o.getInt("actualSeconds"),
                        completed = o.getBoolean("completed"), interruptions = o.getInt("interruptions"),
                        taskId = o.longOrNull("taskId"), habitId = o.longOrNull("habitId"), label = o.getString("label"),
                    )
                },
                reflections = root.rows("reflections") { o ->
                    ReflectionEntity(
                        date = o.getLong("date"), mood = if (o.isNull("mood")) null else enumValueOf<Mood>(o.getString("mood")),
                        energy = o.intOrNull("energy"), gratitude = o.getString("gratitude"),
                        intention = o.getString("intention"), notes = o.getString("notes"),
                        photoPath = if (o.isNull("photo")) null else o.getString("photo"),
                        updatedAt = o.getLong("updatedAt"),
                    )
                },
                prayerLogs = root.rows("prayerLogs") { o ->
                    PrayerLogEntity(
                        date = o.getLong("date"), prayer = enumValueOf(o.getString("prayer")),
                        status = enumValueOf(o.getString("status")), updatedAt = o.getLong("updatedAt"),
                    )
                },
                goals = root.rows("goals") { o ->
                    GoalEntity(
                        id = o.id(), title = o.getString("title"), description = o.getString("description"),
                        targetDate = o.longOrNull("targetDate"), status = enumValueOf(o.getString("status")),
                        createdAt = o.getLong("createdAt"),
                    )
                },
                goalMilestones = root.rows("goalMilestones") { o ->
                    GoalMilestoneEntity(
                        goalId = o.getLong("goalId"), title = o.getString("title"), done = o.getBoolean("done"),
                        sortOrder = o.getInt("sortOrder"),
                    )
                },
                goalTasks = root.rows("goalTasks") { o -> GoalTaskEntity(taskId = o.getLong("taskId"), goalId = o.getLong("goalId")) },
                goalHabits = root.rows("goalHabits") { o -> GoalHabitEntity(habitId = o.getLong("habitId"), goalId = o.getLong("goalId")) },
                periodDays = if (!root.has("periodDays")) null else root.rows("periodDays") { o ->
                    PeriodDayEntity(date = o.getLong("date"), flow = enumValueOf(o.getString("flow")))
                },
                cycleSymptoms = if (!root.has("cycleSymptoms")) null else root.rows("cycleSymptoms") { o ->
                    CycleSymptomEntity(date = o.getLong("date"), symptoms = o.getString("symptoms"), note = o.getString("note"))
                },
            )
        } catch (e: JSONException) {
            throw damaged()
        } catch (e: IllegalArgumentException) {
            // An enum name this version doesn't know.
            throw damaged()
        }
    }

    private fun damaged() = BackupFormatException("This backup file is damaged or incomplete, so nothing was changed.")

    /** A missing list is read as empty, because older backups don't have the newer lists. */
    private fun <T> JSONObject.rows(key: String, read: (JSONObject) -> T): List<T> {
        if (!has(key)) return emptyList()
        val array = getJSONArray(key)
        return List(array.length()) { read(array.getJSONObject(it)) }
    }

    /** Version 1 stored rest days as plain dates; version 2 stores the note as well. */
    private fun JSONObject.restDays(): List<RestDayEntity> {
        if (!has("restDays")) return emptyList()
        val array = getJSONArray("restDays")
        return List(array.length()) { i ->
            val o = array.optJSONObject(i)
            if (o != null) RestDayEntity(date = o.getLong("date"), note = o.optString("note", "")) else RestDayEntity(date = array.getLong(i))
        }
    }

    /** Ids link habits and tasks to their history, so each one must be a real id. */
    private fun JSONObject.id(): Long = getLong("id").also { if (it <= 0) throw JSONException("id") }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
