package com.example.gymtrack.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.combineTextAndTimes
import com.example.gymtrack.util.formatFullDateTime
import com.example.gymtrack.util.formatElapsedMinutesSeconds
import com.example.gymtrack.util.formatSecondsToMinutesSeconds
import com.example.gymtrack.util.parseNoteText
import com.example.gymtrack.util.RelativeTimeVisualTransformation
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment

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
    val lines = remember(parsed.first) {
        mutableStateListOf<TextFieldValue>().apply {
            if (parsed.first.isEmpty()) add(TextFieldValue("")) else parsed.first.forEach { add(TextFieldValue(it)) }
        }
    }
    val timestamps = remember(parsed.second) { mutableStateListOf<String>().apply { addAll(parsed.second) } }
    var selectedCategory by remember { mutableStateOf<Category?>(settings.categories.find { it.name == note?.categoryName }) }
    var lastEnter by remember { mutableStateOf(System.currentTimeMillis()) }
    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()

    val lifecycleOwner = LocalLifecycleOwner.current
    var saved by remember { mutableStateOf(false) }
    val saveIfNeeded = {
        if (!saved) {
            val title = titleValue.text.trim()
            val content = lines.joinToString("\n") { it.text }.trim()
            if (note != null || title.isNotEmpty() || content.isNotEmpty()) {
                saved = true
                val combined = combineTextAndTimes(lines.joinToString("\n") { it.text }, timestamps)
                onSave(titleValue.text, combined, selectedCategory)
            } else {
                saved = true
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }

    BackHandler {
        saveIfNeeded()
        onCancel()
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
                IconButton(onClick = {
                    saveIfNeeded()
                    onCancel()
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = {
                    saveIfNeeded()
                    onCancel()
                }) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = titleValue,
                        onValueChange = { titleValue = it },
                        placeholder = { Text("Title") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                )}
                if (settings.categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            modifier = Modifier
                                .menuAnchor()
                                .width(140.dp),
                            readOnly = true,
                            value = selectedCategory?.name ?: "None",
                            onValueChange = {},
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surface,
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
                                                .size(14.dp)
                                                .background(Color(cat.color.toInt()))
                                        )
                                    },
                                    onClick = { selectedCategory = cat; expanded = false },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatFullDateTime(noteTimestamp, settings),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            val scope = rememberCoroutineScope()
            val focusRequesters = remember { mutableStateListOf<FocusRequester>() }
            var requestFocusIndex by remember { mutableStateOf(-1) }
            LaunchedEffect(requestFocusIndex, lines.size) {
                if (requestFocusIndex in focusRequesters.indices) {
                    focusRequesters[requestFocusIndex].requestFocus()
                    requestFocusIndex = -1
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                itemsIndexed(lines) { index, value ->
                    val fr = if (focusRequesters.size > index) focusRequesters[index] else FocusRequester().also { focusRequesters.add(it) }
                    val isMain = index == 0 || lines.getOrNull(index - 1)?.text?.isBlank() != false
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                if (newValue.text.endsWith("\n")) {
                                    val content = newValue.text.dropLast(1)
                                    val now = System.currentTimeMillis()
                                    if (content.isNotBlank()) {
                                        val diffSec = (now - lastEnter) / 1000
                                        lastEnter = now
                                        val abs = formatElapsedMinutesSeconds(noteTimestamp, now, settings)
                                        if (timestamps.size <= index) timestamps.add(abs) else timestamps[index] = abs
                                        lines[index] = TextFieldValue(content + " (" + formatSecondsToMinutesSeconds(diffSec) + ")")
                                    } else {
                                        lines[index] = TextFieldValue("")
                                        if (timestamps.size <= index) timestamps.add("") else timestamps[index] = ""
                                    }

                                    val indentNext = content.isNotBlank()
                                    val newValueText = if (indentNext) "\t" else ""
                                    lines.add(index + 1, TextFieldValue(newValueText))
                                    if (timestamps.size <= index + 1) {
                                        timestamps.add("")
                                    } else {
                                        timestamps.add(index + 1, "")
                                    }
                                    if (focusRequesters.size <= index + 1) {
                                        focusRequesters.add(FocusRequester())
                                    } else {
                                        focusRequesters.add(index + 1, FocusRequester())
                                    }
                                    requestFocusIndex = index + 1
                                } else {
                                    lines[index] = newValue
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(fr)
                                .defaultMinSize(minHeight = 0.dp)
                                .heightIn(min = 28.dp),
                            textStyle = if (isMain) LocalTextStyle.current.copy(fontSize = 20.sp) else LocalTextStyle.current.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            placeholder = { if (index == 0) Text("Start typing") },
                            visualTransformation = RelativeTimeVisualTransformation()
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            timestamps.getOrNull(index).orEmpty(),
                            modifier = Modifier.width(90.dp),
                            fontSize = if (isMain) 20.sp else 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
