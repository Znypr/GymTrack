// FILE PATH: C:\Users\znypr\AndroidStudioProjects\GymTrack\app\src\main\java\com\example\gymtrack\NavigationHost.kt

package com.example.gymtrack

import android.widget.Toast
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
import com.example.gymtrack.core.data.NoteLine
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.feature.editor.EditorViewModel
import com.example.gymtrack.feature.editor.NoteEditor
import com.example.gymtrack.feature.home.HomeViewModel
import com.example.gymtrack.feature.home.NotesScreen
import com.example.gymtrack.feature.settings.SettingsScreen
import com.example.gymtrack.feature.stats.StatsScreen
import com.example.gymtrack.feature.stats.StatsViewModel
import kotlinx.coroutines.launch

@Composable
fun NavigationHost(
    navController: NavHostController,
    settings: Settings,
    onSettingsUpdate: (Settings) -> Unit,
    noteRepository: NoteRepository,
    workoutRepository: WorkoutRepository
) {
    NavHost(navController = navController, startDestination = "notes") {

        // 1. HOME SCREEN
        composable("notes") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(noteRepository, workoutRepository)
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
                    selectedNotes = emptySet() },
                onExport = { selected ->
                    if (selected.isNotEmpty()) {
                        scope.launch {
                            try {
                                homeViewModel.exportNotes(context, selected, settings)
                                Toast.makeText(context, "Exported ${selected.size} notes to Downloads", Toast.LENGTH_SHORT).show()
                                selectedNotes = emptySet()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "No notes selected", Toast.LENGTH_SHORT).show()
                    }
                },
                onCreate = { navController.navigate("editor?noteId=-1") },
                // [FIX] Updated for multiple files
                onImport = { uris ->
                    homeViewModel.importNotesFromUris(context, uris, settings)
                },
                onOpenSettings = { navController.navigate("settings") },
                onOpenStats = { navController.navigate("stats") },
                onSwipeRight = { /* Swipe logic */ },
                settings = settings
            )
        }

        // ... (Rest of the composables remain unchanged) ...
        composable("editor?noteId={noteId}") { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")?.toLongOrNull() ?: -1L
            val context = LocalContext.current

            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.Factory(
                    noteId,
                    noteRepository,
                    workoutRepository,
                    context
                )
            )

            NoteEditor(
                viewModel = editorViewModel,
                settings = settings,
                isLastNote = true,
                onCancel = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }

        composable("stats") {
            val statsViewModel: StatsViewModel = viewModel(
                factory = StatsViewModel.Factory(noteRepository)
            )
            val state by statsViewModel.uiState.collectAsState()

            StatsScreen(
                state = state,
                workoutRepository = workoutRepository,
                settings = settings,
                onTimeRangeSelected = { statsViewModel.setTimeRange(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                settings = settings,
                onUpdate = onSettingsUpdate,
                onBack = { navController.popBackStack() }
            )
        }
    }
}