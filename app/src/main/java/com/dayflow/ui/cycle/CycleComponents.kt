package com.dayflow.ui.cycle

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dayflow.domain.CycleDayKind
import com.dayflow.domain.Symptom
import com.dayflow.ui.Fmt
import java.time.LocalDate

/** Cycle colours carry meaning (period, fertile, PMS), so they stay the same in every theme. */
object CycleColors {
    val Period = Color(0xFFE5527A)
    val Fertile = Color(0xFF3AAFA9)
    val Ovulation = Color(0xFF1F8A84)
    val Pms = Color(0xFF9C7BD8)

    fun of(kind: CycleDayKind): Color? = when (kind) {
        CycleDayKind.PERIOD, CycleDayKind.PREDICTED_PERIOD -> Period
        CycleDayKind.OVULATION -> Ovulation
        CycleDayKind.FERTILE -> Fertile
        CycleDayKind.PMS -> Pms
        CycleDayKind.NONE -> null
    }
}

fun CycleDayKind.label(): String = when (this) {
    CycleDayKind.PERIOD -> "period"
    CycleDayKind.PREDICTED_PERIOD -> "predicted period"
    CycleDayKind.OVULATION -> "estimated ovulation"
    CycleDayKind.FERTILE -> "fertile window"
    CycleDayKind.PMS -> "PMS day"
    CycleDayKind.NONE -> ""
}

/** A date drawn the cycle way: filled for period days, dashed for predicted ones, tinted for other phases. */
@Composable
fun DayBubble(
    date: LocalDate,
    kind: CycleDayKind,
    isToday: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val color = CycleColors.of(kind)
    val selectedColor = MaterialTheme.colorScheme.primary
    val todayFill = MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor = when (kind) {
        CycleDayKind.PERIOD -> Color.White
        CycleDayKind.NONE -> MaterialTheme.colorScheme.onSurface
        else -> color!!
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(
                    Fmt.shortDate(date),
                    "today".takeIf { isToday },
                    kind.label().ifEmpty { null },
                ).joinToString(", ")
            }
            .drawBehind {
                val r = this.size.minDimension / 2
                when (kind) {
                    CycleDayKind.PERIOD -> drawCircle(color!!)
                    CycleDayKind.PREDICTED_PERIOD -> drawCircle(
                        color!!, radius = r - 2.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                    )
                    CycleDayKind.OVULATION -> drawCircle(color!!, radius = r - 1.5.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                    CycleDayKind.FERTILE, CycleDayKind.PMS -> drawCircle(color!!.copy(alpha = 0.14f))
                    CycleDayKind.NONE -> if (isToday) drawCircle(todayFill)
                }
                if (selected) drawCircle(selectedColor, radius = r - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            color = if (dimmed) textColor.copy(alpha = 0.35f) else textColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleLegend(showFertility: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendDot("Period") { Box(Modifier.size(12.dp).clip(CircleShape).background(CycleColors.Period)) }
        LegendDot("Predicted") { Box(Modifier.size(12.dp).border(1.5.dp, CycleColors.Period, CircleShape)) }
        if (showFertility) {
            LegendDot("Fertile") { Box(Modifier.size(12.dp).clip(CircleShape).background(CycleColors.Fertile.copy(alpha = 0.35f))) }
            LegendDot("Ovulation") { Box(Modifier.size(12.dp).border(1.5.dp, CycleColors.Ovulation, CircleShape)) }
        }
        LegendDot("PMS") { Box(Modifier.size(12.dp).clip(CircleShape).background(CycleColors.Pms.copy(alpha = 0.35f))) }
    }
}

@Composable
private fun LegendDot(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A row of small dots, one per cycle day, coloured by phase (like a cycle history strip). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CycleDots(kinds: List<CycleDayKind>, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        kinds.forEach { k ->
            val c = when (k) {
                CycleDayKind.PREDICTED_PERIOD -> CycleColors.Period.copy(alpha = 0.4f)
                CycleDayKind.FERTILE -> CycleColors.Fertile.copy(alpha = 0.55f)
                else -> CycleColors.of(k) ?: empty
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomsDialog(
    date: LocalDate,
    initial: Set<Symptom>,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (Set<Symptom>, String) -> Unit,
) {
    var picked by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Symptoms · ${Fmt.relative(date)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symptom.entries.forEach { s ->
                        FilterChip(
                            selected = s in picked,
                            onClick = { picked = if (s in picked) picked - s else picked + s },
                            label = { Text(s.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked, note); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
