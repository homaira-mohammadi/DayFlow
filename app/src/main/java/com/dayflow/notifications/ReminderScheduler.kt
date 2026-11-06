package com.dayflow.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.HabitEntity
import com.dayflow.data.db.TaskEntity
import com.dayflow.data.prefs.SettingsRepository
import com.dayflow.data.prefs.UserSettings
import com.dayflow.domain.HabitStatus
import com.dayflow.domain.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Keeps one alarm per reminder. Alarms are only wake-ups: [ReminderReceiver] re-checks the data
 * when one fires, so a stale alarm (deleted habit, completed task) never produces a notification.
 */
class ReminderScheduler(
    context: Context,
    private val db: DayFlowDatabase,
    private val settings: SettingsRepository,
) {
    private val app = context.applicationContext
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refreshAll() {
        scope.launch { syncAll() }
    }

    fun refreshHabit(id: Long) {
        scope.launch { syncHabit(id, db.habitDao().get(id)?.habit, settings.current()) }
    }

    fun refreshTask(id: Long) {
        scope.launch { syncTask(id, db.taskDao().get(id), settings.current()) }
    }

    fun cancelHabitNotification(id: Long) = Notifier.cancel(app, KIND_HABIT, id.toInt())

    fun cancelTaskNotification(id: Long) = Notifier.cancel(app, KIND_TASK, id.toInt())

    suspend fun syncAll() {
        val s = settings.current()
        db.habitDao().all().forEach { syncHabit(it.habit.id, it.habit, s) }
        db.taskDao().pendingWithReminders(LocalDate.now().toEpochDay()).forEach { syncTask(it.id, it, s) }
        syncDaily(KIND_MORNING, s.morningPlanMinutes, s)
        syncDaily(KIND_EVENING, s.eveningReviewMinutes, s)
        syncDaily(KIND_CYCLE, if (s.cycleTracking && s.cycleReminders) CYCLE_CHECK_MINUTES else null, s)
    }

    /** Wakes [ReminderReceiver] when the focus timer runs out, even if the app is closed. */
    fun scheduleFocusEnd(at: Long) = set(at, pendingIntent(KIND_FOCUS, 0))

    fun cancelFocusEnd() = alarms.cancel(pendingIntent(KIND_FOCUS, 0))

    /** On Android 12+ the user may deny exact alarms, in which case alerts can arrive a few minutes late. */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun snooze(kind: String, id: Long, minutes: Long = 15) {
        set(System.currentTimeMillis() + minutes * 60_000, pendingIntent(kind, id))
    }

    private fun syncHabit(id: Long, habit: HabitEntity?, s: UserSettings) {
        val pi = pendingIntent(KIND_HABIT, id)
        val minutes = habit?.reminderMinutes
        if (s.notifications && habit?.status == HabitStatus.ACTIVE && minutes != null) set(nextOccurrence(minutes), pi)
        else alarms.cancel(pi)
    }

    private fun syncTask(id: Long, task: TaskEntity?, s: UserSettings) {
        val pi = pendingIntent(KIND_TASK, id)
        val time = task?.timeMinutes
        val at = if (time != null) epochMillis(LocalDate.ofEpochDay(task.date), time) else 0L
        if (s.notifications && task?.status == TaskStatus.PENDING && task.remind && time != null && at > System.currentTimeMillis()) set(at, pi)
        else alarms.cancel(pi)
    }

    private fun syncDaily(kind: String, minutes: Int?, s: UserSettings) {
        val pi = pendingIntent(kind, 0)
        if (s.notifications && minutes != null) set(nextOccurrence(minutes), pi) else alarms.cancel(pi)
    }

    private fun set(at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pendingIntent(kind: String, id: Long): PendingIntent {
        val intent = Intent(app, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .setData(Uri.parse("dayflow://reminder/$kind/$id"))
            .putExtra(ReminderReceiver.EXTRA_KIND, kind)
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun nextOccurrence(minutes: Int): Long {
        val today = LocalDate.now()
        val at = epochMillis(today, minutes)
        return if (at > System.currentTimeMillis()) at else epochMillis(today.plusDays(1), minutes)
    }

    private fun epochMillis(date: LocalDate, minutes: Int): Long =
        LocalDateTime.of(date, LocalTime.of(minutes / 60, minutes % 60))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val KIND_HABIT = "habit"
        const val KIND_TASK = "task"
        const val KIND_MORNING = "morning"
        const val KIND_EVENING = "evening"
        const val KIND_FOCUS = "focus"
        const val KIND_CYCLE = "cycle"
        /** The daily cycle check runs at 9:00. */
        const val CYCLE_CHECK_MINUTES = 9 * 60
    }
}
