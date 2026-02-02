package com.example.gymtrack.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.repository.NoteRepository
import com.example.gymtrack.data.WorkoutRepository
import com.example.gymtrack.feature.editor.NoteEditor
import com.example.gymtrack.presentation.home.HomeViewModel
import com.example.gymtrack.util.WorkoutParser
import com.example.gymtrack.util.exportNote
import com.example.gymtrack.util.importAndProcessCsv
import com.example.gymtrack.util.importNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NavigationHost(
    navController: NavHostController,
    settingsState: MutableState<Settings>,
    noteRepository: NoteRepository,
    workoutRepository: WorkoutRepository,
    startDestination: String = "main"
) {
    val context = LocalContext.current

    // REVERTED: Standard ViewModel factory
    val homeViewModelFactory = HomeViewModel.Factory(noteRepository)

    var selectedNotes by remember { mutableStateOf(setOf<NoteLine>()) }
    var currentNote by remember { mutableStateOf<NoteLine?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            CoroutineScope(Dispatchers.IO).launch {
                val importedNotes = mutableListOf<NoteLine>()
                val parser = WorkoutParser()

                uris.forEachIndexed { index, uri ->
                    val temp = java.io.File.createTempFile("import", ".csv", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }

                    val note = importNote(temp, settingsState.value)

                    if (note != null) {
                        val uniqueWorkoutId = note.timestamp + index
                        val rawTextLines = note.text.split('\n')
                        val correctedSets = importAndProcessCsv(
                            csvRows = rawTextLines,
                            parser = parser,
                            workoutTimestamp = uniqueWorkoutId
                        )
                        workoutRepository.saveParsedSets(correctedSets, uniqueWorkoutId)
                        noteRepository.saveNote(note.copy(timestamp = uniqueWorkoutId))
                        exportNote(context, note, settingsState.value)
                        importedNotes += note
                    }
                }
                withContext(Dispatchers.Main) {
                    val msg = if (importedNotes.size == 1) "Imported ${importedNotes.first().title}" else "Imported ${importedNotes.size} notes"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("main") {
            // REVERTED: ViewModel created locally here.
            // This is the standard, stable Android pattern.
            val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory)
            val notes by homeViewModel.notes.collectAsState()

            NotesScreen(
                notes = notes,
                selectedNotes = selectedNotes,
                onSelect = { selectedNotes = it },
                onEdit = { note ->
                    navController.navigate("edit?noteId=${note.timestamp}")
                },
                onDelete = { homeViewModel.deleteNotes(it) },
                onExport = { toExport ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val files = homeViewModel.exportNotes(context, toExport, settingsState.value)
                        val msg = if (files.size == 1) "Exported ${files.first().name}" else "Exported ${files.size} notes"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        selectedNotes = emptySet()
                    }
                },
                onCreate = {
                    navController.navigate("edit")
                },
                onImport = { importLauncher.launch("text/*") },
                onOpenSettings = { navController.navigate("settings") },
                onSwipeRight = { navController.navigate("stats") },
                settings = settingsState.value,
            )
        }

        composable("stats") {
            val statsViewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.Factory(noteRepository)
            )
            val statsState by statsViewModel.uiState.collectAsState()

            StatsScreen(
                state = statsState,
                settings = settingsState.value,
                onBack = { navController.popBackStack() },
                workoutRepository = workoutRepository
            )
        }

        composable(
            route = "edit?noteId={noteId}",
            arguments = listOf(navArgument("noteId") { defaultValue = -1L })
        ) { backStackEntry ->
            val isLast = backStackEntry.arguments?.getBoolean("isLast") ?: false
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: -1L

            // Pass 'currentNote' directly to the factory
            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.Factory(
                    noteId = noteId, // Changed from 'note' object to 'noteId'
                    noteRepo = noteRepository,
                    workoutRepo = workoutRepository,
                    context = context.applicationContext
                )
            )

            NoteEditor(
                viewModel = editorViewModel,
                settings = settingsState.value,
                isLastNote = isLast,
                onCancel = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                settings = settingsState.value,
                onChange = { settingsState.value = it },
                onBack = { navController.popBackStack() },
            )
        }
    }
}