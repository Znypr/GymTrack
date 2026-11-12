package com.example.gymtrack.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.SketchGeometry
import com.example.gymtrack.core.ui.theme.drawPencilRuns
import com.example.gymtrack.core.ui.theme.highlighter
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.core.ui.theme.sketchSeed
import com.example.gymtrack.core.ui.theme.sketchUnderline
import com.example.gymtrack.core.ui.theme.toPencilRuns

/**
 * Mistake hint (README rule 5/6): "!" + text + Fix / Keep. It never changes data itself.
 * Normal: amber outline. Notebook: red-pen correction (circled "!", handwritten note).
 */
@Composable
fun HintRow(
    text: String,
    onFix: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
    fixLabel: String = "Fix",
    keepLabel: String = "Keep",
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val seed = sketchSeed(text)
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.warn, seed, strokeWidth = 1.5.dp, cornerRadius = 12.dp, wobble = 1.6.dp)
    } else {
        Modifier.border(1.dp, c.warn, RoundedCornerShape(tokens.radii.small))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(outline)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HintBang()
        Spacer(Modifier.width(tokens.spacing.xs))
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(vertical = tokens.spacing.xs),
            style = if (tokens.isNotebook) tokens.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp) else tokens.type.body,
            color = if (tokens.isNotebook) c.warn else c.ink,
        )
        HintAction(fixLabel, c.action, bold = true, seed = seed + 1, onClick = onFix)
        HintAction(keepLabel, c.inkMuted, bold = false, seed = seed + 2, onClick = onKeep)
    }
}

