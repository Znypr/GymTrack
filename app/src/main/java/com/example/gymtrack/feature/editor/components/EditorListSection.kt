package com.example.gymtrack.feature.editor.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.ExerciseFlag
import com.example.gymtrack.feature.editor.NoteEditorState
import com.example.gymtrack.core.util.CapitalizeWordsTransformation
import com.example.gymtrack.core.util.SmallSecondsVisualTransformation
import com.example.gymtrack.core.util.rememberRelativeTimeVisualTransformation
import kotlinx.coroutines.launch


// --- COMPONENT: EDITOR LIST SECTION (UPDATED) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorListSection(state: NoteEditorState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val capitalizeTransformation = remember { CapitalizeWordsTransformation() }

    LazyColumn(
        state = state.listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 300.dp)
    ) {
        itemsIndexed(state.lines, key = { _, row -> row.id }) { index, row ->
            val fr = row.focusRequester
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            val isMain = index == 0 || state.lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false

            val fontSize = if (isMain) 22.sp else 14.sp
            val fontWeight = if (isMain) FontWeight.Black else FontWeight.Medium
            val textColor = if (isMain) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

            val visualTransformation = if (isMain) {
                capitalizeTransformation
            } else {
                rememberRelativeTimeVisualTransformation(fontSize)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flag / Tag Column
                Box(
                    modifier = Modifier.width(50.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (row.text.value.text.isNotBlank()) {
                        if (isMain) {
                            ExerciseFlagButton(
                                flag = row.flag.value,
                                relColor = textColor,
                                onToggle = { state.toggleFlag(index) }
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

                // Input Field
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fr)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusChanged { if (it.isFocused) coroutineScope.launch { bringIntoViewRequester.bringIntoView() } }
                    )
                }

                // Timestamp
                val absText = state.timestamps.getOrElse(index) { "" }
                if (absText.isNotBlank()) {
                    val absAnnotated = SmallSecondsVisualTransformation(14.sp).filter(AnnotatedString(absText)).text
                    Text(
                        text = absAnnotated,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}