package com.dayflow.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dayflow.domain.SleepNight
import com.dayflow.domain.SleepRules
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.TimePickerDialogFor

data class SleepCardState(
    /** Last night's sleep, filed under today's date; null until logged. */
    val lastNight: SleepNight?,
    val goalMinutes: Int,
    val use24h: Boolean,
    /** Suggested times for a new entry: the latest logged night, or 23:00 → 07:00. */
    val defaultBed: Int,
    val defaultWake: Int,
)

@Composable
fun SleepCard(state: SleepCardState, onSave: (bed: Int, wake: Int) -> Unit, onDelete: () -> Unit, onOpenHistory: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val night = state.lastNight
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Last night · goal ${SleepRules.formatDuration(state.goalMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onOpenHistory,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("History", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (night == null) {
                Text(
                    "How did you sleep? Add when you went to bed and when you woke up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { editing = true }, modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log last night")
                }
            } else {
                Row(Modifier.fillMaxWidth().clickable { editing = true }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(SleepRules.formatDuration(night.durationMinutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${Fmt.time(night.bedMinutes, state.use24h)} → ${Fmt.time(night.wakeMinutes, state.use24h)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit sleep") }
                }
                val fraction = (night.durationMinutes.toFloat() / state.goalMinutes).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    if (night.durationMinutes >= state.goalMinutes) "Goal reached. Well rested! 🌙"
                    else "${SleepRules.formatDuration(state.goalMinutes - night.durationMinutes)} short of your goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    if (editing) {
        SleepDialog(
            bed = night?.bedMinutes ?: state.defaultBed,
            wake = night?.wakeMinutes ?: state.defaultWake,
            use24h = state.use24h,
            canDelete = night != null,
            onDismiss = { editing = false },
            onSave = { b, w -> onSave(b, w); editing = false },
            onDelete = { onDelete(); editing = false },
        )
    }
}

@Composable
fun SleepDialog(
    bed: Int,
    wake: Int,
    use24h: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var b by remember { mutableIntStateOf(bed) }
    var w by remember { mutableIntStateOf(wake) }
    var picking by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
        title = { Text("Last night's sleep") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeRow(Icons.Filled.Bedtime, "Went to bed", Fmt.time(b, use24h)) { picking = "bed" }
                TimeRow(Icons.Filled.WbSunny, "Woke up", Fmt.time(w, use24h)) { picking = "wake" }
                Text(
                    "You slept ${SleepRules.formatDuration(SleepRules.duration(b, w))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(b, w) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    when (picking) {
        "bed" -> TimePickerDialogFor(b, use24h, onDismiss = { picking = null }) { b = it; picking = null }
        "wake" -> TimePickerDialogFor(w, use24h, onDismiss = { picking = null }) { w = it; picking = null }
    }
}

@Composable
private fun TimeRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
