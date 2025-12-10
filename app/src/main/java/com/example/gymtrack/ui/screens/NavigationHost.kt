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
import com.example.gymtrack.data.NoteEntity
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.repository.NoteRepository
import com.example.gymtrack.data.WorkoutRepository
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
    noteRepository: NoteRepository,       // NEW: Received from MainActivity
    workoutRepository: WorkoutRepository, // NEW: Received from MainActivity
    startDestination: String = "main"
) {
    val context = LocalContext.current

    // Notes are now observed directly from the repository flow
    // "collectAsState" converts the Flow to a UI State
    val notes by noteRepository.getAllNotes().collectAsState(initial = emptyList())

    var selectedNotes by remember { mutableStateOf(setOf<NoteLine>()) }
    var currentNote by remember { mutableStateOf<NoteLine?>(null) }
    val homeViewModelFactory = HomeViewModel.Factory(noteRepository)

    // --- IMPORT LOGIC ---
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

                        // Save Structured Data
                        workoutRepository.saveParsedSets(correctedSets, uniqueWorkoutId)

                        // Save Note Entity (UI)
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
            // 1. Get VM
            val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory) // Pass this factory in

            // 2. Observe VM state instead of repo
            val notes by homeViewModel.notes.collectAsState()
            NotesScreen(
                notes = notes,
                selectedNotes = selectedNotes,
                onSelect = { selectedNotes = it },
                onEdit = {
                    currentNote = it
                    navController.navigate("edit")
                },
                onDelete = { homeViewModel.deleteNotes(it) },
                onExport = { toExport ->
                    // We launch in the UI scope (Effectively Main thread) to handle the Toast
                    CoroutineScope(Dispatchers.Main).launch {
                        // 1. Call the heavy function in the VM (Suspends, doesn't block UI)
                        val files = homeViewModel.exportNotes(context, toExport, settingsState.value)

                        // 2. Resume on Main Thread to show Toast
                        val msg = if (files.size == 1) "Exported ${files.first().name}" else "Exported ${files.size} notes"
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

                        // 3. Clear selection
                        selectedNotes = emptySet()
                    }
                },
                onCreate = {
                    currentNote = null
                    navController.navigate("edit")
                },
                onImport = { importLauncher.launch("text/*") },
                onOpenSettings = { navController.navigate("settings") },
                onSwipeRight = { navController.navigate("stats") },
                settings = settingsState.value,
            )
        }

        composable("stats") {
            // 1. Create the StatsViewModel using the factory
            val statsViewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.Factory(noteRepository) // Wired correctly now!
            )
            val statsState by statsViewModel.uiState.collectAsState()

            StatsScreen(
                state = statsState,
                settings = settingsState.value,
                onBack = { navController.popBackStack() }
            )
        }

        composable("edit") {
            val lastTimestamp = notes.maxOfOrNull { it.timestamp }
            val isLast = currentNote == null || currentNote?.timestamp == lastTimestamp

            // Create the ViewModel
            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.Factory(
                    noteId = currentNote?.timestamp,
                    noteRepo = noteRepository,
                    workoutRepo = workoutRepository,
                    context = context.applicationContext
                )
            )

            // Initialize it with the passed data (if any)
            // LaunchedEffect ensures this runs only once
            LaunchedEffect(currentNote) {
                editorViewModel.initialize(currentNote)
            }

            NoteEditor(
                viewModel = editorViewModel, // <-- Pass VM instead of raw data
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