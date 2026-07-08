package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.feature.stats.TrainingInsightRow
import com.example.gymtrack.feature.stats.TrainingMetricShift
import java.util.Locale
import kotlin.math.abs

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

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

        MetricLine(metric = row.strength)
        MetricLine(metric = row.density)
        MetricLine(metric = row.setDepth)
        MetricLine(metric = row.exerciseCount)
        MetricLine(metric = row.reps)
        MetricLine(metric = row.duration)
    }
}

@Composable
private fun MetricLine(metric: TrainingMetricShift) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            metric.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            metricChangeText(metric),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            maxLines = 1,
        )
    }
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

    return "$direction $change · now ${formatMetricValue(metric.recent, metric.unit)}"
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
