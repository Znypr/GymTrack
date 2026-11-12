package com.example.gymtrack.feature.finish

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
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
import com.example.gymtrack.core.ui.components.BodyMap
import com.example.gymtrack.core.ui.components.BodyMapLegend
import com.example.gymtrack.core.ui.components.BodyMapLevelList
import com.example.gymtrack.core.ui.components.BodyMapView
import com.example.gymtrack.core.muscle.MuscleLevel
import com.example.gymtrack.core.ui.components.setsPhrase
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.PrimaryButton
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchUnderline

const val FINISH_SHEET_DONE_TAG = "finish-sheet-done"
const val FINISH_SHEET_EDIT_TAG = "finish-sheet-edit"
const val FINISH_SHEET_SAVE_TEMPLATE_TAG = "finish-sheet-save-template"
const val FINISH_SHEET_TEMPLATE_SAVED_TAG = "finish-sheet-template-saved"
const val FINISH_SHEET_NEXT_TAG = "finish-sheet-next"
const val FINISH_SHEET_MUSCLES_TAG = "finish-sheet-muscles"

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
    onSaveTemplate: (() -> Unit)? = null,
    savedTemplateName: String? = null,
) {
    AppBottomSheet(onDismissRequest = onDone) {
        FinishSummaryContent(summary, onDone, onEdit, onSaveTemplate = onSaveTemplate, savedTemplateName = savedTemplateName)
    }
}

/** The sheet's body, without the sheet (for previews and tests). */
@Composable
fun FinishSummaryContent(
    summary: FinishSummary,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Planner v1 (#314): saves this workout's exercise names as a template; null hides it. */
    onSaveTemplate: (() -> Unit)? = null,
    /** Set once saved: the row then says "Saved as template "Push"" instead of the button. */
    savedTemplateName: String? = null,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = tokens.spacing.md),
    ) {
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
        MusclesSection(summary)
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(summary.weekLine, style = tokens.type.small, color = c.inkMuted, modifier = Modifier.testTag("finish-week"))
        if (savedTemplateName != null) {
            Spacer(Modifier.height(tokens.spacing.sm))
            Text(
                "Saved as template “$savedTemplateName”",
                style = tokens.type.small,
                color = c.inkMuted,
                modifier = Modifier.heightIn(min = 48.dp).testTag(FINISH_SHEET_TEMPLATE_SAVED_TAG),
            )
        } else if (onSaveTemplate != null) {
            Spacer(Modifier.height(tokens.spacing.xs))
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Save these exercises as a template", onClick = onSaveTemplate)
                    .testTag(FINISH_SHEET_SAVE_TEMPLATE_TAG),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Save as template", style = tokens.type.body, color = c.action)
            }
        }
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

/**
 * Body map of this workout (#311): the map with the "next" muscle outlined, sets per muscle and
 * a "Next time: …" line. Honest when nothing is mapped yet: dotted map plus one sentence.
 */
@Composable
private fun MusclesSection(summary: FinishSummary) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val week = summary.muscles
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().testTag(FINISH_SHEET_MUSCLES_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(tokens.spacing.sm))
        Text(
            text = if (tokens.isNotebook) "Muscles" else "MUSCLES",
            style = if (tokens.isNotebook) tokens.type.title else tokens.type.label,
            color = c.inkMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        BodyMap(
            levels = week.levels,
            view = BodyMapView.BOTH,
            highlight = summary.nextTime,
            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        BodyMapLegend(showUnmapped = week.levels.values.any { it == MuscleLevel.UNMAPPED })
        Spacer(Modifier.height(tokens.spacing.xs))
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            if (week.isAllUnmapped) {
                Text(
                    text = "Muscles aren't mapped for these exercises yet, so the map shows dotted outlines.",
                    style = tokens.type.small,
                    color = c.inkMuted,
                )
            } else {
                val trained = summary.setsPerMuscle
                Text(
                    text = if (trained.isEmpty()) {
                        "No mapped muscle trained in this workout."
                    } else {
                        trained.joinToString(" · ") { (m, sets) -> "${m.label} ${setsPhrase(sets)}" }
                    },
                    style = tokens.type.body,
                    color = c.ink,
                )
                BodyMapLevelList(week.levels, Modifier.padding(top = tokens.spacing.xxs))
            }
            summary.nextTime?.let { next ->
                Spacer(Modifier.height(tokens.spacing.xxs))
                Text(
                    text = "Next time: ${next.label} (outlined on the map)",
                    style = tokens.type.body,
                    color = c.ink,
                    modifier = Modifier.testTag(FINISH_SHEET_NEXT_TAG),
                )
            }
        }
    }
}

private val previewSummary = FinishSummary(
    title = "Push",
    durationMinutes = 64,
    exerciseCount = 3,
    setCount = 9,
    topSets = listOf(TopSet("Bench Press", "8 × 85"), TopSet("Lateral Raise", "15 × 10")),
    workoutsThisWeek = 3,
)

@androidx.compose.ui.tooling.preview.Preview(name = "Finish · muscles", widthDp = 360, heightDp = 1100)
@Composable
private fun FinishMusclesPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(com.example.gymtrack.core.ui.components.ThemeTokensProvider::class)
    tokens: com.example.gymtrack.core.ui.theme.GymTrackTokens,
) = GymTrackTheme(tokens = tokens) {
    val week = com.example.gymtrack.core.ui.components.BodyMapDemoWeek
    FinishSummaryContent(previewSummary.copy(muscles = week, nextTime = nextMuscleSuggestion(week)), onDone = {}, onEdit = {})
}

@androidx.compose.ui.tooling.preview.Preview(name = "Finish · unmapped", widthDp = 360, heightDp = 900)
@Composable
private fun FinishUnmappedPreview(
    @androidx.compose.ui.tooling.preview.PreviewParameter(com.example.gymtrack.core.ui.components.ThemeTokensProvider::class)
    tokens: com.example.gymtrack.core.ui.theme.GymTrackTokens,
) = GymTrackTheme(tokens = tokens) {
    FinishSummaryContent(previewSummary, onDone = {}, onEdit = {})
}
