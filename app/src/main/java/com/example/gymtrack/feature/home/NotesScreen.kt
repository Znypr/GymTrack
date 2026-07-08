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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.HomeOverviewWidget
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutIntensityFormula
import com.example.gymtrack.core.util.formatWeekRelativeTime
import com.example.gymtrack.feature.home.components.WorkoutAlbumCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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

        val sortedNotes = remember(notes) { notes.sortedByDescending { it.timestamp } }
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
                        allWorkouts = sortedNotes,
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
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    flamesByNote: Map<NoteLine, Int>,
    settings: Settings,
    onStartWorkout: () -> Unit,
) {
    when (widget) {
        HomeOverviewWidget.LAST_WORKOUT -> TodayTargetOverview(
            allWorkouts = allWorkouts,
            statsByNote = statsByNote,
            settings = settings,
        )
        HomeOverviewWidget.RECENT_INTENSITY -> RecentIntensityOverview(
            allWorkouts = allWorkouts,
            statsByNote = statsByNote,
            settings = settings,
        )
        HomeOverviewWidget.CYCLE_PROGRESS -> CycleProgressOverview(
            allWorkouts = allWorkouts,
            statsByNote = statsByNote,
            settings = settings,
        )
        HomeOverviewWidget.QUICK_START -> QuickStartOverview(onStartWorkout = onStartWorkout)
    }
}

