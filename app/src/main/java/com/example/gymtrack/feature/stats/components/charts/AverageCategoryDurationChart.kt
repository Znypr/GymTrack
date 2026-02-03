package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.feature.stats.StatsState

@Composable
fun AverageCategoryDurationChart(
    state: StatsState,
    modifier: Modifier = Modifier
) {
    val data = state.averageDurations.toList()
        .sortedByDescending { it.second }
        .filter { it.second > 0 }

    Column(modifier.fillMaxWidth()) {
        Text(
            "Avg Duration (min)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        if (data.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No duration data available",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return
        }

        val theme = rememberChartTheme()
        val density = LocalDensity.current
        val textPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })
        val scaleY = buildScaleY(data.map { it.second })

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val layout = ChartLayout()
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            // Draw Grid
            drawYGridAndLabels(scaleY, theme, layout, textPaint)

            val n = data.size
            val groupGap = chartW / (n * 2f).coerceAtLeast(1f)
            val barW = groupGap.coerceAtMost(60f)

            data.forEachIndexed { idx, (cat, avg) ->
                val slotWidth = chartW / n
                val xCenter = x0 + (slotWidth * idx) + (slotWidth / 2)
                val xLeft = xCenter - (barW / 2)
                val barH = chartH * (avg / scaleY.top)

                // Rounded Green Bar
                drawRoundRect(
                    color = theme.primary.copy(alpha = 0.9f),
                    topLeft = Offset(xLeft, y0 - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                val label = if (cat.length > 3) cat.take(3) else cat
                drawXTickLabel(label, xCenter, y0, textPaint)
            }
        }
    }
}