package com.example.gymtrack.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.gymtrack.data.NoteDatabase
import com.example.gymtrack.data.NoteEntity
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.NoteDao
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.WorkoutRepository
import com.example.gymtrack.util.exportNote
import com.example.gymtrack.util.importNote
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.gymtrack.util.WorkoutParser
import com.example.gymtrack.util.importAndProcessCsv

@Composable
fun NavigationHost(
    navController: NavHostController,
    settingsState: MutableState<Settings>,
    startDestination: String = "main"
) {
    val context = LocalContext.current
    val daoState = remember { mutableStateOf<NoteDao?>(null) }
    val repoState = remember { mutableStateOf<WorkoutRepository?>(null) }

    var notes by remember { mutableStateOf(listOf<NoteLine>()) }
    var selectedNotes by remember { mutableStateOf(setOf<NoteLine>()) }
    var currentNote by remember { mutableStateOf<NoteLine?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            CoroutineScope(Dispatchers.IO).launch {
                val importedNotes = mutableListOf<NoteLine>()
                val parser = WorkoutParser()

                // FIX: Use forEachIndexed to get the unique index
                uris.forEachIndexed { index, uri ->
                    val temp = java.io.File.createTempFile("import", ".csv", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }

                    val note = importNote(temp, settingsState.value)

                    if (note != null) {
                        // CRITICAL FIX: Add the index to the timestamp to guarantee uniqueness
                        val uniqueWorkoutId = note.timestamp + index

                        val rawTextLines = note.text.split('\n')
                        val correctedSets = importAndProcessCsv(
                            csvRows = rawTextLines,
                            parser = parser,
                            workoutTimestamp = uniqueWorkoutId // Use the unique ID
                        )

                        // 1. Save corrected sets (uses the unique ID for DELETE/INSERT)
                        repoState.value?.saveParsedSets(correctedSets, uniqueWorkoutId)

                        // 2. Save the NoteEntity blob (for UI/editing)
                        val entity = NoteEntity(
                            timestamp = uniqueWorkoutId, // Use the unique ID here
                            title = note.title,
                            text = note.text,
                            categoryName = note.categoryName,
                            categoryColor = note.categoryColor,
                            learnings = note.learnings
                        )
                        daoState.value?.insert(entity)

                        // Final housekeeping
                        exportNote(context, note, settingsState.value)
                        importedNotes += note
                    }
                }
                withContext(Dispatchers.Main) {
                    val msg = if (importedNotes.size == 1) "Imported ${importedNotes.first().title}" else "Imported ${importedNotes.size} notes"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

    LaunchedEffect(Unit) {
        val db = NoteDatabase.getDatabase(context)
        val dao = db.noteDao()
        daoState.value = dao

        // NEW: Initialize Repo and run migration if needed
        val repo = WorkoutRepository(dao, db.exerciseDao(), db.setDao())
        repoState.value = repo
        withContext(Dispatchers.IO) {
            repo.checkAndMigrate()
        }

        dao.getAll().collect { entities ->
            notes = entities.map {
                NoteLine(it.title, it.text, it.timestamp, it.categoryName, it.categoryColor, it.learnings ?: "")
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("main") {
            NotesScreen(
                notes = notes,
                selectedNotes = selectedNotes,
                onSelect = { selectedNotes = it },
                onEdit = {
                    currentNote = it
                    navController.navigate("edit")
                },
                onDelete = { toDelete ->
                    daoState.value?.let { dao ->
                        CoroutineScope(Dispatchers.IO).launch {
                            toDelete.forEach {
                                dao.delete(NoteEntity(it.timestamp, it.title, it.text, it.categoryName, it.categoryColor, it.learnings))
                            }
                            withContext(Dispatchers.Main) { selectedNotes = emptySet() }
                        }
                    }
                },
                onExport = { toExport ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val files = toExport.map { exportNote(context, it, settingsState.value) }
                        withContext(Dispatchers.Main) {
                            val msg = if (files.size == 1) "Exported ${files.first().name}" else "Exported ${files.size} notes"
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    selectedNotes = emptySet()
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
            StatsScreen(
                notes = notes,
                settings = settingsState.value,
                repository = repoState.value, // <--- PASSED HERE
                onBack = {
                    if (!navController.popBackStack("main", false)) {
                        navController.navigate("main")
                    }
                },
            )
        }
        composable("edit") {
            val lastTimestamp = notes.maxOfOrNull { it.timestamp }
            val isLast = currentNote == null || currentNote?.timestamp == lastTimestamp
            NoteEditor(
                note = currentNote,
                settings = settingsState.value,
                isLastNote = isLast,
                onSave = { title, text, category, learn, start ->
                    val updated = currentNote?.copy(
                        title = title, text = text, categoryName = category?.name, categoryColor = category?.color, learnings = learn,
                    ) ?: NoteLine(title, text, start, category?.name, category?.color, learn)

                    daoState.value?.let { dao ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val entity = NoteEntity(
                                updated.timestamp, updated.title, updated.text, updated.categoryName, updated.categoryColor, updated.learnings
                            )
                            // 1. Save Legacy
                            dao.insert(entity)
                            // 2. NEW: Parse & Save Structure
                            repoState.value?.syncNoteToWorkout(entity)

                            exportNote(context, updated, settingsState.value)
                        }
                    }
                },
                onCancel = { navController.popBackStack() },
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