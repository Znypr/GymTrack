package com.example.gymtrack.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.R
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    notes: List<NoteLine>,
    settings: Settings,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
                .padding(horizontal = 16.dp),
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
            CategoryChart(notes)
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
                    color = MaterialTheme.colorScheme.primary,
                    topLeft = androidx.compose.ui.geometry.Offset(barWidth * (1 + index * 2), size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
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
