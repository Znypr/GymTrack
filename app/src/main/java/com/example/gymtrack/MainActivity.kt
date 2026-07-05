package com.example.gymtrack

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.gymtrack.core.backup.BackupRepository
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.data.WorkoutRepository
import com.example.gymtrack.core.data.repository.NoteRepository
import com.example.gymtrack.core.data.transition.CanonicalImportRunner
import com.example.gymtrack.core.ui.theme.GymTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = NoteDatabase.getDatabase(applicationContext)
        val noteRepository = NoteRepository(db.noteDao())
        val workoutRepository = WorkoutRepository(db)
        val backupRepository = BackupRepository(db)
        val canonicalImportRunner = CanonicalImportRunner(db)

        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                settingsState.value = SettingsStore.load(context)
                withContext(Dispatchers.IO) {
                    runCatching {
                        workoutRepository.forceUpdateStats()
                        workoutRepository.cleanUpOrphans()
                    }.onFailure { error ->
                        Log.e("LegacyStats", "Legacy workout statistics rebuild failed", error)
                    }

                    runCatching { canonicalImportRunner.run() }
                        .onFailure { error ->
                            Log.e("CanonicalImport", "Canonical workout import failed", error)
                        }
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
                    workoutRepository = workoutRepository,
                    backupRepository = backupRepository,
                )
            }
        }
    }
}
