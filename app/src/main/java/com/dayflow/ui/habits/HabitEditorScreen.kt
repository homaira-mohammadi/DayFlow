package com.dayflow.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.DaySection
import com.dayflow.domain.Days
import com.dayflow.domain.HabitGoalType
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.HabitIcons
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.TimePickerDialogFor
import com.dayflow.ui.theme.HabitColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    onClose: () -> Unit,
    vm: HabitEditorViewModel = viewModel(factory = HabitEditorViewModel.Factory),
) {
    val f = vm.form
    val categories by vm.categories.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val weekStart = settings?.weekStart ?: DayOfWeek.MONDAY
    val use24h = settings?.use24h ?: false
    var pickingTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "New habit" else "Edit habit") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") } },
                actions = { TextButton(onClick = { vm.save(onClose) }) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") },
                placeholder = { Text("e.g. Study programming") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HabitIcons.all.forEach { (key, icon) ->
                    val selected = f.icon == key
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(f.color) else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { vm.update { it.copy(icon = key) } }
                            .semantics { contentDescription = "Icon $key"; this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SectionHeader("Color")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitColors.forEachIndexed { i, color ->
                    val selected = f.color == color.toArgb()
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { vm.update { it.copy(color = color.toArgb()) } }
                            .semantics { contentDescription = "Color ${i + 1}"; this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            SectionHeader("Category")
            OutlinedTextField(
                value = f.category,
                onValueChange = { v -> vm.update { it.copy(category = v) } },
                placeholder = { Text("e.g. Learning, Health") },
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

            SectionHeader("Time of day")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DaySection.entries.forEach { s ->
                    FilterChip(selected = f.section == s, onClick = { vm.update { it.copy(section = s) } }, label = { Text(s.label) })
                }
            }

            SectionHeader("Schedule")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Every day" to Days.ALL, "Weekdays" to Days.WEEKDAYS, "Weekends" to Days.WEEKENDS).forEach { (label, mask) ->
                    FilterChip(selected = f.daysMask == mask, onClick = { vm.update { it.copy(daysMask = mask) } }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Days.orderedWeek(weekStart).forEach { day ->
                    val on = Days.contains(f.daysMask, day)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { vm.update { it.copy(daysMask = Days.toggle(it.daysMask, day)) } }
                            .semantics {
                                contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                                this.selected = on
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionHeader("Goal")
            val types = HabitGoalType.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = f.goalType == t,
                        onClick = { vm.update { it.copy(goalType = t) } },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) {
                        Text(
                            when (t) {
                                HabitGoalType.CHECK -> "Yes / no"
                                HabitGoalType.QUANTITY -> "Amount"
                                HabitGoalType.DURATION -> "Duration"
                            },
                        )
                    }
                }
            }
            if (f.goalType != HabitGoalType.CHECK) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = f.target,
                        onValueChange = { v -> vm.update { it.copy(target = v) } },
                        label = { Text(if (f.goalType == HabitGoalType.DURATION) "Target minutes" else "Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (f.goalType == HabitGoalType.QUANTITY) {
                        OutlinedTextField(
                            value = f.unit,
                            onValueChange = { v -> vm.update { it.copy(unit = v) } },
                            label = { Text("Unit") },
                            placeholder = { Text("pages") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimum version", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "A smaller target for busy days, e.g. 5 minutes instead of 20. The app records which one you reached.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = f.minimumEnabled, onCheckedChange = { v -> vm.update { it.copy(minimumEnabled = v) } })
                }
                if (f.minimumEnabled) {
                    OutlinedTextField(
                        value = f.minimum,
                        onValueChange = { v -> vm.update { it.copy(minimum = v) } },
                        label = { Text("Minimum") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            SectionHeader("Reminder")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    f.reminderMinutes?.let { "Every scheduled day at ${Fmt.time(it, use24h)}" } ?: "No reminder",
                    Modifier.weight(1f),
                )
                if (f.reminderMinutes != null) OutlinedButton(onClick = { pickingTime = true }) { Text("Change") }
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = f.reminderMinutes != null,
                    onCheckedChange = { on -> vm.update { it.copy(reminderMinutes = if (on) 9 * 60 else null) }; if (on) pickingTime = true },
                )
            }

            if (!vm.isNew) {
                Text(
                    "Schedule and target changes apply from today. Past days keep the schedule they had, so your history stays accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
            Spacer(Modifier.size(40.dp))
        }
    }

    if (pickingTime) {
        TimePickerDialogFor(f.reminderMinutes ?: (9 * 60), use24h, onDismiss = { pickingTime = false }) { m ->
            vm.update { it.copy(reminderMinutes = m) }
        }
    }
}
