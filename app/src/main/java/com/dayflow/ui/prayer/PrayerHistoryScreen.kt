package com.dayflow.ui.prayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dayflow.domain.Prayer
import com.dayflow.domain.PrayerDay
import com.dayflow.ui.Fmt
import com.dayflow.ui.components.EmptyState
import com.dayflow.ui.components.SectionHeader
import com.dayflow.ui.components.StatTile
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Teal from the Dayflow logo, used for prayers kept on time. */
private val OnTimeTeal = Color(0xFF14B8A6)

@Composable
private fun excusedColor() = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerHistoryScreen(onBack: () -> Unit, vm: PrayerHistoryViewModel = viewModel(factory = PrayerHistoryViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prayer history") },
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
                    PrayerRange.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = state.range == r,
                            onClick = { vm.setRange(r) },
                            shape = SegmentedButtonDefaults.itemShape(i, PrayerRange.entries.size),
                        ) { Text(r.label) }
                    }
                }
            }
            item { ChartCard(state) }
            item {
                SectionHeader("Summary")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Prayed", Fmt.percent(state.rate(state.prayed)), Modifier.weight(1f), "${state.prayed} of ${state.expected}")
                    StatTile("On time", Fmt.percent(state.rate(state.onTime)), Modifier.weight(1f), "${state.onTime} prayers")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Current streak", "${state.currentStreak}", Modifier.weight(1f), if (state.currentStreak == 1) "day" else "days")
                    StatTile("Best streak", "${state.bestStreak}", Modifier.weight(1f), "in this range")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Late", "${state.late}", Modifier.weight(1f), "prayed late")
                    StatTile("Missed", "${state.missed}", Modifier.weight(1f), if (state.excusedDays > 0) "${state.excusedDays} excused days" else "prayers")
                }
            }
            item {
                SectionHeader("By prayer")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.perPrayer.forEach { BreakdownRow(it) }
                        Legend()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(state: PrayerHistoryState) {
    Card(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Prayers per day", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Out of 5 · last ${state.range.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            if (!state.loading && !state.hasData) {
                EmptyState("No prayers marked yet", "Mark your prayers on the Today screen and your history will appear here.")
            } else {
                LineChart(state.days, Modifier.fillMaxWidth().height(200.dp))
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, top = 6.dp)) {
                    val fmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
                    val first = state.days.firstOrNull()?.date
                    val mid = state.days.getOrNull(state.days.size / 2)?.date
                    Text(first?.format(fmt) ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mid?.format(fmt) ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Today", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendLine(MaterialTheme.colorScheme.primary, "Prayed")
                    LegendLine(OnTimeTeal, "On time", dashed = true)
                    if (state.excusedDays > 0) LegendBox(excusedColor(), "Excused")
                }
            }
        }
    }
}

/** Two lines over 0–5: all prayers prayed, and those prayed on time. Excused days are shaded gaps. */
@Composable
private fun LineChart(days: List<PrayerDay>, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val teal = OnTimeTeal
    val grid = MaterialTheme.colorScheme.outlineVariant
    val excusedColor = excusedColor()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val reveal = remember(days.size) { Animatable(0f) }
    LaunchedEffect(days.size) { reveal.animateTo(1f, tween(700)) }
    val desc = "Line chart of prayers per day. " + days.takeLast(7).joinToString { "${Fmt.shortDate(it.date)}: " + if (it.excused) "excused" else "${it.summary.prayed} prayed" }

    Canvas(modifier.semantics { contentDescription = desc }) {
        val left = 28.dp.toPx()
        val w = size.width - left
        val h = size.height
        val pad = 8.dp.toPx()
        val chartH = h - pad * 2
        fun x(i: Int) = left + if (days.size <= 1) w / 2 else w * i / (days.size - 1)
        fun y(v: Int) = pad + chartH * (1f - v / 5f)

        // Grid lines and labels for 0–5.
        for (v in 0..5) {
            drawLine(grid, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx(), pathEffect = if (v == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            if (v % 1 == 0) {
                val t = measurer.measure("$v", labelStyle.copy(color = labelColor))
                drawText(t, topLeft = Offset(0f, y(v) - t.size.height / 2f))
            }
        }
        // Excused (period) days.
        val step = if (days.size <= 1) w else w / (days.size - 1)
        days.forEachIndexed { i, d ->
            if (d.excused) drawRect(excusedColor, Offset((x(i) - step / 2).coerceAtLeast(left), pad), Size(step, chartH))
        }

        fun series(value: (PrayerDay) -> Int, color: Color, dashed: Boolean, fill: Boolean) {
            val segments = mutableListOf<MutableList<Offset>>()
            var current: MutableList<Offset>? = null
            days.forEachIndexed { i, d ->
                if (d.excused) { current = null; return@forEachIndexed }
                val p = Offset(x(i), y(value(d)))
                if (current == null) current = mutableListOf<Offset>().also { segments += it }
                current!!.add(p)
            }
            segments.forEach { pts ->
                if (pts.isEmpty()) return@forEach
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (k in 1 until pts.size) {
                        // Smooth curve through the points.
                        val a = pts[k - 1]; val b = pts[k]
                        val cx = (a.x + b.x) / 2
                        cubicTo(cx, a.y, cx, b.y, b.x, b.y)
                    }
                }
                if (fill && pts.size > 1) {
                    val area = Path().apply {
                        addPath(line)
                        lineTo(pts.last().x, y(0)); lineTo(pts.first().x, y(0)); close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f * reveal.value), Color.Transparent), startY = pad, endY = y(0)))
                }
                drawPath(
                    line, color.copy(alpha = reveal.value),
                    style = Stroke(
                        width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                    ),
                )
                if (days.size <= 14) pts.forEach { p ->
                    drawCircle(surface, 5.dp.toPx(), p)
                    drawCircle(color, 3.5.dp.toPx(), p)
                }
            }
        }
        series({ it.summary.onTime }, teal, dashed = true, fill = false)
        series({ it.summary.prayed }, primary, dashed = false, fill = true)
    }
}

@Composable
private fun BreakdownRow(b: PrayerBreakdown) {
    val onTime = OnTimeTeal
    val late = MaterialTheme.colorScheme.tertiary
    val missed = MaterialTheme.colorScheme.error
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(b.prayer.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("  ${b.prayer.arabic}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                "${b.onTime + b.late}/${b.expected}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val total = b.expected.coerceAtLeast(1).toFloat()
            listOf(b.onTime to onTime, b.late to late, b.missed to missed).forEach { (n, color) ->
                if (n > 0) Box(Modifier.weight(n / total).fillMaxHeight().background(color))
            }
            val rest = (b.expected - b.onTime - b.late - b.missed).coerceAtLeast(0)
            if (rest > 0) Spacer(Modifier.weight(rest / total))
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendBox(OnTimeTeal, "On time")
        LegendBox(MaterialTheme.colorScheme.tertiary, "Late")
        LegendBox(MaterialTheme.colorScheme.error, "Missed")
    }
}

@Composable
private fun LegendBox(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
