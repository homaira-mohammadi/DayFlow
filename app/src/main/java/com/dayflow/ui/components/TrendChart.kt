package com.dayflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A smooth line chart over days. Null values leave a gap in the line. An optional [goal] is drawn
 * as a dashed reference line.
 */
@Composable
fun TrendChart(
    values: List<Float?>,
    minY: Float,
    maxY: Float,
    gridLines: List<Float>,
    gridLabel: (Float) -> String,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    goal: Float? = null,
    goalColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val reveal = remember(values.size) { Animatable(0f) }
    LaunchedEffect(values.size) { reveal.animateTo(1f, tween(700)) }

    Canvas(modifier.semantics { contentDescription = description }) {
        val left = 44.dp.toPx()
        val pad = 8.dp.toPx()
        val w = size.width - left
        val chartH = size.height - pad * 2
        val span = (maxY - minY).takeIf { it > 0f } ?: 1f
        fun x(i: Int) = left + if (values.size <= 1) w / 2 else w * i / (values.size - 1)
        fun y(v: Float) = pad + chartH * (1f - (v - minY) / span)

        gridLines.forEachIndexed { i, v ->
            drawLine(
                grid, Offset(left, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx(),
                pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val t = measurer.measure(gridLabel(v), labelStyle)
            drawText(t, topLeft = Offset(0f, y(v) - t.size.height / 2f))
        }
        if (goal != null) {
            drawLine(
                goalColor.copy(alpha = 0.9f), Offset(left, y(goal)), Offset(size.width, y(goal)),
                strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }

        val segments = mutableListOf<MutableList<Offset>>()
        var current: MutableList<Offset>? = null
        values.forEachIndexed { i, v ->
            if (v == null) { current = null; return@forEachIndexed }
            if (current == null) current = mutableListOf<Offset>().also { segments += it }
            current!!.add(Offset(x(i), y(v.coerceIn(minY, maxY))))
        }
        segments.forEach { pts ->
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (k in 1 until pts.size) {
                    val a = pts[k - 1]; val b = pts[k]
                    val cx = (a.x + b.x) / 2
                    cubicTo(cx, a.y, cx, b.y, b.x, b.y)
                }
            }
            if (pts.size > 1) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, y(minY)); lineTo(pts.first().x, y(minY)); close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f * reveal.value), Color.Transparent), startY = pad, endY = y(minY)))
            }
            drawPath(line, color.copy(alpha = reveal.value), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (values.size <= 14 || pts.size == 1) pts.forEach { p ->
                drawCircle(surface, 5.dp.toPx(), p)
                drawCircle(color, 3.5.dp.toPx(), p)
            }
        }
    }
}

/** First, middle and last date under a chart. */
@Composable
fun ChartDates(days: List<LocalDate>, modifier: Modifier = Modifier, startPadding: androidx.compose.ui.unit.Dp = 44.dp) {
    val fmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(start = startPadding, top = 6.dp)) {
        Text(days.firstOrNull()?.format(fmt) ?: "", Modifier.weight(1f), style = style, color = color)
        Text(days.getOrNull(days.size / 2)?.format(fmt) ?: "", Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
        Text("Today", Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.End)
    }
}
