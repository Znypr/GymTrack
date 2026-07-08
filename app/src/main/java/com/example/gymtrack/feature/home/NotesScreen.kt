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
    settings: Settings,
    onStartWorkout: () -> Unit,
) {
    when (widget) {
        HomeOverviewWidget.LAST_WORKOUT -> TodayTargetOverview(
            allWorkouts = allWorkouts,
            statsByNote = statsByNote,
        )
        HomeOverviewWidget.RECENT_INTENSITY -> RecentIntensityOverview(
            allWorkouts = allWorkouts,
            statsByNote = statsByNote,
            settings = settings,
        )
        HomeOverviewWidget.CYCLE_PROGRESS -> CycleProgressOverview(
            allWorkouts = allWorkouts,
            statsByNote = statsByNote,
        )
        HomeOverviewWidget.QUICK_START -> QuickStartOverview(onStartWorkout = onStartWorkout)
    }
}

@Composable
private fun TodayTargetOverview(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
) {
    val accent = MaterialTheme.colorScheme.primary
    val targetCategory = inferNextPplCategory(allWorkouts)
    val actionTarget = targetCategory?.let { buildTodayActionTarget(it, allWorkouts, statsByNote) }

    OverviewCard {
        Text("TODAY TARGET", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        if (targetCategory == null || actionTarget == null) {
            Text(
                text = "Start your first cycle",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Log Push, Pull, and Legs once. Then this will suggest one concrete target for today.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = "Today: $targetCategory",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = actionTarget.headline,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = actionTarget.instruction,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill(actionTarget.baselineLabel, actionTarget.baselineValue)
                MetricPill(actionTarget.targetLabel, actionTarget.targetValue)
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
            text = "Last 30 days",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        if (trend.isEmpty()) {
            Text("No workout intensity data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val average = trend.map { it.score }.average().toFloat()
            val latest = trend.maxByOrNull { it.note.timestamp }?.score ?: trend.first().score
            val delta = scoreDeltaPercent(latest, average)
            Text(
                text = "1 bar = 1 workout · ${formulaExplanation(settings.workoutIntensityFormula)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = delta?.let { "Latest: ${it.percentText} vs 30-day average." } ?: "Average appears after more logged workouts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            MiniIntensityBars(trend = trend.sortedBy { it.note.timestamp })
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("30 days ago", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text("Today", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CycleProgressOverview(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
) {
    val accent = MaterialTheme.colorScheme.primary
    val targetCategory = remember(allWorkouts) { inferNextPplCategory(allWorkouts) }
    val rows = remember(allWorkouts, statsByNote, targetCategory) {
        buildCycleRows(allWorkouts, statsByNote, targetCategory)
    }
    val todayTarget = rows.firstOrNull { it.isTodayTarget }

    OverviewCard {
        Text("CYCLE PROGRESS", color = accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = todayTarget?.let { "Today: ${it.category}" } ?: "Build the next cycle",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Main row = what to improve today. Other rows show this cycle versus the last one.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                CycleRowView(row = row)
            }
        }
    }
}

@Composable
private fun CycleRowView(row: CycleComparisonRow) {
    if (row.isTodayTarget) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.category,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text("TODAY", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = row.actionTarget?.headline ?: "Create today's baseline",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = row.actionTarget?.targetValue?.let { "Aim: $it" } ?: "Log this workout first.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        return
    }

    val delta = row.delta
    val statusColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delta.ratio >= 1.05f -> MaterialTheme.colorScheme.primary
        delta.ratio <= 0.95f -> MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val arrow = when {
        delta == null -> "—"
        delta.ratio >= 1.05f -> "↑"
        delta.ratio <= 0.95f -> "↓"
        else -> "→"
    }

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
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val fill = (delta?.ratio ?: 0f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill)
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusColor.copy(alpha = 0.72f)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier.width(66.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = arrow,
                color = statusColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = delta?.percentText ?: "—",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
            )
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
    val visibleTrend = trend.takeLast(24)
    val maxScore = visibleTrend.maxOfOrNull { it.score }?.takeIf { it > 0f } ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        visibleTrend.forEach { point ->
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
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (point.isLatest) 0.95f else 0.34f)),
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
    val actionTarget: TodayActionTarget?,
)

private data class TodayActionTarget(
    val headline: String,
    val instruction: String,
    val baselineLabel: String,
    val baselineValue: String,
    val targetLabel: String,
    val targetValue: String,
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

private fun buildTodayActionTarget(
    category: String,
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
): TodayActionTarget? {
    val sameCategory = allWorkouts
        .filter { it.categoryName.equals(category, ignoreCase = true) }
        .sortedByDescending { it.timestamp }
    val baseline = sameCategory.firstOrNull()?.let { statsByNote[it] } ?: return null
    val peers = sameCategory.drop(1).take(5).mapNotNull { statsByNote[it] }
    val avgDuration = peers.map { it.durationMinutes }.filter { it > 0 }.averageOrNull()
    val avgSets = peers.map { it.setCount }.filter { it > 0 }.averageOrNull()
    val avgReps = peers.map { it.totalReps }.filter { it > 0 }.averageOrNull()

    if (baseline.durationMinutes > 0 && avgDuration != null && baseline.durationMinutes > avgDuration * 1.08) {
        val targetMinutes = minOf((baseline.durationMinutes * 0.95f).roundToInt(), avgDuration.roundToInt()).coerceAtLeast(1)
        return TodayActionTarget(
            headline = "Keep rests shorter",
            instruction = "Use the elapsed timer. Start the next set sooner and finish the same work faster.",
            baselineLabel = "Last",
            baselineValue = "${baseline.durationMinutes} min",
            targetLabel = "Aim",
            targetValue = "≤ $targetMinutes min",
        )
    }

    if (avgSets != null && baseline.setCount > 0 && baseline.setCount < avgSets * 0.92) {
        return TodayActionTarget(
            headline = "Add 1 quality set",
            instruction = "Do not add junk volume. Add one clean set to an exercise that still feels strong.",
            baselineLabel = "Last",
            baselineValue = "${baseline.setCount} sets",
            targetLabel = "Aim",
            targetValue = "${baseline.setCount + 1} sets",
        )
    }

    if (avgReps != null && baseline.totalReps > 0 && baseline.totalReps < avgReps * 0.92) {
        val targetReps = (baseline.totalReps + 5).coerceAtLeast((avgReps * 0.95).roundToInt())
        return TodayActionTarget(
            headline = "Beat total reps",
            instruction = "Keep form clean and try to add reps across your working sets.",
            baselineLabel = "Last",
            baselineValue = "${baseline.totalReps} reps",
            targetLabel = "Aim",
            targetValue = "≥ $targetReps reps",
        )
    }

    if (baseline.durationMinutes > 0 && baseline.setCount > 0) {
        val targetMinutes = (baseline.durationMinutes * 0.95f).roundToInt().coerceAtLeast(1)
        return TodayActionTarget(
            headline = "Same work, less time",
            instruction = "Match last workout's sets, but keep pauses tighter and finish faster.",
            baselineLabel = "Last",
            baselineValue = "${baseline.setCount} sets · ${baseline.durationMinutes} min",
            targetLabel = "Aim",
            targetValue = "${baseline.setCount} sets ≤ $targetMinutes min",
        )
    }

    return TodayActionTarget(
        headline = "Create today's baseline",
        instruction = "Log this session normally. The app will give a concrete target next time.",
        baselineLabel = "Last",
        baselineValue = "none",
        targetLabel = "Aim",
        targetValue = "log cleanly",
    )
}

private fun recentIntensityTrend(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    formula: WorkoutIntensityFormula,
): List<IntensityPoint> {
    val latestTimestamp = allWorkouts.maxOfOrNull { it.timestamp } ?: return emptyList()
    val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
    val recent = allWorkouts
        .filter { it.timestamp >= latestTimestamp - thirtyDaysMs }
        .sortedBy { it.timestamp }

    return recent.mapNotNull { note ->
        val score = statsByNote[note]?.intensityScore(formula) ?: return@mapNotNull null
        if (score <= 0f) return@mapNotNull null
        IntensityPoint(note = note, score = score, isLatest = note.timestamp == latestTimestamp)
    }
}

private fun buildCycleRows(
    allWorkouts: List<NoteLine>,
    statsByNote: Map<NoteLine, HomeWorkoutStats>,
    targetCategory: String?,
): List<CycleComparisonRow> {
    val cycleCategories = listOf("Push", "Pull", "Legs")
    val orderedCategories = cycleCategories.sortedBy { if (it.equals(targetCategory, ignoreCase = true)) 0 else 1 }
    return orderedCategories.map { category ->
        val categoryWorkouts = allWorkouts
            .filter { it.categoryName.equals(category, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
        val isTarget = category.equals(targetCategory, ignoreCase = true)
        val current = if (isTarget) null else categoryWorkouts.getOrNull(0)
        val previous = if (isTarget) categoryWorkouts.getOrNull(0) else categoryWorkouts.getOrNull(1)
        val currentStats = current?.let { statsByNote[it] }
        val previousStats = previous?.let { statsByNote[it] }
        val actionTarget = if (isTarget) buildTodayActionTarget(category, allWorkouts, statsByNote) else null
        val currentScore = currentStats?.simpleCycleScore() ?: 0f
        val previousScore = previousStats?.simpleCycleScore() ?: 0f
        CycleComparisonRow(
            category = category,
            current = current,
            previous = previous,
            currentStats = currentStats,
            previousStats = previousStats,
            delta = scoreDeltaPercent(currentScore, previousScore),
            isTodayTarget = isTarget,
            actionTarget = actionTarget,
        )
    }
}

private fun HomeWorkoutStats.simpleCycleScore(): Float {
    if (durationMinutes <= 0) return setCount.toFloat()
    return setCount * 10f + totalReps * 0.25f - durationMinutes * 0.25f
}

private fun formulaExplanation(formula: WorkoutIntensityFormula): String = when (formula) {
    WorkoutIntensityFormula.SET_DENSITY -> "Density = sets/min"
    WorkoutIntensityFormula.SET_VOLUME -> "Volume = total sets"
    WorkoutIntensityFormula.AVG_SETS_PER_EXERCISE -> "Depth = sets/exercise"
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

private fun List<Int>.averageOrNull(): Double? {
    return if (isEmpty()) null else average()
}

private fun Float.format1(): String = String.format(Locale.US, "%.1f", this)

private fun Float.format2(): String = String.format(Locale.US, "%.2f", this)
