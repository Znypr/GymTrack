import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.formatWeekRelativeTime
import com.example.gymtrack.core.util.parseDurationSeconds
import com.example.gymtrack.core.util.parseNoteText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernNoteCard(
    note: NoteLine,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    settings: Settings,
) {
    val categoryColor = DEFAULT_CATEGORIES.find { it.name.equals(note.categoryName, ignoreCase = true) }
        ?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurface

    val totalMinutes = remember(note.text) {
        val seconds = parseNoteText(note.text).second.mapNotNull {
            if (it.isBlank()) null else parseDurationSeconds(it)
        }.maxOrNull()
        seconds?.div(60)
    }

    // Use Theme Surface
    val containerColor = if (isSelected) categoryColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) categoryColor else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(width = if (isSelected) 2.dp else 0.dp, color = borderColor, shape = RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = (note.categoryName ?: "Workout").uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = categoryColor,
                letterSpacing = (-0.5).sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatWeekRelativeTime(note.timestamp, settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (totalMinutes != null && totalMinutes > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), // Subtle badge
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${totalMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}