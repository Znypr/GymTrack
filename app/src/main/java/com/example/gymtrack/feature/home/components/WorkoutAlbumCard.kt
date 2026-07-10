package com.example.gymtrack.feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.formatTime
import com.example.gymtrack.core.util.formatWeekRelativeTime
import com.example.gymtrack.feature.home.HomeWorkoutStats
import com.example.gymtrack.feature.home.cardMetricLabel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutAlbumCard(
    note: NoteLine,
    stats: HomeWorkoutStats,
    flames: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    settings: Settings
) {
    val fallbackCategoryColor = MaterialTheme.colorScheme.primary
    val categoryColor = remember(note.categoryName, settings.categories, fallbackCategoryColor) {
        val found = settings.categories.find { it.name == note.categoryName }
        if (found != null) Color(found.color) else fallbackCategoryColor
    }

    val cardBaseColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardShape = MaterialTheme.shapes.large

    val displayTitle = note.title.ifBlank {
        val full = formatWeekRelativeTime(note.timestamp, settings)
        if (full.contains(" ")) full.substringBeforeLast(" ") else full
    }
    val displaySubtitle = if (note.title.isNotBlank()) formatWeekRelativeTime(note.timestamp, settings) else formatTime(note.timestamp, settings)
    val cardMetric = stats.cardMetricLabel(settings.homeCardMetric)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.16f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                } else {
                    Modifier
                },
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBaseColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(categoryColor),
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (note.categoryName ?: "Workout").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CompactIntensityMark(flames = flames)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = displaySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = mutedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = cardMetric,
                    color = mutedTextColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )

                if (stats.durationMinutes > 0) {
                    Text(
                        text = "${stats.durationMinutes} min",
                        color = mutedTextColor,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIntensityMark(flames: Int) {
    val count = flames.coerceIn(0, 3)
    if (count == 0) return

    Row(
        horizontalArrangement = Arrangement.spacedBy((-2).dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp),
    ) {
        repeat(3) { index ->
            val active = index < count
            Box(
                modifier = Modifier
                    .size(if (active) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                        },
                    ),
            )
        }
    }
}
