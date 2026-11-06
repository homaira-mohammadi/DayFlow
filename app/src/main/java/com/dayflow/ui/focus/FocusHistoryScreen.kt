package com.dayflow.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.data.db.FocusSessionEntity
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.StatTile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusHistoryScreen(
    onBack: () -> Unit,
    vm: FocusHistoryViewModel = viewModel(factory = FocusHistoryViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "totals") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Last 30 days", formatMinutes(state.totalMinutes30), Modifier.weight(1f), caption = "focused")
                    StatTile("Completed", "${state.completed30}", Modifier.weight(1f), caption = "full sessions")
                }
            }
            item(key = "week") {
                SectionHeader("Last 7 days")
                WeekBars(state.week)
            }
            if (state.days.isEmpty()) {
                item(key = "empty") { EmptyState("No focus sessions yet", "Start a session on the Focus tab. Sessions you stop after a minute or more are saved too.") }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") { SectionHeader("${Fmt.relative(day.date, today)} · ${formatMinutes(day.minutes)}") }
                items(day.sessions, key = { it.id }) { SessionRow(it) }
            }
        }
    }
}

@Composable
private fun WeekBars(week: List<Pair<LocalDate, Int>>) {
    val max = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEach { (d, minutes) ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .semantics { contentDescription = "${Fmt.shortDate(d)}: $minutes minutes" },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (minutes > 0) Box(Modifier.fillMaxWidth().fillMaxHeight(minutes.toFloat() / max).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        week.forEach { (d, minutes) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$minutes", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SessionRow(s: FocusSessionEntity) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalTime()
    val end = Instant.ofEpochMilli(s.endedAt).atZone(zone).toLocalTime()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.label.ifBlank { "Focus session" }, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${Fmt.time(start.hour * 60 + start.minute, false)} – ${Fmt.time(end.hour * 60 + end.minute, false)}" +
                    (if (s.interruptions > 0) " · paused ${s.interruptions}×" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${s.actualSeconds / 60} min", style = MaterialTheme.typography.titleSmall)
            Text(
                if (s.completed) "Completed" else "Stopped early",
                style = MaterialTheme.typography.bodySmall,
                color = if (s.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
