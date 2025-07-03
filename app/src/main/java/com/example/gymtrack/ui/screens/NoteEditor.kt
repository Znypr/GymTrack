package com.example.gymtrack.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.combineTextAndTimes
import com.example.gymtrack.util.formatElapsedMinutesSeconds
import com.example.gymtrack.util.formatSecondsToMinutesSeconds
import com.example.gymtrack.util.SmallSecondsVisualTransformation
import com.example.gymtrack.util.parseNoteText
import com.example.gymtrack.util.parseDurationSeconds
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.gymtrack.R
import com.example.gymtrack.util.formatDate
import com.example.gymtrack.util.formatTime
import com.example.gymtrack.util.rememberRelativeTimeVisualTransformation

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    note: NoteLine?,
    settings: Settings,
    onSave: (String, String, Category?, String, Long) -> Unit,
    onCancel: () -> Unit,
) {
    var titleValue by remember { mutableStateOf(TextFieldValue(note?.title ?: "")) }
    var learningsValue by remember { mutableStateOf(TextFieldValue(note?.learnings ?: "")) }
    val parsed = remember(note) { parseNoteText(note?.text ?: "") }

    data class Row(
        val id: Long, val text: MutableState<TextFieldValue>   // ← wrapped in state
    )

    var nextId by remember { mutableStateOf(0L) }

    val lines = remember(parsed.first) {
        mutableStateListOf<Row>().apply {
            if (parsed.first.isEmpty()) add(Row(nextId++, mutableStateOf(TextFieldValue(""))))
            else parsed.first.forEach { add(Row(nextId++, mutableStateOf(TextFieldValue(it)))) }
        }
    }
    val timestamps =
        remember(parsed.second) { mutableStateListOf<String>().apply { addAll(parsed.second) } }
    var selectedCategory by remember { mutableStateOf<Category?>(settings.categories.find { it.name == note?.categoryName }) }

    val initialTimes = remember(note) {
        if (note != null) {
            val list = parsed.second
            val lastIdx = list.indexOfLast { it.isNotBlank() }
            val lastSec = if (lastIdx >= 0) parseDurationSeconds(list[lastIdx]).toLong() else 0L
            val start = note.timestamp
            start to (start + lastSec * 1000)
        } else {
            null to System.currentTimeMillis()
        }
    }
    var startTime by remember { mutableStateOf(initialTimes.first) }
    var lastEnter by remember { mutableStateOf(initialTimes.second) }
    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()

    val lifecycleOwner = LocalLifecycleOwner.current
    var saved by remember { mutableStateOf(false) }

    val saveIfNeeded = {
        if (!saved) {
            val title = titleValue.text.trim()

            lines.forEachIndexed { i, row ->
                if (row.text.value.text.isNotBlank() && timestamps[i].isBlank()) {
                    // write current clock value if user never pressed Enter on that line
                    val nowAbs = formatElapsedMinutesSeconds(
                        startTime ?: noteTimestamp,
                        System.currentTimeMillis(),
                        settings,
                    )
                    timestamps[i] = nowAbs
                }
            }

            val plainContent = lines.joinToString("\n") { it.text.value.text }

            if (note != null || title.isNotEmpty() || plainContent.isNotEmpty()) {
                saved = true

                if (timestamps.size < lines.size) {
                    repeat(lines.size - timestamps.size) { timestamps.add("") }
                } else if (timestamps.size > lines.size) {
                    timestamps.subList(lines.size, timestamps.size).clear()
                }


                val combined = combineTextAndTimes(plainContent, timestamps)

                onSave(
                    titleValue.text,
                    combined,
                    selectedCategory,
                    learningsValue.text,
                    startTime ?: noteTimestamp,
                )
            } else {
                saved = true
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }
    var showLearnings by remember { mutableStateOf(false) }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val scroll = rememberScrollState()
            var autoScrolled by remember { mutableStateOf(false) }
            LaunchedEffect(scroll.maxValue) {
                if (!autoScrolled && scroll.maxValue > 0) {
                    scroll.scrollTo(scroll.maxValue)
                    autoScrolled = true
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(16.dp)
                    .imePadding(),
        ) {
            // Navigation Row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Image(
                    painter = painterResource(id = R.drawable.ic_gymtrack_logo),
                    contentDescription = "GymTrack logo",
                    modifier = Modifier.size(45.dp) // optional size
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatDate(noteTimestamp, settings),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        formatTime(noteTimestamp, settings),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
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

            Spacer(Modifier.height(12.dp))

            // Header
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
                        onValueChange = {
                            titleValue = it
                            saved = false
                        },
                        placeholder = { Text("Title") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.background,
                            unfocusedBorderColor = MaterialTheme.colorScheme.background,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                if (settings.categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded, onExpandedChange = { expanded = !expanded }) {
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
                                focusedBorderColor = MaterialTheme.colorScheme.background,
                                unfocusedBorderColor = MaterialTheme.colorScheme.background,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                cursorColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    selectedCategory = null
                                    expanded = false
                                    saved = false
                                })
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
                                    onClick = {
                                        selectedCategory = cat
                                        expanded = false
                                        saved = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val focusRequesters = remember { mutableStateListOf<FocusRequester>() }
            var pendingFocusId by remember { mutableStateOf<Long?>(null) }

            // Body
            LazyColumn(
                modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 20.dp),
            ) {
                itemsIndexed(
                    lines, key = { _, row -> row.id }) { index, row ->
                    val fr =
                        if (focusRequesters.size > index) focusRequesters[index] else FocusRequester().also {
                            focusRequesters.add(it)
                        }

                    LaunchedEffect(pendingFocusId, row.id) {
                        if (pendingFocusId == row.id) {
                            withFrameNanos { }         // wait one frame -> requester attached
                            fr.requestFocus()
                            pendingFocusId = null
                        }
                    }

                    val isMain =
                        index == 0 || lines.getOrNull(index - 1)?.text?.value?.text?.isBlank() != false

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = row.text.value,
                            onValueChange = { newValue ->
                                saved = false
                                if (newValue.text.endsWith("\n")) {

                                    val content = newValue.text.dropLast(1)

                                    val now = System.currentTimeMillis()
                                    val diffSec = (now - lastEnter) / 1000
                                    if (content.isNotBlank()) lastEnter = now

                                    val rel = formatSecondsToMinutesSeconds(diffSec)
                                    if (startTime == null && content.isNotBlank() && isMain) {
                                        startTime = now
                                    }
                                    val abs = formatElapsedMinutesSeconds(
                                        startTime ?: now, now, settings
                                    )

                                    row.text.value =
                                        if (content.isNotBlank() && isMain)
                                            TextFieldValue("$content ($rel)")
                                        else if (content.isNotBlank() && !isMain)
                                            TextFieldValue("    $content ($rel)")
                                        else TextFieldValue("")

                                    if (content.isNotBlank()) {
                                        if (timestamps.size <= index) timestamps.add(abs)
                                        else timestamps[index] = abs
                                    } else {
                                        if (timestamps.size <= index) timestamps.add("")
                                        else timestamps[index] = ""
                                    }

                                    lines.add(
                                        index + 1, Row(
                                            nextId++, mutableStateOf(TextFieldValue(""))
                                        )
                                    )
                                    timestamps.add(index + 1, "")

                                    if (focusRequesters.size <= index + 1) {
                                        focusRequesters.add(FocusRequester())
                                    } else {
                                        focusRequesters.add(index + 1, FocusRequester())
                                    }

                                    pendingFocusId = nextId - 1
                                } else {
                                    row.text.value = newValue
                                }
                            },
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (isMain) 20.sp else 14.sp,
                                fontWeight = if (isMain) FontWeight.Bold else null,
                            ),
                            visualTransformation = rememberRelativeTimeVisualTransformation( if (isMain) 20.sp else 14.sp),
                            modifier = Modifier
                                .focusRequester(fr),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        )
                        val absText = timestamps.getOrNull(index).orEmpty()
                        val absAnnotated =
                            SmallSecondsVisualTransformation(if (isMain) 20.sp else 14.sp,).filter(
                                AnnotatedString(absText)
                            ).text
                        Text(
                            absAnnotated,
                            fontSize = if (isMain) 20.sp else 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isMain) FontWeight.Bold else null,
                        )
                    }
                }
            }
        }
    }

        FloatingActionButton(
            onClick = { showLearnings = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }

        AnimatedVisibility(
            visible = showLearnings,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxSize()
                    .clickable { showLearnings = false },
                contentAlignment = Alignment.TopCenter
            ) {
                BoxWithConstraints {
                    val offset = maxHeight / 3
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(2.dp, Color.LightGray.copy(alpha = 0.2F)),
                        modifier = Modifier
                            .padding(
                                top = offset,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp
                            )
                            .imePadding()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Notes",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                OutlinedTextField(
                                    value = learningsValue,
                                    onValueChange = {
                                        learningsValue = it
                                        saved = false
                                    },
                                    placeholder = { Text("Learnings") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        cursorColor = MaterialTheme.colorScheme.onSurface,
                                    )
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showLearnings = false },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }

    }
}
