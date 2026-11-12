package com.dayflow.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.SleepNight
import com.dayflow.domain.SleepRules
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.ChartDates
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.StatTile
import com.dayflow.ui.components.TrendChart
import com.dayflow.ui.today.SleepDialog
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Warm sunrise orange for the wake-up chart. */
private val SunriseOrange = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepHistoryScreen(onBack: () -> Unit, vm: SleepHistoryViewModel = viewModel(factory = SleepHistoryViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SleepNight?>(null) }
    val s = state.summary
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep history") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SleepRange.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = state.range == r,
                            onClick = { vm.setRange(r) },
                            shape = SegmentedButtonDefaults.itemShape(i, SleepRange.entries.size),
                        ) { Text(r.label) }
                    }
                }
            }
            if (!state.loading && s.nights == 0) {
                item {
                    Spacer(Modifier.height(24.dp))
                    EmptyState("No sleep logged yet", "Log last night on the Today screen, and your sleep history will appear here.")
                }
                return@LazyColumn
            }
            item { HoursChart(state) }
            item { WakeChart(state) }
            item {
                SectionHeader("Summary")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Average sleep", s.averageMinutes?.let(SleepRules::formatDuration) ?: "–", Modifier.weight(1f), "${s.nights} nights logged")
                    StatTile("Goal reached", "${s.metGoal}/${s.nights}", Modifier.weight(1f), "nights of ${SleepRules.formatDuration(state.goalMinutes)}+")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Usual bedtime", s.averageBedtime?.let { Fmt.time(it, state.use24h) } ?: "–", Modifier.weight(1f), "on average")
                    StatTile("Usual wake-up", s.averageWake?.let { Fmt.time(it, state.use24h) } ?: "–", Modifier.weight(1f), s.wakeSpread?.let { "± $it min" } ?: "on average")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Longest night", s.longest?.let { SleepRules.formatDuration(it.durationMinutes) } ?: "–", Modifier.weight(1f), s.longest?.let { Fmt.shortDate(it.date) })
                    StatTile("Shortest night", s.shortest?.let { SleepRules.formatDuration(it.durationMinutes) } ?: "–", Modifier.weight(1f), s.shortest?.let { Fmt.shortDate(it.date) })
                }
            }
            item { SectionHeader("Nights") }
            items(state.logged.reversed(), key = { it.date.toEpochDay() }) { night ->
                NightRow(night, state.goalMinutes, state.use24h) { editing = night }
            }
        }
    }

    editing?.let { night ->
        SleepDialog(
            bed = night.bedMinutes,
            wake = night.wakeMinutes,
            use24h = state.use24h,
            canDelete = true,
            onDismiss = { editing = null },
            onSave = { b, w -> vm.save(night.date, b, w); editing = null },
            onDelete = { vm.delete(night.date); editing = null },
        )
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun HoursChart(state: SleepHistoryState) {
    val hours = state.days.map { it.second?.hours }
    val goal = state.goalMinutes / 60f
    val top = max(10f, ceil((hours.filterNotNull().maxOrNull() ?: 0f) / 2f) * 2f)
    val primary = MaterialTheme.colorScheme.primary
    ChartCard("Hours slept", "Per night · last ${state.range.label}") {
        TrendChart(
            values = hours,
            minY = 0f,
            maxY = top,
            gridLines = (0..top.toInt() step 2).map { it.toFloat() },
            gridLabel = { "${it.toInt()}h" },
            color = primary,
            goal = goal,
            description = "Line chart of hours slept. Average ${state.summary.averageMinutes?.let(SleepRules::formatDuration) ?: "none"}.",
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )
        ChartDates(state.days.map { it.first })
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendLine(primary, "Sleep")
            LegendLine(MaterialTheme.colorScheme.tertiary, "Goal ${SleepRules.formatDuration(state.goalMinutes)}", dashed = true)
        }
    }
}

@Composable
private fun WakeChart(state: SleepHistoryState) {
    val wakes = state.days.map { it.second?.wakeMinutes?.toFloat() }
    val logged = wakes.filterNotNull()
    // Whole hours around the wake-up times, at least three hours tall.
    var low = floor(((logged.minOrNull() ?: 360f) - 30f) / 60f) * 60f
    var high = ceil(((logged.maxOrNull() ?: 480f) + 30f) / 60f) * 60f
    if (high - low < 180f) { val mid = (low + high) / 2; low = floor((mid - 90f) / 60f) * 60f; high = low + 180f }
    low = low.coerceAtLeast(0f); high = high.coerceAtMost(24 * 60f)
    val avg = state.summary.averageWake
    ChartCard("Wake-up time", "When you got up · last ${state.range.label}") {
        TrendChart(
            values = wakes,
            minY = low,
            maxY = high,
            gridLines = generateSequence(low) { it + 60f }.takeWhile { it <= high }.toList(),
            gridLabel = { Fmt.time(it.toInt() % (24 * 60), state.use24h).replace(":00", "") },
            color = SunriseOrange,
            goal = avg?.toFloat(),
            goalColor = MaterialTheme.colorScheme.outline,
            description = "Line chart of wake-up times. Usual wake-up ${avg?.let { Fmt.time(it, state.use24h) } ?: "none"}.",
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        ChartDates(state.days.map { it.first })
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendLine(SunriseOrange, "Woke up")
            if (avg != null) LegendLine(MaterialTheme.colorScheme.outline, "Usual ${Fmt.time(avg, state.use24h)}", dashed = true)
        }
    }
}

@Composable
private fun NightRow(night: SleepNight, goal: Int, use24h: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.relative(night.date), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${Fmt.time(night.bedMinutes, use24h)} → ${Fmt.time(night.wakeMinutes, use24h)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                SleepRules.formatDuration(night.durationMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (night.durationMinutes >= goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendLine(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 10.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 6f)) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
