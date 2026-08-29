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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

internal fun insertSetEntryToken(value: TextFieldValue, token: String): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val updated = value.text.replaceRange(start, end, token)
    val cursor = start + token.length
    return TextFieldValue(updated, TextRange(cursor))
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
    onBackspace: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    secondsEnabled: Boolean = false,
    onSeconds: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
            KeypadButton("+") { onDigit('+') }
        }
        KeypadRow {
            KeypadButton(".", onClick = onDecimal)
            KeypadButton("0") { onDigit('0') }
            KeypadButton("S", enabled = secondsEnabled, onClick = onSeconds)
            KeypadButton("Enter", onClick = onNext)
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
            .heightIn(min = 48.dp),
    ) {
        Text(text = label)
    }
}
