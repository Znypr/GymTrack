package com.example.gymtrack.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.R
import com.example.gymtrack.data.ExerciseEntity
import com.example.gymtrack.data.GraphPoint
import com.example.gymtrack.data.Note
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.WorkoutRepository
import com.example.gymtrack.ui.components.charts.AverageDurationChart
import com.example.gymtrack.ui.components.charts.DurationTrendChart
import com.example.gymtrack.ui.components.charts.ExerciseProgressCard
import com.example.gymtrack.ui.components.charts.LearningsOverview
import com.example.gymtrack.ui.components.charts.SetsDistributionChart
import com.example.gymtrack.ui.components.charts.StatsOverview
import com.example.gymtrack.ui.components.charts.TimeOfDayHeatmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    notes: List<NoteLine>,
    settings: Settings,
    repository: WorkoutRepository?, // NEW PARAMETER
    onBack: () -> Unit,
) {
    var dragX by remember { mutableStateOf(0f) }

    // --- NEW STATE FOR EXERCISE CHART ---
    val allExercises by (repository?.getAllExercises() ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) })
        .collectAsState(initial = emptyList())

    var selectedExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }

    // Auto-select first exercise if none selected
    LaunchedEffect(allExercises) {
        if (selectedExercise == null && allExercises.isNotEmpty()) {
            selectedExercise = allExercises.first()
        }
    }

    val graphData by remember(selectedExercise) {
        selectedExercise?.let { repository?.getWeightHistory(it.exerciseId) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

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
                            if (dragX < -100f) onBack()
                            dragX = 0f
                        }
                    ) { _, dragAmount ->
                        if (dragAmount < 0) dragX += dragAmount
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Welcome back!", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))

            ExerciseProgressCard(repository, modifier = Modifier)

            Spacer(Modifier.height(32.dp))

            val chartNotes: List<Note> = remember(notes) {
                notes.map { noteLine ->
                    Note(
                        text = noteLine.text,
                        date = noteLine.timestamp,
                        categoryName = noteLine.categoryName
                    )
                }
            }

            // --- EXISTING CHARTS ---
            StatsOverview(chartNotes)
            Spacer(Modifier.height(32.dp))
            AverageDurationChart(notes)
            Spacer(Modifier.height(32.dp))
            DurationTrendChart(notes, showRollingAvg = true)
            Spacer(Modifier.height(32.dp))
            SetsDistributionChart(chartNotes)
            Spacer(Modifier.height(32.dp))
            TimeOfDayHeatmap(notes)
            Spacer(Modifier.height(32.dp))
            LearningsOverview(notes, settings)
            Spacer(Modifier.height(32.dp))
        }
    }
}
