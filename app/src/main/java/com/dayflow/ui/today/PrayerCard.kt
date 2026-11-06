package com.dayflow.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerStatus
import com.dayflow.domain.PrayerSummary
import com.dayflow.ui.Fmt
import java.time.LocalDate

data class PrayerCardState(
    val date: LocalDate,
    val isToday: Boolean,
    val canGoBack: Boolean,
    /** A logged period day: prayers are excused, so nothing is asked for. */
    val excused: Boolean,
    val day: Map<Prayer, PrayerStatus>,
    val summary: PrayerSummary,
    /** Days in a row with all five prayers prayed. */
    val streak: Int,
)

@Composable
fun PrayerCard(
    state: PrayerCardState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSet: (LocalDate, Prayer, PrayerStatus?) -> Unit,
    onOpenHistory: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mosque, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Prayers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.excused) "Excused · period day"
                        else "${state.summary.prayed}/${Prayer.entries.size} prayed" +
                            if (state.summary.missed > 0) " · ${state.summary.missed} missed" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.streak > 0) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Text("${state.streak}", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onOpenHistory,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(start = 8.dp).height(34.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("History", style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousDay, enabled = state.canGoBack) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
                Text(
                    if (state.isToday) "Today" else Fmt.dayHeader(state.date),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onNextDay, enabled = !state.isToday) { Icon(Icons.Filled.ChevronRight, "Next day") }
            }
            if (state.excused) {
                Row(
                    Modifier.fillMaxWidth().padding(end = 8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "This is a logged period day, so prayers are excused. It won't break your streak.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(end = 8.dp)) {
                    Prayer.entries.forEach { prayer ->
                        PrayerRow(prayer, state.day[prayer]) { onSet(state.date, prayer, it) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrayerRow(prayer: Prayer, status: PrayerStatus?, onSet: (PrayerStatus?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
            Text(prayer.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(prayer.arabic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            PrayerStatus.entries.forEachIndexed { i, option ->
                val selected = status == option
                SegmentedButton(
                    selected = selected,
                    // Tapping the chosen status again clears it.
                    onClick = { onSet(if (selected) null else option) },
                    shape = SegmentedButtonDefaults.itemShape(i, PrayerStatus.entries.size),
                    colors = when (option) {
                        PrayerStatus.MISSED -> SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.errorContainer,
                            activeContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        PrayerStatus.LATE -> SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        PrayerStatus.ON_TIME -> SegmentedButtonDefaults.colors()
                    },
                    icon = {},
                    label = { Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                )
            }
        }
    }
}
