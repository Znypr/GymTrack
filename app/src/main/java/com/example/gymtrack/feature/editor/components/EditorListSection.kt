package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.core.util.CanonicalExerciseVisualTransformation
import com.example.gymtrack.core.util.ExerciseIdentityResolver
import com.example.gymtrack.core.util.ExerciseVariantLabel
import com.example.gymtrack.core.util.ExerciseVariantLabelKind
import com.example.gymtrack.core.util.SmallSecondsVisualTransformation
import com.example.gymtrack.core.util.rememberRelativeTimeVisualTransformation
import com.example.gymtrack.core.util.variantLabelSpecs
import com.example.gymtrack.feature.editor.NoteEditorState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val EDITOR_FIRST_INPUT_TEST_TAG = "editor-first-input"
internal const val EDITOR_INPUT_FRAME_TEST_TAG = "editor-input-frame"

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun EditorListSection(state: NoteEditorState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val notebookRuleColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val notebookPageHeight = maxHeight

        LazyColumn(
            state = state.listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(bottom = 150.dp),
        ) {
            itemsIndexed(state.lines, key = { _, row -> row.id }) { index, row ->
                val fr = row.focusRequester
                val bringIntoViewRequester = remember { BringIntoViewRequester() }
                val isMain = index == 0 || state.lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false
                var isFocused by remember(row.id) { mutableStateOf(false) }

                // Request initial focus once for this row. Do not key this effect to the row text:
                // replacing/restarting the focused field after the first character dismisses the IME.
                LaunchedEffect(row.id) {
                    if (index == 0 && state.lines.size == 1 && row.text.value.text.isBlank()) {
                        delay(150L)
                        fr.requestFocus()
                        keyboardController?.show()
                    }
                }

                val fontSize = if (isMain) 22.sp else 14.sp
                val fontWeight = if (isMain) FontWeight.Black else FontWeight.Medium
                val textColor = if (isMain) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                val rowIsUnilateral = row.flag.value == ExerciseFlag.UNILATERAL

                val visualTransformation = if (isMain) {
                    if (isFocused) {
                        VisualTransformation.None
                    } else {
                        remember(rowIsUnilateral) { CanonicalExerciseVisualTransformation(rowIsUnilateral) }
                    }
                } else {
                    rememberRelativeTimeVisualTransformation(fontSize)
                }

                // The notebook rule is part of the real row. Its vertical position therefore follows
                // the row's measured height: taller exercise rows and shorter set rows align naturally.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.dp)
                            .bringIntoViewRequester(bringIntoViewRequester),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.width(50.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (row.text.value.text.isNotBlank()) {
                                if (isMain) {
                                    ExerciseFlagButton(
                                        flag = row.flag.value,
                                        relColor = textColor,
                                        onToggle = { state.toggleFlag(index) },
                                    )
                                } else {
                                    var p = index - 1
                                    while (p >= 0 && (state.lines.getOrNull(p - 1)?.text?.value?.text?.isNotBlank() == true)) p--
                                    val parentFlag = state.lines.getOrNull(p)?.flag?.value ?: ExerciseFlag.BILATERAL
                                    ExerciseFlagTag(flag = parentFlag, relColor = textColor)
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(
                                    top = if (isMain) 8.dp else 2.dp,
                                    bottom = if (isMain) 6.dp else 2.dp,
                                ),
                        ) {
                            BasicTextField(
                                value = row.text.value,
                                onValueChange = { state.onTextChange(index, it) },
                                textStyle = LocalTextStyle.current.copy(
                                    color = textColor,
                                    fontSize = fontSize,
                                    fontWeight = fontWeight,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                visualTransformation = visualTransformation,
                                decorationBox = { innerTextField ->
                                    NotebookInputFrame(innerTextField = innerTextField)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.testTag(EDITOR_FIRST_INPUT_TEST_TAG) else Modifier)
                                    .focusRequester(fr)
                                    .onFocusChanged {
                                        isFocused = it.isFocused
                                        if (it.isFocused) {
                                            coroutineScope.launch {
                                                bringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                            )

                            if (isMain && row.text.value.text.isNotBlank()) {
                                ExerciseIdentityPreview(
                                    rawName = row.text.value.text,
                                    isUnilateral = rowIsUnilateral,
                                )
                            }
                        }

                        val absText = state.timestamps.getOrElse(index) { "" }
                        if (absText.isNotBlank()) {
                            val absAnnotated = SmallSecondsVisualTransformation(14.sp).filter(AnnotatedString(absText)).text
                            Text(
                                text = absAnnotated,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = notebookRuleColor,
                    )
                }
            }

            // Continue the ruled paper after the final editable row. This is actual LazyColumn
            // content, so it scrolls naturally with the workout instead of needing a synchronized
            // background offset. Real rows above keep their own measured-height boundaries.
            item(key = "notebook-page-filler") {
                NotebookPageFiller(
                    ruleColor = notebookRuleColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(notebookPageHeight),
                )
            }
        }
    }
}

@Composable
internal fun NotebookInputFrame(
    modifier: Modifier = Modifier,
    innerTextField: @Composable () -> Unit = {},
) {
    // Deliberately transparent: the page ruling is the only field boundary. Focus is communicated
    // by the native blinking caret rather than a card, outline, or focused container state.
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

@Composable
private fun NotebookPageFiller(
    ruleColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            val spacing = 40.dp.toPx()
            val strokeWidth = 1.dp.toPx()
            var y = spacing
            while (y < size.height) {
                drawLine(
                    color = ruleColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeWidth,
                )
                y += spacing
            }
        },
    )
}

@Composable
private fun ExerciseIdentityPreview(
    rawName: String,
    isUnilateral: Boolean,
) {
    val labels = remember(rawName, isUnilateral) {
        ExerciseIdentityResolver.resolve(
            rawName = rawName,
            isUnilateral = isUnilateral,
        ).variantLabelSpecs()
            .filterNot { it.kind == ExerciseVariantLabelKind.SIDE }
    }

    if (labels.isNotEmpty()) {
        Row(
            modifier = Modifier.padding(top = 5.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.take(4).forEach { label ->
                InlineVariantChip(label)
            }
        }
    }
}

@Composable
private fun InlineVariantChip(label: ExerciseVariantLabel) {
    val accent = editorVariantAccentColor(label.kind)
    Surface(
        color = Color.Transparent,
        contentColor = accent,
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.82f)),
    ) {
        Text(
            text = label.text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun editorVariantAccentColor(kind: ExerciseVariantLabelKind): Color = when (kind) {
    ExerciseVariantLabelKind.BRAND -> Color(0xFF2E7D32)
    ExerciseVariantLabelKind.ATTACHMENT -> Color(0xFF6A1B9A)
    ExerciseVariantLabelKind.EQUIPMENT -> Color(0xFF1565C0)
    ExerciseVariantLabelKind.SIDE -> Color(0xFFC62828)
    ExerciseVariantLabelKind.WARNING -> MaterialTheme.colorScheme.error
}
