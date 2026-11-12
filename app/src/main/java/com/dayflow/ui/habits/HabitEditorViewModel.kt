package com.dayflow.ui.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.HabitEntity
import com.dayflow.domain.DaySection
import com.dayflow.domain.Days
import com.dayflow.domain.HabitGoalType
import com.dayflow.ui.Fmt
import com.dayflow.ui.appViewModelFactory
import com.dayflow.ui.theme.HabitColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitForm(
    val name: String = "",
    val icon: String = "check",
    val color: Int = HabitColors[0].toArgb(),
    val category: String = "",
    val section: DaySection = DaySection.ANYTIME,
    val goalType: HabitGoalType = HabitGoalType.CHECK,
    val unit: String = "",
    val daysMask: Int = Days.ALL,
    val target: String = "",
    val minimumEnabled: Boolean = false,
    val minimum: String = "",
    val reminderMinutes: Int? = null,
)

class HabitEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val habitId: Long = handle.get<Long>("id") ?: -1L
    val isNew = habitId <= 0
    /** Set when the editor was opened from a goal: the new habit is linked to it. */
    private val goalId: Long = handle.get<Long>("goal") ?: -1L
    val linksToGoal = isNew && goalId > 0
    private var original: HabitEntity? = null

    var form by mutableStateOf(HabitForm())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val categories: StateFlow<List<String>> =
        c.plans.history.map { it.categories }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        if (!isNew) viewModelScope.launch {
            val h = c.habits.get(habitId)?.habit ?: return@launch
            original = h
            form = HabitForm(
                name = h.name, icon = h.icon, color = h.color, category = h.category, section = h.section,
                goalType = h.goalType, unit = h.unit, daysMask = h.daysMask,
                target = if (h.goalType == HabitGoalType.CHECK) "" else Fmt.number(h.target),
                minimumEnabled = h.minimumTarget != null,
                minimum = h.minimumTarget?.let(Fmt::number) ?: "",
                reminderMinutes = h.reminderMinutes,
            )
        }
    }

    fun update(transform: (HabitForm) -> HabitForm) {
        form = transform(form)
        error = null
    }

    fun save(onSaved: () -> Unit) {
        val f = form
        val target = if (f.goalType == HabitGoalType.CHECK) 1.0 else f.target.replace(',', '.').toDoubleOrNull()
        val minimum = if (f.goalType != HabitGoalType.CHECK && f.minimumEnabled) f.minimum.replace(',', '.').toDoubleOrNull() else null
        error = when {
            f.name.isBlank() -> "Give the habit a name."
            f.daysMask == 0 -> "Choose at least one day."
            target == null || target <= 0 -> "Enter a target greater than zero."
            f.goalType != HabitGoalType.CHECK && f.minimumEnabled && (minimum == null || minimum <= 0) -> "Enter a minimum greater than zero."
            minimum != null && minimum >= target -> "The minimum should be smaller than the full target."
            else -> null
        }
        if (error != null) return
        val base = original ?: HabitEntity(name = "", color = f.color)
        val entity = base.copy(
            name = f.name.trim(), icon = f.icon, color = f.color, category = f.category.trim(), section = f.section,
            goalType = f.goalType, unit = if (f.goalType == HabitGoalType.QUANTITY) f.unit.trim() else "",
            daysMask = f.daysMask, target = target!!, minimumTarget = minimum, reminderMinutes = f.reminderMinutes,
        )
        viewModelScope.launch {
            val id = c.habits.save(entity)
            if (linksToGoal) c.goals.linkHabit(goalId, id)
            onSaved()
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> HabitEditorViewModel(c, h) }
    }
}
