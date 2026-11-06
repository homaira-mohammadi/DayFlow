package com.dayflow.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.Rate
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ColorDot
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.LegendItem
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.StatTile
import com.dayflow.ui.components.Swatch
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(vm: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { vm.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                    ) { Text(if (p == Period.WEEK) "Week" else "Month") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous period") }
                Text(
                    "${Fmt.shortDate(state.from)} – ${Fmt.shortDate(state.to)}",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = { vm.shift(1) }, enabled = state.offset < 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next period") }
            }

            val sum = state.summary
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Completion", if (sum.overall.isEmpty) "–" else Fmt.percent(sum.overall.fraction), Modifier.weight(1f),
                    caption = "${sum.overall.completed} of ${sum.overall.planned} planned")
                StatTile("Tasks", rateValue(sum.tasks), Modifier.weight(1f), caption = "${sum.tasks.done} of ${sum.tasks.eligible} done")
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Habit consistency", rateValue(sum.habits), Modifier.weight(1f), caption = "${sum.habits.done} of ${sum.habits.eligible} scheduled")
                StatTile("Postponed", "${sum.postponed}", Modifier.weight(1f), caption = "tasks moved elsewhere")
            }

            if (state.insights.isNotEmpty()) {
                SectionHeader("Insights")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.insights.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                }
            }

            SectionHeader("Daily completion")
            DayBars(state)

            SectionHeader("Habits")
            if (state.habitRates.isEmpty()) {
                Text("No habits were scheduled in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.habitRates.forEach { hr ->
                RateRow(hr.habit.name, hr.rate, Color(hr.habit.color), trailing = if (hr.streak > 0) "🔥 ${hr.streak}" else null)
            }

            if (state.categories.isNotEmpty()) {
                SectionHeader("By category")
                state.categories.forEach { (name, rate) -> RateRow(name, rate, MaterialTheme.colorScheme.primary) }
            }

            SectionHeader("Last 18 weeks")
            Heatmap(state)
            Spacer(Modifier.height(32.dp))

            if (state.summary.overall.isEmpty && state.habitRates.isEmpty() && state.offset == 0) {
                EmptyState("Your progress appears here", "Complete a few tasks and habits and come back to see patterns.")
            }
        }
    }
}

private fun rateValue(r: Rate) = if (r.eligible == 0) "–" else Fmt.percent(r.fraction)

@Composable
private fun RateRow(label: String, rate: Rate, color: Color, trailing: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color)
            Spacer(Modifier.width(8.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (trailing != null) Text("$trailing  ", style = MaterialTheme.typography.bodySmall)
            Text("${Fmt.percent(rate.fraction)} · ${rate.done}/${rate.eligible}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { rate.fraction },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

@Composable
private fun DayBars(state: ProgressState) {
    val primary = MaterialTheme.colorScheme.primary
    val compact = state.days.size > 7
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp)) {
        state.days.forEach { (date, stats) ->
            val desc = when {
                stats == null -> "${Fmt.shortDate(date)}: upcoming"
                stats.isEmpty -> "${Fmt.shortDate(date)}: nothing planned"
                else -> "${Fmt.shortDate(date)}: ${stats.completed} of ${stats.planned} done"
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .semantics { contentDescription = desc },
                contentAlignment = Alignment.BottomCenter,
            ) {
                val f = stats?.takeIf { !it.isEmpty }?.fraction ?: 0f
                if (f > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(f).background(primary))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 8.dp)) {
        state.days.forEach { (date, _) ->
            Text(
                if (compact) (if (date.dayOfMonth == 1 || date.dayOfMonth % 5 == 0) "${date.dayOfMonth}" else "")
                else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Heatmap(state: ProgressState) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        state.heatmap.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { (date, f) ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                            .background(if (f == null || date > state.today) empty else primary.copy(alpha = 0.15f + 0.85f * f))
                            .semantics { contentDescription = "${Fmt.shortDate(date)}: " + (f?.let { Fmt.percent(it) } ?: "no data") },
                    )
                }
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem("No plan") { Swatch(empty) }
        LegendItem("0%") { Swatch(primary.copy(alpha = 0.15f)) }
        LegendItem("50%") { Swatch(primary.copy(alpha = 0.57f)) }
        LegendItem("100%") { Swatch(primary) }
    }
}
