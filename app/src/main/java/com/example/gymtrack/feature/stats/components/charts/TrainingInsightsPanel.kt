package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.feature.stats.MetricImpact
import com.example.gymtrack.feature.stats.TrainingInsightRow
import com.example.gymtrack.feature.stats.TrainingMetricShift
import com.example.gymtrack.feature.stats.TrainingTrendPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val StrengthColor = Color(0xFFE15A5A)
private val DensityColor = Color(0xFFB56CFF)
private val DepthColor = Color(0xFFE2B84A)
private val ExerciseColor = Color(0xFF5FA8FF)
private val RepsColor = Color(0xFF66C48D)
private val DurationColor = Color(0xFFD58A52)
private val PositiveColor = Color(0xFF5CBF7A)
private val NegativeColor = Color(0xFFE06A6A)
private val NeutralColor = Color(0xFF9AA0A6)

@Composable
fun TrainingInsightsPanel(
    data: List<TrainingInsightRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "Training Insights",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Early vs recent workouts in this range · strategy, progression, and time use",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
        Spacer(Modifier.height(16.dp))

        if (data.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Need at least two parsed workouts for trend insights",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            return
        }

        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            data.forEachIndexed { index, row ->
                TrainingInsightRowView(row = row)
                if (index < data.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun TrainingInsightRowView(row: TrainingInsightRow) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${row.workoutCount} workouts · ${row.summary}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }

        MetricLine(metric = row.strength, lineColor = StrengthColor)
        MetricLine(metric = row.density, lineColor = DensityColor)
        MetricLine(metric = row.setDepth, lineColor = DepthColor)
        MetricLine(metric = row.exerciseCount, lineColor = ExerciseColor)
        MetricLine(metric = row.reps, lineColor = RepsColor)
        MetricLine(metric = row.duration, lineColor = DurationColor)

        if (row.trendPoints.size >= 2) {
            TrainingInsightTrendChart(
                points = row.trendPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
        }
    }
}

@Composable
private fun MetricLine(metric: TrainingMetricShift, lineColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(lineColor, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                metric.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        MetricDeltaBadge(metric = metric)
    }
}

@Composable
private fun MetricDeltaBadge(metric: TrainingMetricShift) {
    val color = impactColor(metric.impact)
    val label = impactLabel(metric.impact)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            text = "${metricChangeText(metric)} · $label",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TrainingInsightTrendChart(
    points: List<TrainingTrendPoint>,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Trend since first workout in range · % change",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f),
        )
        LegendRow()
        Canvas(modifier = modifier) {
            val horizontalPad = 10f
            val topPad = 14f
            val bottomPad = 18f
            val chartWidth = size.width - horizontalPad * 2f
            val chartHeight = size.height - topPad - bottomPad
            val centerY = topPad + chartHeight / 2f
            val maxAbsChange = points.flatMap {
                listOf(
                    it.strengthPercent,
                    it.densityPercent,
                    it.depthPercent,
                    it.exerciseCountPercent,
                    it.repsPercent,
                    it.durationPercent,
                )
            }.fold(10f) { acc, value -> max(acc, abs(value)) }.coerceAtMost(120f)

            drawLine(
                color = NeutralColor.copy(alpha = 0.45f),
                start = Offset(horizontalPad, centerY),
                end = Offset(size.width - horizontalPad, centerY),
                strokeWidth = 1.4f,
            )

            fun yFor(value: Float): Float {
                val clamped = value.coerceIn(-maxAbsChange, maxAbsChange)
                return centerY - (clamped / maxAbsChange) * (chartHeight / 2f)
            }

            fun xFor(index: Int): Float {
                return if (points.size <= 1) {
                    horizontalPad
                } else {
                    horizontalPad + (chartWidth * index / (points.size - 1).toFloat())
                }
            }

            fun drawSeries(color: Color, selector: (TrainingTrendPoint) -> Float) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = xFor(index)
                    val y = yFor(selector(point))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = color, style = Stroke(width = 3.5f))
                points.forEachIndexed { index, point ->
                    drawCircle(color = color, radius = 3.8f, center = Offset(xFor(index), yFor(selector(point))))
                }
            }

            drawSeries(StrengthColor) { it.strengthPercent }
            drawSeries(DensityColor) { it.densityPercent }
            drawSeries(DepthColor) { it.depthPercent }
            drawSeries(ExerciseColor) { it.exerciseCountPercent }
            drawSeries(RepsColor) { it.repsPercent }
            drawSeries(DurationColor) { it.durationPercent }
        }
    }
}

@Composable
private fun LegendRow() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendItem("Strength", StrengthColor)
            LegendItem("Density", DensityColor)
            LegendItem("Depth", DepthColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendItem("Exercises", ExerciseColor)
            LegendItem("Reps", RepsColor)
            LegendItem("Time", DurationColor)
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            maxLines = 1,
        )
    }
}

@Composable
private fun impactColor(impact: MetricImpact): Color = when (impact) {
    MetricImpact.POSITIVE -> PositiveColor
    MetricImpact.NEGATIVE -> NegativeColor
    MetricImpact.NEUTRAL -> NeutralColor
}

private fun impactLabel(impact: MetricImpact): String = when (impact) {
    MetricImpact.POSITIVE -> "good"
    MetricImpact.NEGATIVE -> "watch"
    MetricImpact.NEUTRAL -> "neutral"
}

private fun metricChangeText(metric: TrainingMetricShift): String {
    val direction = when {
        metric.absoluteChange > 0.01f -> "↑"
        metric.absoluteChange < -0.01f -> "↓"
        else -> "→"
    }
    val change = metric.percentChange?.let { percent ->
        if (abs(percent) >= 1f) {
            "${percent.signPrefix()}${String.format(Locale.getDefault(), "%.0f", percent)}%"
        } else {
            "flat"
        }
    } ?: metric.absoluteChange.let { delta ->
        if (abs(delta) >= 0.1f) "${delta.signPrefix()}${String.format(Locale.getDefault(), "%.1f", delta)}" else "flat"
    }

    return "$direction $change"
}

private fun formatMetricValue(value: Float, unit: String): String {
    val formatted = when {
        value >= 100f -> String.format(Locale.getDefault(), "%.0f", value)
        value >= 10f -> String.format(Locale.getDefault(), "%.1f", value)
        else -> String.format(Locale.getDefault(), "%.2f", value)
    }
    return "$formatted $unit"
}

private fun Float.signPrefix(): String = if (this > 0f) "+" else ""
