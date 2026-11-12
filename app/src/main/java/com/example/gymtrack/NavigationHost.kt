package com.example.gymtrack

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.gymtrack.core.backup.BackupManifest
import com.example.gymtrack.core.backup.BackupRepository
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.DerivedIndexWriter
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.RepositoryMuscleLoadSource
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.muscle.MuscleWeek
import com.example.gymtrack.core.time.AppClock
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.editor.EditorViewModel
import com.example.gymtrack.feature.editor.NoteEditor
import com.example.gymtrack.feature.editor.shouldShowActiveWorkoutControls
import com.example.gymtrack.feature.history.HistoryScreen
import com.example.gymtrack.feature.history.workoutCount
import com.example.gymtrack.feature.home.HomeViewModel
import com.example.gymtrack.feature.home.OrphanChoice
import com.example.gymtrack.feature.home.WorkoutStartDecision
import com.example.gymtrack.feature.home.WorkoutStartSheet
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.gymtrack.feature.finish.FinishSummarySheet
import com.example.gymtrack.feature.finish.buildFinishSummary
import com.example.gymtrack.feature.stats.rememberTodayNotTrainedLine
import com.example.gymtrack.feature.stats.ProgressRoute
import com.example.gymtrack.feature.home.activeWorkoutInfo
import com.example.gymtrack.feature.home.decideWorkoutStart
import com.example.gymtrack.feature.home.orphanPrompt
import com.example.gymtrack.feature.home.screenBackground
import com.example.gymtrack.feature.settings.SettingsScreen
import com.example.gymtrack.feature.stats.StatsScreen
import com.example.gymtrack.feature.stats.StatsViewModel
import com.example.gymtrack.feature.today.TodayScreen
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CURRENT_DATABASE_SCHEMA_VERSION = 10

