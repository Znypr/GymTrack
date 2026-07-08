package com.example.gymtrack.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.HomeOverviewWidget
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.util.formatWeekRelativeTime
import com.example.gymtrack.feature.home.components.WorkoutAlbumCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<NoteLine>,
    selectedNotes: Set<NoteLine> = emptySet(),
    onSelect: (Set<NoteLine>) -> Unit,
    onEdit: (NoteLine) -> Unit,
    onDelete: (Set<NoteLine>) -> Unit,
    onExport: (Set<NoteLine>) -> Unit,
    onCreate: () -> Unit,
    onImport: (List<Uri>) -> Unit,
    showLegacyCsvImport: Boolean = false,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onSwipeRight: () -> Unit,
    settings: Settings,
) {
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onImport(uris)
            }
        },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedNotes.isEmpty()) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note", modifier = Modifier.size(30.dp))
                }
            }
        },
        topBar = {
            if (selectedNotes.isEmpty()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Text(
                            "GymTrack",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-1).sp,
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenStats) {
                            Icon(Icons.Default.BarChart, contentDescription = "Stats", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        if (showLegacyCsvImport) {
                            IconButton(onClick = {
                                importLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel"))
                            }) {
                                Icon(
                                    Icons.Default.UploadFile,
                                    contentDescription = "Import legacy CSV",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
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
                                contentDescription = "Select All",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
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
        val statsByNote = remember(notes, settings.defaultWeightUnit) {
            notes.associateWith { analyzeWorkoutForHome(it, settings) }
        }
        val flamesByNote = remember(notes, statsByNote, settings.workoutIntensityFormula) {
            notes.associateWith { note ->
                val current = statsByNote.getValue(note)
                val peers = notes
                    .filter { it != note && it.categoryName == note.categoryName }
                    .sortedByDescending { it.timestamp }
                    .take(6)
                    .mapNotNull { statsByNote[it] }
                intensityFlames(current, peers, settings.workoutIntensityFormula)
            }
        }
        val latestWorkout = remember(notes) { notes.maxByOrNull { it.timestamp } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (dragX > 100f) onSwipeRight(); dragX = 0f },
                    ) { _, dragAmount -> if (dragAmount > 0) dragX += dragAmount }
                },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeTrainingOverview(
                        widget = settings.homeOverviewWidget,
                        latestWorkout = latestWorkout,
                        recentWorkouts = displayNotes.take(5),
                        statsByNote = statsByNote,
                        flamesByNote = flamesByNote,
                        settings = settings,
                        onStartWorkout = onCreate,
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Workout history",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { filterExpanded = true }) {
                                    Text(
                                        text = categoryFilter?.name ?: "All Categories",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(
                                    expanded = filterExpanded,
                                    onDismissRequest = { filterExpanded = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = { categoryFilter = null; filterExpanded = false },
                                    )
                                    settings.categories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat.name, color = MaterialTheme.colorScheme.onSurface) },
                                            trailingIcon = { Box(Modifier.size(10.dp).background(Color(cat.color), CircleShape)) },
                                            onClick = { categoryFilter = cat; filterExpanded = false },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { newestFirst = !newestFirst }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Sort", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                items(displayNotes) { note ->
                    val isSelected = selectedNotes.contains(note)
                    WorkoutAlbumCard(
                        note = note,
                        stats = statsByNote.getValue(note),
                        flames = flamesByNote[note] ?: 0,
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
                        settings = settings,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTrainingOverview(
    widget: HomeOverviewWidget,
    latestWorkout: NoteLine?,
    recentWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    flamesByNote: Map<NoteLine, Int>,
    settings: Settings,
    onStartWorkout: () -> Unit,
) {
    when (widget) {
        HomeOverviewWidget.LAST_WORKOUT -> LastWorkoutOverview(
            latestWorkout = latestWorkout,
            stats = latestWorkout?.let { statsByNote[it] },
            flames = latestWorkout?.let { flamesByNote[it] } ?: 0,
            settings = settings,
        )
        HomeOverviewWidget.RECENT_INTENSITY -> RecentIntensityOverview(
            recentWorkouts = recentWorkouts,
            statsByNote = statsByNote,
            flamesByNote = flamesByNote,
            settings = settings,
        )
        HomeOverviewWidget.QUICK_START -> QuickStartOverview(onStartWorkout = onStartWorkout)
    }
}

@Composable
private fun LastWorkoutOverview(
    latestWorkout: NoteLine?,
    stats: HomeWorkoutStats?,
    flames: Int,
    settings: Settings,
) {
    val accent = MaterialTheme.colorScheme.primary
    OverviewCard {
        Text("LAST WORKOUT", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        if (latestWorkout == null || stats == null) {
            Text(
                text = "Ready for your first session",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${latestWorkout.categoryName ?: "Workout"} · ${formatWeekRelativeTime(latestWorkout.timestamp, settings)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(
                            stats.durationMinutes.takeIf { it > 0 }?.let { "$it min" },
                            stats.cardMetricLabel(settings.homeCardMetric),
                        ).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(flameText(flames), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun RecentIntensityOverview(
    recentWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    flamesByNote: Map<NoteLine, Int>,
    settings: Settings,
) {
    val accent = MaterialTheme.colorScheme.primary
    OverviewCard {
        Text("RECENT INTENSITY", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        if (recentWorkouts.isEmpty()) {
            Text("No workouts yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentWorkouts.take(4).forEach { note ->
                    val stats = statsByNote[note]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${note.categoryName ?: "Workout"} · ${formatWeekRelativeTime(note.timestamp, settings)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = listOfNotNull(
                                stats?.durationMinutes?.takeIf { it > 0 }?.let { "$it min" },
                                flameText(flamesByNote[note] ?: 0).takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStartOverview(onStartWorkout: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    OverviewCard(modifier = Modifier.clickable(onClick = onStartWorkout)) {
        Text("READY TO TRAIN", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Start current workout",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap here or use + to begin logging fast.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(18.dp),
            content = content,
        )
    }
}
