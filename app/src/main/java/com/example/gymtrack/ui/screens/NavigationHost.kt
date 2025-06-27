package com.example.gymtrack.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.gymtrack.data.NoteDatabase
import com.example.gymtrack.data.NoteEntity
import com.example.gymtrack.data.NoteLine
import com.example.gymtrack.data.NoteDao
import com.example.gymtrack.data.Settings
import com.example.gymtrack.ui.screens.StatsScreen
import com.example.gymtrack.util.exportNote
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    LaunchedEffect(Unit) {
        val dao = withContext(Dispatchers.IO) {
            NoteDatabase.getDatabase(context).noteDao()
        }
        daoState.value = dao
        val retrieved = withContext(Dispatchers.IO) { dao.getAll() }
        notes = retrieved.map {
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
                                notes = notes.filterNot { it in toDelete }
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
                onOpenSettings = { navController.navigate("settings") },
                onSwipeRight = { navController.navigate("stats") },
                settings = settingsState.value,
            )
        }
        composable("stats") {
            StatsScreen(
                notes = notes,
                settings = settingsState.value,
                onBack = { navController.popBackStack() },
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
                            withContext(Dispatchers.Main) {
                                notes = if (notes.any { it.timestamp == updated.timestamp }) {
                                    notes.map { if (it.timestamp == updated.timestamp) updated else it }
                                } else {
                                    notes + updated
                                }
                            }
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
