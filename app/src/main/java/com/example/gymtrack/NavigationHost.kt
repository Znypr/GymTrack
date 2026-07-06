package com.example.gymtrack

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun NavigationHost(
    navController: NavHostController,
    settings: Settings,
    onSettingsUpdate: (Settings) -> Unit,
    noteRepository: NoteRepository,
    workoutRepository: WorkoutRepository,
    backupRepository: BackupRepository,
) {
    NavHost(navController = navController, startDestination = "notes") {
        composable("notes") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(noteRepository, workoutRepository),
            )
            val notes by homeViewModel.notes.collectAsState()

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
            val statsViewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.Factory(noteRepository),
            )
            val state by statsViewModel.uiState.collectAsState()

            StatsScreen(
                state = state,
                workoutRepository = workoutRepository,
                settings = settings,
                onTimeRangeSelected = { statsViewModel.setTimeRange(it) },
                onBack = { navController.popBackStack() },
            )
        }

        composable("settings") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var operationInProgress by remember { mutableStateOf(false) }
            var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
            var pendingRestoreManifest by remember { mutableStateOf<BackupManifest?>(null) }

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
                            databaseSchemaVersion = 9,
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

            val restoreBackupLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { source ->
                source ?: return@rememberLauncherForActivityResult
                scope.launch {
                    operationInProgress = true
                    try {
                        pendingRestoreManifest = backupRepository.inspectBackup(
                            context.contentResolver,
                            source,
                        )
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
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    createBackupLauncher.launch("GymTrack-backup-$date.gymtrack-backup")
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
                AlertDialog(
                    onDismissRequest = {
                        pendingRestoreUri = null
                        pendingRestoreManifest = null
                    },
                    title = { Text("Replace all local data?") },
                    text = {
                        Text(
                            "This validated backup contains ${restoreManifest.counts.totalRecords} records. " +
                                "Your current GymTrack data will be replaced.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingRestoreUri = null
                                pendingRestoreManifest = null
                                scope.launch {
                                    operationInProgress = true
                                    try {
                                        val result = backupRepository.restoreBackup(
                                            context = context.applicationContext,
                                            contentResolver = context.contentResolver,
                                            source = restoreUri,
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
                            },
                        ) { Text("Replace and restore") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                pendingRestoreUri = null
                                pendingRestoreManifest = null
                            },
                        ) { Text("Cancel") }
                    },
                )
            }
        }
    }
}
