package com.dayflow.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.AppContainer
import com.dayflow.data.prefs.UserSettings
import com.dayflow.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun update(transform: (UserSettings) -> UserSettings) = viewModelScope.launch {
        c.settings.update(transform)
        c.reminders.refreshAll()
    }

    fun export(resolver: ContentResolver, uri: Uri, includeCycle: Boolean) = viewModelScope.launch {
        _message.value = runCatching {
            resolver.openOutputStream(uri)?.use { c.backup.exportJson(it, includeCycle) } ?: error("Couldn't open the file")
            "Data exported"
        }.getOrElse { "Export failed: ${it.message}" }
    }

    fun deleteAll() = viewModelScope.launch {
        c.backup.deleteAll()
        _message.value = "All tasks, habits and history were deleted"
    }

    fun deleteCycleData() = viewModelScope.launch {
        c.cycle.deleteAll()
        _message.value = "Period and symptom records were deleted"
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun markUnlocked() = c.appLock.markUnlocked()

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> SettingsViewModel(c) }
    }
}
