package com.example.gymtrack.feature.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.gymtrack.BuildConfig
import com.example.gymtrack.core.backup.BackupManifest
import com.example.gymtrack.core.backup.BackupRepository
import com.example.gymtrack.core.data.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BackupSettingsRoute(
    settings: Settings,
    onSettingsUpdate: (Settings) -> Unit,
    backupRepository: BackupRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedManifest by remember { mutableStateOf<BackupManifest?>(null) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { destination ->
        if (destination != null) {
            scope.launch {
                busy = true
                try {
                    val result = backupRepository.createBackup(
                        contentResolver = context.contentResolver,
                        destination = destination,
                        settings = settings,
                        appVersion = BuildConfig.VERSION_NAME,
                        databaseSchemaVersion = 9,
                    )
                    Toast.makeText(
                        context,
                        "Backup created: ${result.manifest.counts.totalRecords} records",
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        "Backup failed: ${error.localizedMessage}",
                        Toast.LENGTH_LONG,
                    ).show()
                } finally {
                    busy = false
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        if (source != null) {
            scope.launch {
                busy = true
                try {
                    selectedManifest = backupRepository.inspectBackup(context.contentResolver, source)
                    selectedUri = source
                } catch (error: Exception) {
                    Toast.makeText(
                        context,
                        "Invalid backup: ${error.localizedMessage}",
                        Toast.LENGTH_LONG,
                    ).show()
                } finally {
                    busy = false
                }
            }
        }
    }

    SettingsScreen(
        settings = settings,
        onUpdate = onSettingsUpdate,
        onBack = onBack,
        onCreateBackup = {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            createLauncher.launch("GymTrack-backup-$date.gymtrack-backup")
        },
        onRestoreBackup = {
            restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip"))
        },
        dataOperationInProgress = busy,
    )

    val restoreUri = selectedUri
    val manifest = selectedManifest
    if (restoreUri != null && manifest != null) {
        AlertDialog(
            onDismissRequest = {
                selectedUri = null
                selectedManifest = null
            },
            title = { Text("Replace all local data?") },
            text = {
                Text(
                    "The validated backup contains ${manifest.counts.totalRecords} records. " +
                        "Your current GymTrack data will be replaced.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedUri = null
                        selectedManifest = null
                        scope.launch {
                            busy = true
                            try {
                                val result = backupRepository.restoreBackup(
                                    context = context.applicationContext,
                                    contentResolver = context.contentResolver,
                                    source = restoreUri,
                                )
                                onSettingsUpdate(result.settings)
                                Toast.makeText(
                                    context,
                                    "Restored ${result.manifest.counts.totalRecords} records",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context,
                                    "Restore failed: ${error.localizedMessage}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text("Replace and restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedUri = null
                        selectedManifest = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}
