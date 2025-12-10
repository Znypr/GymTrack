package com.example.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gymtrack.data.NoteDatabase
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.SettingsStore
import com.example.gymtrack.data.repository.NoteRepository
import com.example.gymtrack.data.WorkoutRepository
import com.example.gymtrack.presentation.home.HomeViewModel
import com.example.gymtrack.ui.screens.NavigationHost
import com.example.gymtrack.ui.theme.GymTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize Database & Repositories immediately
        val db = NoteDatabase.getDatabase(applicationContext)
        val noteRepository = NoteRepository(db.noteDao())
        // We keep WorkoutRepository for the legacy/CSV logic for now
        val workoutRepository = WorkoutRepository(db.noteDao(), db.exerciseDao(), db.setDao())
        val homeViewModelFactory = HomeViewModel.Factory(noteRepository)

        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                settingsState.value = SettingsStore.load(context)
                // Run migration in background on startup
                withContext(Dispatchers.IO) {
                    workoutRepository.checkAndMigrate()
                }
            }

            LaunchedEffect(settingsState.value) {
                SettingsStore.save(context, settingsState.value)
            }

            GymTrackTheme(darkTheme = settingsState.value.darkMode) {
                val lastRoute = rememberSaveable { mutableStateOf("main") }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()

                LaunchedEffect(navBackStackEntry) {
                    val route = navBackStackEntry?.destination?.route
                    if (route != null && route !in setOf("settings", "edit")) {
                        lastRoute.value = route
                    }
                }

                val start = if (lastRoute.value == "settings") "main" else lastRoute.value

                // 2. Pass repositories to NavigationHost
                NavigationHost(
                    navController = navController,
                    settingsState = settingsState,
                    noteRepository = noteRepository,       // NEW
                    workoutRepository = workoutRepository, // NEW
                    startDestination = start
                )
            }
        }
    }
}