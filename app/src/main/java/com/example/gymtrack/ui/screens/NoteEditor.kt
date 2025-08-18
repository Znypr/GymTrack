package com.example.gymtrack.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.combineTextAndTimes
import com.example.gymtrack.util.formatElapsedMinutesSeconds
import com.example.gymtrack.util.formatSecondsToMinutesSeconds
import com.example.gymtrack.util.SmallSecondsVisualTransformation
import com.example.gymtrack.util.parseNoteText
import com.example.gymtrack.util.parseDurationSeconds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import com.example.gymtrack.R
import com.example.gymtrack.util.formatDate
import com.example.gymtrack.util.formatTime
import com.example.gymtrack.util.rememberRelativeTimeVisualTransformation
import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Menu
import com.example.gymtrack.ui.components.LearningsPopup
import com.example.gymtrack.ui.components.ExerciseFlagButton
import com.example.gymtrack.ui.components.ExerciseFlagTag
import com.example.gymtrack.data.ExerciseFlag
import com.example.gymtrack.ui.components.NoteTimer
import com.example.gymtrack.timer.NoteTimerService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GymTrackTopBar(onEdit: () -> Unit) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        navigationIcon = {
            Image(
                painter = painterResource(id = R.drawable.ic_gymtrack_logo),
                contentDescription = "GymTrack logo",
                modifier = Modifier.size(45.dp)
            )
        },
        title = { Text("GymTrack", fontSize = 24.sp) },
        actions = {
            IconButton(onClick = onEdit, modifier = Modifier.padding(end = 10.dp)) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditor(
    note: NoteLine?,
    settings: Settings,
    isLastNote: Boolean,
    onSave: (String, String, Category?, String, Long) -> Unit,
    onCancel: () -> Unit,
) {
    var learningsValue by remember { mutableStateOf(TextFieldValue(note?.learnings ?: "")) }
    val parsed = remember(note) { parseNoteText(note?.text ?: "") }

    data class NoteRow(
        val id: Long,
        val text: MutableState<TextFieldValue>,   // ← wrapped in state
        val flag: MutableState<ExerciseFlag> = mutableStateOf(ExerciseFlag.BILATERAL),
    )

    var nextId by remember { mutableStateOf(0L) }

    val lines = remember(parsed.first) {
        mutableStateListOf<NoteRow>().apply {
            if (parsed.first.isEmpty()) add(NoteRow(nextId++, mutableStateOf(TextFieldValue(""))))
            else parsed.first.forEachIndexed { idx, txt ->
                add(
                    NoteRow(
                        nextId++,
                        mutableStateOf(TextFieldValue(txt)),
                        mutableStateOf(parsed.third.getOrNull(idx) ?: ExerciseFlag.BILATERAL)
                    )
                )
            }
        }
    }
    val timestamps =
        remember(parsed.second) { mutableStateListOf<String>().apply { addAll(parsed.second) } }
    val flags =
        remember(parsed.third) { mutableStateListOf<ExerciseFlag>().apply { addAll(parsed.third) } }
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
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    var saved by remember { mutableStateOf(false) }

    val saveIfNeeded = {
        if (!saved) {
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
                if (flags.size <= i) flags.add(row.flag.value) else flags[i] = row.flag.value
            }

            val plainContent = lines.joinToString("\n") { it.text.value.text }

            if (note != null || plainContent.isNotEmpty()) {
                saved = true

                if (timestamps.size < lines.size) {
                    repeat(lines.size - timestamps.size) { timestamps.add("") }
                } else if (timestamps.size > lines.size) {
                    timestamps.subList(lines.size, timestamps.size).clear()
                }


                if (flags.size < lines.size) {
                    for (i in flags.size until lines.size) {
                        flags.add(lines[i].flag.value)
                    }
                } else if (flags.size > lines.size) {
                    flags.subList(lines.size, flags.size).clear()
                }

                val combined = combineTextAndTimes(plainContent, timestamps, flags)

                onSave(
                    "",
                    combined,
                    selectedCategory,
                    learningsValue.text,
                    startTime ?: noteTimestamp,
                )
                if (isLastNote) {
                    context.startService(Intent(context, NoteTimerService::class.java).apply {
                        action = NoteTimerService.ACTION_STOP
                    })
                }
            } else {
                saved = true
                if (isLastNote) {
                    context.startService(Intent(context, NoteTimerService::class.java).apply {
                        action = NoteTimerService.ACTION_STOP
                    })
                }
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }
    var showLearnings by remember { mutableStateOf(false) }
    var pendingFocusId by remember { mutableStateOf<Long?>(null) }

    BackHandler {
        saveIfNeeded()
        onCancel()
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowInsetsControllerCompat(window, view)
        val light = !settings.darkMode
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { GymTrackTopBar(onEdit = { showLearnings = true }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                val scroll = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(horizontal = 12.dp, vertical = 15.dp),
                ) {
                    // Navigation Row
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatDate(noteTimestamp, settings),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            formatTime(noteTimestamp, settings),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (settings.categories.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = expanded, onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                readOnly = true,
                                value = selectedCategory?.name ?: "None",
                                onValueChange = {},
                                label = { Text("Category") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    cursorColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
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
                                                    .size(12.dp)
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

                    Spacer(Modifier.height(8.dp))
                    if (isLastNote) {
                        NoteTimer(noteTimestamp = noteTimestamp, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                    }
                    Divider()
                    Spacer(Modifier.height(8.dp))

                    val focusRequesters = remember { mutableStateListOf<FocusRequester>() }
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()

                    LaunchedEffect(note?.timestamp) {
                        if (note != null && lines.isNotEmpty()) {
                            withFrameNanos { }
                            listState.scrollToItem(lines.lastIndex)
                        }
                    }

                    // Body
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .imePadding(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 20.dp),
                        ) {
                            itemsIndexed(
                                lines, key = { _, row -> row.id }) { index, row ->
                            val fr =
                                if (focusRequesters.size > index) focusRequesters[index] else FocusRequester().also {
                                    focusRequesters.add(it)
                                }
                            val bringIntoViewRequester = remember { BringIntoViewRequester() }

                            var rowFocused by remember { mutableStateOf(false) }

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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (rowFocused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val relColor = MaterialTheme.colorScheme.onSurface
                                        .copy(alpha = if (isSystemInDarkTheme()) 0.75f else 0.45f)

                                    if (row.text.value.text.isNotBlank()) {
                                        Box(
                                            Modifier
                                                .width(30.dp)
                                                .height(20.dp), contentAlignment =
                                                Alignment
                                                    .Center
                                        ) {
                                            if (isMain) {
                                                ExerciseFlagButton(
                                                    flag = row.flag.value,
                                                    relColor = relColor,
                                                    onToggle = {
                                                        val newFlag = row.flag.value.next()
                                                        row.flag.value = newFlag
                                                        flags[index] = newFlag
                                                        var j = index + 1
                                                        while (j < lines.size) {
                                                            val prevText = lines[j - 1].text.value.text
                                                            val isMainLine = j == 0 || prevText.isBlank()
                                                            if (isMainLine) break
                                                            lines[j].flag.value = newFlag
                                                            if (flags.size <= j) flags.add(newFlag) else flags[j] = newFlag
                                                            j++
                                                        }
                                                        saved = false
                                                    }
                                                )
                                            } else {
                                                val parentFlag = run {
                                                    var p = index - 1
                                                    while (p >= 0) {
                                                        val pIsMain =
                                                            p == 0 || lines.getOrNull(p - 1)?.text?.value?.text?.isBlank() != false
                                                        if (pIsMain && lines[p].text.value.text.isNotBlank()) break
                                                        p--
                                                    }
                                                    lines.getOrNull(p)?.flag?.value ?: ExerciseFlag.BILATERAL
                                                }
                                                ExerciseFlagTag(
                                                    flag = parentFlag,
                                                    relColor = relColor
                                                )
                                            }
                                        }

                                    }
                                    Spacer(Modifier.width(8.dp))
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

                                                val formatted = when {
                                                    content.isNotBlank() && isMain -> "$content ($rel)"
                                                    content.isNotBlank() && !isMain -> "    $content ($rel)"
                                                    else -> ""
                                                }
                                                row.text.value = TextFieldValue(
                                                    formatted,
                                                    TextRange(formatted.length)
                                                )

                                                if (content.isNotBlank()) {
                                                    if (timestamps.size <= index) timestamps.add(abs)
                                                    else timestamps[index] = abs
                                                } else {
                                                    if (timestamps.size <= index) timestamps.add("")
                                                    else timestamps[index] = ""
                                                }

                                                lines.add(
                                                    index + 1,
                                                    NoteRow(
                                                        nextId++,
                                                        mutableStateOf(TextFieldValue("")),
                                                        mutableStateOf(row.flag.value)
                                                    )
                                                )

                                                if (flags.size <= index) flags.add(row.flag.value)
                                                else flags[index] = row.flag.value

                                                if (flags.size <= index + 1) flags.add(row.flag.value)
                                                else flags.add(index + 1, row.flag.value)

                                                if (timestamps.size <= index + 1) timestamps.add("")
                                                else timestamps.add(index + 1, "")

                                                if (focusRequesters.size <= index + 1) {
                                                    focusRequesters.add(FocusRequester())
                                                } else {
                                                    focusRequesters.add(index + 1, FocusRequester())
                                                }

                                                coroutineScope.launch {
                                                    withFrameNanos { }
                                                    listState.animateScrollToItem(index + 1)
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
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                        visualTransformation = rememberRelativeTimeVisualTransformation(
                                            if (isMain) 20.sp else 14.sp
                                        ),
                                        modifier = Modifier
                                            .focusRequester(fr)
                                            .bringIntoViewRequester(bringIntoViewRequester)
                                            .onFocusChanged {
                                                rowFocused = it.isFocused
                                                if (it.isFocused) {
                                                    coroutineScope.launch {
                                                        bringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            },
                                    )
                                }
                                val absText = timestamps.getOrNull(index).orEmpty()
                                val absAnnotated =
                                    SmallSecondsVisualTransformation(if (isMain) 20.sp else 14.sp).filter(
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
            LearningsPopup(
                showLearnings = showLearnings,
                learningsValue = learningsValue,
                onDismiss = { showLearnings = false },
                onValueChange = {
                    learningsValue = it
                    saved = false
                }
            )
        }
    }
}
}
