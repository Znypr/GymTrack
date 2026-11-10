package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.example.gymtrack.core.util.ExerciseIdentityResolver
import com.example.gymtrack.core.util.ExerciseVariantLabel
import com.example.gymtrack.core.util.ExerciseVariantLabelKind
import com.example.gymtrack.core.util.variantLabelSpecs
import com.example.gymtrack.feature.editor.BracketPosition
import com.example.gymtrack.feature.editor.SetRowDisplay
import kotlinx.coroutines.delay

internal const val EDITOR_INPUT_FRAME_TEST_TAG = "editor-input-frame"
internal const val EDITOR_ACTIVE_ROW_TEST_TAG = "editor-active-row"
internal const val EDITOR_GHOST_SLOT_TEST_TAG = "editor-ghost-slot"
internal const val EDITOR_HINT_SLOT_TEST_TAG = "editor-hint-slot"

internal fun editorAddSetTestTag(exerciseIndex: Int): String = "editor-add-set-$exerciseIndex"

internal fun editorUnilateralChipTestTag(exerciseIndex: Int): String = "editor-lr-chip-$exerciseIndex"

/** Left inset that holds the superset bracket. Constant on every row: toggling SS never shifts text. */
internal val BRACKET_INSET = 18.dp

/** Reserved height of the hint line under the active row (#304 hints, §5 keypad hint). */
internal val HINT_SLOT_HEIGHT = 22.dp

/** A muted colour for secondary text (rest times, carried weights, myo totals). */
@Composable
internal fun mutedInk(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

/**
 * Draws the thin superset bracket (EDITOR_V2 §3) in the row's left inset: it starts with a tick at
 * the first exercise of the group and ends with a tick at its last set.
 */
internal fun Modifier.supersetBracket(position: BracketPosition, color: Color): Modifier =
    if (position == BracketPosition.NONE) this else drawBehind {
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

/** Row wrapper: bracket inset on the left (with the `SS` label on the group's first row), content right. */
@Composable
internal fun BracketRow(
    position: BracketPosition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bracketColor = MaterialTheme.colorScheme.outline
    Row(modifier = modifier.fillMaxWidth().supersetBracket(position, bracketColor)) {
        Box(Modifier.width(BRACKET_INSET)) {
            if (position == BracketPosition.TOP || position == BracketPosition.SINGLE) {
                Text(
                    text = "SS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Neutral `L/R` chip next to a unilateral exercise's name. Ink outline, never red (red = Push/errors). */
@Composable
internal fun UnilateralChip(modifier: Modifier = Modifier) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
        modifier = modifier.semantics { contentDescription = "One side at a time" },
    ) {
        Text(
            text = "L/R",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Canonical identity chips under an exercise name (existing identity preview, theme colours only). */
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
    val accent = if (label.kind == ExerciseVariantLabelKind.WARNING) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = Color.Transparent,
        contentColor = accent,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
    ) {
        Text(
            text = if (label.kind == ExerciseVariantLabelKind.WARNING) "! ${label.text}" else label.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
        style = MaterialTheme.typography.labelMedium,
        color = mutedInk(),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** `8 × 80`, carried weight muted, unit only when not the default, myo total `=15` muted. */
@Composable
internal fun setDisplayText(display: SetRowDisplay): AnnotatedString {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = mutedInk()
    return buildAnnotatedString {
        when {
            display.freeText != null -> withStyle(SpanStyle(color = ink)) { append(display.freeText) }
            display.seconds != null -> {
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(display.seconds) }
                withStyle(SpanStyle(color = muted)) { append(" s") }
                if (display.weight != null) {
                    withStyle(SpanStyle(color = muted)) { append(" × ") }
                    withStyle(SpanStyle(color = ink)) { append(display.weight) }
                }
            }
            else -> {
                withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(display.reps.orEmpty()) }
                if (display.weight != null) {
                    withStyle(SpanStyle(color = muted)) { append(" × ") }
                    withStyle(SpanStyle(color = if (display.weightIsCarried) muted else ink)) { append(display.weight) }
                }
            }
        }
        if (display.unit != null) {
            withStyle(SpanStyle(color = if (display.weightIsCarried) muted else ink)) { append(" ${display.unit}") }
        }
        if (display.myoTotal != null) {
            withStyle(SpanStyle(color = muted, fontSize = 12.sp)) { append("  =${display.myoTotal}") }
        }
    }
}

/**
 * A committed (or half-typed) set row: tap edits it on the keypad, swipe left deletes (with Undo),
 * long-press shows its absolute time and "Edit as text".
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
) {
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
    val label = setDisplayText(display)
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Delete", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .heightIn(min = 40.dp)
                    .combinedClickable(
                        onClickLabel = "Edit set",
                        onLongClickLabel = "Set details",
                        onClick = onTap,
                        onLongClick = { menuOpen = true },
                    )
                    .testTag(testTag)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                )
                if (display.rest != null) {
                    Text(
                        text = display.rest,
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedInk(),
                        modifier = Modifier.semantics { contentDescription = "rest ${display.rest}" },
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!absoluteTime.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Logged at $absoluteTime into the workout") },
                        onClick = { menuOpen = false },
                        enabled = false,
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit as text (unit, notes)") },
                    onClick = {
                        menuOpen = false
                        onEditAsText()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete set") },
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
 * list.
 */
@Composable
internal fun ActiveSetRow(
    entry: String,
    hint: String?,
    testTag: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    ghostSlot: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable(onClick = onTap)
                .testTag(testTag)
                .semantics { contentDescription = "Set being typed: ${entry.ifBlank { "empty" }}" }
                .padding(start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = com.example.gymtrack.feature.editor.prettyKeypadEntry(entry),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(EDITOR_ACTIVE_ROW_TEST_TAG),
            )
            BlinkingCaret()
            Spacer(Modifier.weight(1f))
            Box(Modifier.testTag(EDITOR_GHOST_SLOT_TEST_TAG)) { ghostSlot() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HINT_SLOT_HEIGHT)
                .padding(start = 8.dp)
                .testTag(EDITOR_HINT_SLOT_TEST_TAG),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = mutedInk())
            }
        }
    }
}

@Composable
private fun BlinkingCaret() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            visible = !visible
        }
    }
    Text(
        text = "│",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 17.sp,
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
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SeparatorRow(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))
    }
}
