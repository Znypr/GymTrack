package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.feature.stats.WeeklyWorkoutCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WeeklyConsistencyChart(
    data: List<WeeklyWorkoutCount>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "Workout Consistency",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))

        if (data.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No workouts in this period",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            return
        }

        val totalSessions = data.sumOf { it.count }
        val weeklyAverage = data.map { it.count }.average()
        Text(
            "Sessions per ISO week · $totalSessions total · ${String.format(Locale.getDefault(), "%.1f", weeklyAverage)}/week",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
        Spacer(Modifier.height(16.dp))

        val theme = rememberChartTheme()
        val scaleY = buildScaleY(data.map { it.count.toFloat() })
        val density = LocalDensity.current
        val textPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })
        val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val layout = ChartLayout(leftPad = 56f, rightPad = 32f, topPad = 8f, bottomPad = 40f)
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)
            val n = data.size.coerceAtLeast(1)
            val slotWidth = chartW / n
            val barWidth = (slotWidth * 0.56f).coerceIn(8f, 48f)

            drawYGridAndLabels(scaleY, theme, layout, textPaint) { value ->
                if (value % 1.0f == 0f) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)
            }

            data.forEachIndexed { index, week ->
                val xCenter = x0 + (slotWidth * index) + (slotWidth / 2f)
                val xLeft = xCenter - (barWidth / 2f)
                val barHeight = chartH * (week.count / scaleY.top)

                drawRoundRect(
                    color = theme.primary,
                    topLeft = Offset(xLeft, y0 - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }

            drawXTickLabel(dateFormatter.format(Date(data.first().weekStart)), x0, y0, textPaint)
            if (data.size > 1) {
                drawXTickLabel(dateFormatter.format(Date(data.last().weekStart)), x0 + chartW, y0, textPaint)
            }
        }
    }
}
