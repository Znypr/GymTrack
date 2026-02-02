package com.example.gymtrack.ui.screens

import ModernNoteCard
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.data.Category
import com.example.gymtrack.data.DEFAULT_CATEGORIES
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.util.formatWeekRelativeTime
import com.example.gymtrack.util.parseDurationSeconds
import com.example.gymtrack.util.parseNoteText

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
        // REVERTED: Uses Theme background (Black in Dark Mode, Off-White in Light)
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedNotes.isEmpty()) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note", modifier = Modifier.size(30.dp))
                }
            }
        },
        topBar = {
            if (selectedNotes.isEmpty()) {
                // Main Header
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        // REVERTED: Transparent/Theme color
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Text(
                            "GymTrack",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-1).sp
                        )
                    },
                    actions = {
                        IconButton(onClick = onImport) {
                            Icon(Icons.Default.Add , contentDescription = "Import", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                )
            } else {
                // Selection Header
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = { Text("${selectedNotes.size} selected", color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = {
                        IconButton(onClick = { onSelect(emptySet()) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onDelete(selectedNotes) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onExport(selectedNotes) }) {
                            Icon(Icons.Default.Download, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = {
                            if (selectedNotes.size == notes.size) onSelect(emptySet()) else onSelect(notes.toSet())
                        }) {
                            Icon(
                                imageVector = if (selectedNotes.size == notes.size) Icons.Default.ChecklistRtl else Icons.Default.Checklist,
                                contentDescription = "Select All", tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        var dragX by remember { mutableStateOf(0f) }
        var newestFirst by remember { mutableStateOf(true) }
        var categoryFilter by remember { mutableStateOf<Category?>(null) }
        var filterExpanded by remember { mutableStateOf(false) }

        val displayNotes = remember(notes, categoryFilter, newestFirst) {
            notes
                .filter { n -> categoryFilter?.let { n.categoryName == it.name } ?: true }
                .let { list -> if (newestFirst) list.sortedByDescending { it.timestamp } else list.sortedBy { it.timestamp } }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (dragX > 100f) onSwipeRight(); dragX = 0f }
                    ) { _, dragAmount -> if (dragAmount > 0) dragX += dragAmount }
                }
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Filter Row
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            TextButton(onClick = { filterExpanded = true }) {
                                Text(
                                    text = categoryFilter?.name ?: "All Categories",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = filterExpanded,
                                onDismissRequest = { filterExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { categoryFilter = null; filterExpanded = false }
                                )
                                settings.categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = MaterialTheme.colorScheme.onSurface) },
                                        trailingIcon = { Box(Modifier.size(10.dp).background(Color(cat.color), CircleShape)) },
                                        onClick = { categoryFilter = cat; filterExpanded = false }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { newestFirst = !newestFirst }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(displayNotes) { note ->
                    val isSelected = selectedNotes.contains(note)

                    WorkoutAlbumCard(
                        note = note,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedNotes.isNotEmpty()) {
                                val newSet = if (isSelected) selectedNotes - note else selectedNotes + note
                                onSelect(newSet)
                            } else {
                                onEdit(note)
                            }
                        },
                        onLongClick = {
                            val newSet = if (isSelected) selectedNotes - note else selectedNotes + note
                            onSelect(newSet)
                        },
                        settings = settings
                    )
                }
            }
        }
    }
}
