package com.example.gymtrack.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.data.GraphPoint
import com.example.gymtrack.data.WorkoutRepository
import com.example.gymtrack.data.ExerciseWithCount
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flowOf
import kotlin.collections.List
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.maxOf
import kotlin.collections.minOf
import kotlin.collections.mutableListOf

// Data class to hold the slope and intercept of the line
private data class RegressionLine(val slope: Float, val intercept: Float)

// --- IQR HELPER FUNCTIONS ---

// Helper function to calculate quartiles
private fun calculateQuartiles(sortedValues: List<Float>): Triple<Float, Float, Float> {
    if (sortedValues.isEmpty()) return Triple(0f, 0f, 0f)

    val N = sortedValues.size
    val Q2 = sortedValues[N / 2] // Median (Q2)

    val lowerHalf = sortedValues.subList(0, N / 2)
    val upperHalfStartIndex = if (N % 2 == 0) N / 2 else N / 2 + 1
    val upperHalf = sortedValues.subList(upperHalfStartIndex, N)

    val Q1 = lowerHalf.let { if (it.isEmpty()) 0f else it[it.size / 2] }
    val Q3 = upperHalf.let { if (it.isEmpty()) 0f else it[it.size / 2] }

    return Triple(Q1, Q2, Q3)
}

// Main function to identify outliers using IQR method
private fun calculateOutliers(data: List<GraphPoint>): List<GraphPoint> {
    if (data.size < 5) return emptyList()

    // 1. Get and sort the average weight values
    val sortedValues = data.map { it.avgVal }.sorted()

    // 2. Calculate Q1, Q3, and IQR
    val (Q1, _, Q3) = calculateQuartiles(sortedValues)
    val IQR = Q3 - Q1

    // 3. Define the bounds
    // The multiplier 1.5 is the standard for detecting mild outliers
    val lowerBound = Q1 - 1.5f * IQR
    val upperBound = Q3 + 1.5f * IQR

    // 4. Filter data points outside the bounds
    return data.filter { it.avgVal < lowerBound || it.avgVal > upperBound }
}


// --- LINEAR REGRESSION HELPER ---
private fun calculateLinearRegression(data: List<GraphPoint>): RegressionLine? {
    if (data.size < 2) return null

    val N = data.size.toFloat()
    val minTime = data.minOf { it.originTimestamp } // FIND MINIMUM

    // NORMALIZE: Subtract minTime from every timestamp
    val sumX = data.sumOf { (it.originTimestamp - minTime).toDouble() }
    val sumY = data.sumOf { it.avgVal.toDouble() }
    val sumX2 = data.sumOf {
        val x = (it.originTimestamp - minTime).toDouble()
        x * x
    }
    val sumXY = data.sumOf {
        val x = (it.originTimestamp - minTime).toDouble()
        x * it.avgVal.toDouble()
    }

    val m_numerator = (N * sumXY - sumX * sumY).toFloat()
    val m_denominator = (N * sumX2 - sumX * sumX).toFloat()

    if (m_denominator == 0f) {
        return RegressionLine(0f, (sumY / N).toFloat())
    }

    val slope = m_numerator / m_denominator
    // Intercept is now relative to 0 (which is minTime)
    val intercept = ((sumY - slope * sumX) / N).toFloat()

    // Adjust intercept back to absolute time for drawing logic compatibility?
    // Actually, it's easier to keep the drawing logic aware of the shift.
    // But to minimize changes to your drawing code, we can return the "Virtual" intercept:
    // y = m(x - minX) + c  ->  y = mx - m*minX + c
    val realIntercept = intercept - (slope * minTime)

    return RegressionLine(slope, realIntercept)
}

