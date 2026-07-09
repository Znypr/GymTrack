package com.example.gymtrack

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.gymtrack.core.backup.BackupManifest
import com.example.gymtrack.core.backup.BackupRepository
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.timer.NoteTimerState
import com.example.gymtrack.core.timer.NoteTimerStore
import com.example.gymtrack.feature.editor.EditorViewModel
import com.example.gymtrack.feature.editor.NoteEditor
import com.example.gymtrack.feature.editor.shouldShowActiveWorkoutControls
import com.example.gymtrack.feature.home.HomeViewModel
import com.example.gymtrack.feature.home.NotesScreen
import com.example.gymtrack.feature.settings.SettingsScreen
import com.example.gymtrack.feature.stats.StatsScreen
import com.example.gymtrack.feature.stats.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
    val appContext = LocalContext.current.applicationContext
    val retainedStatsViewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(noteRepository, appContext),
    )

    NavHost(navController = navController, startDestination = "notes") {
        composable("notes") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(noteRepository, workoutRepository),
            )
            val notes by homeViewModel.notes.collectAsState()
            val importSummary by homeViewModel.legacyCsvImportSummary.collectAsState()
            val importProgress by homeViewModel.legacyCsvImportProgress.collectAsState()
            val nextWorkoutSuggestion by homeViewModel.nextWorkoutSuggestion.collectAsState()

            LaunchedEffect(importSummary) {
                val summary = importSummary ?: return@LaunchedEffect
                if (summary.selected > 0) {
                    Toast.makeText(
                        context,
                        summary.toUserMessage(),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                homeViewModel.clearLegacyCsvImportSummary()
            }

            var selectedNotes by remember { mutableStateOf<Set<NoteLine>>(emptySet()) }

            NotesScreen(
                notes = notes,
                selectedNotes = selectedNotes,
                onSelect = { selectedNotes = it },
                onEdit = { note ->
                    navController.navigate("editor?noteId=${note.timestamp}")
                },
                onDelete = { selected ->
                    homeViewModel.deleteNotes(selected)
                    selectedNotes = emptySet()
                },
                onExport = { selected ->
                    if (selected.isNotEmpty()) {
                        scope.launch {
                            try {
                                homeViewModel.exportNotes(context, selected, settings)
                                Toast.makeText(
                                    context,
                                    "Exported ${selected.size} notes to Downloads",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                selectedNotes = emptySet()
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context,
                                    "Export failed: ${error.localizedMessage}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "No notes selected", Toast.LENGTH_SHORT).show()
                    }
                },
                onCreate = { navController.navigate("editor?noteId=-1") },
                onImport = { uris ->
                    homeViewModel.importNotesFromUris(context, uris, settings)
                },
                legacyCsvImportProgress = importProgress,
                showLegacyCsvImport = BuildConfig.DEBUG,
                nextWorkoutSuggestion = nextWorkoutSuggestion,
                onDismissNextWorkoutSuggestion = homeViewModel::dismissNextWorkoutSuggestion,
                onStartSuggestedWorkout = { suggestion ->
                    Toast.makeText(
                        context,
                        "Opening blank workout. Suggested label: ${suggestion.workoutLabel}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    navController.navigate("editor?noteId=-1")
                },
                onOpenSettings = { navController.navigate("settings") },
                onOpenStats = { navController.navigate("stats") },
                onSwipeRight = {},
                settings = settings,
            )
        }

        composable("editor?noteId={noteId}") { backStackEntry ->
            val context = LocalContext.current
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
            val timerStates = remember(context) { NoteTimerStore.observe(context) }
            val timerState by timerStates.collectAsState(initial = NoteTimerState())
            val isActiveWorkout = shouldShowActiveWorkoutControls(
                noteId = noteId,
                activeNoteTimestamp = timerState.activeNoteTimestamp,
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
            )
        }

        composable("stats") {
            val state by retainedStatsViewModel.uiState.collectAsState()

            StatsScreen(
                state = state,
                workoutRepository = workoutRepository,
                settings = settings,
                onTimeRangeSelected = { retainedStatsViewModel.setTimeRange(it) },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
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
                val restoreSource = pendingRestoreUri
                if (destination == null || restoreSource == null) {
                    clearPendingRestore()
                    return@rememberLauncherForActivityResult
                }
                scope.launch {
                    operationInProgress = true
                    try {
                        backupRepository.createBackup(
                            contentResolver = context.contentResolver,
                            destination = destination,
                            settings = settings,
                            appVersion = BuildConfig.VERSION_NAME,
                            databaseSchemaVersion = CURRENT_DATABASE_SCHEMA_VERSION,
                        )
                        operationInProgress = false
                        restoreSelectedBackup(restoreSource)
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            "Safety backup failed: ${error.localizedMessage}",
                            Toast.LENGTH_LONG,
                        ).show()
                        operationInProgress = false
                    }
                }
            }

            val restoreBackupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { source ->
                source ?: return@rememberLauncherForActivityResult
                scope.launch {
                    operationInProgress = true
                    try {
                        val manifest = backupRepository.inspectBackup(context.contentResolver, source)
                        pendingRestoreUri = source
                        pendingRestoreManifest = manifest
                        pendingRestoreHasLocalData = backupRepository.hasLocalData()
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            "Could not read backup: ${error.localizedMessage}",
                            Toast.LENGTH_LONG,
                        ).show()
                    } finally {
                        operationInProgress = false
                    }
                }
            }

            SettingsScreen(
                settings = settings,
                onSettingsChange = onSettingsUpdate,
                onBack = { navController.popBackStack() },
                onCreateBackup = {
                    createBackupLauncher.launch(backupFileName("gymtrack-backup"))
                },
                onRestoreBackup = {
                    restoreBackupLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                },
                isBackupOperationInProgress = operationInProgress,
            )

            pendingRestoreManifest?.let { manifest ->
                RestoreConfirmationDialog(
                    manifest = manifest,
                    hasLocalData = pendingRestoreHasLocalData,
                    onDismiss = { clearPendingRestore() },
                    onRestore = {
                        pendingRestoreUri?.let(::restoreSelectedBackup)
                    },
                    onBackupThenRestore = {
                        safetyBackupBeforeRestoreLauncher.launch(backupFileName("gymtrack-safety-backup"))
                    },
                )
            }
        }
    }
}

@Composable
private fun RestoreConfirmationDialog(
    manifest: BackupManifest,
    hasLocalData: Boolean,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onBackupThenRestore: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "Restore backup?",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Backup created ${manifest.createdAtIso}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${manifest.counts.totalRecords} records · schema ${manifest.databaseSchemaVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (hasLocalData) {
                        "This will replace your current local GymTrack data. Create a safety backup first unless you are sure."
                    } else {
                        "Your current local GymTrack data appears empty."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (hasLocalData) {
                        TextButton(onClick = onBackupThenRestore) { Text("Backup first") }
                    }
                    TextButton(onClick = onRestore) { Text("Restore") }
                }
            }
        }
    }
}
