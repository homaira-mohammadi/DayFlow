package com.dayflow.ui.reflection

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.db.ReflectionEntity
import com.dayflow.domain.Mood
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class ReflectionForm(
    val mood: Mood? = null,
    val energy: Int? = null,
    val gratitude: String = "",
    val intention: String = "",
    val notes: String = "",
    val photoPath: String? = null,
) {
    val isBlank: Boolean
        get() = mood == null && energy == null && gratitude.isBlank() && intention.isBlank() && notes.isBlank() && photoPath == null
}

class ReflectionEditorViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val date: LocalDate = handle.get<Long>("date")?.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay) ?: LocalDate.now()

    var form by mutableStateOf(ReflectionForm())
        private set
    var exists by mutableStateOf(false)
        private set
    var photoBusy by mutableStateOf(false)
        private set
    var photoError by mutableStateOf(false)
        private set

    /** The photo stored with the saved reflection, and photos picked during this edit. */
    private var savedPhoto: String? = null
    private val pickedPhotos = mutableListOf<String>()
    private var committed = false

    init {
        viewModelScope.launch {
            val r = c.reflections.get(date) ?: return@launch
            exists = true
            savedPhoto = r.photoPath
            form = ReflectionForm(r.mood, r.energy, r.gratitude, r.intention, r.notes, r.photoPath)
        }
    }

    fun photoFile(name: String): File = c.photos.file(name)

    fun importPhoto(uri: Uri) {
        photoBusy = true
        photoError = false
        viewModelScope.launch {
            val name = c.photos.import(uri, "reflection-$date")
            photoBusy = false
            if (name == null) {
                photoError = true
            } else {
                pickedPhotos += name
                form = form.copy(photoPath = name)
            }
        }
    }

    fun removePhoto() {
        form = form.copy(photoPath = null)
    }

    fun update(transform: (ReflectionForm) -> ReflectionForm) {
        form = transform(form)
    }

    fun save(onSaved: () -> Unit) {
        val f = form
        viewModelScope.launch {
            if (f.isBlank) {
                c.reflections.delete(date)
            } else {
                c.reflections.save(
                    ReflectionEntity(
                        date = date.toEpochDay(), mood = f.mood, energy = f.energy,
                        gratitude = f.gratitude.trim(), intention = f.intention.trim(), notes = f.notes.trim(),
                        photoPath = f.photoPath,
                    ),
                )
            }
            // Remove photos that were replaced or picked and then dropped.
            (pickedPhotos + listOfNotNull(savedPhoto)).filter { it != f.photoPath }.forEach(c.photos::delete)
            committed = true
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        c.reflections.delete(date)
        onDeleted()
    }

    override fun onCleared() {
        // Leaving without saving: photos picked in this session aren't referenced by anything.
        if (!committed) pickedPhotos.forEach(c.photos::delete)
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> ReflectionEditorViewModel(c, h) }
    }
}

data class ReflectionHistoryState(
    val entries: List<ReflectionEntity> = emptyList(),
    /** Share of each mood among reflections from the last 30 days, in [Mood] order. */
    val moodShare: List<Pair<Mood, Float>> = emptyList(),
    val last30Count: Int = 0,
    val loading: Boolean = true,
)

class ReflectionHistoryViewModel(private val c: AppContainer) : ViewModel() {
    fun photoFile(name: String): File = c.photos.file(name)

    val state: StateFlow<ReflectionHistoryState> = c.reflections.all.map { all ->
        val since = LocalDate.now().minusDays(29).toEpochDay()
        val recent = all.filter { it.date >= since && it.mood != null }
        val share = Mood.entries.map { m -> m to if (recent.isEmpty()) 0f else recent.count { it.mood == m }.toFloat() / recent.size }
        ReflectionHistoryState(all, share, recent.size, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReflectionHistoryState())

    companion object {
        val Factory = appViewModelFactory { c, _ -> ReflectionHistoryViewModel(c) }
    }
}