// The internal drawing function (The actual line graph)
@Composable
private fun ExerciseProgressChartGraph(
    dataPoints: List<GraphPoint>,
    anomalies: List<GraphPoint>, // Pass anomalies for visual distinction
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (dataPoints.isEmpty()) {
        Box(modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No data for this exercise yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxVal = dataPoints.maxOf { it.avgVal }
    val minVal = dataPoints.minOf { it.avgVal } * 0.9f
    val yRange = (maxVal - minVal).let { if (it == 0f) 1f else it }

    val maxTime = dataPoints.maxOf { it.originTimestamp }
    val minTime = dataPoints.minOf { it.originTimestamp }
    val timeRange = (maxTime - minTime).let { if (it == 0L) 1L else it }

    val dateFormatter = remember { SimpleDateFormat("MMM yy", Locale.getDefault()) }

    val regressionLine = calculateLinearRegression(dataPoints)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(start = 40.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            val width = size.width
            val height = size.height

            val labelCount = 4
            for (i in 0..labelCount) {
                val ratio = i / labelCount.toFloat()
                val labelVal = minVal + yRange * ratio
                val y = height * (1 - ratio)

                // Draw horizontal grid line
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(0f, y),
                    end = Offset(width, y)
                )

                // Draw Y-Axis label
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "${labelVal.roundToInt()}",
                        0f,
                        y - 10f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 30f
                        }
                    )
                }
            }

            // --- DRAW LINEAR REGRESSION TREND LINE ---
            regressionLine?.let { line ->
                val yStart = line.slope * minTime + line.intercept
                val yStartNormalized = height - (((yStart - minVal) / yRange) * height)

                val yEnd = line.slope * maxTime + line.intercept
                val yEndNormalized = height - (((yEnd - minVal) / yRange) * height)

                // Draw the line of best fit (dimmed)
                drawLine(
                    color = lineColor.copy(alpha = 0.3f), // Dim color
                    start = Offset(0f, yStartNormalized),
                    end = Offset(width, yEndNormalized),
                    strokeWidth = 5f
                )
            }

            // --- DRAW PRIMARY DATA LINE AND POINTS ---
            val primaryPath = Path()
            val anomalyTimestamps = anomalies.map { it.originTimestamp }.toSet()

            dataPoints.forEachIndexed { index, point ->
                val x = ((point.originTimestamp - minTime) / timeRange.toFloat()) * width
                val y = height - (((point.avgVal - minVal) / yRange) * height)

                if (index == 0) primaryPath.moveTo(x, y) else primaryPath.lineTo(x, y)

                // Highlight Anomalies
                val pointColor = if (point.originTimestamp in anomalyTimestamps) {
                    Color.Red // Use Red for outliers
                } else {
                    lineColor
                }

                drawCircle(color = pointColor, radius = 6f, center = Offset(x, y))
            }

            drawPath(
                path = primaryPath,
                color = lineColor,
                style = Stroke(width = 5f)
            )
        }

        // Simple X-Axis Labels (Start/End)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dateFormatter.format(Date(minTime)), style = MaterialTheme.typography.bodySmall)
            if (minTime != maxTime) {
                Text(dateFormatter.format(Date(maxTime)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


// The new component responsible for the entire Card, Dropdown, and Data Fetching
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressCard(
    repository: WorkoutRepository?,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()) }

    // 1. FETCH SORTED DATA: Use getExercisesSortedByCount()
    val allExercises by (repository?.getExercisesSortedByCount() ?: remember { flowOf(emptyList()) })
        .collectAsState(initial = emptyList())

    // 2. STATE: Use the new DTO ExerciseWithCount
    var selectedExercise by remember { mutableStateOf<ExerciseWithCount?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    // 3. GRAPH DATA: Fetch history for the selected exercise
    val graphData by remember(selectedExercise) {
        selectedExercise?.let { repository?.getWeightHistory(it.exerciseId) }
            ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // 4. CALCULATE ANOMALIES
    val anomalies = remember(graphData) {
        calculateOutliers(graphData)
    }

    // Auto-select first exercise if none selected
    LaunchedEffect(allExercises) {
        if (selectedExercise == null && allExercises.isNotEmpty()) {
            selectedExercise = allExercises.first()
        }
    }

    // --- CARD UI MOVED FROM StatsScreen ---
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Exercise Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // Dropdown Selector
            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = !expandedDropdown }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    // Display name and count
                    value = selectedExercise?.let { "${it.name} (${it.setTotalCount} sets)" } ?: "Select Exercise",
                    onValueChange = {},
                    label = { Text("Exercise") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedDropdown) },
                    colors = OutlinedTextFieldDefaults.colors()
                )
                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    // The list is already sorted by the database query (by count DESC)
                    allExercises.forEach { exercise ->
                        DropdownMenuItem(
                            // Display name and count
                            text = { Text("${exercise.name} (${exercise.setTotalCount} sets)") },
                            onClick = {
                                selectedExercise = exercise
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // --- ANOMALY DISPLAY ---
            if (anomalies.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "⚠️ ${anomalies.size} Anomalies Detected!",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))

                // Show the details of the first 3 anomalies
                anomalies.take(3).forEach { anomaly ->
                    val date = dateFormatter.format(Date(anomaly.originTimestamp))
                    Text(
                        "-> ${date}: ${anomaly.avgVal.roundToInt()} kg",
                        color = Color.Red.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // The Graph (Calls the helper function defined above)
            ExerciseProgressChartGraph(dataPoints = graphData, anomalies = anomalies)
        }
    }
}