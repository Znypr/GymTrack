package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.gymtrack.feature.suggest.SuggestionLine
import com.example.gymtrack.feature.suggest.SuggestionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.components.activeMark
import com.example.gymtrack.core.ui.theme.GymTrackColors
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.GymTrackTokens
import com.example.gymtrack.core.ui.theme.SketchGeometry
import com.example.gymtrack.core.ui.theme.drawPencilRuns
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.core.ui.theme.sketchSeed
import com.example.gymtrack.core.ui.theme.toPencilRuns
import com.example.gymtrack.core.util.ExerciseIdentityResolver
import com.example.gymtrack.core.util.ExerciseVariantLabel
import com.example.gymtrack.core.util.ExerciseVariantLabelKind
import com.example.gymtrack.core.util.variantLabelSpecs
import com.example.gymtrack.feature.editor.BracketPosition
import com.example.gymtrack.feature.editor.SetRowDisplay
import com.example.gymtrack.feature.editor.prettyKeypadEntry
import kotlinx.coroutines.delay

internal const val EDITOR_INPUT_FRAME_TEST_TAG = "editor-input-frame"
internal const val EDITOR_ACTIVE_ROW_TEST_TAG = "editor-active-row"
internal const val EDITOR_GHOST_SLOT_TEST_TAG = "editor-ghost-slot"
internal const val EDITOR_HINT_SLOT_TEST_TAG = "editor-hint-slot"
internal const val EDITOR_GHOST_PILL_TEST_TAG = "editor-ghost-pill"

internal fun editorAddSetTestTag(exerciseIndex: Int): String = "editor-add-set-$exerciseIndex"

internal fun editorUnilateralChipTestTag(exerciseIndex: Int): String = "editor-lr-chip-$exerciseIndex"

/** Left inset that holds the superset bracket. Constant on every row: toggling SS never shifts text. */
internal val BRACKET_INSET = 18.dp

/** Reserved height of the hint line under the active row (#304 hints, §5 keypad hint). */
internal val HINT_SLOT_HEIGHT = 22.dp

/** Notebook ditto mark for a carried weight (EDITOR_V2 §1): a handwritten `"` under the earlier weight. */
internal const val DITTO_MARK = "\""

/** Secondary text (rest times, carried weights, myo totals): the `inkMuted` token. */
@Composable
internal fun mutedInk(): Color = GymTrackTheme.tokens.colors.inkMuted

/** Exercise names: Caveat in Notebook (handwritten heading), crisp heavy sans in Normal. */
@Composable
internal fun exerciseNameStyle(tokens: GymTrackTokens = GymTrackTheme.tokens) =
    if (tokens.isNotebook) {
        tokens.type.title.copy(fontSize = 28.sp, lineHeight = 32.sp, color = tokens.colors.ink)
    } else {
        tokens.type.title.copy(fontSize = 22.sp, fontWeight = FontWeight.Black, color = tokens.colors.ink)
    }

/** Set rows: `setNumber` (Patrick Hand 18 sp in Notebook, tabular figures; never below 17 sp). */
@Composable
internal fun setRowStyle(tokens: GymTrackTokens = GymTrackTheme.tokens) =
    tokens.type.setNumber.copy(fontSize = maxOf(tokens.type.setNumber.fontSize.value, 17f).sp)

/**
 * The weight part of a set row as shown: the weight itself, or the ditto mark when it is carried
 * from an earlier set and the Notebook skin is on. Display only; the stored text is untouched.
 */
internal fun shownWeight(display: SetRowDisplay, notebook: Boolean): String? = when {
    display.weight == null -> null
    display.weightIsCarried && notebook -> DITTO_MARK
    else -> display.weight
}

/**
 * Draws the thin superset bracket (EDITOR_V2 §3) in the row's left inset: it starts with a tick at
 * the first exercise of the group and ends with a tick at its last set. Notebook: a pencil line.
 */
