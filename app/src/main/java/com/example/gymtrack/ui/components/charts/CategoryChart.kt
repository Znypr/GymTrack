package com.example.gymtrack.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.ui.screens.StatsState

@Composable
fun CategoryChart(
    state: StatsState,
    modifier: Modifier = Modifier
) {
    // 1. Prepare Data: Sort categories by count (highest first)
    val data = state.categoryCounts.toList()
        .sortedByDescending { it.second }
        .filter { it.second > 0 } // Hide categories with 0 workouts

    if (data.isEmpty()) return

    // 2. Setup Theme & Dimensions
    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })

    // 3. Determine Scale
    val maxCount = data.maxOf { it.second }
    val scaleY = buildScaleY(
        values = data.map { it.second.toFloat() },
        stepPicker = ::niceStepCount // Use integer steps for counts
    )

    Column(modifier.fillMaxWidth()) {
        Text("Workouts per Category", style = MaterialTheme.typography.titleLarge)
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
                yAxisTitle = "Count"
            )

            // Draw Bars
            val n = data.size
            // Dynamic bar width based on number of items
            val groupGap = chartW / (n * 2f).coerceAtLeast(1f)
            val barW = groupGap.coerceAtMost(100f) // Cap max width

            data.forEachIndexed { idx, (cat, count) ->
                // Center the bar in its slot
                val slotWidth = chartW / n
                val xCenter = x0 + (slotWidth * idx) + (slotWidth / 2)
                val xLeft = xCenter - (barW / 2)

                val barH = chartH * (count / scaleY.top)

                // Draw Bar
                drawRect(
                    color = theme.primary.copy(alpha = 0.7f),
                    topLeft = Offset(xLeft, y0 - barH),
                    size = Size(barW, barH)
                )

                // Draw X-Label (Category Name)
                // We truncate long names to fit
                val label = if (cat.length > 5) cat.take(4) + ".." else cat
                drawXTickLabel(drawContext.canvas.nativeCanvas, label, xCenter, y0, textPaint)
            }
        }
    }
}