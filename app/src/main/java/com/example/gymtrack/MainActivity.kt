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

// ... imports ...

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Database & Repositories
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

            GymTrackTheme(darkTheme = settingsState.value.darkMode) {
                // [FIX] Just create the controller
                val navController = rememberNavController()

                // [FIX] Pass fixed startDestination = "main"
                NavigationHost(
                    navController = navController,
                    settingsState = settingsState,
                    noteRepository = noteRepository,
                    workoutRepository = workoutRepository,
                    startDestination = "main"
                )
            }
        }
    }
}