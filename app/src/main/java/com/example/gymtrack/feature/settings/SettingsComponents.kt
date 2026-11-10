package com.example.gymtrack.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.AppCard
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.AppIcon
import com.example.gymtrack.core.ui.components.AppIconView
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.sketchSeed

/*
 * Small building blocks shared by Progress, Settings, About and Help (wave 2). They wrap the
 * core/ui components so every row reads tokens only and works in Normal and Notebook.
 * Kept here instead of core/ui (off-limits this wave); candidates to move there later.
 */

/** Read-only screen title at the top (README rule 2: the top area is never tappable). */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val tokens = GymTrackTheme.tokens
    Column(modifier.fillMaxWidth().padding(top = tokens.spacing.lg, bottom = tokens.spacing.xs)) {
        Text(
            text = title,
            style = tokens.type.display,
            color = tokens.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = tokens.type.small, color = tokens.colors.inkMuted)
        }
    }
}

/** Group label above a card ("PREFERENCES"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val tokens = GymTrackTheme.tokens
    Text(
        text = if (tokens.isNotebook) text else text.uppercase(),
        style = if (tokens.isNotebook) tokens.type.title else tokens.type.label,
        color = tokens.colors.inkMuted,
        modifier = modifier
            .padding(start = tokens.spacing.xxs, top = tokens.spacing.md, bottom = tokens.spacing.xs)
            .semantics { heading() },
    )
}

/** A card holding rows; rows are separated by [AppDivider]. Rows bring their own padding. */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(title)
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            seed = sketchSeed(title),
        ) {
            content()
        }
    }
}

/**
 * One tappable row: optional icon, title, optional subtitle, and a chevron. 56 dp tall, the whole
 * row is the touch target. Text always says what it does (never colour or icon alone).
 */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIconView(icon, contentDescription = null, tint = c.ink, size = 22.dp)
            Spacer(Modifier.width(tokens.spacing.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = tokens.type.body, color = c.ink, fontWeight = if (tokens.isNotebook) null else FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = tokens.type.small, color = c.inkMuted)
            }
        }
        if (showChevron) {
            AppIconView(AppIcon.CHEVRON, contentDescription = null, tint = c.inkMuted, size = 20.dp)
        }
    }
}

/** Plain body text inside a group (explanations, version line). */
@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    val tokens = GymTrackTheme.tokens
    Text(
        text = text,
        style = tokens.type.small,
        color = tokens.colors.inkMuted,
        modifier = modifier.padding(vertical = tokens.spacing.xs),
    )
}

/**
 * Segmented choice ("4W 3M 1Y All", "kg lb"). The selected segment is bold and marked with the
 * `active` role (outline or highlighter) and a check, so it never relies on colour alone.
 * Each segment is at least 48 dp tall.
 */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptions: List<String>? = null,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = tokens.spacing.touchTarget)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics {
                        contentDescriptions?.getOrNull(index)?.let { this.contentDescription = it }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .activeMark(selected, tokens, seed = index * 17 + 5)
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        AppIconView(AppIcon.CHECK, contentDescription = null, tint = c.ink, size = 14.dp)
                        Spacer(Modifier.width(tokens.spacing.xxs))
                    }
                    Text(
                        text = label,
                        style = tokens.type.body,
                        color = if (selected) c.ink else c.inkMuted,
                        fontWeight = if (selected && !tokens.isNotebook) FontWeight.SemiBold else null,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Divider between rows in a [SettingsGroup]. */
@Composable
fun RowDivider(seed: Int = 0) = AppDivider(seed = seed)
