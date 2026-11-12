package com.dayflow.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ConfirmDialog
import com.dayflow.ui.components.DatePickerDialogFor
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.TimePickerDialogFor
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    vm: TaskEditorViewModel = viewModel(factory = TaskEditorViewModel.Factory),
) {
    val f = vm.form
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val use24h = settings?.use24h ?: false
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New task" else "Edit task") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete task") }
                    TextButton(onClick = { vm.save(onClose) }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            if (vm.linksToGoal) {
                Text("This task will be linked to your goal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            OutlinedTextField(
                value = f.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.notes,
                onValueChange = { v -> vm.update { it.copy(notes = v) } },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionHeader("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickingDate = true }) {
                    Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(Fmt.relative(f.date))
                }
                OutlinedButton(onClick = { pickingTime = true }) {
                    Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(f.timeMinutes?.let { Fmt.time(it, use24h) } ?: "Any time")
                }
                if (f.timeMinutes != null) TextButton(onClick = { vm.update { it.copy(timeMinutes = null, remind = false) } }) { Text("Clear") }
            }
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                AssistChip(onClick = { vm.update { it.copy(date = today) } }, label = { Text("Today") })
                AssistChip(onClick = { vm.update { it.copy(date = today.plusDays(1)) } }, label = { Text("Tomorrow") })
            }
            if (f.timeMinutes != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me at this time", Modifier.weight(1f))
                    Switch(checked = f.remind, onCheckedChange = { v -> vm.update { it.copy(remind = v) } })
                }
            }
            vm.original?.takeIf { it.rescheduleCount > 0 }?.let { t ->
                Text(
                    "Originally planned for ${Fmt.shortDate(LocalDate.ofEpochDay(t.originalDate))} · moved ${t.rescheduleCount}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionHeader("Priority")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("One of today's top priorities", style = MaterialTheme.typography.bodyLarge)
                    Text("Shown first on the Today screen. Aim for three or fewer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.isFocus, onCheckedChange = { v -> vm.update { it.copy(isFocus = v) } })
            }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Low", 1 to "Normal", 2 to "High").forEach { (p, label) ->
                    FilterChip(selected = f.priority == p, onClick = { vm.update { it.copy(priority = p) } }, label = { Text(label) })
                }
            }

            SectionHeader("Details")
            OutlinedTextField(
                value = f.category,
                onValueChange = { v -> vm.update { it.copy(category = v) } },
                label = { Text("Category") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            if (categories.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        FilterChip(selected = f.category == cat, onClick = { vm.update { it.copy(category = cat) } }, label = { Text(cat) })
                    }
                }
            }
            OutlinedTextField(
                value = f.estimate,
                onValueChange = { v -> vm.update { it.copy(estimate = v.filter(Char::isDigit)) } },
                label = { Text("Estimated minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Spacer(Modifier.size(40.dp))
        }
    }

    if (pickingDate) DatePickerDialogFor(f.date, onDismiss = { pickingDate = false }) { d -> vm.update { it.copy(date = d) } }
    if (pickingTime) {
        val initial = f.timeMinutes ?: LocalTime.now().plusHours(1).withMinute(0).let { it.hour * 60 }
        TimePickerDialogFor(initial, use24h, onDismiss = { pickingTime = false }) { m -> vm.update { it.copy(timeMinutes = m) } }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete task?",
            body = "This removes the task and its history. To keep a record, cancel it instead from the task's menu.",
            confirm = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.delete(onClose) },
        )
    }
}
