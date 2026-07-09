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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
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
                    val draftText = suggestion.suggestedExercises.joinToString("\n\n")
                    val encodedCategory = Uri.encode(suggestion.workoutLabel)
                    val encodedDraftText = Uri.encode(draftText)
                    Toast.makeText(
                        context,
                        if (suggestion.suggestedExercises.isEmpty()) {
                            "Opening blank workout. Suggested label: ${suggestion.workoutLabel}"
                        } else {
                            "Opening ${suggestion.workoutLabel} draft with ${suggestion.suggestedExercises.size} exercises"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    navController.navigate(
                        "editor?noteId=-1&suggestedCategory=$encodedCategory&suggestedText=$encodedDraftText",
                    )
                },
                onOpenSettings = { navController.navigate("settings") },
                onOpenStats = { navController.navigate("stats") },
                onSwipeRight = {},
                settings = settings,
            )
        }

        composable(
            route = "editor?noteId={noteId}&suggestedCategory={suggestedCategory}&suggestedText={suggestedText}",
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.StringType
                    defaultValue = "-1"
                },
                navArgument("suggestedCategory") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("suggestedText") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val context = LocalContext.current
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
            val suggestedCategory = backStackEntry.arguments?.getString("suggestedCategory")
                ?.takeIf { it.isNotBlank() }
            val suggestedText = backStackEntry.arguments?.getString("suggestedText").orEmpty()
            val timerStates = remember(context) { NoteTimerStore.observe(context) }
            val timerState by timerStates.collectAsState(initial = NoteTimerState())
            val isActiveWorkout = shouldShowActiveWorkoutControls(
                noteId = noteId,
                activeNoteTimestamp = timerState.activeNoteTimestamp,
            )

            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.Factory(
                    noteId = noteId,
                    noteRepo = noteRepository,
                    workoutRepo = workoutRepository,
                    suggestedCategoryName = suggestedCategory,
                    suggestedDraftText = suggestedText,
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
                    createBackupLauncher.launch(backupFileName("GymTrack-backup"))
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
                            backupFileName("GymTrack-safety-backup-before-restore"),
                        )
                    },
                    onRestoreWithoutBackup = { restoreSelectedBackup(restoreUri) },
                    onDismiss = { clearPendingRestore() },
                )
            }
        }
    }
}

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
                        text = "This validated backup contains $totalRecords records. Your current GymTrack data will be replaced.",
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
