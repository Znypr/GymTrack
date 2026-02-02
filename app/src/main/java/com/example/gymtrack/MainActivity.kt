package com.example.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import com.example.gymtrack.feature.editor.EditorViewModel
import com.example.gymtrack.feature.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Database & Repositories from CORE
        val db = NoteDatabase.getDatabase(applicationContext)
        val noteRepository = NoteRepository(db.noteDao())
        val workoutRepository = WorkoutRepository(db.noteDao(), db.exerciseDao(), db.setDao())

        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            val context = LocalContext.current

            // Load Settings
            LaunchedEffect(Unit) {
                settingsState.value = SettingsStore.load(context)
                withContext(Dispatchers.IO) {
                    workoutRepository.checkAndMigrate()
                }
            }

            // Save Settings
            LaunchedEffect(settingsState.value) {
                SettingsStore.save(context, settingsState.value)
            }

            // 2. ViewModels Factories
            val homeFactory = HomeViewModel.Factory(noteRepository)
            // Note: You need to update EditorViewModel to accept a Factory if you haven't already,
            // or instantiate it manually in the graph if using simpler DI.
            val editorFactory = EditorViewModel.Factory(
                -1L, // Initial ID is handled inside NavigationHost usually
                noteRepository,
                workoutRepository,
                applicationContext
            )

            GymTrackTheme(darkTheme = settingsState.value.darkMode) {
                val navController = rememberNavController()

                NavigationHost(
                    navController = navController,
                    settings = settingsState.value,
                    onSettingsUpdate = { newSettings -> settingsState.value = newSettings },
                    noteRepository = noteRepository,
                    workoutRepository = workoutRepository
                )
            }
        }
    }
}