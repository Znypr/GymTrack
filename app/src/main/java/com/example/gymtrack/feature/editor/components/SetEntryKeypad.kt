package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.ui.components.AppDivider
import com.example.gymtrack.core.ui.theme.GymTrackColors
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.ui.theme.SketchShape
import com.example.gymtrack.core.ui.theme.pencilHatch
import com.example.gymtrack.core.ui.theme.sketchBorder
import com.example.gymtrack.core.ui.theme.sketchSeed
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

/** One keypad key. Applied by [applyKeypadKey] to the value as it is at tap time (#362). */
internal sealed interface KeypadKey {
    data class Digit(val digit: Char) : KeypadKey
    data object Decimal : KeypadKey
    data object Times : KeypadKey
    data object Seconds : KeypadKey
    data object Plus : KeypadKey
    data object Backspace : KeypadKey
}

/**
 * The keypad grammar in one pure function: [key] applied to [value] with the reps-first guards
 * (§8.1 #3), or null when a guard refuses it. Callers pass the CURRENT value, read at tap time,
 * never one captured when the keypad was composed (#362: a captured row lost a repeated digit).
 */
internal fun applyKeypadKey(value: TextFieldValue, key: KeypadKey): TextFieldValue? = when (key) {
    is KeypadKey.Digit -> insertSetEntryToken(value, key.digit.toString())
    KeypadKey.Decimal -> if (canInsertDecimal(value)) insertSetEntryToken(value, ".") else null
    KeypadKey.Times -> if (canInsertSetMode(value)) insertSetEntryToken(value, "x ") else null
    KeypadKey.Seconds -> if (canInsertSetMode(value)) insertSetEntryToken(value, "s ") else null
    KeypadKey.Plus -> if (canInsertMyoRepPlus(value)) insertSetEntryToken(value, "+") else null
    KeypadKey.Backspace -> backspaceSetEntry(value).takeIf { it != value }
}

/** How a key is drawn: digits on `surface`, operators on `surface2`, Next in the action colour. */
internal enum class KeypadKeyRole { DIGIT, SPECIAL, ACTION }

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
    val tokens = GymTrackTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.colors.bg)
            .testTag(SET_ENTRY_KEYPAD_TEST_TAG),
    ) {
        if (tokens.isNotebook) AppDivider(seed = 41)
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeypadRow {
                KeypadButton("1") { onDigit('1') }
                KeypadButton("2") { onDigit('2') }
                KeypadButton("3") { onDigit('3') }
                KeypadButton("⌫", role = KeypadKeyRole.SPECIAL, spoken = "Delete", onClick = onBackspace)
            }
            KeypadRow {
                KeypadButton("4") { onDigit('4') }
                KeypadButton("5") { onDigit('5') }
                KeypadButton("6") { onDigit('6') }
                KeypadButton("X", role = KeypadKeyRole.SPECIAL, spoken = "Reps times weight", onClick = onRepsSeparator)
            }
            KeypadRow {
                KeypadButton("7") { onDigit('7') }
                KeypadButton("8") { onDigit('8') }
                KeypadButton("9") { onDigit('9') }
                KeypadButton("+", role = KeypadKeyRole.SPECIAL, spoken = "Plus, more reps", onClick = onPlus)
            }
            KeypadRow {
                KeypadButton(".", role = KeypadKeyRole.SPECIAL, spoken = "Decimal point", onClick = onDecimal)
                KeypadButton("0") { onDigit('0') }
                KeypadButton("S", role = KeypadKeyRole.SPECIAL, spoken = "Seconds", enabled = secondsEnabled, onClick = onSeconds)
                KeypadButton(NEXT_KEY_LABEL, role = KeypadKeyRole.ACTION, spoken = "Next set", onClick = onNext)
            }
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

/** Key fill (Normal) or wash (Notebook), text and outline colour for a role, from the tokens. */
internal data class KeypadKeyColors(val container: Color, val content: Color, val outline: Color)

internal fun GymTrackColors.keypadKeyColors(role: KeypadKeyRole, notebook: Boolean): KeypadKeyColors = when (role) {
    // Notebook: no opaque fills, the paper shows through; the pencil outline carries the key.
    KeypadKeyRole.DIGIT -> KeypadKeyColors(if (notebook) Color.Transparent else surface, ink, if (notebook) ink else hairline)
    KeypadKeyRole.SPECIAL -> KeypadKeyColors(if (notebook) Color.Transparent else surface2, ink, if (notebook) inkMuted else hairline)
    KeypadKeyRole.ACTION -> KeypadKeyColors(
        container = if (notebook) actionContainer.copy(alpha = 0.75f) else actionContainer,
        content = onAction,
        outline = action,
    )
}

/**
 * One key, drawn from the tokens (not Material's tonal tint). Normal: crisp rounded key with a
 * hairline, Next filled amber. Notebook: a hand-drawn pencil box, operators lightly hatched,
 * Next in blue pen with a light wash (like PrimaryButton).
 */
@Composable
private fun RowScope.KeypadButton(
    label: String,
    role: KeypadKeyRole = KeypadKeyRole.DIGIT,
    spoken: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = GymTrackTheme.tokens
    val notebook = tokens.isNotebook
    val colors = tokens.colors.keypadKeyColors(role, notebook)
    val seed = sketchSeed("key-$label")
    val skin = if (notebook) {
        val shape = SketchShape(seed, cornerRadius = 10.dp)
        Modifier
            .clip(shape)
            .background(colors.container, shape)
            .then(
                if (role == KeypadKeyRole.SPECIAL) {
                    Modifier.pencilHatch(colors.outline.copy(alpha = 0.22f), seed, shape, spacing = 7.dp)
                } else {
                    Modifier
                },
            )
            .sketchBorder(
                colors.outline,
                seed,
                strokeWidth = if (role == KeypadKeyRole.ACTION) 2.dp else 1.4.dp,
                cornerRadius = 10.dp,
            )
    } else {
        val shape = RoundedCornerShape(tokens.radii.small)
        Modifier
            .clip(shape)
            .background(colors.container, shape)
            .then(if (role == KeypadKeyRole.ACTION) Modifier else Modifier.border(1.dp, colors.outline, shape))
    }
    val style = when {
        role == KeypadKeyRole.ACTION && notebook -> tokens.type.title.copy(fontSize = 22.sp)
        role == KeypadKeyRole.ACTION -> tokens.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        else -> tokens.type.setNumber.copy(fontSize = 22.sp, lineHeight = 26.sp)
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(skin)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(setEntryKeyTestTag(label))
            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = style, color = colors.content)
    }
}

/**
 * The keypad wired to the editor state (§8.1 #3). Every key goes through
 * [NoteEditorState.onKeypadKey], which reads the row the keypad owns NOW: a tap that lands before
 * the next frame (two quick `1`s, or a digit right after Next) still edits the right text (#362).
 */
@Composable
internal fun EditorSetKeypad(state: NoteEditorState, modifier: Modifier = Modifier) {
    SetEntryKeypad(
        onDigit = { digit -> state.onKeypadKey(KeypadKey.Digit(digit)) },
        onDecimal = { state.onKeypadKey(KeypadKey.Decimal) },
        onRepsSeparator = { state.onKeypadKey(KeypadKey.Times) },
        onPlus = { state.onKeypadKey(KeypadKey.Plus) },
        onSeconds = { state.onKeypadKey(KeypadKey.Seconds) },
        onBackspace = { state.onKeypadKey(KeypadKey.Backspace) },
        onNext = { state.onKeypadNext() },
        secondsEnabled = true,
        modifier = modifier,
    )
}
