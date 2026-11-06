package com.dayflow.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dayflow.AppContainer
import com.dayflow.DayFlowApp
import com.dayflow.domain.Achievement
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.CycleEngine
import com.dayflow.domain.CycleStatus
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.HabitRules
import com.dayflow.domain.HabitStatus
import com.dayflow.domain.History
import com.dayflow.domain.TaskStatus
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_CYCLE
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_EVENING
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_FOCUS
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_HABIT
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_MORNING
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_TASK
import com.dayflow.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as DayFlowApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, container, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, c: AppContainer, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val today = LocalDate.now()
        if (kind == KIND_FOCUS) {
            when (intent.action) {
                ACTION_FIRE -> c.focus.finishIfDue()
                ACTION_FOCUS_PAUSE -> c.focus.pause()
                ACTION_FOCUS_START -> c.focus.start()
            }
            return
        }
        when (intent.action) {
            ACTION_FIRE -> fire(context, c, kind, id, today)
            ACTION_DONE -> {
                Notifier.cancel(context, kind, id.toInt())
                if (kind == KIND_HABIT) {
                    val h = c.habits.get(id) ?: return
                    val due = HabitRules.dueOn(h.schedules, today) ?: return
                    c.habits.setValue(id, today, due.target)
                } else if (kind == KIND_TASK) {
                    c.tasks.get(id)?.let { c.tasks.setStatus(it, TaskStatus.DONE) }
                }
            }
            ACTION_TOMORROW -> {
                Notifier.cancel(context, kind, id.toInt())
                c.tasks.get(id)?.let { c.tasks.reschedule(it, today.plusDays(1)) }
            }
            ACTION_SNOOZE -> {
                Notifier.cancel(context, kind, id.toInt())
                c.reminders.snooze(kind, id)
            }
        }
    }

    private suspend fun fire(context: Context, c: AppContainer, kind: String, id: Long, today: LocalDate) {
        val settings = c.settings.current()
        if (!settings.notifications) return
        val history = snapshot(c)
        val plan = history.dayPlan(today, settings.minimumCounts)
        when (kind) {
            KIND_HABIT -> {
                c.reminders.refreshHabit(id) // arm tomorrow's alarm
                val item = plan.habits.firstOrNull { it.habit.id == id } ?: return
                if (item.habit.status != HabitStatus.ACTIVE || plan.isRestDay || item.skipped || item.isDone) return
                val target = item.schedule.target
                val text = when (item.habit.goalType) {
                    HabitGoalType.CHECK -> "Time for this habit."
                    else -> buildString {
                        append("Target: ${Fmt.amount(target, item.habit.goalType, item.habit.unit)}")
                        item.schedule.minimumTarget?.let { append(" · busy-day minimum: ${Fmt.amount(it, item.habit.goalType, item.habit.unit)}") }
                        if (item.achievement == Achievement.PARTIAL) append(" · so far ${Fmt.amount(item.value, item.habit.goalType, item.habit.unit)}")
                    }
                }
                Notifier.show(
                    context, KIND_HABIT, id.toInt(), item.habit.name, text,
                    listOf(action(context, ACTION_DONE, kind, id, "Done"), action(context, ACTION_SNOOZE, kind, id, "Snooze 15 min")),
                )
            }
            KIND_TASK -> {
                val task = c.tasks.get(id) ?: return
                if (task.status != TaskStatus.PENDING || task.date != today.toEpochDay()) return
                Notifier.show(
                    context, KIND_TASK, id.toInt(), task.title,
                    task.notes.ifBlank { "Scheduled for ${Fmt.time(task.timeMinutes ?: 0, settings.use24h)}" },
                    listOf(
                        action(context, ACTION_DONE, kind, id, "Done"),
                        action(context, ACTION_TOMORROW, kind, id, "Tomorrow"),
                        action(context, ACTION_SNOOZE, kind, id, "Snooze 15 min"),
                    ),
                )
            }
            KIND_MORNING -> {
                c.reminders.refreshAll()
                val stats = plan.stats
                if (stats.planned == 0) {
                    Notifier.show(context, kind, 0, "Plan your day", "Start with a short morning reflection, then pick up to three priorities for today.")
                } else {
                    Notifier.show(context, kind, 0, "Plan your day", "${plan.tasks.size} tasks and ${plan.habits.size} habits planned today. Take a minute for your morning reflection.")
                }
            }
            KIND_CYCLE -> {
                c.reminders.refreshAll()
                if (!settings.cycleTracking || !settings.cycleReminders) return
                val engine = CycleEngine(c.cycle.periodDaysNow(), settings.cycleConfig, today)
                val status = engine.status
                val pmsStarts = engine.kindOn(today) == CycleDayKind.PMS && engine.kindOn(today.minusDays(1)) != CycleDayKind.PMS
                val text = when {
                    status is CycleStatus.Upcoming && status.daysUntil == 2 ->
                        "Your period may start in 2 days. Keep your plans flexible."
                    pmsStarts -> "PMS days may be starting. Consider a lighter plan and use minimum versions of habits."
                    status is CycleStatus.Late && (status.days == 3 || status.days == 7) ->
                        "Your period is ${status.days} days later than predicted. Log it when it starts."
                    else -> return
                }
                Notifier.show(context, kind, 0, "Cycle", text, sensitive = true)
            }
            KIND_EVENING -> {
                c.reminders.refreshAll()
                val stats = plan.stats
                if (stats.planned == 0) return
                Notifier.show(context, kind, 0, "Evening review", "${stats.completed} of ${stats.planned} planned activities done. Anything to move to tomorrow?")
            }
        }
    }

    private suspend fun snapshot(c: AppContainer): History {
        val db = c.db
        return History(
            db.habitDao().all(), db.taskDao().all(), db.habitDao().allLogs(),
            db.restDayDao().all().map { it.date }.toSet(), db.taskDao().allReschedules(),
        )
    }

    companion object Actions {
        fun build(context: Context, action: String, kind: String, id: Long, label: String): NotificationAction {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("dayflow://action/$action/$kind/$id"))
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_ID, id)
            val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return NotificationAction(label, pi)
        }

        const val ACTION_FIRE = "com.dayflow.reminder.FIRE"
        const val ACTION_DONE = "com.dayflow.reminder.DONE"
        const val ACTION_TOMORROW = "com.dayflow.reminder.TOMORROW"
        const val ACTION_SNOOZE = "com.dayflow.reminder.SNOOZE"
        const val ACTION_FOCUS_PAUSE = "com.dayflow.focus.PAUSE"
        const val ACTION_FOCUS_START = "com.dayflow.focus.START"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
    }

    private fun action(context: Context, action: String, kind: String, id: Long, label: String) =
        build(context, action, kind, id, label)
}
