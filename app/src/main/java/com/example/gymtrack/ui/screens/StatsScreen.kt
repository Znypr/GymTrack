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
import androidx.compose.ui.platform.LocalContext
import com.example.gymtrack.ui.components.charts.CategoryChart
import com.example.gymtrack.ui.theme.AppleBlack
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsState,
    settings: Settings,
    onBack: () -> Unit,
    workoutRepository: WorkoutRepository
) {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<File?>(null) }
    // (Keep csvFiles logic if you want, or move to ViewModel later)
    var dragX by remember { mutableStateOf(0f) }

    Scaffold(
        containerColor = AppleBlack,
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
            Image(
                painter = painterResource(id = R.drawable.ic_gymtrack_logo),
                contentDescription = "GymTrack logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Welcome back!", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))

            // Pass the state values directly
            // Inside StatsScreen.kt
            StatsOverview(state)
            Spacer(Modifier.height(32.dp))

            ExerciseProgressCard(repository = workoutRepository)
            Spacer(Modifier.height(32.dp))

            SetsDistributionChart(topExercises = state.topExercises)
            Spacer(Modifier.height(32.dp))

            AverageDurationChart(state)
            Spacer(Modifier.height(32.dp))

            TimeOfDayHeatmap(data = state.heatmapData)
            Spacer(Modifier.height(32.dp))

            CategoryChart(state)
            // Note: LearningsOverview still needs raw notes or should be moved to VM.
            // For now, we can leave it or disable it.
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatsOverview(state: StatsState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Total notes: ${state.totalNotes}")
            Text("Categories: ${state.totalCategories}")
            Text("Average Sets: ${"%.1f".format(state.avgSets)}")
        }
    }
}

