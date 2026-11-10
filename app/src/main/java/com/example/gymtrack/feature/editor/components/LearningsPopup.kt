package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.gymtrack.core.ui.components.AppBottomSheet
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.core.util.rememberBulletListTransformation

internal const val EDITOR_NOTES_SHEET_TEST_TAG = "editor-notes-sheet"

/**
 * Notes & learnings for this workout, opened from the ⋯ menu (#297). A bottom sheet, so it sits in
 * the thumb zone. Opening it only moves the cursor to the end: the text is never changed by
 * opening or closing (only by typing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningsPopup(
    showLearnings: Boolean,
    learningsValue: TextFieldValue,
    onDismiss: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
) {
    if (!showLearnings) return
    val tokens = GymTrackTheme.tokens
    val c = tokens.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val end = TextRange(learningsValue.text.length)
        if (learningsValue.selection != end) onValueChange(learningsValue.copy(selection = end))
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(EDITOR_NOTES_SHEET_TEST_TAG)) {
        Text("Notes & learnings", style = tokens.type.title, color = c.ink)
        Spacer(Modifier.height(tokens.spacing.xs))
        BasicTextField(
            value = learningsValue,
            onValueChange = onValueChange,
            textStyle = tokens.type.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.action),
            visualTransformation = rememberBulletListTransformation(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs)) {
                    if (learningsValue.text.isEmpty()) {
                        Text("What to remember next time", style = tokens.type.body, color = c.inkMuted)
                    }
                    inner()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .focusRequester(focusRequester),
        )
        Spacer(Modifier.height(tokens.spacing.md).imePadding())
    }
}
