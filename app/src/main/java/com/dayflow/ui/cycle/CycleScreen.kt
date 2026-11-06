package com.dayflow.ui.cycle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.CycleEngine
import com.dayflow.domain.CycleStatus
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.DatePickerDialogFor
import com.dayflow.ui.components.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(
    onOpenCalendar: () -> Unit,
    vm: CycleViewModel = viewModel(factory = CycleViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var symptomsFor by remember { mutableStateOf<LocalDate?>(null) }
    var info by remember { mutableStateOf(false) }
    val today = state.today
    val engine = state.engine

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()).format(today)) },
                actions = { IconButton(onClick = onOpenCalendar) { Icon(Icons.Filled.CalendarMonth, "Cycle calendar") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "week") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekOf(state.selected, state.settings.weekStart).forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (d == today) "TODAY" else d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            DayBubble(d, state.kind(d), d == today, d == state.selected && d != today, size = 42.dp) { vm.select(d) }
                        }
                    }
                }
            }

            item(key = "hero") {
                if (!engine.hasData) SetupCard(engine.averagePeriodLength, vm::logPastPeriod) else Hero(state)
            }

            item(key = "actions") {
                val inPeriod = engine.lastPeriod?.let { today in it } == true
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActionCircle(Icons.Filled.WaterDrop, if (inPeriod) "End period" else "Log period", filled = true, onClick = vm::togglePeriodToday)
                    ActionCircle(Icons.Filled.Add, "Symptoms", filled = false) { symptomsFor = state.selected }
                    ActionCircle(Icons.Filled.CalendarMonth, "Edit dates", filled = false, onClick = onOpenCalendar)
                }
            }

            item(key = "selected") { SelectedDayCard(state) { symptomsFor = state.selected } }

            if (engine.hasData) {
                item(key = "insights-h") { SectionHeader("My daily insights · ${Fmt.relative(state.selected, today)}") }
                item(key = "insights") { InsightCards(state) { symptomsFor = state.selected } }
                item(key = "cycles") { MyCycles(engine, today) { info = true } }
                if (engine.cycles.size >= 2) item(key = "trends") { TrendsCard(engine) }
                if (state.patterns.isNotEmpty()) item(key = "patterns") { PatternsCard(state) }
                if (state.history.isNotEmpty()) item(key = "history") { HistoryCard(state) }
            }
            item(key = "note") {
                Text(
                    "Predictions are estimates based on what you log. Dayflow is not a contraceptive or diagnostic tool; " +
                        "talk to a health professional about anything that worries you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }

    symptomsFor?.let { date ->
        val existing = state.symptoms[date]
        SymptomsDialog(date, existing?.symptoms.orEmpty(), existing?.note.orEmpty(), onDismiss = { symptomsFor = null }) { s, n ->
            vm.setSymptoms(date, s, n)
        }
    }
    if (info) {
        AlertDialog(
            onDismissRequest = { info = false },
            title = { Text("About these ranges") },
            text = {
                Text(
                    "Cycles of ${CycleEngine.TYPICAL_CYCLE.first}–${CycleEngine.TYPICAL_CYCLE.last} days and periods of " +
                        "${CycleEngine.TYPICAL_PERIOD.first}–${CycleEngine.TYPICAL_PERIOD.last} days are commonly considered typical for adults. " +
                        "Cycles that vary by more than ${CycleEngine.REGULAR_VARIATION} days are often described as irregular. " +
                        "Stress, illness, travel and other factors can shift a cycle. These labels are for orientation only.",
                )
            },
            confirmButton = { TextButton(onClick = { info = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun Hero(state: CycleUiState) {
    val engine = state.engine
    val (label, big, sub) = when (val st = engine.status) {
        is CycleStatus.InPeriod -> Triple("Period", "Day ${st.day}", "Expected to end around ${Fmt.shortDate(engine.lastPeriod!!.start.plusDays(engine.averagePeriodLength - 1L))}")
        is CycleStatus.Upcoming -> Triple(
            if (st.daysUntil == 0) "Period expected" else "Period in",
            when (st.daysUntil) { 0 -> "Today"; 1 -> "1 day"; else -> "${st.daysUntil} days" },
            "Cycle day ${st.cycleDay} · ${state.phase(state.today)}",
        )
        is CycleStatus.Late -> Triple("Period is late by", if (st.days == 1) "1 day" else "${st.days} days", "Cycle day ${st.cycleDay} · log it when it starts")
        CycleStatus.NoData -> Triple("", "", "")
    }
    val tint = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().height(240.dp).drawBehind {
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.03f)), center = center, radius = size.minDimension / 2),
                radius = size.minDimension / 2,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(big, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun SetupCard(defaultLength: Int, onSave: (LocalDate, Int) -> Unit) {
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var days by remember { mutableIntStateOf(defaultLength) }
    var picking by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Let's set up your cycle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Log your last period to get predictions. The more cycles you log, the more accurate they become.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { picking = true }) { Text(start?.let { "Started ${Fmt.shortDate(it)}" } ?: "When did your last period start?") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("It lasted", Modifier.weight(1f))
                TextButton(onClick = { days = (days - 1).coerceAtLeast(1) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text("$days days", fontWeight = FontWeight.Medium)
                TextButton(onClick = { days = (days + 1).coerceAtMost(10) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            Button(onClick = { start?.let { onSave(it, days) } }, enabled = start != null, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
    if (picking) DatePickerDialogFor(LocalDate.now(), onDismiss = { picking = false }) { start = minOf(it, LocalDate.now()) }
}

@Composable
private fun ActionCircle(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Surface(
            shape = CircleShape,
            color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 3.dp,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SelectedDayCard(state: CycleUiState, onSymptoms: () -> Unit) {
    val d = state.selected
    val flow = state.flows[d]
    val symptoms = state.symptoms[d]
    if (d == state.today && flow == null && symptoms == null) return
    Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.clickable(onClick = onSymptoms).padding(16.dp)) {
            Text(Fmt.relative(d, state.today) + " · " + state.phase(d), style = MaterialTheme.typography.titleSmall)
            if (flow != null) Text("Period · ${flow.label} flow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(
                symptoms?.symptoms?.joinToString { it.label }?.ifEmpty { null } ?: "No symptoms logged. Tap to add.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Insight(val title: String, val body: String)

@Composable
private fun InsightCards(state: CycleUiState, onSymptoms: () -> Unit) {
    val d = state.selected
    val kind = state.kind(d)
    val phase = state.phase(d)
    val phaseTip = when (kind) {
        CycleDayKind.PERIOD -> "Rest when you can. Warmth and gentle movement often ease cramps."
        CycleDayKind.PREDICTED_PERIOD -> "Your period may come now. Keep supplies handy and plans flexible."
        CycleDayKind.PMS -> "Energy and mood may dip. Keep the plan small and be kind to yourself."
        CycleDayKind.FERTILE, CycleDayKind.OVULATION -> "Many people feel most energetic around now. A good time for demanding work."
        CycleDayKind.NONE -> if (phase == "Follicular phase") "Energy often rises after a period. Try something challenging." else "Steady days. Wrap up important work before PMS days."
    }
    val productivity = state.productivity
    val planTip = when {
        state.engine.isSensitive(d) -> "Use the minimum versions of your habits and pick one or two priorities."
        else -> "A good day to schedule focus sessions for your bigger goals."
    } + (productivity?.let {
        "\n\nYour data: ${Fmt.percent(it.sensitive.fraction)} done on period/PMS days vs ${Fmt.percent(it.other.fraction)} on other days (last 90 days)."
    } ?: "")
    val logged = state.symptoms[d]?.symptoms.orEmpty()
    val cards = listOf(
        Insight(phase, phaseTip),
        Insight("Plan with your cycle", planTip),
        Insight("Symptoms", if (logged.isEmpty()) "Nothing logged yet. Tap to add how you feel." else logged.joinToString { it.label }),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards.size) { i ->
            val c = cards[i]
            Card(
                Modifier.width(220.dp).height(170.dp).then(if (i == 2) Modifier.clickable(onClick = onSymptoms) else Modifier),
                colors = CardDefaults.cardColors(
                    containerColor = if (i == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        c.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (i == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    )
                    Text(c.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(ok: Boolean, okText: String, badText: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, Modifier.size(18.dp), tint = if (ok) Color(0xFF2E9E5B) else Color(0xFFE0A100))
        Spacer(Modifier.width(6.dp))
        Text(if (ok) okText else badText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MyCycles(engine: CycleEngine, today: LocalDate, onInfo: () -> Unit) {
    SectionHeader("My cycles") {
        IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, "About these ranges", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        val last = engine.cycles.lastOrNull()
        StatRow("Previous cycle length", last?.let { "${it.length} days" } ?: "Need one full cycle") {
            last?.let { StatusChip(it.length in CycleEngine.TYPICAL_CYCLE, "TYPICAL", "OUTSIDE TYPICAL RANGE") }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        // The most recent period that has finished; one still in progress isn't counted yet.
        val periodLen = engine.periods.lastOrNull { it.end < today }?.length
        StatRow("Previous period length", periodLen?.let { "$it days" } ?: "–") {
            periodLen?.let { StatusChip(it in CycleEngine.TYPICAL_PERIOD, "TYPICAL", "OUTSIDE TYPICAL RANGE") }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        val recent = engine.cycles.takeLast(6).map { it.length }
        StatRow("Cycle length variation", if (recent.size >= 2) "${recent.min()}–${recent.max()} days" else "Need two full cycles") {
            if (recent.size >= 2) StatusChip(recent.max() - recent.min() <= CycleEngine.REGULAR_VARIATION, "REGULAR", "IRREGULAR")
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        StatRow("Average cycle · period", "${engine.averageCycleLength} days · ${engine.averagePeriodLength} days") {}
    }
}

@Composable
private fun StatRow(label: String, value: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        trailing()
    }
}

@Composable
private fun TrendsCard(engine: CycleEngine) {
    val cycles = engine.cycles.takeLast(6)
    val lengths = cycles.map { it.length }
    val lo = minOf(CycleEngine.TYPICAL_CYCLE.first, lengths.min()) - 2
    val hi = maxOf(CycleEngine.TYPICAL_CYCLE.last, lengths.max()) + 2
    val band = MaterialTheme.colorScheme.surfaceContainerHigh
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val normal = MaterialTheme.colorScheme.primary
    val outside = Color(0xFFE0A100)
    SectionHeader("Cycle trends")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(160.dp).semantics {
                    contentDescription = "Cycle lengths: " + lengths.joinToString { "$it days" }
                },
            ) {
                fun y(v: Int) = size.height * (1f - (v - lo).toFloat() / (hi - lo))
                val pad = 14.dp.toPx() // keeps the end points (and their halos) inside the card
                val stepX = if (lengths.size > 1) (size.width - 2 * pad) / (lengths.size - 1) else 0f
                fun x(i: Int) = pad + i * stepX
                drawRect(band, topLeft = Offset(0f, y(CycleEngine.TYPICAL_CYCLE.last)), size = Size(size.width, y(CycleEngine.TYPICAL_CYCLE.first) - y(CycleEngine.TYPICAL_CYCLE.last)))
                val path = Path()
                lengths.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
                lengths.forEachIndexed { i, v ->
                    val off = v !in CycleEngine.TYPICAL_CYCLE
                    if (off) drawCircle(outside.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = Offset(x(i), y(v)))
                    drawCircle(if (off) outside else normal, radius = 5.dp.toPx(), center = Offset(x(i), y(v)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                cycles.forEach { c ->
                    Text("${c.start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}\n${c.length}d", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
            Text(
                "Shaded: typical range (${CycleEngine.TYPICAL_CYCLE.first}–${CycleEngine.TYPICAL_CYCLE.last} days). Highlighted points fall outside it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PatternsCard(state: CycleUiState) {
    SectionHeader("My symptom patterns")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("From your last ${minOf(3, state.engine.periods.size)} cycles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.patterns.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dot = MaterialTheme.colorScheme.primary
                    Box(Modifier.size(10.dp).drawBehind { drawCircle(dot) })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.symptom.label, style = MaterialTheme.typography.bodyLarge)
                        Text(p.whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${p.days} days", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(state: CycleUiState) {
    var showAll by remember { mutableStateOf(false) }
    SectionHeader("Cycle history") {
        if (state.history.size > 3) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show less" else "See all") }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        val rows = if (showAll) state.history else state.history.take(3)
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Column(Modifier.padding(16.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (r.current) FontWeight.SemiBold else FontWeight.Normal)
                Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CycleDots(r.kinds, Modifier.padding(top = 8.dp))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    CycleLegend(state.settings.showFertility)
}
