package com.dayflow.ui.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.TaskEntity
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskForm(
    val title: String = "",
    val notes: String = "",
    val date: LocalDate = LocalDate.now(),
    val timeMinutes: Int? = null,
    val priority: Int = 1,
    val category: String = "",
    val estimate: String = "",
    val isFocus: Boolean = false,
    val remind: Boolean = false,
)

class TaskEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val taskId: Long = handle.get<Long>("id") ?: -1L
    val isNew = taskId <= 0
    var original by mutableStateOf<TaskEntity?>(null)
        private set

    var form by mutableStateOf(
        TaskForm(date = handle.get<Long>("date")?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay) ?: LocalDate.now()),
    )
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val categories: StateFlow<List<String>> =
        c.plans.history.map { it.categories }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (!isNew) viewModelScope.launch {
            val t = c.tasks.get(taskId) ?: return@launch
            original = t
            form = TaskForm(
                title = t.title, notes = t.notes, date = LocalDate.ofEpochDay(t.date), timeMinutes = t.timeMinutes,
                priority = t.priority, category = t.category, estimate = t.estimatedMinutes?.toString() ?: "",
                isFocus = t.isFocus, remind = t.remind,
            )
        }
    }

    fun update(transform: (TaskForm) -> TaskForm) {
        form = transform(form)
        error = null
    }

    fun save(onSaved: () -> Unit) {
        val f = form
        val estimate = f.estimate.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        error = when {
            f.title.isBlank() -> "Give the task a title."
            f.estimate.isNotBlank() && (estimate == null || estimate <= 0) -> "Estimated minutes must be a whole number."
            else -> null
        }
        if (error != null) return
        val base = original ?: TaskEntity(title = "", date = f.date.toEpochDay())
        val task = base.copy(
            title = f.title.trim(), notes = f.notes.trim(), date = f.date.toEpochDay(), timeMinutes = f.timeMinutes,
            priority = f.priority, category = f.category.trim(), estimatedMinutes = estimate, isFocus = f.isFocus,
            remind = f.remind && f.timeMinutes != null,
        )
        viewModelScope.launch {
            c.tasks.save(task)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val t = original ?: return
        viewModelScope.launch {
            c.tasks.delete(t)
            onDeleted()
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> TaskEditorViewModel(c, h) }
    }
}
