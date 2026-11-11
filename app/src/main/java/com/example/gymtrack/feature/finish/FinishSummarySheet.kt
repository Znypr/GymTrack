package com.example.gymtrack.feature.finish

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.AppBottomSheet
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.PrimaryButton
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchUnderline

const val FINISH_SHEET_DONE_TAG = "finish-sheet-done"
const val FINISH_SHEET_EDIT_TAG = "finish-sheet-edit"

/**
 * Shown after Finish (DESIGN_LANGUAGE_V2 "Finish sheet": torn paper in Notebook). Read-only
 * summary, then [Edit workout] and [Done] in the thumb zone. Dismissing the sheet = Done.
 *
 * Build [summary] with [buildFinishSummary] from the saved workout.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FinishSummarySheet(
    summary: FinishSummary,
    onDone: () -> Unit,
    onEdit: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDone) {
        FinishSummaryContent(summary, onDone, onEdit)
    }
}

/** The sheet's body, without the sheet (for previews and tests). */
@Composable
fun FinishSummaryContent(
    summary: FinishSummary,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(bottom = tokens.spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(AppIcon.CHECK, contentDescription = null, tint = c.ink, size = 22.dp)
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text = "Workout saved",
                style = tokens.type.title,
                color = c.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(tokens.spacing.xxs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryMark(CategoryKind.fromName(summary.title), label = summary.title, size = 12.dp)
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(summary.headline, style = tokens.type.body, color = c.ink, modifier = Modifier.testTag("finish-headline"))
        }
        if (summary.topSets.isNotEmpty()) {
            Spacer(Modifier.height(tokens.spacing.sm))
            Text(
                text = if (tokens.isNotebook) "Top sets" else "TOP SETS",
                style = if (tokens.isNotebook) tokens.type.title else tokens.type.label,
                color = c.inkMuted,
            )
            summary.topSets.forEachIndexed { index, top ->
                if (index > 0) AppDivider(seed = index * 11)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = top.exercise,
                        style = tokens.type.body,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(top.display, style = tokens.type.setNumber, color = c.ink)
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(summary.weekLine, style = tokens.type.small, color = c.inkMuted, modifier = Modifier.testTag("finish-week"))
        Spacer(Modifier.height(tokens.spacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .testTag(FINISH_SHEET_EDIT_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Edit workout",
                    style = tokens.type.body,
                    color = c.ink,
                    modifier = if (tokens.isNotebook) Modifier.sketchUnderline(c.ink, seed = 83) else Modifier,
                )
            }
            PrimaryButton(
                text = "Done",
                onClick = onDone,
                icon = AppIcon.CHECK,
                modifier = Modifier.weight(1f).testTag(FINISH_SHEET_DONE_TAG),
            )
        }
    }
}
