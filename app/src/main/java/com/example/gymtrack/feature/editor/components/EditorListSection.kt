package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.data.WeightUnit
import com.example.gymtrack.core.ui.components.HintRow
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.notebookPaper
import com.example.gymtrack.core.util.CanonicalExerciseVisualTransformation
import com.example.gymtrack.feature.editor.EditorRowInfo
import com.example.gymtrack.feature.editor.EditorRowKind
import com.example.gymtrack.feature.editor.KeypadMode
import com.example.gymtrack.feature.editor.NoteEditorState
import com.example.gymtrack.feature.editor.NoteRow
import com.example.gymtrack.feature.editor.buildEditorStructure
import com.example.gymtrack.feature.editor.setRowDisplay
import com.example.gymtrack.feature.editor.shortTimeLabel
import com.example.gymtrack.feature.editor.splitSetRow
import com.example.gymtrack.feature.editor.guardrail.GuardrailHint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val EDITOR_FIRST_INPUT_TEST_TAG = "editor-first-input"

/** Row inputs after the first one. Row 0 keeps [EDITOR_FIRST_INPUT_TEST_TAG]. */
internal fun editorRowInputTestTag(index: Int): String =
    if (index == 0) EDITOR_FIRST_INPUT_TEST_TAG else "editor-row-input-$index"

internal fun activeSetAnchorIndex(activeIndex: Int): Int = (activeIndex - 2).coerceAtLeast(0)

internal const val KEYPAD_HINT_NEXT_EXERCISE = "Next on an empty row → next exercise"
internal const val KEYPAD_HINT_EDIT = "Editing this set · Next saves, times stay"
internal const val KEYPAD_HINT_INSERT = "New set · Next on empty closes"

/** The mistake hint (#304) of the set row at [index]. */
internal fun editorGuardHintTestTag(index: Int): String = "editor-guard-hint-$index"

/** Which scroll brings a row fully inside the viewport (NONE = already inside). */
internal enum class RevealMove { NONE, TO_ANCHOR, TO_TOP, BY_DELTA }

/**
 * Where the row stands against the viewport the list has right now: the viewport already ends at
 * the accessory bar, which sits above the keypad or the IME (#366).
 */
internal fun revealMove(itemOffset: Int?, itemSize: Int, viewportStart: Int, viewportEnd: Int): RevealMove = when {
    itemOffset == null -> RevealMove.TO_ANCHOR
    itemOffset < viewportStart -> RevealMove.TO_TOP
    itemOffset + itemSize > viewportEnd -> RevealMove.BY_DELTA
    else -> RevealMove.NONE
}

/**
 * Scrolls only when the row is not fully inside the viewport: a row off screen comes back with two
 * rows of context above it; a row cut by the bottom edge (under the bar) is pushed up just enough.
 * Checks twice, a frame apart, because a jump to the anchor can still leave a tall row cut off.
 */
private suspend fun LazyListState.revealRow(index: Int) {
    if (index < 0) return
    repeat(2) {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val end = info.viewportEndOffset - info.afterContentPadding
        when (revealMove(item?.offset, item?.size ?: 0, info.viewportStartOffset, end)) {
            RevealMove.NONE -> return
            RevealMove.TO_ANCHOR -> scrollToItem(activeSetAnchorIndex(index))
            RevealMove.TO_TOP -> scrollToItem(index)
            RevealMove.BY_DELTA -> scrollBy((item!!.offset + item.size - end).toFloat())
        }
        withFrameNanos { }
    }
}

/**
 * The workout as a compact list (EDITOR_V2 §1–§3, §6): exercise lines typed on the IME, set rows
 * shown as `8 × 80` with one muted rest time, and the keypad's active row. The keypad itself and
 * the accessory bar live in NoteEditor so they stay in the thumb zone.
 */
