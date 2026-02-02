package com.example.gymtrack.ui.screens // or feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable // Import this
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.ui.theme.*
import com.example.gymtrack.util.formatTime
import com.example.gymtrack.util.formatWeekRelativeTime
import com.example.gymtrack.util.parseDurationSeconds
import com.example.gymtrack.util.parseNoteText

@OptIn(ExperimentalFoundationApi::class) // Required for combinedClickable
@Composable
fun WorkoutAlbumCard(
    note: NoteLine,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    settings: Settings
) {
    // ... [Keep Gradient/Date Logic from previous response] ...
    // (If you need me to repost the Date/Title logic, let me know, otherwise keep what you have)
    val gradientColors = when (note.categoryName?.lowercase()) {
        "push" -> PushGradient
        "pull" -> PullGradient
        "legs" -> LegsGradient
        else -> DefaultGradient
    }

    val totalMinutes = remember(note.text) {
        val seconds = parseNoteText(note.text).second.mapNotNull {
            if (it.isBlank()) null else parseDurationSeconds(it)
        }.maxOrNull()
        seconds?.div(60) ?: 0
    }

    val displayTitle = if (note.title.isNotBlank()) note.title else {
        val full = formatWeekRelativeTime(note.timestamp, settings)
        if (full.contains(" ")) full.substringBeforeLast(" ") else full
    }
    val displaySubtitle = if (note.title.isNotBlank()) formatWeekRelativeTime(note.timestamp, settings) else formatTime(note.timestamp, settings)

    // --- CARD UI ---
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            // [FIX] Use combinedClickable instead of clickable
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            // [FIX] Add a border if selected so you can visually see it
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            // Change color slightly if selected
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        // ... [Rest of your Column/Box content remains the same] ...
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(gradientColors))
                    .padding(12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = (note.categoryName ?: "Work").take(4).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Black,
                    fontSize = 60.sp
                )
                if (totalMinutes > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = "${totalMinutes}m",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}