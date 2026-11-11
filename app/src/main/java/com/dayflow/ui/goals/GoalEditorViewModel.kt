package com.dayflow.ui.goals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.GoalEntity
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalForm(
    val title: String = "",
    val description: String = "",
    val targetDate: LocalDate? = null,
)

class GoalEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val goalId: Long = handle.get<Long>("id") ?: -1L
    val isNew = goalId <= 0
    private var original: GoalEntity? = null

    var form by mutableStateOf(GoalForm())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (!isNew) viewModelScope.launch {
            val g = c.goals.get(goalId) ?: return@launch
            original = g
            form = GoalForm(title = g.title, description = g.description, targetDate = g.targetDate?.let(LocalDate::ofEpochDay))
        }
    }

    fun update(transform: (GoalForm) -> GoalForm) {
        form = transform(form)
        error = null
    }

    /** [onSaved] receives the goal's id, so a new goal can be opened straight away. */
    fun save(onSaved: (Long) -> Unit) {
        val f = form
        if (f.title.isBlank()) {
            error = "Give the goal a title."
            return
        }
        val goal = (original ?: GoalEntity(title = "")).copy(
            title = f.title.trim(), description = f.description.trim(), targetDate = f.targetDate?.toEpochDay(),
        )
        viewModelScope.launch { onSaved(c.goals.save(goal)) }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> GoalEditorViewModel(c, h) }
    }
}