@Composable
fun EditorListSection(
    state: NoteEditorState,
    defaultUnit: WeightUnit,
    hintsEnabled: Boolean,
    onDeleteSet: (index: Int) -> Unit,
    onEditSetAsText: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onGuardFix: (rowId: Long) -> Unit = { },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Layout from blank-ness and flags only: typing digits never changes it, so set rows are not
    // recomposed per keystroke.
    val structure by remember(state) {
        derivedStateOf {
            buildEditorStructure(
                isBlank = state.lines.map { it.text.value.text.isBlank() },
                flags = state.lines.map { it.flag.value },
            )
        }
    }

    // One input owner at a time: the keypad takes over from the IME.
    LaunchedEffect(state.keypadRowId, state.keypadMode) {
        if (state.keypadRowId == null) return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        delay(240L)
        keyboardController?.hide()
    }

    // #366: the active row (keypad row, else the focused exercise row) stays above the accessory
    // bar + keypad/IME. Re-checked every time the viewport height changes (IME sliding away, the
    // keypad appearing, the bar growing) or a hint opens under it, instead of on fixed delays that
    // lost the race on slow frames. Scrolling by hand changes neither, so it is never fought.
    val revealTargetId = state.keypadRowId ?: state.focusedRowId
    LaunchedEffect(revealTargetId) {
        if (revealTargetId == null) return@LaunchedEffect
        snapshotFlow {
            Triple(
                state.listState.layoutInfo.viewportSize.height,
                state.indexOfRow(revealTargetId),
                state.latestHintRowId to state.guardHints.size,
            )
        }
            .distinctUntilChanged()
            .collect { (_, index, _) ->
                withFrameNanos { }
                state.listState.revealRow(index)
            }
    }

    LaunchedEffect(state.releaseFocusTick) {
        if (state.releaseFocusTick > 0) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val keypadExercise = state.indexOfRow(state.keypadRowId).let { index ->
        if (index >= 0) structure.getOrNull(index)?.exerciseIndex else null
    }

    val tokens = GymTrackTheme.tokens
    // Notebook: rows are written on ruled paper with a margin line (the bracket inset sits in it).
    val paper = if (tokens.isNotebook) {
        Modifier.notebookPaper(paper = tokens.colors.bg, rule = tokens.colors.paperRule, margin = tokens.colors.paperMargin, marginX = 26.dp)
    } else {
        Modifier
    }
    LazyColumn(
        state = state.listState,
        modifier = modifier.fillMaxSize().then(paper),
        contentPadding = PaddingValues(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
    ) {
        itemsIndexed(state.lines, key = { _, row -> row.id }) { index, row ->
            val info = structure.getOrNull(index) ?: return@itemsIndexed
            BracketRow(position = info.bracket) {
                when (info.kind) {
                    EditorRowKind.EXERCISE -> ExerciseItem(
                        state = state,
                        row = row,
                        index = index,
                        info = info,
                        showAddSet = info.isBlockEnd &&
                            index < state.lines.lastIndex &&
                            keypadExercise != info.exerciseIndex,
                    )
                    EditorRowKind.SET -> SetItem(
                        state = state,
                        row = row,
                        index = index,
                        info = info,
                        defaultUnit = defaultUnit,
                        hintsEnabled = hintsEnabled,
                        keypadInThisBlock = keypadExercise == info.exerciseIndex,
                        onDeleteSet = onDeleteSet,
                        onEditSetAsText = onEditSetAsText,
                        onGuardFix = onGuardFix,
                    )
                    EditorRowKind.SEPARATOR -> SeparatorRow()
                }
            }
        }
    }
}

@Composable
private fun ExerciseItem(
    state: NoteEditorState,
    row: NoteRow,
    index: Int,
    info: EditorRowInfo,
    showAddSet: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val text = row.text.value.text
    var isFocused by remember(row.id) { mutableStateOf(false) }
    val isUnilateral = info.exerciseFlag == ExerciseFlag.UNILATERAL
    val shouldAutoFocus = text.isBlank() &&
        ((index == 0 && state.lines.size == 1) || state.pendingExerciseFocusRowId == row.id)

    LaunchedEffect(row.id, shouldAutoFocus) {
        if (!shouldAutoFocus) return@LaunchedEffect
        state.closeKeypad()
        focusManager.clearFocus(force = true)
        delay(40L)
        state.listState.revealRow(state.indexOfRow(row.id))
        runCatching { row.focusRequester.requestFocus() }
        state.consumePendingExerciseFocus(row.id)
        delay(60L)
        keyboardController?.show()
    }

    val visualTransformation = if (isFocused || text.isBlank()) {
        VisualTransformation.None
    } else {
        remember(isUnilateral) { CanonicalExerciseVisualTransformation(isUnilateral) }
    }

    val nameStyle = exerciseNameStyle()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = row.text.value,
                onValueChange = { state.onTextChange(index, it) },
                textStyle = nameStyle,
                cursorBrush = SolidColor(GymTrackTheme.tokens.colors.action),
                visualTransformation = visualTransformation,
                decorationBox = { inner ->
                    NotebookInputFrame {
                        if (text.isEmpty()) {
                            Text(
                                text = if (index == 0) "First exercise" else "Next exercise",
                                style = nameStyle,
                                color = mutedInk(),
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(editorRowInputTestTag(index))
                    .focusRequester(row.focusRequester)
                    .onFocusChanged {
                        if (it.isFocused == isFocused) return@onFocusChanged
                        isFocused = it.isFocused
                        if (it.isFocused) state.onExerciseFocused(row.id) else state.onExerciseFocusLost(row.id)
                    },
            )
            if (isUnilateral && text.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                UnilateralChip(Modifier.testTag(editorUnilateralChipTestTag(index)))
            }
        }
        if (text.isNotBlank()) {
            ExerciseIdentityPreview(rawName = text, isUnilateral = isUnilateral)
            LastSessionLine(summary = null) // #308 hook
        }
        if (state.keypadRowId == row.id && state.keypadMode == KeypadMode.INSERT) {
            ActiveSetRow(
                entry = state.keypadBuffer.text,
                hint = null,
                testTag = "editor-insert-row-$index",
                onTap = {},
            )
        }
        if (showAddSet && text.isNotBlank()) {
            AddSetRow(
                testTag = editorAddSetTestTag(index),
                onClick = { state.addSetToExercise(index) },
            )
        }
    }
}

@Composable
private fun SetItem(
    state: NoteEditorState,
    row: NoteRow,
    index: Int,
    info: EditorRowInfo,
    defaultUnit: WeightUnit,
    hintsEnabled: Boolean,
    keypadInThisBlock: Boolean,
    onDeleteSet: (Int) -> Unit,
    onEditSetAsText: (Int) -> Unit,
    onGuardFix: (Long) -> Unit,
) {
    val text = row.text.value.text
    val isKeypadRow = state.keypadRowId == row.id
    val mode = state.keypadMode
    val inputTag = editorRowInputTestTag(index)
    val ownHint = state.guardHints[row.id]
    val ownHintAtKeypad = ownHint != null && state.hintShownAtKeypad(row.id)
    // The newest hint is drawn in the keypad row's reserved hint line, right under the set it
    // is about, so the keypad row itself never moves (#304).
    val keypadGuard: (@Composable () -> Unit)? = when {
        !isKeypadRow -> null
        mode == KeypadMode.LIVE -> state.lines.getOrNull(index - 1)?.let { above ->
            state.guardHints[above.id]?.takeIf { state.hintShownAtKeypad(above.id) }?.let { hint ->
                { GuardHint(hint, index - 1, state, onGuardFix, above.id) }
            }
        }
        mode == KeypadMode.INSERT && ownHintAtKeypad -> { { GuardHint(ownHint!!, index, state, onGuardFix, row.id) } }
        else -> null
    }

    Column(Modifier.fillMaxWidth()) {
        when {
            isKeypadRow && mode != KeypadMode.INSERT -> {
                val entry = if (mode == KeypadMode.EDIT) state.keypadBuffer.text else splitSetRow(text).entry
                ActiveSetRow(
                    entry = entry,
                    hint = when {
                        !hintsEnabled -> null
                        mode == KeypadMode.EDIT -> KEYPAD_HINT_EDIT
                        entry.isBlank() && index - info.exerciseIndex > 1 -> KEYPAD_HINT_NEXT_EXERCISE
                        else -> null
                    },
                    testTag = inputTag,
                    onTap = {},
                    guard = keypadGuard,
                )
            }
            text.isBlank() -> {
                // The open set row at the end, keypad closed (e.g. a reopened workout): `+ set`.
                AddSetRow(testTag = inputTag, onClick = { state.openKeypad(row.id) })
            }
            else -> {
                // Carried weight needs the exercise's earlier sets: read only this block, above.
                val earlier = (info.exerciseIndex + 1 until index).map { state.lines[it].text.value.text }
                val display = remember(text, earlier, defaultUnit) { setRowDisplay(text, earlier, defaultUnit) }
                val absolute = state.timestamps.getOrElse(index) { "" }.takeIf(String::isNotBlank)?.let(::shortTimeLabel)
                SetRow(
                    display = display,
                    absoluteTime = absolute,
                    testTag = inputTag,
                    onTap = { state.openKeypad(row.id) },
                    onDelete = { onDeleteSet(state.indexOfRow(row.id)) },
                    onEditAsText = { onEditSetAsText(state.indexOfRow(row.id)) },
                    flagged = ownHint != null,
                )
                if (ownHint != null && !ownHintAtKeypad) {
                    GuardHint(ownHint, index, state, onGuardFix, row.id)
                }
            }
        }
        if (isKeypadRow && mode == KeypadMode.INSERT) {
            ActiveSetRow(
                entry = state.keypadBuffer.text,
                hint = if (hintsEnabled) KEYPAD_HINT_INSERT else null,
                testTag = "editor-insert-row-$index",
                onTap = {},
                guard = keypadGuard,
            )
        }
        if (info.isBlockEnd && text.isNotBlank() && !keypadInThisBlock) {
            AddSetRow(
                testTag = editorAddSetTestTag(info.exerciseIndex),
                onClick = { state.addSetToExercise(index) },
            )
        }
    }
}

/**
 * Whether the hint of [rowId] is drawn in the keypad row's hint line (it is the newest hint and
 * the keypad row sits right under that set) instead of under the set itself.
 */
internal fun NoteEditorState.hintShownAtKeypad(rowId: Long): Boolean {
    val keypadId = keypadRowId ?: return false
    if (rowId != latestHintRowId) return false
    return when (keypadMode) {
        KeypadMode.LIVE -> indexOfRow(keypadId) == indexOfRow(rowId) + 1
        KeypadMode.INSERT -> keypadId == rowId
        KeypadMode.EDIT -> false
    }
}

/** "!" + reason + Fix / Keep (#304). Nothing changes without a tap; Keep is remembered. */
@Composable
private fun GuardHint(
    hint: GuardrailHint,
    setIndex: Int,
    state: NoteEditorState,
    onGuardFix: (Long) -> Unit,
    rowId: Long,
) {
    HintRow(
        text = hint.message,
        onFix = { onGuardFix(rowId) },
        onKeep = { state.keepGuardHint(rowId) },
        fixLabel = hint.fixLabel,
        modifier = Modifier.padding(vertical = 2.dp).testTag(editorGuardHintTestTag(setIndex)),
    )
}