/** The "!" badge: an outlined circle (Normal) or a red-pen circle (Notebook). Decorative. */
@Composable
private fun HintBang() {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val ring = if (tokens.isNotebook) {
        Modifier.drawWithCache {
            val runs = SketchGeometry.ellipse(
                size.width / 2f, size.height / 2f, size.width * 0.44f, size.height * 0.44f,
                seed = 5, pass = 0, wobble = 1.dp.toPx(), step = 2.dp.toPx(), overshoot = 0.2f,
            ).toPencilRuns(1.6.dp.toPx(), 0.95f)
            onDrawBehind { drawPencilRuns(runs, c.warn) }
        }
    } else {
        Modifier.border(1.5.dp, c.warn, CircleShape)
    }
    Box(
        modifier = Modifier.size(24.dp).then(ring).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text("!", color = c.warn, style = tokens.type.body, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HintAction(label: String, color: Color, bold: Boolean, seed: Int, onClick: () -> Unit) {
    val tokens = GymTrackTheme.tokens
    Box(
        modifier = Modifier
            // Short labels ("Fix") must still give a full touch target in both directions.
            .sizeIn(minWidth = tokens.spacing.touchTarget, minHeight = tokens.spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        val underline = if (tokens.isNotebook && bold) Modifier.sketchUnderline(color, seed, 1.3.dp) else Modifier
        Text(
            text = label,
            modifier = underline,
            color = color,
            style = tokens.type.body,
            fontWeight = if (bold && !tokens.isNotebook) FontWeight.SemiBold else null,
        )
    }
}

/**
 * The slim bar above the bottom nav while a workout runs (#293): "● Push · 24:10   Resume ›".
 * The whole bar is one 56 dp target. The caller formats and ticks [elapsed].
 */
@Composable
fun ActiveWorkoutBar(
    category: CategoryKind,
    title: String,
    elapsed: String,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    resumeLabel: String = "Resume",
    customColor: Color? = null,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val skin = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.ink, seed = 41, strokeWidth = 1.5.dp, cornerRadius = 10.dp)
    } else {
        Modifier
            .background(c.surface, RoundedCornerShape(tokens.radii.small))
            .border(1.dp, c.hairline, RoundedCornerShape(tokens.radii.small))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(skin)
            .clickable(role = Role.Button, onClickLabel = "Resume workout", onClick = onResume)
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(category, label = title, size = 12.dp, customColor = customColor)
        Spacer(Modifier.width(tokens.spacing.xs))
        Text(
            text = "$title · ",
            style = tokens.type.body,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(text = elapsed, style = tokens.type.setNumber.copy(fontSize = tokens.type.body.fontSize), color = c.ink)
        Spacer(Modifier.weight(1f))
        Text(resumeLabel, style = tokens.type.body, color = c.action, fontWeight = if (tokens.isNotebook) null else FontWeight.SemiBold)
        AppIconView(AppIcon.CHEVRON, contentDescription = null, tint = c.action, size = 20.dp)
    }
}

/** One bottom-navigation destination. Text label is always shown with the icon. */
@Immutable
data class NavItem(val label: String, val icon: AppIcon)

/**
 * Bottom navigation (DESIGN_LANGUAGE_V2: Today · History · Progress). Pads itself for the
 * system navigation bar.
 * Normal: #0B0B0C bar, 64 dp, active = amber icon + label. Notebook: index tabs hanging below
 * the page edge; the active tab is raised 4 dp, paper-white and highlighted.
 */
@Composable
fun BottomNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    if (tokens.isNotebook) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(c.navBar)
                .drawWithCache {
                    // The notebook page's bottom edge, 4 dp below the bar top.
                    val runs = SketchGeometry.line(
                        0f, 4.dp.toPx(), size.width, 4.dp.toPx(), seed = 77, wobble = 1.dp.toPx(), step = 6.dp.toPx(),
                    ).toPencilRuns(1.6.dp.toPx(), 0.9f)
                    onDrawBehind { drawPencilRuns(runs, c.ink) }
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .padding(horizontal = tokens.spacing.xs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEachIndexed { index, item ->
                NotebookTab(item, selected = index == selectedIndex, seed = index * 19 + 3, onClick = { onSelect(index) })
            }
        }
    } else {
        Column(modifier.fillMaxWidth().background(c.navBar)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .height(64.dp)
                    .selectableGroup(),
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val color = if (selected) c.action else c.inkMuted
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppIconView(item.icon, contentDescription = null, tint = color)
                        Text(
                            item.label,
                            style = tokens.type.small,
                            color = color,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NotebookTab(
    item: NavItem,
    selected: Boolean,
    seed: Int,
    onClick: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(top = if (selected) 0.dp else 4.dp, bottom = 4.dp)
            .indexTab(fill = if (selected) c.surface else Color.Transparent, ink = c.ink, seed = seed)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconView(item.icon, contentDescription = null, tint = c.ink, size = 20.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = item.label,
                modifier = if (selected) Modifier.highlighter(c.active, seed) else Modifier,
                style = tokens.type.title.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = c.ink,
            )
        }
    }
}

/**
 * An index tab hanging from the page edge: a sketched box whose top edge is cut off, so only
 * the sides and rounded bottom are drawn. Built once per size.
 */
private fun Modifier.indexTab(fill: Color, ink: Color, seed: Int): Modifier = clipToBounds().drawWithCache {
    val extra = 24.dp.toPx()
    val sw = 1.5.dp.toPx()
    val outline = SketchGeometry.roundedRect(
        width = size.width, height = size.height + extra, seed = seed, pass = 0,
        cornerRadius = 12.dp.toPx(), wobble = 1.dp.toPx(), step = 3.dp.toPx(), closed = true, inset = sw,
    )
    val fillPath = Path().apply {
        moveTo(outline.xs[0], outline.ys[0] - extra)
        for (i in 1 until outline.size) lineTo(outline.xs[i], outline.ys[i] - extra)
        close()
    }
    val runs = (0..1).flatMap { pass ->
        val s = SketchGeometry.roundedRect(
            width = size.width, height = size.height + extra, seed = seed, pass = pass,
            cornerRadius = 12.dp.toPx(), wobble = 1.dp.toPx(), step = 3.dp.toPx(), inset = sw + 0.6f * pass,
        )
        for (i in 0 until s.size) s.ys[i] -= extra
        s.toPencilRuns(if (pass == 0) sw else sw * 0.7f, if (pass == 0) 0.9f else 0.5f)
    }
    onDrawBehind {
        clipRect {
            if (fill.alpha > 0f) drawPath(fillPath, fill)
            drawPencilRuns(runs, ink)
        }
    }
}
