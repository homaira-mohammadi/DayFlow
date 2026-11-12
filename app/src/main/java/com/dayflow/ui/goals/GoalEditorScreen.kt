package com.dayflow.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.DatePickerDialogFor
import com.dayflow.ui.components.SectionHeader
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorScreen(
    onClose: () -> Unit,
    onSaved: (id: Long, isNew: Boolean) -> Unit,
    vm: GoalEditorViewModel = viewModel(factory = GoalEditorViewModel.Factory),
) {
    val f = vm.form
    var pickingDate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New goal" else "Edit goal") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                actions = { TextButton(onClick = { vm.save { id -> onSaved(id, vm.isNew) } }) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            OutlinedTextField(
                value = f.title,
                onValueChange = { v -> vm.update { it.copy(title = v) } },
                label = { Text("What are you working toward?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.description,
                onValueChange = { v -> vm.update { it.copy(description = v) } },
                label = { Text("Why it matters (optional)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionHeader("Target date")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickingDate = true }) {
                    Icon(Icons.Filled.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(f.targetDate?.let(Fmt::fullDate) ?: "No target date")
                }
                if (f.targetDate != null) TextButton(onClick = { vm.update { it.copy(targetDate = null) } }) { Text("Clear") }
            }
            Text(
                "After saving, add milestones and link the tasks and habits that move this goal forward.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(Modifier.size(40.dp))
        }
    }

    if (pickingDate) {
        DatePickerDialogFor(f.targetDate ?: LocalDate.now().plusMonths(1), onDismiss = { pickingDate = false }) { d ->
            vm.update { it.copy(targetDate = d) }
        }
    }
}
