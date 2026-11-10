package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.gymtrack.feature.editor.NoteEditorState

internal const val SET_ENTRY_KEYPAD_TEST_TAG = "set-entry-keypad"

/** Stable test tag for a keypad key, e.g. `set-entry-key-8`, `set-entry-key-enter`. */
internal fun setEntryKeyTestTag(label: String): String = "set-entry-key-" + when (label) {
    "⌫" -> "backspace"
    "." -> "decimal"
    NEXT_KEY_LABEL -> "enter"
    else -> label.lowercase()
}

/** The commit key: Next = commit this set and open the next row (Enter on an empty row = next exercise). */
internal const val NEXT_KEY_LABEL = "Next ↵"

private fun hasSetMode(text: String): Boolean = text.any { it == 'x' || it == 'X' || it == 's' || it == 'S' }

/** X / S guard: only once, and only after a digit (reps first). */
internal fun canInsertSetMode(value: TextFieldValue): Boolean {
    val beforeCursor = value.text.take(value.selection.start.coerceIn(0, value.text.length))
    return !hasSetMode(value.text) && beforeCursor.any(Char::isDigit)
}

/** `.` guard: only in the weight part, at most once. */
internal fun canInsertDecimal(value: TextFieldValue): Boolean {
    val separatorIndex = value.text.indexOfFirst { it == 'x' || it == 'X' || it == 's' || it == 'S' }
    return separatorIndex >= 0 && !value.text.substring(separatorIndex + 1).contains('.')
}

internal fun insertSetEntryToken(value: TextFieldValue, token: String): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val updated = value.text.replaceRange(start, end, token)
    val cursor = start + token.length
    return TextFieldValue(updated, TextRange(cursor))
}

internal fun canInsertMyoRepPlus(value: TextFieldValue): Boolean {
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val beforeCursor = value.text.take(cursor)
    val afterCursor = value.text.drop(cursor)
    val separatorIndex = value.text.indexOfFirst { it == 'x' || it == 'X' || it == 's' || it == 'S' }

    if (separatorIndex >= 0 && cursor > separatorIndex) return false
    if (beforeCursor.isEmpty() || !beforeCursor.last().isDigit()) return false
    if (afterCursor.firstOrNull() == '+') return false

    val repsPart = if (separatorIndex >= 0) value.text.substring(0, separatorIndex) else value.text
    val candidate = repsPart.take(cursor.coerceAtMost(repsPart.length)) + "+" +
        repsPart.drop(cursor.coerceAtMost(repsPart.length))
    return candidate.matches(Regex("\\d+(?:\\+\\d*)*"))
}

internal fun backspaceSetEntry(value: TextFieldValue): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    if (start != end) {
        val updated = value.text.removeRange(start, end)
        return TextFieldValue(updated, TextRange(start))
    }
    if (start == 0) return value
    val updated = value.text.removeRange(start - 1, start)
    return TextFieldValue(updated, TextRange(start - 1))
}

@Composable
internal fun SetEntryKeypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onRepsSeparator: () -> Unit,
    onPlus: () -> Unit,
    onBackspace: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    secondsEnabled: Boolean = false,
    onSeconds: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SET_ENTRY_KEYPAD_TEST_TAG)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeypadRow {
            KeypadButton("1") { onDigit('1') }
            KeypadButton("2") { onDigit('2') }
            KeypadButton("3") { onDigit('3') }
            KeypadButton("⌫", onClick = onBackspace)
        }
        KeypadRow {
            KeypadButton("4") { onDigit('4') }
            KeypadButton("5") { onDigit('5') }
            KeypadButton("6") { onDigit('6') }
            KeypadButton("X", onClick = onRepsSeparator)
        }
        KeypadRow {
            KeypadButton("7") { onDigit('7') }
            KeypadButton("8") { onDigit('8') }
            KeypadButton("9") { onDigit('9') }
            KeypadButton("+", onClick = onPlus)
        }
        KeypadRow {
            KeypadButton(".", onClick = onDecimal)
            KeypadButton("0") { onDigit('0') }
            KeypadButton("S", enabled = secondsEnabled, onClick = onSeconds)
            KeypadButton(NEXT_KEY_LABEL, onClick = onNext)
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun RowScope.KeypadButton(
    label: String,
    weight: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(weight)
            .heightIn(min = 48.dp)
            .testTag(setEntryKeyTestTag(label)),
    ) {
        Text(text = label)
    }
}


/** The keypad wired to the editor state, with the reps-first guards (§8.1 #3). */
@Composable
internal fun EditorSetKeypad(state: NoteEditorState, modifier: Modifier = Modifier) {
    fun apply(token: String) = state.onKeypadInput(insertSetEntryToken(state.keypadValue, token))
    SetEntryKeypad(
        onDigit = { digit -> apply(digit.toString()) },
        onDecimal = { if (canInsertDecimal(state.keypadValue)) apply(".") },
        onRepsSeparator = { if (canInsertSetMode(state.keypadValue)) apply("x ") },
        onPlus = { if (canInsertMyoRepPlus(state.keypadValue)) apply("+") },
        onSeconds = { if (canInsertSetMode(state.keypadValue)) apply("s ") },
        onBackspace = { state.onKeypadInput(backspaceSetEntry(state.keypadValue)) },
        onNext = { state.onKeypadNext() },
        secondsEnabled = true,
        modifier = modifier,
    )
}