internal fun Modifier.supersetBracket(position: BracketPosition, color: Color, notebook: Boolean): Modifier =
    if (position == BracketPosition.NONE) {
        this
    } else if (notebook) {
        drawWithCache {
            val x = 3.dp.toPx()
            val tick = 7.dp.toPx()
            val mid = size.height / 2f
            val hasTop = position == BracketPosition.TOP || position == BracketPosition.SINGLE
            val hasBottom = position == BracketPosition.BOTTOM || position == BracketPosition.SINGLE
            val top = if (hasTop) mid else 0f
            val bottom = if (hasBottom) mid else size.height
            val unit = 1.dp.toPx()
            val seed = position.ordinal * 17 + 3
            val runs = buildList {
                addAll(SketchGeometry.line(x, top, x, bottom, seed, 0.8f * unit, 4f * unit).toPencilRuns(1.6f * unit, 0.9f))
                if (hasTop) addAll(SketchGeometry.line(x, top, x + tick, top, seed + 1, 0.5f * unit, 2f * unit).toPencilRuns(1.6f * unit, 0.9f))
                if (hasBottom) addAll(SketchGeometry.line(x, bottom, x + tick, bottom, seed + 2, 0.5f * unit, 2f * unit).toPencilRuns(1.6f * unit, 0.9f))
            }
            onDrawBehind { drawPencilRuns(runs, color) }
        }
    } else {
        drawBehind {
            val x = 3.dp.toPx()
            val stroke = 1.5.dp.toPx()
            val tick = 7.dp.toPx()
            val mid = size.height / 2f
            val top = if (position == BracketPosition.TOP || position == BracketPosition.SINGLE) mid else 0f
            val bottom = if (position == BracketPosition.BOTTOM || position == BracketPosition.SINGLE) mid else size.height
            drawLine(color, Offset(x, top), Offset(x, bottom), stroke)
            if (position == BracketPosition.TOP || position == BracketPosition.SINGLE) {
                drawLine(color, Offset(x, top), Offset(x + tick, top), stroke)
            }
            if (position == BracketPosition.BOTTOM || position == BracketPosition.SINGLE) {
                drawLine(color, Offset(x, bottom), Offset(x + tick, bottom), stroke)
            }
        }
    }

