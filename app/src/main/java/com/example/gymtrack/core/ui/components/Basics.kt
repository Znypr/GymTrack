package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens
import com.example.gymtrack.core.ui.theme.SketchShape
import com.example.gymtrack.core.ui.theme.highlighter
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.core.ui.theme.sketchSeed
import com.example.gymtrack.core.ui.theme.sketchUnderline

/**
 * Read-only top line (README rule 2: the top area is read-only), e.g.
 * "Mon 10 Nov · Push · 24:10". Parts are joined with " · ", empty parts are dropped.
 * Never clickable.
 */
@Composable
fun InfoLine(
    parts: List<String>,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val tokens = GymTrackTheme.tokens
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(tokens.spacing.xs))
        }
        Text(
            text = parts.filter { it.isNotBlank() }.joinToString(" · "),
            style = tokens.type.small,
            color = tokens.colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The one primary action of a screen (Start, Resume, Next, Finish). Place it in the thumb zone.
 * Normal: filled amber, black text. Notebook: blue-pen sketched outline with a light blue wash.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    enabled: Boolean = true,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val seed = sketchSeed(text)
    val shape = if (tokens.isNotebook) SketchShape(seed, cornerRadius = 18.dp) else RoundedCornerShape(tokens.radii.card)
    val skin = if (tokens.isNotebook) {
        Modifier
            .clip(shape)
            .background(c.actionContainer.copy(alpha = 0.75f), shape)
            .sketchBorder(c.action, seed, strokeWidth = 2.dp, cornerRadius = 18.dp)
    } else {
        Modifier.clip(shape).background(c.actionContainer, shape)
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(skin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIconView(icon, contentDescription = null, tint = c.onAction, size = 20.dp)
            Spacer(Modifier.width(tokens.spacing.xs))
        }
        Text(
            text = text,
            color = c.onAction,
            style = if (tokens.isNotebook) tokens.type.title.copy(fontSize = 24.sp) else tokens.type.body.copy(fontSize = 16.sp),
            fontWeight = if (tokens.isNotebook) null else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A suggestion the user may tap to use (README rule 6: nothing is saved without a tap).
 * Dashed outline, so it reads as "not yours yet". 48 dp touch height around a slimmer pill.
 */
@Composable
fun GhostPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = AppIcon.ADD,
    onClickLabel: String = "Use suggestion",
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val seed = sketchSeed(text)
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.inkMuted, seed, strokeWidth = 1.4.dp, cornerRadius = 16.dp, dashed = true)
    } else {
        Modifier.dashedRoundedBorder(c.inkMuted)
    }
    Box(
        modifier = modifier
            .heightIn(min = tokens.spacing.touchTarget)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .then(outline)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                AppIconView(icon, contentDescription = null, tint = c.inkMuted, size = 16.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = tokens.type.small, color = c.ink)
        }
    }
}

private fun Modifier.dashedRoundedBorder(color: Color): Modifier = drawWithCache {
    val width = 1.2.dp.toPx()
    val outline = RoundedCornerShape(50).createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
    val stroke = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    onDrawWithContent {
        drawContent()
        drawPath(path, color, style = stroke)
    }
}

/**
 * Container card. Normal: surface, radius 14, 1 dp hairline, no shadow. Notebook: no fill (the
 * paper shows through) and a sketched pencil box. [active] = the current item.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    active: Boolean = false,
    seed: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val skin = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.ink, seed, strokeWidth = if (active) 2.4.dp else 1.5.dp, cornerRadius = tokens.radii.card)
    } else {
        val shape = RoundedCornerShape(tokens.radii.card)
        Modifier
            .clip(shape)
            .background(c.surface, shape)
            .border(1.dp, if (active) c.active else c.hairline, shape)
    }
    val click = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(skin)
            .then(click)
            .padding(tokens.spacing.md),
        content = content,
    )
}

/**
 * Marks the current row (`active` role): a 1 dp amber outline in Normal, a highlighter swipe
 * in Notebook. Pair it with another cue (bold text, an icon) so colour is never alone.
 */
fun Modifier.activeMark(active: Boolean, tokens: GymTrackTokens, seed: Int = 0): Modifier = when {
    !active -> this
    tokens.isNotebook -> this.highlighter(tokens.colors.active, seed)
    else -> this.border(1.dp, tokens.colors.active, RoundedCornerShape(tokens.radii.small))
}

/** A full-width divider: hairline in Normal, a pencil line in Notebook. */
@Composable
fun AppDivider(modifier: Modifier = Modifier, seed: Int = 0) {
    val tokens = GymTrackTheme.tokens
    if (tokens.isNotebook) {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = 3.dp)
                .sketchUnderline(tokens.colors.inkMuted, seed, 1.1.dp),
        )
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp)
                .background(tokens.colors.hairline),
        )
    }
}
