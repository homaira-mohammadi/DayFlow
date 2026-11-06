package com.dayflow.focus

import android.content.Context
import android.content.SharedPreferences
import com.dayflow.data.db.DayFlowDatabase
import com.dayflow.data.db.FocusSessionEntity
import com.dayflow.data.repo.HabitRepository
import com.dayflow.domain.FocusMode
import com.dayflow.domain.FocusTimerState
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.TimerStatus
import com.dayflow.notifications.Notifier
import com.dayflow.notifications.ReminderReceiver
import com.dayflow.notifications.ReminderScheduler
import com.dayflow.notifications.ReminderScheduler.Companion.KIND_FOCUS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Owns the single Pomodoro timer. State is persisted on every change so a running timer survives
 * the app being closed; an alarm wakes the app when time runs out.
 */
class FocusController(
    context: Context,
    private val db: DayFlowDatabase,
    private val habits: HabitRepository,
    private val reminders: ReminderScheduler,
) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences = app.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    @Synchronized
    fun start() {
        val s = _state.value
        if (s.status == TimerStatus.RUNNING) return
        val now = System.currentTimeMillis()
        val next = s.copy(status = TimerStatus.RUNNING, endAt = now + s.remainingMs, startedAt = s.startedAt ?: now)
        save(next)
        reminders.scheduleFocusEnd(next.endAt)
        showRunning(next)
    }

    @Synchronized
    fun pause() {
        val s = _state.value
        if (s.status != TimerStatus.RUNNING) return
        val now = System.currentTimeMillis()
        save(
            s.copy(
                status = TimerStatus.PAUSED,
                remainingMs = s.remaining(now),
                interruptions = if (s.mode == FocusMode.FOCUS) s.interruptions + 1 else s.interruptions,
            ),
        )
        reminders.cancelFocusEnd()
        Notifier.cancel(app, Notifier.FOCUS_TAG, 0)
    }

    /** Stops the current session. Focus time already spent (a minute or more) is still recorded. */
    @Synchronized
    fun reset() {
        val s = _state.value
        recordPartial(s)
        save(idle(s, s.mode))
        reminders.cancelFocusEnd()
        Notifier.cancel(app, Notifier.FOCUS_TAG, 0)
    }

    @Synchronized
    fun selectMode(mode: FocusMode) {
        val s = _state.value
        if (s.mode == mode && s.status == TimerStatus.IDLE) return
        recordPartial(s)
        save(idle(s, mode))
        reminders.cancelFocusEnd()
        Notifier.cancel(app, Notifier.FOCUS_TAG, 0)
    }

    @Synchronized
    fun link(taskId: Long?, habitId: Long?, label: String?) {
        save(_state.value.copy(linkTaskId = taskId, linkHabitId = habitId, linkLabel = label))
    }

    @Synchronized
    fun setDurations(focus: Int, shortBreak: Int, longBreak: Int, longEvery: Int) {
        val s = _state.value.copy(focusMinutes = focus, shortBreakMinutes = shortBreak, longBreakMinutes = longBreak, longBreakEvery = longEvery)
        // Only an untouched timer picks up the new length; a session in progress keeps its own.
        save(if (s.status == TimerStatus.IDLE) s.copy(remainingMs = s.durationMs()) else s)
    }

    /** Completes the session if its time is up. Safe to call repeatedly (UI ticks and the alarm both call it). */
    @Synchronized
    fun finishIfDue() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (s.status != TimerStatus.RUNNING || now < s.endAt - 500) return
        reminders.cancelFocusEnd()
        val nextMode: FocusMode
        val completed: Int
        if (s.mode == FocusMode.FOCUS) {
            record(s, actualMs = s.durationMs(), completed = true, endedAt = s.endAt)
            completed = s.completedInCycle + 1
            nextMode = if (completed >= s.longBreakEvery) FocusMode.LONG_BREAK else FocusMode.SHORT_BREAK
        } else {
            completed = if (s.mode == FocusMode.LONG_BREAK) 0 else s.completedInCycle
            nextMode = FocusMode.FOCUS
        }
        val next = idle(s, nextMode).copy(completedInCycle = completed)
        save(next)
        val (title, text, button) = if (s.mode == FocusMode.FOCUS) {
            Triple(
                "Focus session complete",
                "${s.focusMinutes} minutes${s.linkLabel?.let { " on $it" } ?: ""}. Time for a ${if (nextMode == FocusMode.LONG_BREAK) "long" else "short"} break.",
                "Start break",
            )
        } else {
            Triple("Break's over", "Ready for another ${s.focusMinutes}-minute focus session?", "Start focus")
        }
        Notifier.showFocusDone(
            app, title, text,
            listOf(ReminderReceiver.build(app, ReminderReceiver.ACTION_FOCUS_START, KIND_FOCUS, 0, button)),
        )
    }

    /** Re-arms the end alarm after a reboot or app restart. */
    fun rearm() {
        val s = _state.value
        if (s.status != TimerStatus.RUNNING) return
        if (System.currentTimeMillis() >= s.endAt) finishIfDue() else reminders.scheduleFocusEnd(s.endAt)
    }

    private fun idle(s: FocusTimerState, mode: FocusMode) = s.copy(
        mode = mode,
        status = TimerStatus.IDLE,
        remainingMs = s.durationMs(mode),
        endAt = 0,
        startedAt = null,
        interruptions = 0,
    )

    private fun recordPartial(s: FocusTimerState) {
        if (s.mode != FocusMode.FOCUS || s.startedAt == null) return
        val elapsed = s.elapsedMs(System.currentTimeMillis())
        if (elapsed >= 60_000) record(s, elapsed, completed = false, endedAt = System.currentTimeMillis())
    }

    private fun record(s: FocusTimerState, actualMs: Long, completed: Boolean, endedAt: Long) {
        val startedAt = s.startedAt ?: return
        val minutes = (actualMs / 60_000).toInt()
        scope.launch {
            db.focusDao().insert(
                FocusSessionEntity(
                    startedAt = startedAt,
                    endedAt = endedAt,
                    plannedSeconds = (s.durationMs() / 1000).toInt(),
                    actualSeconds = (actualMs / 1000).toInt(),
                    completed = completed,
                    interruptions = s.interruptions,
                    taskId = s.linkTaskId,
                    habitId = s.linkHabitId,
                    label = s.linkLabel.orEmpty(),
                ),
            )
            // Focus minutes count toward a linked duration habit on the day the session started.
            val habitId = s.linkHabitId ?: return@launch
            val habit = habits.get(habitId)?.habit ?: return@launch
            if (habit.goalType == HabitGoalType.DURATION && minutes > 0) {
                val day = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                habits.addValue(habitId, day, minutes.toDouble())
            }
        }
    }

    private fun showRunning(s: FocusTimerState) {
        val title = when (s.mode) {
            FocusMode.FOCUS -> "Focusing" + (s.linkLabel?.let { " on $it" } ?: "")
            FocusMode.SHORT_BREAK -> "Short break"
            FocusMode.LONG_BREAK -> "Long break"
        }
        Notifier.showFocusRunning(
            app, title, s.endAt,
            listOf(ReminderReceiver.build(app, ReminderReceiver.ACTION_FOCUS_PAUSE, KIND_FOCUS, 0, "Pause")),
        )
    }

    private fun save(s: FocusTimerState) {
        _state.value = s
        prefs.edit()
            .putString("mode", s.mode.name)
            .putString("status", s.status.name)
            .putInt("focus", s.focusMinutes)
            .putInt("short", s.shortBreakMinutes)
            .putInt("long", s.longBreakMinutes)
            .putInt("longEvery", s.longBreakEvery)
            .putLong("endAt", s.endAt)
            .putLong("remaining", s.remainingMs)
            .putLong("startedAt", s.startedAt ?: -1)
            .putInt("interruptions", s.interruptions)
            .putInt("cycle", s.completedInCycle)
            .putLong("linkTask", s.linkTaskId ?: -1)
            .putLong("linkHabit", s.linkHabitId ?: -1)
            .putString("linkLabel", s.linkLabel)
            .apply()
    }

    private fun load(): FocusTimerState {
        val d = FocusTimerState()
        if (!prefs.contains("mode")) return d
        return FocusTimerState(
            mode = runCatching { FocusMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(d.mode),
            status = runCatching { TimerStatus.valueOf(prefs.getString("status", null)!!) }.getOrDefault(d.status),
            focusMinutes = prefs.getInt("focus", d.focusMinutes),
            shortBreakMinutes = prefs.getInt("short", d.shortBreakMinutes),
            longBreakMinutes = prefs.getInt("long", d.longBreakMinutes),
            longBreakEvery = prefs.getInt("longEvery", d.longBreakEvery),
            endAt = prefs.getLong("endAt", 0),
            remainingMs = prefs.getLong("remaining", d.remainingMs),
            startedAt = prefs.getLong("startedAt", -1).takeIf { it >= 0 },
            interruptions = prefs.getInt("interruptions", 0),
            completedInCycle = prefs.getInt("cycle", 0),
            linkTaskId = prefs.getLong("linkTask", -1).takeIf { it >= 0 },
            linkHabitId = prefs.getLong("linkHabit", -1).takeIf { it >= 0 },
            linkLabel = prefs.getString("linkLabel", null),
        )
    }
}
