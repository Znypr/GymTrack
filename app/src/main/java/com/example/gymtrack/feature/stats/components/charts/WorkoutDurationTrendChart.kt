package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.feature.stats.buildWeeklyAverageWorkoutDurations
import com.example.gymtrack.feature.stats.countUnreasonableWorkoutDurations
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WorkoutDurationTrendChart(
    notes: List<NoteLine>,
    showRollingAvg: Boolean = true,
    modifier: Modifier = Modifier
) {
    val weekly = buildWeeklyAverageWorkoutDurations(notes)
    val filteredOutliers = countUnreasonableWorkoutDurations(notes)

    if (weekly.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(
                "No duration data in this period",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        return
    }

    val scaleY = buildScaleY(weekly.map { it.second })
    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })
    val df = SimpleDateFormat("dd MMM", Locale.getDefault())

    Column(modifier.fillMaxWidth()) {
        Text(
            "Workout Duration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (showRollingAvg) "Weekly average · minutes" else "Workout duration · minutes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
        )
        if (filteredOutliers > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Ignoring $filteredOutliers duration outlier${if (filteredOutliers == 1) "" else "s"} over 5h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
        Spacer(Modifier.height(16.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val layout = ChartLayout(leftPad = 64f)
            val chartW = layout.width(size.width)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            drawYGridAndLabels(scaleY, theme, layout, textPaint) { value ->
                if (value % 1.0f == 0f) "${value.toInt()}m" else String.format(Locale.getDefault(), "%.1fm", value)
            }

            val n = weekly.size
            val dx = if (n <= 1) chartW else chartW / (n - 1).toFloat()

            val points = weekly.mapIndexed { i, pair ->
                val x = x0 + dx * i
                val y = scaleY.yToPx(pair.second, y0, layout.topPad)
                x to y
            }

            val linePath = createSmoothPath(points)
            val fillPath = createFillPath(linePath, size.width, y0)

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(theme.primary.copy(alpha = 0.3f), Color.Transparent),
                    startY = layout.topPad,
                    endY = y0
                )
            )

            drawPath(
                path = linePath,
                color = theme.primary,
                style = Stroke(width = 5f)
            )

            drawXTickLabel(df.format(weekly.first().first), x0, y0, textPaint)
            if (n > 1) {
                drawXTickLabel(df.format(weekly.last().first), x0 + chartW, y0, textPaint)
            }
        }
    }
}
