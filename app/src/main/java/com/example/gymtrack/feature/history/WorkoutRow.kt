package com.example.gymtrack.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.feature.home.WorkoutRowFacts
import com.example.gymtrack.feature.home.categoryLook
import com.example.gymtrack.feature.home.shortName

fun historyRowTestTag(timestamp: Long): String = "history-row-$timestamp"
fun todayRecentRowTestTag(timestamp: Long): String = "today-recent-row-$timestamp"

/**
 * A compact workout row (History and Today's Recent): category mark · name · date · duration ·
 * top set (REPS × WEIGHT). Each fact is shown once; unfinished workouts carry an "Unfinished"
 * marker (text in an outline, never colour alone).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutRow(
    note: NoteLine,
    facts: WorkoutRowFacts?,
    dateLabel: String,
    unfinished: Boolean,
    settings: Settings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val (kind, custom) = note.categoryLook(settings)
    val minutes = facts?.minutes ?: 0
    val details = listOfNotNull(dateLabel, if (minutes > 0) "$minutes min" else null).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .activeMark(selected, tokens, seed = note.timestamp.hashCode())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
            .semantics { this.selected = selected }
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(kind, label = note.categoryName ?: "No category", size = 14.dp, customColor = custom)
        Spacer(Modifier.width(tokens.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                note.title.trim().ifEmpty { note.shortName() },
                style = tokens.type.body,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(details, style = tokens.type.small, color = c.inkMuted, maxLines = 1)
        }
        Spacer(Modifier.width(tokens.spacing.xs))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            facts?.topSet?.let { top ->
                Text(
                    formatTopSet(top, settings.defaultWeightUnit),
                    style = tokens.type.small,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 168.dp),
                )
            }
            if (unfinished) UnfinishedMarker()
        }
        if (selected) {
            Spacer(Modifier.width(tokens.spacing.xs))
            AppIconView(AppIcon.CHECK, contentDescription = "Selected", tint = c.ink, size = 20.dp)
        }
    }
}

@Composable
private fun UnfinishedMarker() {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.inkMuted, seed = 5, strokeWidth = 1.2.dp, cornerRadius = 8.dp, dashed = true)
    } else {
        Modifier.border(1.dp, c.inkMuted, RoundedCornerShape(tokens.radii.small))
    }
    Text(
        "Unfinished",
        style = tokens.type.label,
        color = c.inkMuted,
        modifier = Modifier
            .then(outline)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("unfinished-marker"),
    )
}
