package com.example.gymtrack.ui.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.barchart.BarChart
import co.yml.charts.ui.barchart.models.BarChartData
import co.yml.charts.ui.barchart.models.BarData
import com.example.gymtrack.data.Note
import com.example.gymtrack.util.WorkoutParser

@Composable
fun SetsDistributionChart(notes: List<Note>) {
    val parser = remember { WorkoutParser() }

    // 1. Parse notes to get accurate exercise names and counts
    val exerciseCounts = remember(notes) {
        notes.flatMap { note ->
            val sets = parser.parseWorkout(note.text)
            sets.map { it.exerciseName } // Extract name from every valid set
        }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5) // Top 5 exercises only
    }

    if (exerciseCounts.isEmpty()) return

    // 2. Map to Chart Data
    val barData = exerciseCounts.mapIndexed { index, (name, count) ->
        BarData(
            point = Point(index.toFloat(), count.toFloat()),
            label = name,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    val maxVal = (exerciseCounts.maxOfOrNull { it.second } ?: 0) + 1

    val xAxisData = AxisData.Builder()
        .steps(barData.size - 1)
        .bottomPadding(40.dp)
        .labelData { index -> barData.getOrNull(index)?.label ?: "" }
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .axisLineColor(MaterialTheme.colorScheme.onSurfaceVariant)
        .build()

    val yAxisData = AxisData.Builder()
        .steps(5)
        .labelAndAxisLinePadding(20.dp)
        .axisOffset(20.dp)
        .labelData { i -> ((i * (maxVal / 5f)).toInt()).toString() }
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .axisLineColor(MaterialTheme.colorScheme.onSurfaceVariant)
        .build()

    val barChartData = BarChartData(
        chartData = barData,
        xAxisData = xAxisData,
        yAxisData = yAxisData,
        backgroundColor = Color.Transparent,
        paddingEnd = 0.dp
    )

    BarChart(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        barChartData = barChartData
    )
}