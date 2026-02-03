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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetsDistributionChart(
    topExercises: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    if (topExercises.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp))
        return
    }

    val theme = rememberChartTheme()
    val density = LocalDensity.current
    val labelPaint = makeTextPaint(theme.label, with(density) { 11.sp.toPx() })
    val countPaint = makeTextPaint(theme.label, with(density) { 10.sp.toPx() })

    // Scale based on max count
    val maxCount = topExercises.maxOf { it.second }.toFloat()

    Column(modifier.fillMaxWidth()) {
        Text(
            "Top Exercises (Sets)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val barHeight = 24.dp.toPx()
            val gap = 12.dp.toPx()
            val startY = 10f
            val maxBarWidth = size.width - 40f // Leave space for count label

            topExercises.forEachIndexed { i, (name, count) ->
                val y = startY + i * (barHeight + gap)

                // 1. Draw Exercise Name (Above Bar)
                drawContext.canvas.nativeCanvas.drawText(name, 0f, y, labelPaint)

                // 2. Draw Bar (Below Name)
                val barY = y + 10f
                val width = (count / maxCount) * maxBarWidth

                drawRoundRect(
                    color = theme.primary,
                    topLeft = Offset(0f, barY),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // 3. Draw Count Label (Right of bar)
                drawContext.canvas.nativeCanvas.drawText(
                    count.toString(),
                    width + 12f,
                    barY + barHeight - 6f,
                    countPaint
                )
            }
        }
    }
}