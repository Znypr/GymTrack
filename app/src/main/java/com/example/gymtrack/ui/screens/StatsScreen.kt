package com.example.gymtrack.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.R
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings

import com.example.gymtrack.util.getSavedCsvFiles
import com.example.gymtrack.util.parseDurationSeconds
import com.example.gymtrack.util.parseNoteText
import java.io.File

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    notes: List<NoteLine>,
    settings: Settings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<File?>(null) }
    val csvFiles = remember { getSavedCsvFiles(context) }
    var dragX by remember { mutableStateOf(0f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Stats", fontSize = 24.sp) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX < -100f) {
                                onBack()
                            }
                            dragX = 0f
                        }
                    ) { _, dragAmount ->
                        if (dragAmount < 0) dragX += dragAmount
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Image(
                painter = painterResource(id = R.drawable.ic_gymtrack_logo),
                contentDescription = "GymTrack logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Welcome back!", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            StatsOverview(notes)
            Spacer(Modifier.height(32.dp))
            WorkoutDurationChart(notes)
            Spacer(Modifier.height(32.dp))
            CategoryChart(notes)
            Spacer(Modifier.height(32.dp))
            Text("Saved CSVs", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

        }
        selectedFile?.let { file ->
            AlertDialog(
                onDismissRequest = { selectedFile = null },
                confirmButton = {
                    TextButton(onClick = { selectedFile = null }) { Text("Close") }
                },
                title = { Text(file.name) },
                text = {
                    Box(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        Text(file.readText())
                    }
                }
            )
        }
    }
}

@Composable
private fun StatsOverview(notes: List<NoteLine>) {
    val categories = notes.mapNotNull { it.categoryName }.distinct().size
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Total notes: ${notes.size}")
            Text("Categories: $categories")
        }
    }
}

@Composable
private fun CategoryChart(notes: List<NoteLine>) {
    val counts = notes.groupingBy { it.categoryName ?: "Other" }.eachCount()
    val max = counts.values.maxOrNull() ?: 1
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Notes per Category", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)) {
            val barWidth = size.width / (counts.size * 2f)
            counts.entries.forEachIndexed { index, entry ->
                val barHeight = size.height * (entry.value.toFloat() / max)
                drawRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(barWidth * (1 + index * 2), size.height - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            counts.keys.forEach { label ->
                Text(label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WorkoutDurationChart(notes: List<NoteLine>) {
    val points = notes.mapNotNull { note ->
        val secs = parseNoteText(note.text).second.mapNotNull {
            if (it.isBlank()) null else parseDurationSeconds(it)
        }.maxOrNull()
        secs?.let { note.timestamp to it / 60f }
    }.sortedBy { it.first }
    if (points.isEmpty()) return

    val max = points.maxOf { it.second }
    val density = LocalDensity.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Workout length (min)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val labelSpace = with(density) { 32.dp.toPx() }
            val chartWidth = size.width - labelSpace
            val stepX = chartWidth / (points.size - 1).coerceAtLeast(1)
            val scaleY = size.height / max

            drawRect(color = Color.White, size = size)

            // axes
            drawLine(
                color = Color.DarkGray,
                start = Offset(labelSpace, 0f),
                end = Offset(labelSpace, size.height),
                strokeWidth = 2f
            )
            drawLine(
                color = Color.DarkGray,
                start = Offset(labelSpace, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f
            )

            // grid lines and labels
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = with(density) { 12.sp.toPx() }
            }
            val stepY = max / 4
            for (i in 0..4) {
                val value = stepY * i
                val y = size.height - (value * scaleY)
                drawLine(
                    color = Color.Gray.copy(alpha = 0.3f),
                    start = Offset(labelSpace, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    value.toInt().toString(),
                    0f,
                    y + textPaint.textSize / 2,
                    textPaint
                )
            }

            // plot line
            for (i in 0 until points.lastIndex) {
                val x1 = labelSpace + i * stepX
                val y1 = size.height - points[i].second * scaleY
                val x2 = labelSpace + (i + 1) * stepX
                val y2 = size.height - points[i + 1].second * scaleY
                drawLine(
                    color = Color.White,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 4f
                )
            }
        }
    }
}
