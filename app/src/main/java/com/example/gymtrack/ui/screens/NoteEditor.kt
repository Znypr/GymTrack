package com.example.gymtrack.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.WorkoutVisualTransformation
import com.example.gymtrack.util.combineTextAndTimes
import com.example.gymtrack.util.formatFullDateTime
import com.example.gymtrack.util.formatRoundedTime
import com.example.gymtrack.util.parseNoteText
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import android.app.Activity
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    note: NoteLine?,
    settings: Settings,
    onSave: (String, String, Category?) -> Unit,
    onCancel: () -> Unit,
) {
    var titleValue by remember { mutableStateOf(TextFieldValue(note?.title ?: "")) }
    val parsed = remember(note) { parseNoteText(note?.text ?: "") }
    var textValue by remember { mutableStateOf(TextFieldValue(parsed.first.joinToString("\n"))) }
    var timestamps by remember { mutableStateOf(parsed.second.toMutableList()) }
    var selectedCategory by remember { mutableStateOf<Category?>(settings.categories.find { it.name == note?.categoryName }) }
    var lastEnter by remember { mutableStateOf(System.currentTimeMillis()) }
    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()

    val lifecycleOwner = LocalLifecycleOwner.current
    var saved by remember { mutableStateOf(false) }
    val saveIfNeeded = {
        if (!saved) {
            saved = true
            val combined = combineTextAndTimes(textValue.text, timestamps)
            onSave(titleValue.text, combined, selectedCategory)
        }
    }

    var expanded by remember { mutableStateOf(false) }

    BackHandler { saveIfNeeded() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !settings.darkMode
                isAppearanceLightNavigationBars = !settings.darkMode
            }

        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(16.dp)
                .imePadding(),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { saveIfNeeded() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = { saveIfNeeded() }) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = titleValue,
                onValueChange = { titleValue = it },
                placeholder = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(lineHeight = 18.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                formatFullDateTime(noteTimestamp, settings),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            if (settings.categories.isNotEmpty()) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedCategory?.name ?: "None",
                        onValueChange = {},
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { selectedCategory = null; expanded = false })
                        settings.categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .background(Color(cat.color.toInt()))
                                    )
                                },
                                onClick = { selectedCategory = cat; expanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    val oldText = textValue.text
                    val oldLines = oldText.split('\n')
                    val newLines = newValue.text.split('\n')
                    var updatedValue = newValue
                    if (newValue.text.length > oldText.length && newValue.text.endsWith("\n")) {
                        val currentLineIndex = oldLines.size - 1
                        val now = System.currentTimeMillis()
                        val diffSec = (now - lastEnter) / 1000
                        lastEnter = now
                        val idx = newLines.size - 2
                        if (idx >= 0) {
                            val completedLineContent = oldLines.getOrNull(currentLineIndex).orEmpty().trim()
                            if (completedLineContent.isNotEmpty()) {
                                val time = formatRoundedTime(now, settings)
                                if (timestamps.size <= idx) {
                                    timestamps = (timestamps + List(idx - timestamps.size + 1) { "" }).toMutableList()
                                }
                                timestamps = timestamps.toMutableList().also { it[idx] = time }
                                val rel = " (${diffSec}s)"
                                val lines = newLines.toMutableList()
                                lines[idx] = lines[idx] + rel
                                val joined = lines.joinToString("\n")
                                val sel = TextRange(updatedValue.selection.end + rel.length)
                                updatedValue = updatedValue.copy(text = joined, selection = sel)
                            }
                        }
                    }
                    if (newLines.size < oldLines.size) {
                        timestamps = timestamps.take(newLines.size).toMutableList()
                    }
                    textValue = updatedValue
                },
                visualTransformation = WorkoutVisualTransformation(timestamps),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                placeholder = { Text("Start typing") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}
