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
import com.example.gymtrack.util.exportNote
import com.example.gymtrack.util.importNote
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

@Composable
fun NavigationHost(
    navController: NavHostController,
    settingsState: MutableState<Settings>,
    startDestination: String = "main"
) {
    val context = LocalContext.current
    val daoState = remember { mutableStateOf<NoteDao?>(null) }
    var notes by remember { mutableStateOf(listOf<NoteLine>()) }
    var selectedNotes by remember { mutableStateOf(setOf<NoteLine>()) }
    var currentNote by remember { mutableStateOf<NoteLine?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            CoroutineScope(Dispatchers.IO).launch {
                val importedNotes = mutableListOf<NoteLine>()
                uris.forEach { uri ->
                    val temp = java.io.File.createTempFile("import", ".csv", context.cacheDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    val note = importNote(temp, settingsState.value)
                    if (note != null) {
                        daoState.value?.insert(
                            NoteEntity(
                                note.timestamp,
                                note.title,
                                note.text,
                                note.categoryName,
                                note.categoryColor,
                                note.learnings,
                            )
                        )
                        exportNote(context, note, settingsState.value)
                        importedNotes += note
                    }
                }
                withContext(Dispatchers.Main) {
                    if (importedNotes.isNotEmpty()) {
                        val msg = if (importedNotes.size == 1)
                            "Imported ${importedNotes.first().title}"
                        else
                            "Imported ${importedNotes.size} notes"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to import", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    LaunchedEffect(Unit) {
        val dao = withContext(Dispatchers.IO) {
            NoteDatabase.getDatabase(context).noteDao()
        }
        daoState.value = dao
        dao.getAll().collect { entities ->
            notes = entities.map {
                NoteLine(
                    it.title,
                    it.text,
                    it.timestamp,
                    it.categoryName,
                    it.categoryColor,
                    it.learnings ?: "",
                )
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
                                dao.delete(
                                    NoteEntity(
                                        it.timestamp,
                                        it.title,
                                        it.text,
                                        it.categoryName,
                                        it.categoryColor,
                                        it.learnings,
                                    )
                                )
                            }
                            withContext(Dispatchers.Main) {
                                selectedNotes = emptySet()
                            }
                        }
                    }
                },
                onExport = { toExport ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val files = toExport.map {
                            exportNote(context, it, settingsState.value)
                        }
                        withContext(Dispatchers.Main) {
                            val msg = if (files.size == 1)
                                "Exported ${files.first().name}"
                            else
                                "Exported ${files.size} notes"
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
                settings = settingsState.value,
            )
        }
        composable("edit") {
            NoteEditor(
                note = currentNote,
                settings = settingsState.value,
                onSave = { title, text, category, learn, start ->
                    val updated = currentNote?.copy(
                        title = title,
                        text = text,
                        categoryName = category?.name,
                        categoryColor = category?.color,
                        learnings = learn,
                    ) ?: NoteLine(
                        title,
                        text,
                        start,
                        category?.name,
                        category?.color,
                        learn,
                    )
                    daoState.value?.let { dao ->
                        CoroutineScope(Dispatchers.IO).launch {
                           dao.insert(
                               NoteEntity(
                                   updated.timestamp,
                                   updated.title,
                                   updated.text,
                                   updated.categoryName,
                                   updated.categoryColor,
                                   updated.learnings,
                               )
                           )
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
