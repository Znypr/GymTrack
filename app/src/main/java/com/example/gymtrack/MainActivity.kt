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
import com.example.gymtrack.feature.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = NoteDatabase.getDatabase(applicationContext)
        val noteRepository = NoteRepository(db.noteDao())
        val workoutRepository = WorkoutRepository(db.noteDao(), db.exerciseDao(), db.setDao())

        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                settingsState.value = SettingsStore.load(context)
                withContext(Dispatchers.IO) {
                    // [FIX] This re-reads ALL notes and rebuilds the charts.
                    // This will restore your missing 100+ sets immediately.
                    workoutRepository.forceUpdateStats()
                    workoutRepository.cleanUpOrphans()
                }
            }

            LaunchedEffect(settingsState.value) {
                SettingsStore.save(context, settingsState.value)
            }

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