@Composable
private fun TodayTargetOverview(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    settings: Settings,
) {
    val accent = MaterialTheme.colorScheme.primary
    val targetCategory = inferNextPplCategory(allWorkouts)
    val baselineWorkout = targetCategory?.let { latestSameCategoryWorkout(it, allWorkouts) }
    val baselineStats = baselineWorkout?.let { statsByNote[it] }
    val baselineScore = baselineStats?.intensityScore(settings.workoutIntensityFormula) ?: 0f
    val targetScore = baselineScore * 1.05f

    OverviewCard {
        Text("TODAY TARGET", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        if (targetCategory == null) {
            Text(
                text = "Start your first cycle",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "After a few Push/Pull/Legs workouts, this will suggest what to beat today.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = "Likely next: $targetCategory",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (baselineStats == null || baselineScore <= 0f) {
                    "Log today to create your first $targetCategory baseline."
                } else {
                    "Beat your last $targetCategory using ${settings.workoutIntensityFormula.displayLabel.lowercase()} as the target."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill("Last", baselineStats?.formulaLabel(settings.workoutIntensityFormula) ?: "none")
                MetricPill(
                    "Target",
                    if (targetScore > 0f) "> ${scoreLabel(targetScore, settings.workoutIntensityFormula)}" else "baseline",
                )
            }
        }
    }
}

@Composable
private fun RecentIntensityOverview(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    settings: Settings,
) {
    val accent = MaterialTheme.colorScheme.primary
    val trend = remember(allWorkouts, statsByNote, settings.workoutIntensityFormula) {
        recentIntensityTrend(allWorkouts, statsByNote, settings.workoutIntensityFormula)
    }

    OverviewCard {
        Text("INTENSITY TREND", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Beat your recent baseline today",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        if (trend.isEmpty()) {
            Text("No workout intensity data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val average = trend.map { it.score }.average().toFloat()
            val last = trend.first().score
            val delta = scoreDeltaPercent(last, average)
            Text(
                text = "Last ${trend.size} workouts · each bar = 1 workout · ${settings.workoutIntensityFormula.displayLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = delta?.let { "Latest is ${it.percentText} vs recent average." } ?: "Recent average appears after more logged workouts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            MiniIntensityBars(trend = trend.reversed())
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Older", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text("Latest", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CycleProgressOverview(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    settings: Settings,
) {
    val accent = MaterialTheme.colorScheme.primary
    val targetCategory = remember(allWorkouts) { inferNextPplCategory(allWorkouts) }
    val rows = remember(allWorkouts, statsByNote, settings.workoutIntensityFormula) {
        buildCycleRows(allWorkouts, statsByNote, settings.workoutIntensityFormula, targetCategory)
    }
    val todayTarget = rows.firstOrNull { it.isTodayTarget }
    val ahead = rows.count { !it.isTodayTarget && (it.delta?.ratio ?: 0f) >= 1.05f }

    OverviewCard {
        Text("CYCLE PROGRESS", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = todayTarget?.let { "Today: ${it.category}" } ?: if (ahead >= 2) "This cycle is ahead" else "Build the next cycle",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Full bar = matched previous same-category baseline. Target row shows what to beat today.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                CycleRowView(row = row, settings = settings)
            }
        }
    }
}

@Composable
private fun CycleRowView(row: CycleComparisonRow, settings: Settings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = row.category,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(46.dp),
        )

        if (row.isTodayTarget) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Today target",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = row.previousStats?.let { "Beat ${it.formulaLabel(settings.workoutIntensityFormula)}" } ?: "Create baseline",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text("→", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val fill = (row.delta?.ratio ?: 0f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(
                        row.currentStats?.let { "Now ${it.formulaLabel(settings.workoutIntensityFormula)}" },
                        row.previousStats?.let { "target ${it.formulaLabel(settings.workoutIntensityFormula)}" },
                    ).joinToString(" · ").ifBlank { "Need more data" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(62.dp)) {
                Text(
                    text = row.delta?.statusText ?: if (row.current == null) "Missing" else "Baseline",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = row.delta?.percentText ?: "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
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
private fun MiniIntensityBars(trend: List<IntensityPoint>) {
    val maxScore = trend.maxOfOrNull { it.score }?.takeIf { it > 0f } ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        trend.takeLast(12).forEach { point ->
            val heightFraction = (point.score / maxScore).coerceIn(0.08f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (point.isLatest) 1f else 0.42f)),
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
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

private data class ScoreDelta(
    val ratio: Float,
    val percent: Int,
    val statusText: String,
    val percentText: String,
)

private data class IntensityPoint(
    val note: NoteLine,
    val score: Float,
    val isLatest: Boolean,
)

private data class CycleComparisonRow(
    val category: String,
    val current: NoteLine?,
    val previous: NoteLine?,
    val currentStats: HomeWorkoutStats?,
    val previousStats: HomeWorkoutStats?,
    val delta: ScoreDelta?,
    val isTodayTarget: Boolean,
)

private fun inferNextPplCategory(allWorkouts: List<NoteLine>): String? {
    val cycle = listOf("Push", "Pull", "Legs")
    val latestCategory = allWorkouts.firstOrNull()?.categoryName ?: return cycle.firstOrNull()
    val index = cycle.indexOfFirst { it.equals(latestCategory, ignoreCase = true) }
    return if (index >= 0) cycle[(index + 1) % cycle.size] else cycle.firstOrNull()
}

private fun latestSameCategoryWorkout(category: String, allWorkouts: List<NoteLine>): NoteLine? {
    return allWorkouts.firstOrNull { it.categoryName.equals(category, ignoreCase = true) }
}

private fun recentIntensityTrend(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    formula: WorkoutIntensityFormula,
): List<IntensityPoint> {
    val latestTimestamp = allWorkouts.maxOfOrNull { it.timestamp } ?: return emptyList()
    val ninetyDaysMs = 90L * 24L * 60L * 60L * 1000L
    val recent = allWorkouts
        .filter { it.timestamp >= latestTimestamp - ninetyDaysMs }
        .sortedByDescending { it.timestamp }
        .take(12)
        .ifEmpty { allWorkouts.sortedByDescending { it.timestamp }.take(12) }

    return recent.mapIndexedNotNull { index, note ->
        val score = statsByNote[note]?.intensityScore(formula) ?: return@mapIndexedNotNull null
        if (score <= 0f) return@mapIndexedNotNull null
        IntensityPoint(note = note, score = score, isLatest = index == 0)
    }
}

private fun buildCycleRows(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    formula: WorkoutIntensityFormula,
    targetCategory: String?,
): List<CycleComparisonRow> {
    val cycleCategories = listOf("Push", "Pull", "Legs")
    return cycleCategories.map { category ->
        val categoryWorkouts = allWorkouts
            .filter { it.categoryName.equals(category, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
        val isTarget = category.equals(targetCategory, ignoreCase = true)
        val current = if (isTarget) null else categoryWorkouts.getOrNull(0)
        val previous = if (isTarget) categoryWorkouts.getOrNull(0) else categoryWorkouts.getOrNull(1)
        val currentStats = current?.let { statsByNote[it] }
        val previousStats = previous?.let { statsByNote[it] }
        val currentScore = currentStats?.intensityScore(formula) ?: 0f
        val previousScore = previousStats?.intensityScore(formula) ?: 0f
        CycleComparisonRow(
            category = category,
            current = current,
            previous = previous,
            currentStats = currentStats,
            previousStats = previousStats,
            delta = scoreDeltaPercent(currentScore, previousScore),
            isTodayTarget = isTarget,
        )
    }
}

private fun scoreDeltaPercent(current: Float, previous: Float): ScoreDelta? {
    if (current <= 0f || previous <= 0f) return null
    val ratio = current / previous
    val percent = ((ratio - 1f) * 100f).roundToInt()
    val status = when {
        ratio >= 1.05f -> "Ahead"
        ratio <= 0.95f -> "Behind"
        else -> "On pace"
    }
    val sign = when {
        percent > 0 -> "+"
        percent < 0 -> "−"
        else -> "±"
    }
    return ScoreDelta(
        ratio = ratio,
        percent = percent,
        statusText = status,
        percentText = "$sign${abs(percent)}%",
    )
}

private fun HomeWorkoutStats.formulaLabel(formula: WorkoutIntensityFormula): String {
    return scoreLabel(intensityScore(formula), formula)
}

private fun scoreLabel(score: Float, formula: WorkoutIntensityFormula): String = when (formula) {
    WorkoutIntensityFormula.SET_DENSITY -> "${score.format2()} sets/min"
    WorkoutIntensityFormula.SET_VOLUME -> "${score.roundToInt()} sets"
    WorkoutIntensityFormula.AVG_SETS_PER_EXERCISE -> "${score.format1()} sets/ex"
}

private fun Float.format1(): String = String.format(Locale.US, "%.1f", this)

private fun Float.format2(): String = String.format(Locale.US, "%.2f", this)