/** Row wrapper: bracket inset on the left (with the `SS` label on the group's first row), content right. */
@Composable
internal fun BracketRow(
    position: BracketPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    Row(modifier = modifier.fillMaxWidth().supersetBracket(position, tokens.colors.inkMuted, tokens.isNotebook)) {
        Box(Modifier.width(BRACKET_INSET)) {
            if (position == BracketPosition.TOP || position == BracketPosition.SINGLE) {
                Text(
                    text = "SS",
                    style = tokens.type.label.copy(fontSize = if (tokens.isNotebook) 11.sp else 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = tokens.colors.inkMuted,
                    modifier = Modifier
                        .padding(start = 5.dp, top = 36.dp)
                        .semantics { contentDescription = "Superset" },
                )
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun NotebookInputFrame(
    modifier: Modifier = Modifier,
    innerTextField: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(EDITOR_INPUT_FRAME_TEST_TAG)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        innerTextField()
    }
}

/** A small outlined chip: crisp hairline pill in Normal, a sketched pencil pill in Notebook. */
@Composable
private fun EditorChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = GymTrackTheme.tokens
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(color.copy(alpha = 0.8f), sketchSeed(text), strokeWidth = 1.2.dp, cornerRadius = 10.dp, wobble = 0.8.dp)
    } else {
        Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
    }
    Text(
        text = text,
        style = if (tokens.isNotebook) tokens.type.small else tokens.type.label,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .then(outline)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Neutral `L/R` chip next to a unilateral exercise's name. Ink outline, never red (red = Push/errors). */
@Composable
internal fun UnilateralChip(modifier: Modifier = Modifier) {
    EditorChip(
        text = "L/R",
        color = GymTrackTheme.tokens.colors.ink,
        modifier = modifier.semantics { contentDescription = "One side at a time" },
    )
}

/** Canonical identity chips under an exercise name (existing identity preview). */
@Composable
internal fun ExerciseIdentityPreview(rawName: String, isUnilateral: Boolean) {
    val labels = remember(rawName, isUnilateral) {
        ExerciseIdentityResolver.resolve(rawName = rawName, isUnilateral = isUnilateral)
            .variantLabelSpecs()
            .filterNot { it.kind == ExerciseVariantLabelKind.SIDE }
    }
    if (labels.isEmpty()) return
    Row(
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.take(4).forEach { label -> InlineVariantChip(label) }
    }
}

@Composable
private fun InlineVariantChip(label: ExerciseVariantLabel) {
    val c = GymTrackTheme.tokens.colors
    val warning = label.kind == ExerciseVariantLabelKind.WARNING
    // A warning is never colour alone: it also carries "!".
    EditorChip(
        text = if (warning) "! ${label.text}" else label.text,
        color = if (warning) c.warn else c.inkMuted,
    )
}

/**
 * Hook for #308: a muted "Last: 8 × 80 · 8 × 80" line under the exercise name. Nothing provides
 * it yet (it needs a history query off the Enter path), so it draws nothing for null.
 */
@Composable
internal fun LastSessionLine(summary: String?) {
    if (summary.isNullOrBlank()) return
    Text(
        text = "Last: $summary",
        style = GymTrackTheme.tokens.type.small,
        color = mutedInk(),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * `8 × 80`, carried weight muted (Notebook: the ditto mark), unit only when not the default, myo
 * total `=15` muted.
 */
internal fun setDisplayText(display: SetRowDisplay, colors: GymTrackColors, notebook: Boolean): AnnotatedString {
    val ink = colors.ink
    val muted = colors.inkMuted
    val weight = shownWeight(display, notebook)
    return buildAnnotatedString {
        when {
            display.freeText != null -> withStyle(SpanStyle(color = ink)) { append(display.freeText) }
            display.seconds != null -> {
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(display.seconds) }
                withStyle(SpanStyle(color = muted)) { append(" s") }
                if (weight != null) {
                    withStyle(SpanStyle(color = muted)) { append(" × ") }
                    withStyle(SpanStyle(color = if (display.weightIsCarried) muted else ink)) { append(weight) }
                }
            }
            else -> {
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(display.reps.orEmpty()) }
                if (weight != null) {
                    withStyle(SpanStyle(color = muted)) { append(" × ") }
                    withStyle(SpanStyle(color = if (display.weightIsCarried) muted else ink)) { append(weight) }
                }
            }
        }
        // The ditto stands for weight and unit together.
        if (display.unit != null && weight != DITTO_MARK) {
            withStyle(SpanStyle(color = if (display.weightIsCarried) muted else ink)) { append(" ${display.unit}") }
        }
        if (display.myoTotal != null) {
            withStyle(SpanStyle(color = muted, fontSize = 13.sp)) { append("  =${display.myoTotal}") }
        }
    }
}

/**
 * A committed (or half-typed) set row: tap edits it on the keypad, swipe left deletes (with Undo),
 * long-press shows its absolute time (and the carried weight behind a ditto) and "Edit as text".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SetRow(
    display: SetRowDisplay,
    absoluteTime: String?,
    testTag: String,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onEditAsText: () -> Unit,
    modifier: Modifier = Modifier,
    /** An open mistake hint (#304): amber outline plus a "!" so colour is never alone. */
    flagged: Boolean = false,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        },
    )
    var menuOpen by remember { mutableStateOf(false) }
    val label = setDisplayText(display, c, tokens.isNotebook)
    val ditto = display.weightIsCarried && tokens.isNotebook && display.weight != null
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            // Only while swiping, so a see-through Notebook row never shows it.
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(c.surface2)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = c.ink)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete", color = c.ink, style = tokens.type.body)
                }
            }
        },
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Notebook: no fill, the ruled paper shows through.
                    .background(if (tokens.isNotebook) Color.Transparent else c.bg)
                    .then(
                        when {
                            !flagged -> Modifier
                            tokens.isNotebook -> Modifier.sketchBorder(c.warn, seed = 83, strokeWidth = 1.5.dp, cornerRadius = 8.dp)
                            else -> Modifier.border(1.dp, c.warn, RoundedCornerShape(tokens.radii.small))
                        },
                    )
                    .heightIn(min = 40.dp)
                    .combinedClickable(
                        onClickLabel = "Edit set",
                        onLongClickLabel = "Set details",
                        onClick = onTap,
                        onLongClick = { menuOpen = true },
                    )
                    .testTag(testTag)
                    .then(
                        if (ditto) {
                            Modifier.semantics { contentDescription = "same weight as before, ${display.weight}" }
                        } else {
                            Modifier
                        },
                    )
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = setRowStyle(tokens),
                    modifier = Modifier.weight(1f),
                )
                if (flagged) {
                    Text(
                        text = "!",
                        style = tokens.type.body,
                        fontWeight = FontWeight.Bold,
                        color = c.warn,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .semantics { contentDescription = "Possible mistake" },
                    )
                }
                if (display.rest != null) {
                    Text(
                        text = display.rest,
                        style = tokens.type.small,
                        color = mutedInk(),
                        modifier = Modifier.semantics { contentDescription = "rest ${display.rest}" },
                    )
                }
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = if (tokens.isNotebook) c.bg else c.surface,
            ) {
                if (ditto) {
                    DropdownMenuItem(
                        text = { Text("Same weight as before: ${display.weight}${display.unit?.let { " $it" }.orEmpty()}", style = tokens.type.body, color = c.inkMuted) },
                        onClick = { menuOpen = false },
                        enabled = false,
                    )
                }
                if (!absoluteTime.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Logged at $absoluteTime into the workout", style = tokens.type.body, color = c.inkMuted) },
                        onClick = { menuOpen = false },
                        enabled = false,
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit as text (unit, notes)", style = tokens.type.body, color = c.ink) },
                    onClick = {
                        menuOpen = false
                        onEditAsText()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete set", style = tokens.type.body, color = c.ink) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * The row the keypad is typing into: the entry in display grammar with a caret, an inline slot
 * for the #308 ghost pill, and a reserved hint line (#304, §5) so filling either never shifts the
 * list. Marked as the active row: an amber outline (Normal) or a highlighter swipe (Notebook),
 * plus bold text so colour is never alone.
 */
@Composable
internal fun ActiveSetRow(
    entry: String,
    hint: String?,
    testTag: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    ghostSlot: @Composable () -> Unit = {},
    /** A mistake hint (#304) about the set right above: drawn in the hint line, which then grows. */
    guard: (@Composable () -> Unit)? = null,
) {
    val tokens = GymTrackTheme.tokens
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .activeMark(active = true, tokens = tokens, seed = 7)
                .clickable(onClick = onTap)
                .testTag(testTag)
                .semantics { contentDescription = "Set being typed: ${entry.ifBlank { "empty" }}" }
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = prettyKeypadEntry(entry),
                style = setRowStyle(tokens),
                fontWeight = FontWeight.Bold,
                color = tokens.colors.ink,
                modifier = Modifier.testTag(EDITOR_ACTIVE_ROW_TEST_TAG),
            )
            BlinkingCaret()
            Spacer(Modifier.weight(1f))
            Box(Modifier.testTag(EDITOR_GHOST_SLOT_TEST_TAG)) { ghostSlot() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HINT_SLOT_HEIGHT)
                .padding(start = 8.dp)
                .testTag(EDITOR_HINT_SLOT_TEST_TAG),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (guard != null) {
                guard()
            } else if (hint != null) {
                Text(hint, style = tokens.type.label, color = mutedInk())
            }
        }
    }
}

