package com.dayflow.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.FocusSessionEntity
import com.dayflow.domain.FocusMode
import com.dayflow.domain.FocusTimerState
import com.dayflow.domain.HabitGoalType
import com.dayflow.domain.TaskStatus
import com.dayflow.domain.TimerStatus
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Something a focus session can be linked to. */
data class FocusTarget(val taskId: Long?, val habitId: Long?, val label: String, val addsMinutes: Boolean)

data class FocusUiState(
    val timer: FocusTimerState = FocusTimerState(),
    val now: Long = System.currentTimeMillis(),
    val todayMinutes: Int = 0,
    val todaySessions: Int = 0,
    val weekMinutes: Int = 0,
    val targets: List<FocusTarget> = emptyList(),
) {
    val remainingMs: Long get() = timer.remaining(now)
    val progress: Float get() = timer.progress(now)
}

private fun startOfDayMillis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun FocusSessionEntity.localDate(): LocalDate =
    java.time.Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()

class FocusViewModel(private val c: AppContainer) : ViewModel() {
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(250)
        }
    }

    private val _exactAlarmsAllowed = MutableStateFlow(c.reminders.canScheduleExact())
    val exactAlarmsAllowed: StateFlow<Boolean> = _exactAlarmsAllowed

    private val stats = combine(
        c.db.focusDao().observeSince(startOfDayMillis(LocalDate.now().minusDays(7))),
        c.settings.settings,
    ) { sessions, s ->
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(s.weekStart))
        val todays = sessions.filter { it.localDate() == today }
        Triple(
            todays.sumOf { it.actualSeconds } / 60,
            todays.count { it.completed },
            sessions.filter { it.localDate() >= weekStart }.sumOf { it.actualSeconds } / 60,
        )
    }

    private val targets = c.plans.history.map { h ->
        val plan = h.dayPlan(LocalDate.now(), minimumCounts = true)
        plan.tasks.filter { it.status == TaskStatus.PENDING }.map { FocusTarget(it.id, null, it.title, addsMinutes = false) } +
            plan.habits.filter { !it.skipped }.map {
                FocusTarget(null, it.habit.id, it.habit.name, addsMinutes = it.habit.goalType == HabitGoalType.DURATION)
            }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<FocusUiState> = combine(c.focus.state, ticker, stats, targets) { timer, now, (todayMin, todayCount, weekMin), t ->
        FocusUiState(timer, now, todayMin, todayCount, weekMin, t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState(timer = c.focus.state.value))

    init {
        // When the countdown reaches zero while the screen is open, finish right away instead of waiting for the alarm.
        viewModelScope.launch {
            state.collect { s ->
                if (s.timer.status == TimerStatus.RUNNING && s.remainingMs == 0L) c.focus.finishIfDue()
            }
        }
    }

    fun startOrPause() {
        if (state.value.timer.status == TimerStatus.RUNNING) c.focus.pause() else c.focus.start()
    }

    fun reset() = c.focus.reset()
    fun selectMode(mode: FocusMode) = c.focus.selectMode(mode)
    fun link(target: FocusTarget?) = c.focus.link(target?.taskId, target?.habitId, target?.label)
    fun setDurations(focus: Int, shortBreak: Int, longBreak: Int, longEvery: Int) = c.focus.setDurations(focus, shortBreak, longBreak, longEvery)

    fun refreshPermissions() {
        _exactAlarmsAllowed.value = c.reminders.canScheduleExact()
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> FocusViewModel(c) }
    }
}

data class FocusDay(val date: LocalDate, val sessions: List<FocusSessionEntity>) {
    val minutes: Int get() = sessions.sumOf { it.actualSeconds } / 60
}

data class FocusHistoryState(
    val days: List<FocusDay> = emptyList(),
    /** Minutes per day for the last 7 days, oldest first. */
    val week: List<Pair<LocalDate, Int>> = emptyList(),
    val totalMinutes30: Int = 0,
    val completed30: Int = 0,
)

class FocusHistoryViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<FocusHistoryState> =
        c.db.focusDao().observeSince(startOfDayMillis(LocalDate.now().minusDays(29))).map { sessions ->
            val today = LocalDate.now()
            val byDay = sessions.groupBy { it.localDate() }
            FocusHistoryState(
                days = byDay.map { (d, s) -> FocusDay(d, s) }.sortedByDescending { it.date },
                week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { d -> d to (byDay[d].orEmpty().sumOf { it.actualSeconds } / 60) },
                totalMinutes30 = sessions.sumOf { it.actualSeconds } / 60,
                completed30 = sessions.count { it.completed },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusHistoryState())

    companion object {
        val Factory = appViewModelFactory { c, _ -> FocusHistoryViewModel(c) }
    }
}
