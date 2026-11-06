package com.dayflow.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.Days
import com.dayflow.domain.PeriodFlow
import com.dayflow.domain.Symptom
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.SectionHeader
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CycleCalendarScreen(
    onBack: () -> Unit,
    vm: CycleViewModel = viewModel(factory = CycleViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val month = state.month
    val weekStart = state.settings.weekStart
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cycle calendar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { vm.select(state.today) }) { Icon(Icons.Filled.Today, "Jump to today") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.showMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text(
                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { vm.showMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            Row(Modifier.fillMaxWidth()) {
                Days.orderedWeek(weekStart).forEach {
                    Text(
                        it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
            val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList().chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            DayBubble(
                                d, state.kind(d), d == state.today, d == state.selected,
                                modifier = Modifier.fillMaxSize(),
                                dimmed = YearMonth.from(d) != month,
                            ) { vm.select(d) }
                        }
                    }
                }
            }
            Box(Modifier.padding(top = 8.dp)) { CycleLegend(state.settings.showFertility) }

            val d = state.selected
            val flow = state.flows[d]
            val logged = state.symptoms[d]
            SectionHeader("${Fmt.relative(d, state.today)} · ${state.phase(d)}")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Period day", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (d > state.today) "Future days are predicted automatically" else "Tap to mark or unmark this day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = flow != null,
                            onCheckedChange = { on -> vm.setPeriodDay(d, if (on) PeriodFlow.MEDIUM else null) },
                        )
                    }
                    if (flow != null) {
                        Text("Flow", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PeriodFlow.entries.forEach { f ->
                                FilterChip(
                                    selected = flow == f,
                                    onClick = { vm.setPeriodDay(d, f) },
                                    label = { Text(f.label) },
                                )
                            }
                        }
                    }
                    if (d <= state.today) {
                        Text("Symptoms", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        val current = logged?.symptoms.orEmpty()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Symptom.entries.forEach { s ->
                                FilterChip(
                                    selected = s in current,
                                    onClick = { vm.setSymptoms(d, if (s in current) current - s else current + s, logged?.note.orEmpty()) },
                                    label = { Text(s.label) },
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "Predictions update as you log. They are estimates, not a contraceptive or diagnostic tool.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
