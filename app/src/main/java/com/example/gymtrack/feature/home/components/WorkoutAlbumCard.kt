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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.ui.theme.DefaultGradient
import com.example.gymtrack.core.ui.theme.LegsGradient
import com.example.gymtrack.core.ui.theme.PullGradient
import com.example.gymtrack.core.ui.theme.PushGradient
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
    val baseColor = when (note.categoryName?.lowercase()) {
        "push" -> PushGradient.first()
        "pull" -> PullGradient.first()
        "legs" -> LegsGradient.first()
        else -> DefaultGradient.first()
    }

    val cardBaseColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background

    val fadeGradient = remember(baseColor, cardBaseColor) {
        Brush.verticalGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.50f),
                cardBaseColor
            )
        )
    }

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

    val textColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBaseColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- TOP: Gradient Art ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(cardBaseColor)
                    .background(fadeGradient)
                    .padding(12.dp)
            ) {
                // Large Category Watermark
                Text(
                    text = (note.categoryName ?: "Work").take(4).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = textColor.copy(alpha = 0.1f),
                    fontWeight = FontWeight.Black,
                    fontSize = 50.sp,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            // --- BOTTOM: Metadata Footer ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBaseColor)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // Title
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // Footer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.6f),
                        maxLines = 1
                    )

                    // Duration Badge (Bottom Right)
                    if (totalMinutes > 0) {
                        Surface(
                            color = textColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$totalMinutes min",
                                color = textColor.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}