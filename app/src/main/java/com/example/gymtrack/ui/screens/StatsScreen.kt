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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.gymtrack.util.formatRelativeTime

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
            AverageDurationChart(notes)
            Spacer(Modifier.height(32.dp))
            CategoryChart(notes)
            Spacer(Modifier.height(32.dp))
            LearningsOverview(notes, settings)
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
private fun AverageDurationChart(notes: List<NoteLine>) {
    val averages = notes.groupBy { it.categoryName ?: "Other" }
        .mapValues { entry ->
            val durations = entry.value.mapNotNull { note ->
                parseNoteText(note.text).second.mapNotNull {
                    if (it.isBlank()) null else parseDurationSeconds(it)
                }.maxOrNull()
            }
            if (durations.isEmpty()) 0f else durations.average().toFloat() / 60f
        }

    if (averages.isEmpty()) return

    val max = averages.values.maxOrNull() ?: 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Avg workout length by category (min)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val barWidth = size.width / (averages.size * 2f)
            averages.entries.forEachIndexed { index, entry ->
                val barHeight = size.height * (entry.value / max)
                drawRect(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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
            averages.keys.forEach { label ->
                Text(label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LearningsOverview(notes: List<NoteLine>, settings: Settings) {
    val items = notes.flatMap { note ->
        val time = formatRelativeTime(note.timestamp, settings)
        val category = note.categoryName ?: "Other"
        note.learnings.split("\n").mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) null else Triple(trimmed, time, category)
        }
    }

    if (items.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Learnings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        items.forEach { (text, time, cat) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(time, style = MaterialTheme.typography.bodySmall)
                    Text(cat, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
