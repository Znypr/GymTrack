package com.example.gymtrack.core.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.NoteDatabase
import com.example.gymtrack.core.data.Settings
import com.example.gymtrack.core.data.SettingsStore
import com.example.gymtrack.core.timer.NoteTimerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupRepository(
    private val database: NoteDatabase,
    private val restoreInterruptionProbe: suspend (RestoreInterruptionPoint) -> Unit = {},
) {
    enum class RestoreInterruptionPoint {
        BEFORE_DATABASE_REPLACE,
        AFTER_EXISTING_ROWS_DELETED,
        AFTER_CANONICAL_ROWS_INSERTED,
        AFTER_LEGACY_ROWS_INSERTED,
        BEFORE_SETTINGS_SAVE,
        AFTER_SETTINGS_SAVE,
    }

    suspend fun createBackup(
        contentResolver: ContentResolver,
        destination: Uri,
        settings: Settings,
        appVersion: String,
        databaseSchemaVersion: Int,
    ): BackupWriteResult = withContext(Dispatchers.IO) {
        val payload = snapshot(settings)
        val manifest = contentResolver.openOutputStream(destination, "w")?.use { output ->
            BackupArchive.write(
                output = output,
                payload = payload,
                appVersion = appVersion,
                databaseSchemaVersion = databaseSchemaVersion,
            )
        } ?: throw IllegalStateException("Unable to open the selected backup destination")

        BackupWriteResult(
            manifest = manifest,
            fileName = contentResolver.displayName(destination) ?: "GymTrack backup",
        )
    }

    suspend fun inspectBackup(
        contentResolver: ContentResolver,
        source: Uri,
    ): BackupManifest = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(source)?.use { input ->
            BackupArchive.read(input).manifest
        } ?: throw InvalidBackupException("Unable to open the selected backup")
    }

    suspend fun hasRestorableLocalData(settings: Settings): Boolean = withContext(Dispatchers.IO) {
        BackupCounts.from(snapshot(settings)).totalRecords > 0
    }

    suspend fun restoreBackup(
        context: Context,
        contentResolver: ContentResolver,
        source: Uri,
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        val contents = contentResolver.openInputStream(source)?.use { input ->
            BackupArchive.read(input)
        } ?: throw InvalidBackupException("Unable to open the selected backup")
        val previousSettings = SettingsStore.load(context)
        val previousPayload = snapshot(previousSettings)

        try {
            restoreInterruptionProbe(RestoreInterruptionPoint.BEFORE_DATABASE_REPLACE)
            replaceDatabase(contents.payload)
            restoreInterruptionProbe(RestoreInterruptionPoint.BEFORE_SETTINGS_SAVE)
            SettingsStore.save(context, contents.payload.settings)
            restoreInterruptionProbe(RestoreInterruptionPoint.AFTER_SETTINGS_SAVE)
        } catch (restoreError: Throwable) {
            val rollback = runCatching {
                replaceDatabase(previousPayload)
                SettingsStore.save(context, previousSettings)
            }
            if (rollback.isFailure) {
                throw IllegalStateException(
                    "Restore failed and the previous data could not be restored",
                    rollback.exceptionOrNull(),
                )
            }
            throw restoreError
        }

        runCatching { NoteTimerStore.stop(context) }

        BackupRestoreResult(
            manifest = contents.manifest,
            settings = contents.payload.settings,
        )
    }

    suspend fun snapshot(settings: Settings): GymTrackBackupPayload = database.withTransaction {
        val backupDao = database.backupDao()
        GymTrackBackupPayload(
            settings = settings,
            legacyNotes = database.noteDao().getAllForBackup(),
            legacyExercises = database.exerciseDao().getAllForBackup(),
            legacySets = database.setDao().getAllForBackup(),
            canonicalCategories = backupDao.getCanonicalCategories(),
            canonicalExercises = backupDao.getCanonicalExercises(),
            canonicalExerciseAliases = backupDao.getCanonicalExerciseAliases(),
            canonicalWorkouts = backupDao.getCanonicalWorkouts(),
            canonicalWorkoutExercises = backupDao.getCanonicalWorkoutExercises(),
            canonicalWorkoutSets = backupDao.getCanonicalWorkoutSets(),
        )
    }

    private suspend fun replaceDatabase(payload: GymTrackBackupPayload) {
        BackupValidator.check(payload)
        database.withTransaction {
            val backupDao = database.backupDao()
            backupDao.deleteCanonicalWorkoutSets()
            backupDao.deleteCanonicalWorkoutExercises()
            backupDao.deleteCanonicalWorkouts()
            backupDao.deleteCanonicalExerciseAliases()
            backupDao.deleteCanonicalExercises()
            backupDao.deleteCanonicalCategories()
            database.setDao().deleteAllForRestore()
            database.exerciseDao().deleteAllForRestore()
            database.noteDao().deleteAllForRestore()
            restoreInterruptionProbe(RestoreInterruptionPoint.AFTER_EXISTING_ROWS_DELETED)

            backupDao.insertCanonicalCategories(payload.canonicalCategories)
            backupDao.insertCanonicalExercises(sortCanonicalExercises(payload.canonicalExercises))
            backupDao.insertCanonicalExerciseAliases(payload.canonicalExerciseAliases)
            backupDao.insertCanonicalWorkouts(payload.canonicalWorkouts)
            backupDao.insertCanonicalWorkoutExercises(payload.canonicalWorkoutExercises)
            backupDao.insertCanonicalWorkoutSets(payload.canonicalWorkoutSets)
            restoreInterruptionProbe(RestoreInterruptionPoint.AFTER_CANONICAL_ROWS_INSERTED)

            database.noteDao().insertAllForRestore(payload.legacyNotes)
            database.exerciseDao().insertAllForRestore(payload.legacyExercises)
            database.setDao().insertSets(payload.legacySets)
            restoreInterruptionProbe(RestoreInterruptionPoint.AFTER_LEGACY_ROWS_INSERTED)
        }
    }

    private fun sortCanonicalExercises(values: List<CanonicalExerciseEntity>): List<CanonicalExerciseEntity> {
        val remaining = values.associateBy { it.id }.toMutableMap()
        val sorted = ArrayList<CanonicalExerciseEntity>(values.size)
        val inserted = hashSetOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values
                .filter { it.parentExerciseId == null || it.parentExerciseId in inserted }
                .sortedWith(compareBy<CanonicalExerciseEntity> { it.createdAt }.thenBy { it.id })
            if (ready.isEmpty()) {
                throw InvalidBackupException("Canonical exercise hierarchy contains a cycle")
            }
            ready.forEach { value ->
                sorted += value
                inserted += value.id
                remaining.remove(value.id)
            }
        }
        return sorted
    }

    private fun ContentResolver.displayName(uri: Uri): String? = query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
