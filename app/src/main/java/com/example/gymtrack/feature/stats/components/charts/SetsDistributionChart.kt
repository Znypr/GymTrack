package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetsDistributionChart(
    topExercises: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    if (topExercises.isEmpty()) return

    // 1. Prepare Data
    val data = topExercises // Already sorted by count in ViewModel

    // 2. Setup Theme & Dimensions
    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })

    // 3. Determine Scale
    val maxCount = data.maxOf { it.second }
    val scaleY = buildScaleY(
        values = data.map { it.second.toFloat() },
        stepPicker = ::niceStepCount
    )

    Column(modifier.fillMaxWidth()) {
        Text("Top 5 Exercises (Sets)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val layout = ChartLayout(leftPad = 56f, rightPad = 12f, topPad = 8f, bottomPad = 28f)
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            // Draw Axes
            drawAxes(::drawLine, theme, layout, size.width, size.height)

            // Draw Y-Grid and Labels
            drawYGridAndLabels(
                drawContext.canvas.nativeCanvas, ::drawLine, scaleY, theme, layout,
                size.width, size.height, textPaint,
                yAxisTitle = "Sets"
            )

            // Draw Bars
            val n = data.size
            val groupGap = chartW / (n * 2f).coerceAtLeast(1f)
            val barW = groupGap.coerceAtMost(100f)

            data.forEachIndexed { idx, (name, count) ->
                val slotWidth = chartW / n
                val xCenter = x0 + (slotWidth * idx) + (slotWidth / 2)
                val xLeft = xCenter - (barW / 2)
                val barH = chartH * (count / scaleY.top)

                // Draw Bar
                drawRect(
                    color = theme.secondary.copy(alpha = 0.7f), // Use secondary color to distinguish from other charts
                    topLeft = Offset(xLeft, y0 - barH),
                    size = Size(barW, barH)
                )

                // Draw X-Label (Exercise Name)
                // Truncate if too long
                val label = if (name.length > 8) name.take(6) + ".." else name
                drawXTickLabel(drawContext.canvas.nativeCanvas, label, xCenter, y0, textPaint)
            }
        }
    }
}