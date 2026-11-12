package com.example.gymtrack.feature.stats.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun CategoryChart(
    state: StatsState,
    modifier: Modifier = Modifier
) {
    val data = state.categoryCounts.toList()
        .sortedByDescending { it.second }
        .filter { it.second > 0 }

    if (data.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp))
        return
    }

    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val textPaint = makeTextPaint(theme.label, with(density) { ChartLabelSize.toPx() })

    val scaleY = buildScaleY(data.map { it.second.toFloat() })

    Column(modifier.fillMaxWidth()) {
        Text("Workouts per Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(16.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val layout = ChartLayout()
            val chartW = layout.width(size.width)
            val chartH = layout.height(size.height)
            val x0 = layout.originX()
            val y0 = layout.originY(size.height)

            // Draw Grid & Labels (Now called directly on DrawScope)
            drawYGridAndLabels(scaleY, theme, layout, textPaint)

            val n = data.size
            val groupGap = chartW / (n * 2f).coerceAtLeast(1f)
            val barW = groupGap.coerceAtMost(80f)

            data.forEachIndexed { idx, (cat, count) ->
                val slotWidth = chartW / n
                val xCenter = x0 + (slotWidth * idx) + (slotWidth / 2)
                val xLeft = xCenter - (barW / 2)
                val barH = chartH * (count / scaleY.top)

                // Draw Rounded Bar
                drawRoundRect(
                    color = theme.primary,
                    topLeft = Offset(xLeft, y0 - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // Draw X Label
                val label = if (cat.length > 4) cat.take(3) + "." else cat
                drawXTickLabel(label, xCenter, y0, textPaint)
            }
        }
    }
}