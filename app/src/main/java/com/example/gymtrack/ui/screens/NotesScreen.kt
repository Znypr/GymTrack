package com.example.gymtrack.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.R
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.darken
import com.example.gymtrack.util.formatWeekRelativeTime
import com.example.gymtrack.util.lighten
import com.example.gymtrack.util.parseNoteText
import com.example.gymtrack.util.parseDurationSeconds
import java.util.Calendar
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import com.example.gymtrack.data.DEFAULT_CATEGORIES
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<NoteLine>,
    selectedNotes: Set<NoteLine>,
    onSelect: (Set<NoteLine>) -> Unit,
    onEdit: (NoteLine) -> Unit,
    onDelete: (Set<NoteLine>) -> Unit,
    onExport: (Set<NoteLine>) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwipeRight: () -> Unit,
    settings: Settings,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedNotes.isEmpty()) {
                FloatingActionButton(
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation
                        (defaultElevation = 5.dp),
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.background.lighten(0.1f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        },
        topBar = {
            if (selectedNotes.isEmpty()) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    navigationIcon = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_gymtrack_logo),
                            contentDescription = "GymTrack logo",
                            modifier = Modifier.size(45.dp) // optional size
                        )
                    },
                    title = { Text("GymTrack", fontSize = 24.sp) },
                    actions = {

                        IconButton(onClick = onImport) {
                            Icon(Icons.Default.Add, contentDescription = "Import")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = { Text("${selectedNotes.size} selected", fontSize = 20.sp) },
                    actions = {
                        IconButton(onClick = { onExport(selectedNotes) }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }
                        IconButton(onClick = { onDelete(selectedNotes) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
    ) { padding ->
        var dragX by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX > 100f) onSwipeRight()
                            dragX = 0f
                        }
                    ) { change, dragAmount ->
                        if (dragAmount > 0) dragX += dragAmount
                    }
                }
        ) {
            var newestFirst by remember { mutableStateOf(true) }
            var categoryFilter by remember { mutableStateOf<Category?>(null) }
            var filterExpanded by remember { mutableStateOf(false) }

            val displayNotes = remember(notes, categoryFilter, newestFirst) {
                notes
                    .filter { n ->
                        categoryFilter?.let { n.categoryName == it.name } ?: true
                    }
                    .let { list ->
                        if (newestFirst) list.sortedByDescending { it.timestamp }
                        else list.sortedBy { it.timestamp }
                    }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = padding,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { filterExpanded = true }) {
                                Icon(Icons.Default.List, contentDescription = "Filter")
                            }
                            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("All") },
                                    onClick = {
                                        categoryFilter = null
                                        filterExpanded = false
                                    }
                                )
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
                                            categoryFilter = cat
                                            filterExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { newestFirst = !newestFirst }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sort")
                        }
                    }
                }

                // Replace the week key computation inside the LazyVerticalGrid loop:
                var lastWeek: Pair<Int, Int>? = null
                displayNotes.forEach { note ->
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = note.timestamp
                        firstDayOfWeek = Calendar.MONDAY
                        minimalDaysInFirstWeek = 4
                    }
                    val weekYear = cal.get(Calendar.YEAR)
                    val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
                    val weekPair = weekYear to weekOfYear

                    if (weekPair != lastWeek) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Divider(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp))
                        }
                        lastWeek = weekPair
                    }

                    item(key = note.timestamp) {
                        val isSelected = selectedNotes.contains(note)
                        NoteCard(
                            note = note,
                            isSelected = isSelected,
                            onClick = {
                                if (selectedNotes.isNotEmpty()) {
                                    onSelect(selectedNotes.toMutableSet().also { set ->
                                        if (set.contains(note)) set.remove(note) else set.add(note)
                                    })
                                } else {
                                    onEdit(note)
                                }
                            },
                            onLongClick = { onSelect(selectedNotes.toMutableSet().also { it.add(note) }) },
                            settings = settings
                        )
                    }
                }

            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteLine,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    settings: Settings,
) {
    Card(
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                val base = categoryColorFor(note.categoryName, settings.darkMode)
                if (note.categoryName == null) {
                    base
                } else if (settings.darkMode) {
                    base.darken(0.6f)
                } else {
                    base.lighten(0.55f)
                }
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        )

    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
        ) {
            val totalSec =
                parseNoteText(note.text).second.mapNotNull {
                    if (it.isBlank()) null else parseDurationSeconds(it)
                }.maxOrNull()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                totalSec?.let {
                    Text(
                        text = "${it / 60}'",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = formatWeekRelativeTime(note.timestamp, settings),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.categoryName?.uppercase() ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
        }

    }
}

@Composable
fun categoryColorFor(name: String?, darkMode: Boolean): Color {
    val base = DEFAULT_CATEGORIES.find { it.name.equals(name, ignoreCase = true) }
        ?.let { Color(it.color.toInt()) }
        ?: if (darkMode) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceVariant

    return base
}