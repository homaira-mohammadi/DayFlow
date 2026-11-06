package com.dayflow.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.dayflow.AppContainer
import com.dayflow.DayFlowApp
import com.dayflow.data.db.TaskEntity
import com.dayflow.domain.HabitDayItem
import com.dayflow.domain.TaskStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as DayFlowApp
        create(app.container, createSavedStateHandle())
    }
}

/** Check-in, rescheduling and correction actions available wherever a day's plan is shown. */
abstract class PlanViewModel(protected val c: AppContainer) : ViewModel() {

    fun toggleTask(task: TaskEntity) = viewModelScope.launch {
        c.tasks.setStatus(task, if (task.status == TaskStatus.DONE) TaskStatus.PENDING else TaskStatus.DONE)
    }

    fun reschedule(task: TaskEntity, to: LocalDate) = viewModelScope.launch { c.tasks.reschedule(task, to) }

    fun cancelTask(task: TaskEntity, cancel: Boolean) = viewModelScope.launch {
        c.tasks.setStatus(task, if (cancel) TaskStatus.CANCELLED else TaskStatus.PENDING)
    }

    /** One-tap toggle: full target when not yet recorded, cleared when already recorded. */
    fun quickHabit(item: HabitDayItem, date: LocalDate) = viewModelScope.launch {
        c.habits.setValue(item.habit.id, date, if (item.value > 0) 0.0 else item.schedule.target)
    }

    fun setHabitValue(habitId: Long, date: LocalDate, value: Double) = viewModelScope.launch {
        c.habits.setValue(habitId, date, value)
    }

    fun setSkipped(habitId: Long, date: LocalDate, skipped: Boolean) = viewModelScope.launch {
        c.habits.setSkipped(habitId, date, skipped)
    }

    /** Links the focus timer to this task; the caller then opens the Focus tab. */
    fun focusOn(task: TaskEntity) = c.focus.link(task.id, null, task.title)

    fun setRestDay(date: LocalDate, rest: Boolean) = viewModelScope.launch { c.habits.setRestDay(date, rest) }
}
