package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.InfoLine
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.editor.CategoryDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val EDITOR_TOP_LINE_TEST_TAG = "editor-top-line"
internal const val EDITOR_TOP_LINE_CATEGORY_TEST_TAG = "editor-top-line-category"

/**
 * Read-only top line, about 44 dp (EDITOR_V2 §7): category mark + name · elapsed · small date.
 * Nothing here is a tap target (one-hand rule); a long-press opens the category picker, which is
 * also in the accessory bar's ⋯ menu. An unknown category shows its stored name with the neutral
 * mark (#296).
 */
@Composable
fun EditorTopLine(
    category: CategoryDisplay,
    timestamp: Long,
    timer: WorkoutTimerUi?,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val dateText = remember(timestamp) {
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(timestamp))
    }
    val elapsedText = timer?.let { formatTopLineElapsed(it.elapsedSeconds) }
    // Notebook: the workout name is a handwritten heading (Caveat); Normal stays crisp.
    val titleStyle = if (tokens.isNotebook) tokens.type.title.copy(fontSize = 26.sp) else tokens.type.body.copy(fontSize = 17.sp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp)
            .testTag(EDITOR_TOP_LINE_TEST_TAG)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(
            kind = category.kind,
            label = category.label,
            size = 12.dp,
            customColor = category.customColor?.let { Color(it) },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = category.label,
            style = titleStyle.copy(fontWeight = titleStyle.fontWeight ?: FontWeight.SemiBold),
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(EDITOR_TOP_LINE_CATEGORY_TEST_TAG),
        )
        if (elapsedText != null) {
            Text(text = "  ·  ", style = titleStyle, color = c.inkMuted)
            Text(
                text = elapsedText,
                style = if (tokens.isNotebook) tokens.type.setNumber else titleStyle,
                color = c.ink,
                // Robots read this Text's semantics; keep them.
                modifier = Modifier.testTag(NOTE_TIMER_ELAPSED_TEST_TAG),
            )
        }
        Spacer(Modifier.weight(1f))
        InfoLine(parts = listOf(dateText))
    }
}
