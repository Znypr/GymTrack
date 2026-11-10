package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val EDITOR_TOP_LINE_TEST_TAG = "editor-top-line"

/**
 * Read-only top line, about 44 dp (EDITOR_V2 §7): category mark + name · elapsed · small date.
 * Nothing here is a tap target (one-hand rule); a long-press opens the category picker, which is
 * also in the accessory bar's ⋯ menu.
 */
@Composable
fun EditorTopLine(
    category: Category?,
    timestamp: Long,
    timer: WorkoutTimerUi?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateText = remember(timestamp) {
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(timestamp))
    }
    val categoryName = category?.name ?: "Workout"
    val elapsedText = timer?.let { formatTopLineElapsed(it.elapsedSeconds) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp)
            .testTag(EDITOR_TOP_LINE_TEST_TAG)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .semantics {
                contentDescription = listOfNotNull(categoryName, elapsedText?.let { "elapsed $it" }, dateText)
                    .joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                // The category's own colour is user data (the mark), not a theme colour.
                .background(category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = categoryName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (elapsedText != null) {
            Text(
                text = "  ·  ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = elapsedText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(NOTE_TIMER_ELAPSED_TEST_TAG),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