@Composable
fun NavigationHost(
    navController: NavHostController,
    settings: Settings,
    onSettingsUpdate: (Settings) -> Unit,
    noteRepository: NoteRepository,
    workoutRepository: WorkoutRepository,
    backupRepository: BackupRepository,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val tokens = GymTrackTheme.tokens
    val retainedStatsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(noteRepository, appContext),
    )
    // Stats shows every weight in the user's current default unit (#326).
    LaunchedEffect(settings.defaultWeightUnit) {
        retainedStatsViewModel.setDisplayUnit(settings.defaultWeightUnit)
    }

    // One workout list for Today, History and the active-workout bar.
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(noteRepository, workoutRepository),
    )
    val loadedNotes by homeViewModel.loadedNotes.collectAsState()
    val notes = loadedNotes.orEmpty()
    val finished by homeViewModel.finishedTimestamps.collectAsState()
    val rowFacts by homeViewModel.rowFacts.collectAsState()
    val pendingDelete by homeViewModel.pendingDelete.collectAsState()
    val importSummary by homeViewModel.legacyCsvImportSummary.collectAsState()
    val importProgress by homeViewModel.legacyCsvImportProgress.collectAsState()
    val timerStates = remember(appContext) { NoteTimerStore.observe(appContext) }
    val timer by timerStates.collectAsState(initial = NoteTimerState())

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTab = currentRoute in AppRoutes.TABS
    val active = activeWorkoutInfo(timer, notes) { it in finished }

    var pendingStart by remember { mutableStateOf<PendingStart?>(null) }
    // Workout whose Finish summary sheet is showing (survives rotation).
    val muscleLoadSource = remember(workoutRepository) { RepositoryMuscleLoadSource(workoutRepository) }
    var finishSummaryFor by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(importSummary) {
        val summary = importSummary ?: return@LaunchedEffect
        if (summary.selected > 0) {
            Toast.makeText(context, summary.toUserMessage(), Toast.LENGTH_LONG).show()
        }
        homeViewModel.clearLegacyCsvImportSummary()
    }

    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(AppRoutes.TODAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openWorkout(timestamp: Long) {
        navController.navigate(AppRoutes.editor(timestamp)) { launchSingleTop = true }
    }

    /**
     * Starts the workout after any question about the running one was answered. [prefill] is
     * the "Start from last" text (#305): exercise names only, used with a category.
     */
    fun beginWorkout(category: Category?, prefill: String = "") {
        scope.launch {
            // Navigation must run on the main thread; the suspend calls before it may resume elsewhere.
            if (category == null) {
                // The editor creates a blank workout and starts the timer itself.
                withContext(Dispatchers.Main.immediate) { navController.navigate(AppRoutes.editor(-1L)) }
            } else {
                val timestamp = homeViewModel.createWorkout(category, prefill)
                NoteTimerStore.startOrRestore(appContext, timestamp)
                withContext(Dispatchers.Main.immediate) { openWorkout(timestamp) }
            }
        }
    }

    /** Every Start passes here, so no workout silently loses the timer (#293). */
    fun requestStart(category: Category?, prefill: String = "") {
        if (loadedNotes == null) return
        val activeTimestamp = timer.activeNoteTimestamp
        val activeNote = notes.firstOrNull { it.timestamp == activeTimestamp }
        when (
            val decision = decideWorkoutStart(
                activeTimestamp = activeTimestamp,
                activeNote = activeNote,
                activeIsFinished = activeTimestamp != null && activeTimestamp in finished,
            )
        ) {
            WorkoutStartDecision.StartNow -> beginWorkout(category, prefill)
            is WorkoutStartDecision.ClearStale -> scope.launch {
                if (decision.deleteEmptyNote && activeNote != null) homeViewModel.deleteEmptyWorkout(activeNote)
                NoteTimerStore.stop(appContext, decision.activeTimestamp)
                beginWorkout(category, prefill)
            }
            is WorkoutStartDecision.AskFirst -> pendingStart = PendingStart(decision.active, category, prefill)
        }
    }

    fun resolveStart(request: PendingStart, choice: OrphanChoice) {
        pendingStart = null
        scope.launch {
            try {
                when (choice) {
                    OrphanChoice.FINISH_IT ->
                        homeViewModel.markFinished(listOf(request.active), settings.defaultWeightUnit, AppClock.nowMillis())
                    OrphanChoice.DISCARD_TIMER ->
                        homeViewModel.markFinished(listOf(request.active), settings.defaultWeightUnit, null)
                    OrphanChoice.KEEP_BOTH -> Unit
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    Toast.makeText(context, "Could not finish the workout: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            NoteTimerStore.stop(appContext, request.active.timestamp)
            beginWorkout(request.category, request.prefill)
        }
    }

    Column(Modifier.fillMaxSize().screenBackground(tokens)) {
        NavHost(
            navController = navController,
            startDestination = AppRoutes.TODAY,
            // Tabs draw edge-to-edge; keep their read-only top text clear of the status bar.
            modifier = Modifier.weight(1f).fillMaxWidth().then(if (onTab) Modifier.statusBarsPadding() else Modifier),
        ) {
            composable(AppRoutes.TODAY) {
                val zone = remember { ZoneId.systemDefault() }
                val today = Instant.ofEpochMilli(AppClock.nowMillis()).atZone(zone).toLocalDate()
                TodayScreen(
                    notes = notes,
                    loaded = loadedNotes != null,
                    rowFacts = rowFacts,
                    finishedTimestamps = finished,
                    settings = settings,
                    today = today,
                    hasActiveWorkout = active != null,
                    onStart = { category -> requestStart(category) },
                    onStartFromLast = { prefill -> requestStart(prefill.category, prefill.text) },
                    onOpen = { note -> openWorkout(note.timestamp) },
                    onSeeAll = { openTab(AppRoutes.HISTORY) },
                    onRestoreBackup = { navController.navigate(AppRoutes.SETTINGS) },
                    notTrainedLine = rememberTodayNotTrainedLine(muscleLoadSource, notes.size to notes.firstOrNull()?.timestamp),
                )
            }

            composable(AppRoutes.HISTORY) {
                HistoryScreen(
                    notes = notes,
                    rowFacts = rowFacts,
                    finishedTimestamps = finished,
                    settings = settings,
                    onOpen = { note -> openWorkout(note.timestamp) },
                    onDelete = { selected ->
                        homeViewModel.deleteNotes(selected)
                        // A deleted workout cannot keep the timer (the bar would point at nothing).
                        timer.activeNoteTimestamp?.takeIf { ts -> selected.any { it.timestamp == ts } }?.let { ts ->
                            scope.launch { NoteTimerStore.stop(appContext, ts) }
                        }
                    },
                    pendingDelete = pendingDelete,
                    onUndoDelete = { homeViewModel.undoDelete() },
                    onDeleteFinal = { homeViewModel.commitPendingDelete() },
                    onExport = { selected, onDone ->
                        scope.launch {
                            try {
                                homeViewModel.exportNotes(context, selected, settings)
                                Toast.makeText(
                                    context,
                                    "Exported ${workoutCount(selected.size)} to Downloads",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onDone()
                            } catch (error: Exception) {
                                Toast.makeText(context, "Export failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onMarkFinished = { selected ->
                        scope.launch {
                            try {
                                homeViewModel.markFinished(selected, settings.defaultWeightUnit, null)
                                timer.activeNoteTimestamp?.takeIf { ts -> selected.any { it.timestamp == ts } }?.let { ts ->
                                    NoteTimerStore.stop(appContext, ts)
                                }
                                Toast.makeText(context, "Finished ${workoutCount(selected.size)}", Toast.LENGTH_SHORT).show()
                            } catch (error: Exception) {
                                Toast.makeText(context, "Could not finish: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    showLegacyCsvImport = BuildConfig.DEBUG,
                    legacyCsvImportProgress = importProgress,
                    onImport = { uris -> homeViewModel.importNotesFromUris(context, uris, settings) },
                )
            }

            composable(AppRoutes.PROGRESS) {
                ProgressRoute(
                    viewModel = retainedStatsViewModel,
                    workoutRepository = workoutRepository,
                    settings = settings,
                    onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) },
                    onStartCategory = { name ->
                        requestStart(settings.categories.firstOrNull { it.name == name })
                    },
                    onGoalSelected = { goal -> onSettingsUpdate(settings.copy(trainingGoal = goal)) },
                    muscleLoadSource = muscleLoadSource,
                )
            }

            composable(AppRoutes.EDITOR) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
                // Read once on open: Finish inside the editor must not flip its own controls.
                val finishedOnOpen = remember(noteId) { noteId in finished }
                val isActiveWorkout = shouldShowActiveWorkoutControls(
                    noteId = noteId,
                    activeNoteTimestamp = timer.activeNoteTimestamp,
                    isFinished = finishedOnOpen,
                )

                val editorViewModel: EditorViewModel = viewModel(
                    factory = EditorViewModel.Factory(
                        noteId,
                        noteRepository,
                        workoutRepository,
                    ),
                )

                NoteEditor(
                    viewModel = editorViewModel,
                    settings = settings,
                    isLastNote = isActiveWorkout,
                    onCancel = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() },
                    onWorkoutFinished = { timestamp -> finishSummaryFor = timestamp },
                )
            }

            composable(AppRoutes.STATS) {
                val state by retainedStatsViewModel.uiState.collectAsState()

                StatsScreen(
                    state = state,
                    workoutRepository = workoutRepository,
                    settings = settings,
                    onTimeRangeSelected = { retainedStatsViewModel.setTimeRange(it) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AppRoutes.SETTINGS) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var operationInProgress by remember { mutableStateOf(false) }
                var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
                var pendingRestoreManifest by remember { mutableStateOf<BackupManifest?>(null) }
                var pendingRestoreHasLocalData by remember { mutableStateOf(false) }

                fun backupFileName(prefix: String): String {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    return "$prefix-$date.gymtrack-backup"
                }

                fun clearPendingRestore() {
                    pendingRestoreUri = null
                    pendingRestoreManifest = null
                    pendingRestoreHasLocalData = false
                }

                fun restoreSelectedBackup(source: Uri) {
                    clearPendingRestore()
                    scope.launch {
                        operationInProgress = true
                        try {
                            val result = backupRepository.restoreBackup(
                                context = context.applicationContext,
                                contentResolver = context.contentResolver,
                                source = source,
                            )
                            onSettingsUpdate(result.settings)
                            Toast.makeText(
                                context,
                                "Restored ${result.manifest.counts.totalRecords} records",
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                "Restore failed: ${error.localizedMessage}",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            operationInProgress = false
                        }
                    }
                }

                val createBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream"),
                ) { destination ->
                    destination ?: return@rememberLauncherForActivityResult
                    scope.launch {
                        operationInProgress = true
                        try {
                            val result = backupRepository.createBackup(
                                contentResolver = context.contentResolver,
                                destination = destination,
                                settings = settings,
                                appVersion = BuildConfig.VERSION_NAME,
                                databaseSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION,
                            )
                            Toast.makeText(
                                context,
                                "Backup created: ${result.manifest.counts.totalRecords} records",
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                "Backup failed: ${error.localizedMessage}",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            operationInProgress = false
                        }
                    }
                }

                val safetyBackupBeforeRestoreLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream"),
                ) { destination ->
                    destination ?: return@rememberLauncherForActivityResult
                    val restoreSource = pendingRestoreUri ?: return@rememberLauncherForActivityResult
                    clearPendingRestore()
                    scope.launch {
                        operationInProgress = true
                        try {
                            val safetyBackup = backupRepository.createBackup(
                                contentResolver = context.contentResolver,
                                destination = destination,
                                settings = settings,
                                appVersion = BuildConfig.VERSION_NAME,
                                databaseSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION,
                            )
                            val restoreResult = backupRepository.restoreBackup(
                                context = context.applicationContext,
                                contentResolver = context.contentResolver,
                                source = restoreSource,
                            )
                            onSettingsUpdate(restoreResult.settings)
                            Toast.makeText(
                                context,
                                "Safety backup created (${safetyBackup.manifest.counts.totalRecords} records). " +
                                    "Restored ${restoreResult.manifest.counts.totalRecords} records.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                "Safety backup or restore failed: ${error.localizedMessage}",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            operationInProgress = false
                        }
                    }
                }

                val restoreBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { source ->
                    source ?: return@rememberLauncherForActivityResult
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            source,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    scope.launch {
                        operationInProgress = true
                        try {
                            pendingRestoreManifest = backupRepository.inspectBackup(
                                context.contentResolver,
                                source,
                            )
                            pendingRestoreHasLocalData = backupRepository.hasRestorableLocalData(settings)
                            pendingRestoreUri = source
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                "Invalid backup: ${error.localizedMessage}",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            operationInProgress = false
                        }
                    }
                }

                SettingsScreen(
                    settings = settings,
                    onUpdate = onSettingsUpdate,
                    onBack = { navController.popBackStack() },
                    onCreateBackup = {
                        createBackupLauncher.launch(backupFileName("ZnyprFit-backup"))
                    },
                    onRestoreBackup = {
                        restoreBackupLauncher.launch(
                            arrayOf("application/octet-stream", "application/zip", "application/json"),
                        )
                    },
                    dataOperationInProgress = operationInProgress,
                )

                val restoreUri = pendingRestoreUri
                val restoreManifest = pendingRestoreManifest
                if (restoreUri != null && restoreManifest != null) {
                    RestoreConfirmationDialog(
                        totalRecords = restoreManifest.counts.totalRecords,
                        hasLocalData = pendingRestoreHasLocalData,
                        onCreateSafetyBackup = {
                            safetyBackupBeforeRestoreLauncher.launch(
                                backupFileName("ZnyprFit-safety-backup-before-restore"),
                            )
                        },
                        onRestoreWithoutBackup = { restoreSelectedBackup(restoreUri) },
                        onDismiss = { clearPendingRestore() },
                    )
                }
            }
        }

        if (onTab) {
            TabChrome(
                selectedRoute = currentRoute,
                active = active,
                timer = timer,
                settings = settings,
                onResume = ::openWorkout,
                onSelectTab = ::openTab,
            )
        }
    }

    pendingStart?.let { request ->
        WorkoutStartSheet(
            prompt = orphanPrompt(request.active, AppClock.nowMillis()),
            onChoice = { choice -> resolveStart(request, choice) },
            onDismiss = { pendingStart = null },
        )
    }

    // Finish summary (wave 2): shown once the finished workout is in the list.
    finishSummaryFor?.let { timestamp ->
        val workout = notes.firstOrNull { it.timestamp == timestamp }
        if (workout != null) {
            // Muscles come from the derived rows written at Finish (#309); unmapped until loaded.
            val muscles by produceState(MuscleWeek.unmapped to (null as MuscleWeek?), workout) {
                value = runCatching {
                    muscleLoadSource.workoutLevels(workout) to
                        muscleLoadSource.weekLevels(DerivedIndexWriter.weekKey(AppClock.nowMillis()))
                }.getOrDefault(MuscleWeek.unmapped to null)
            }
            val summary = remember(workout, finished, settings.defaultWeightUnit, muscles) {
                buildFinishSummary(
                    workout = workout,
                    defaultUnit = settings.defaultWeightUnit,
                    finishedWorkoutStarts = finished + timestamp,
                    nowMillis = AppClock.nowMillis(),
                    muscles = muscles.first,
                    weekMuscles = muscles.second,
                )
            }
            FinishSummarySheet(
                summary = summary,
                onDone = { finishSummaryFor = null },
                onEdit = {
                    finishSummaryFor = null
                    openWorkout(timestamp)
                },
            )
        }
    }
}

/** A Start waiting for the start sheet's answer; [category] null = blank workout. */
private data class PendingStart(val active: NoteLine, val category: Category?, val prefill: String = "")

@Composable
private fun RestoreConfirmationDialog(
    totalRecords: Int,
    hasLocalData: Boolean,
    onCreateSafetyBackup: () -> Unit,
    onRestoreWithoutBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Replace all local data?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This validated backup contains $totalRecords records. Your current workouts and settings will be replaced.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (hasLocalData) {
                            "Create a safety backup first if you want a separate file copy of your current data before restore."
                        } else {
                            "No existing workout records were found, so a safety backup is not required."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (hasLocalData) {
                        TextButton(onClick = onCreateSafetyBackup) {
                            Text("Create safety backup first")
                        }
                    }
                    TextButton(onClick = onRestoreWithoutBackup) {
                        Text(if (hasLocalData) "Restore without backup" else "Restore backup")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
