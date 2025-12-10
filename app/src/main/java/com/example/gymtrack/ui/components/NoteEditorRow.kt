package com.example.gymtrack.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.util.rememberRelativeTimeVisualTransformation

@Composable
fun NoteEditorRow(
    textValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onNextAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = textValue,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        // IME Action 'Next' triggers the new line logic
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(
            onNext = { onNextAction() }
        ),
        // Apply the visual transformation for (m'ss'')
        visualTransformation = rememberRelativeTimeVisualTransformation(16.sp)
    )

    HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.3f))
}