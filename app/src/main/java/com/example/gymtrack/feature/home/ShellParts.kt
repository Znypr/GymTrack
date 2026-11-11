package com.example.gymtrack.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.ui.components.AppBottomSheet
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.CategoryKind
import com.example.gymtrack.core.ui.components.CategoryMark
import com.example.gymtrack.core.ui.components.PrimaryButton
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens
import com.example.gymtrack.core.ui.theme.notebookPaper

/*
 * Small local wrappers for the shell, Today and History. core/ui has no screen background or
 * secondary text action yet; these only combine its tokens and components (no new look).
 */

internal const val ORPHAN_SHEET_TEST_TAG = "orphan-start-sheet"
internal fun orphanChoiceTestTag(choice: OrphanChoice): String = "orphan-choice-${choice.name.lowercase()}"

/** The page behind a tab: paper in Notebook, `bg` in Normal. */
internal fun Modifier.screenBackground(tokens: GymTrackTokens): Modifier =
    if (tokens.isNotebook) notebookPaper() else background(tokens.colors.bg)

/** A secondary action: text (plus optional icon), 48 dp tall, in ink or the given colour. */
@Composable
internal fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = GymTrackTheme.tokens.colors.ink,
    icon: AppIcon? = null,
    bold: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = GymTrackTheme.tokens
    val tint = if (enabled) color else tokens.colors.inkMuted.copy(alpha = 0.5f)
    Row(
        modifier = modifier
            .heightIn(min = tokens.spacing.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            AppIconView(icon, contentDescription = null, tint = tint, size = 18.dp)
            Spacer(Modifier.width(tokens.spacing.xxs))
        }
        Text(
            text,
            style = tokens.type.body,
            color = tint,
            fontWeight = if (bold && !tokens.isNotebook) FontWeight.SemiBold else null,
            maxLines = 1,
        )
    }
}

/** Kind and the user's colour for a stored category (#296: unknown ones stay as they are). */
internal fun categoryLook(name: String?, storedColor: Long?, settings: Settings): Pair<CategoryKind, Color?> {
    val kind = CategoryKind.fromName(name)
    val custom = storedColor
        ?: settings.categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.color
    return kind to custom?.let { Color(it) }
}

internal fun NoteLine.categoryLook(settings: Settings) = categoryLook(categoryName, categoryColor, settings)

/** A category as a list row: mark + name, one 56 dp target (sheets, the Other workout picker). */
@Composable
internal fun CategoryChoiceRow(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Start ${category.name}",
    /** A muted second line, e.g. the exercises "Start from last" brings (#305). */
    detail: String? = null,
) {
    val tokens = GymTrackTheme.tokens
    val kind = CategoryKind.fromName(category.name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(kind, label = category.name, size = 14.dp, customColor = Color(category.color))
        Spacer(Modifier.width(tokens.spacing.sm))
        Column(Modifier.weight(1f).padding(vertical = tokens.spacing.xxs)) {
            Text(
                label,
                style = tokens.type.body,
                color = tokens.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = tokens.type.small,
                    color = tokens.colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppIconView(AppIcon.CHEVRON, contentDescription = null, tint = tokens.colors.inkMuted, size = 20.dp)
    }
}

/**
 * The start sheet (#293): a workout still owns the timer. "Finish Pull from Tue first?" with
 * Finish it (primary), Discard timer and Keep both. Each choice says what it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutStartSheet(
    prompt: String,
    onChoice: (OrphanChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(ORPHAN_SHEET_TEST_TAG)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md)
                .padding(bottom = tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            Text(prompt, style = tokens.type.title, color = tokens.colors.ink)
            Text(
                "Only one workout runs at a time.",
                style = tokens.type.small,
                color = tokens.colors.inkMuted,
            )
            Spacer(Modifier.height(tokens.spacing.xs))
            OrphanChoice.entries.filter { it != OrphanChoice.FINISH_IT }.forEach { choice ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onChoice(choice) }
                        .testTag(orphanChoiceTestTag(choice))
                        .padding(vertical = tokens.spacing.xxs),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(choice.label, style = tokens.type.body, color = tokens.colors.ink)
                    Text(choice.hint, style = tokens.type.small, color = tokens.colors.inkMuted)
                }
            }
            Box(Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = OrphanChoice.FINISH_IT.label,
                    onClick = { onChoice(OrphanChoice.FINISH_IT) },
                    icon = AppIcon.CHECK,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(orphanChoiceTestTag(OrphanChoice.FINISH_IT)),
                )
            }
            Text(
                OrphanChoice.FINISH_IT.hint,
                style = tokens.type.small,
                color = tokens.colors.inkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
