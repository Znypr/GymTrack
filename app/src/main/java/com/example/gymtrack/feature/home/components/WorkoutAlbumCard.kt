package com.example.gymtrack.feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.formatTime
import com.example.gymtrack.core.util.formatWeekRelativeTime
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutAlbumCard(
    note: NoteLine,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    settings: Settings
) {
    // [FIX] Dynamic Color Lookup
    // Find the category in settings that matches the note's category name.
    // If not found, default to Gray.
    val categoryColor = remember(note.categoryName, settings.categories) {
        val found = settings.categories.find { it.name == note.categoryName }
        if (found != null) Color(found.color) else Color(0xFF666666) // Default Gray
    }

    val cardBaseColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onSurface

    // Gradient using the dynamic color
    val fadeGradient = remember(categoryColor, cardBaseColor) {
        Brush.verticalGradient(
            colors = listOf(
                categoryColor.copy(alpha = 0.50f),
                cardBaseColor
            )
        )
    }

    // ... (Rest of UI remains identical) ...
    val totalMinutes = remember(note.text) {
        val seconds = parseNoteText(note.text).second.mapNotNull {
            if (it.isBlank()) null else parseDurationSeconds(it)
        }.maxOrNull()
        seconds?.div(60) ?: 0
    }

    val displayTitle = note.title.ifBlank {
        val full = formatWeekRelativeTime(note.timestamp, settings)
        if (full.contains(" ")) full.substringBeforeLast(" ") else full
    }
    val displaySubtitle = if (note.title.isNotBlank()) formatWeekRelativeTime(note.timestamp, settings) else formatTime(note.timestamp, settings)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBaseColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(cardBaseColor)
                    .background(fadeGradient)
                    .padding(12.dp)
            ) {
                Text(
                    text = (note.categoryName ?: "Work").take(6).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = textColor.copy(alpha = 0.1f),
                    fontWeight = FontWeight.Black,
                    fontSize = 50.sp,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBaseColor)
                    .padding(12.dp)
            ) {
                Text(displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(displaySubtitle, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.6f), maxLines = 1)
                    if (totalMinutes > 0) {
                        Surface(color = textColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Text("${totalMinutes} min", color = textColor.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}