/**
 * The #308 history pill in the active row's ghost slot: grey, dashed, labelled "history" so it is
 * never mistaken for a logged set or for advice. Tap fills the row (Next still commits it);
 * long-press shows the "why", never inline. Sized inside the row's 40 dp, so no layout shift.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GhostPill(
    label: String,
    why: List<SuggestionLine>,
    onUse: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    var whyOpen by remember { mutableStateOf(false) }
    val outline = if (tokens.isNotebook) {
        Modifier.sketchBorder(c.inkMuted, seed = 308, strokeWidth = 1.2.dp, cornerRadius = 12.dp, wobble = 0.8.dp, dashed = true)
    } else {
        Modifier.drawBehind {
            val stroke = 1.dp.toPx()
            drawRoundRect(
                color = c.inkMuted.copy(alpha = 0.7f),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
            )
        }
    }
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .combinedClickable(
                    onClickLabel = "Fill this set",
                    onLongClickLabel = "Why this suggestion",
                    onClick = onUse,
                    onLongClick = { whyOpen = true },
                )
                .testTag(EDITOR_GHOST_PILL_TEST_TAG)
                .semantics { contentDescription = "Suggestion from your history: $label. Tap to fill, long-press for why." },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = outline.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("history", style = tokens.type.label, color = c.inkMuted)
                Spacer(Modifier.width(6.dp))
                Text(label, style = tokens.type.body, fontWeight = FontWeight.SemiBold, color = c.inkMuted)
            }
        }
        DropdownMenu(
            expanded = whyOpen,
            onDismissRequest = { whyOpen = false },
            containerColor = if (tokens.isNotebook) c.bg else c.surface,
        ) {
            val history = why.filter { it.source == SuggestionSource.HISTORY }
            val policy = why.filter { it.source == SuggestionSource.POLICY }
            WhyHeader("From your history")
            history.forEach { WhyLine(it.text) }
            if (policy.isNotEmpty()) {
                WhyHeader("Your goal (guideline)")
                policy.forEach { WhyLine(it.text) }
            }
        }
    }
}

@Composable
private fun WhyHeader(text: String) {
    val tokens = GymTrackTheme.tokens
    Text(
        text = text,
        style = tokens.type.label,
        fontWeight = FontWeight.Bold,
        color = tokens.colors.ink,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun WhyLine(text: String) {
    val tokens = GymTrackTheme.tokens
    Text(
        text = text,
        style = tokens.type.small,
        color = tokens.colors.inkMuted,
        modifier = Modifier
            .widthIn(max = 300.dp)
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun BlinkingCaret() {
    val tokens = GymTrackTheme.tokens
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            visible = !visible
        }
    }
    Text(
        text = "│",
        // The pen: amber in Normal, blue pen in Notebook.
        color = tokens.colors.action,
        style = setRowStyle(tokens),
        modifier = Modifier.alpha(if (visible) 1f else 0f),
    )
}

/** `+ set` at the end of an exercise block (#330): opens the keypad on a new set. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AddSetRow(
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = GymTrackTheme.tokens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .combinedClickable(onClickLabel = "Add a set", onClick = onClick)
            .testTag(testTag)
            .padding(start = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "+ set",
            style = if (tokens.isNotebook) tokens.type.body else tokens.type.small,
            fontWeight = FontWeight.Medium,
            color = tokens.colors.inkMuted,
        )
    }
}

@Composable
internal fun SeparatorRow(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        AppDivider(seed = 19)
        Spacer(Modifier.height(4.dp))
    }
}
