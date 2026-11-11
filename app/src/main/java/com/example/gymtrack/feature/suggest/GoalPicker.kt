package com.example.gymtrack.feature.suggest

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.AppBottomSheet
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchBorder

const val GOAL_CHIP_TAG = "goal-chip"
fun goalOptionTestTag(goal: TrainingGoal): String = "goal-option-${goal.storageValue}"

/** What each choice changes, in one line. */
private fun TrainingGoal.description(): String = when (this) {
    TrainingGoal.NONE -> "Suggestions from your history only"
    TrainingGoal.BUILD_MUSCLE -> "6–15 reps on big lifts, 2–3 min rest"
    TrainingGoal.LOSE_FAT -> "Keep your volume and loads; same rest as building muscle"
    TrainingGoal.GET_STRONGER -> "1–6 reps on main lifts, 3–5 min rest"
}

/**
 * The goal choice (#312): one selectable row per goal, selected = check + mark (never colour
 * alone), the disclaimer underneath. Used in Settings and in the Progress sheet.
 */
@Composable
fun GoalOptions(selected: TrainingGoal, onSelect: (TrainingGoal) -> Unit, modifier: Modifier = Modifier) {
    val tokens = GymTrackTheme.tokens
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.selectableGroup()) {
            TrainingGoal.entries.forEachIndexed { index, goal ->
                val isSelected = goal == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(goal) })
                        .testTag(goalOptionTestTag(goal))
                        .padding(vertical = tokens.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) AppIconView(AppIcon.CHECK, contentDescription = null, tint = tokens.colors.ink, size = 20.dp)
                    }
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Column(Modifier.activeMark(isSelected, tokens, seed = index * 11 + 5).padding(horizontal = tokens.spacing.xxs)) {
                        Text(
                            goal.label,
                            style = tokens.type.body,
                            color = tokens.colors.ink,
                            fontWeight = if (isSelected && !tokens.isNotebook) FontWeight.SemiBold else null,
                        )
                        Text(goal.description(), style = tokens.type.small, color = tokens.colors.inkMuted)
                    }
                }
            }
        }
        Text(
            text = "Guidelines from sports-science reviews (averages). $COACHING_DISCLAIMER",
            style = tokens.type.small,
            color = tokens.colors.inkMuted,
            modifier = Modifier.padding(top = tokens.spacing.xs),
        )
    }
}

/** Header chip on Progress: `Goal: Build muscle` or `Set a goal`. Opens [GoalSheet]. */
@Composable
fun GoalChip(goal: TrainingGoal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val text = if (goal == TrainingGoal.NONE) "Set a goal" else "Goal: ${goal.label}"
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.inkMuted, seed = 41, strokeWidth = 1.2.dp, cornerRadius = 12.dp, wobble = 0.8.dp)
    } else {
        Modifier.border(1.dp, c.inkMuted.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
    }
    Box(
        modifier = modifier
            .heightIn(min = tokens.spacing.touchTarget)
            .clickable(role = Role.Button, onClickLabel = "Choose a training goal", onClick = onClick)
            .testTag(GOAL_CHIP_TAG)
            .semantics { contentDescription = "$text. Optional coaching." },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = if (tokens.isNotebook) tokens.type.small else tokens.type.label,
            fontWeight = FontWeight.SemiBold,
            color = if (goal == TrainingGoal.NONE) c.inkMuted else c.ink,
            modifier = Modifier.then(outline).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Bottom sheet with [GoalOptions]; a choice applies and closes. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GoalSheet(selected: TrainingGoal, onSelect: (TrainingGoal) -> Unit, onDismiss: () -> Unit) {
    val tokens = GymTrackTheme.tokens
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text("Training goal", style = tokens.type.title, color = tokens.colors.ink)
        Text(
            "Optional. Adds rep, rest and warm-up guidelines to the suggestions.",
            style = tokens.type.small,
            color = tokens.colors.inkMuted,
        )
        Spacer(Modifier.width(tokens.spacing.xs))
        GoalOptions(
            selected = selected,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
            modifier = Modifier.padding(bottom = tokens.spacing.md),
        )
    }
}
