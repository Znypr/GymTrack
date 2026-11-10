package com.example.gymtrack

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.gymtrack.core.backup.BackupRepository
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.SettingsSession
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

        // #291: settings are loaded once before anything is rendered or saved. The session
        // never writes on load, so stored settings cannot be replaced by defaults.
        val settingsSession = SettingsSession(
            scope = lifecycleScope,
            load = { SettingsStore.load(applicationContext) },
            save = { settings -> SettingsStore.save(applicationContext, settings) },
            onLoadError = { error -> Log.e("Settings", "Could not read stored settings", error) },
        )
        settingsSession.start()

        setContent {
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    runCatching { workoutRepository.checkAndMigrate() }
                        .onFailure { error ->
                            Log.e("LegacyStats", "Legacy workout statistics compatibility repair failed", error)
                        }

                    runCatching { canonicalImportRunner.run() }
                        .onFailure { error ->
                            Log.e("CanonicalImport", "Canonical workout import failed", error)
                        }
                }
            }

            val loadedSettings by settingsSession.settings.collectAsState()
            // Render nothing until stored settings are known: avoids the dark/light flash.
            val settings = loadedSettings ?: return@setContent

            GymTrackTheme(themeChoice = settings.themeChoice) {
                val navController = rememberNavController()

                NavigationHost(
                    navController = navController,
                    settings = settings,
                    onSettingsUpdate = { newSettings -> settingsSession.update(newSettings) },
                    noteRepository = noteRepository,
                    workoutRepository = workoutRepository,
                    backupRepository = backupRepository,
                )
            }
        }
    }
}
