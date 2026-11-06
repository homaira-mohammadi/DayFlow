package com.dayflow.domain

enum class FocusMode(val label: String, val subtitle: String) {
    FOCUS("Focus time", "Time to concentrate"),
    SHORT_BREAK("Short break", "Take a breather"),
    LONG_BREAK("Long break", "Rest and recharge"),
}

enum class TimerStatus { IDLE, RUNNING, PAUSED }

/**
 * The Pomodoro timer. While running, time is derived from [endAt] (a wall-clock timestamp), so the
 * countdown stays correct when the app is in the background or the process is restarted.
 */
data class FocusTimerState(
    val mode: FocusMode = FocusMode.FOCUS,
    val status: TimerStatus = TimerStatus.IDLE,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    /** A long break replaces the short one after this many focus sessions. */
    val longBreakEvery: Int = 4,
    val endAt: Long = 0,
    /** Time left while idle or paused. */
    val remainingMs: Long = 25 * 60_000L,
    /** When the current session was first started; null until Start is pressed. */
    val startedAt: Long? = null,
    /** How many times the current focus session was paused. */
    val interruptions: Int = 0,
    /** Focus sessions completed since the last long break. */
    val completedInCycle: Int = 0,
    val linkTaskId: Long? = null,
    val linkHabitId: Long? = null,
    val linkLabel: String? = null,
) {
    fun durationMs(m: FocusMode = mode): Long = 60_000L * when (m) {
        FocusMode.FOCUS -> focusMinutes
        FocusMode.SHORT_BREAK -> shortBreakMinutes
        FocusMode.LONG_BREAK -> longBreakMinutes
    }

    fun remaining(now: Long): Long = if (status == TimerStatus.RUNNING) (endAt - now).coerceAtLeast(0) else remainingMs

    fun progress(now: Long): Float = (1f - remaining(now).toFloat() / durationMs()).coerceIn(0f, 1f)

    fun elapsedMs(now: Long): Long = durationMs() - remaining(now)
}
