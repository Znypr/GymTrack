package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalExerciseAliasEntity
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.ExerciseEntity
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.SetEntity
import com.example.gymtrack.core.data.Settings

data class GymTrackBackupPayload(
    val settings: Settings,
    val legacyNotes: List<NoteEntity>,
    val legacyExercises: List<ExerciseEntity>,
    val legacySets: List<SetEntity>,
    val canonicalCategories: List<CanonicalCategoryEntity>,
    val canonicalExercises: List<CanonicalExerciseEntity>,
    val canonicalExerciseAliases: List<CanonicalExerciseAliasEntity>,
    val canonicalWorkouts: List<CanonicalWorkoutEntity>,
    val canonicalWorkoutExercises: List<CanonicalWorkoutExerciseEntity>,
    val canonicalWorkoutSets: List<CanonicalWorkoutSetEntity>,
)

data class BackupCounts(
    val legacyNotes: Int,
    val legacyExercises: Int,
    val legacySets: Int,
    val canonicalCategories: Int,
    val canonicalExercises: Int,
    val canonicalExerciseAliases: Int,
    val canonicalWorkouts: Int,
    val canonicalWorkoutExercises: Int,
    val canonicalWorkoutSets: Int,
) {
    val totalRecords: Int
        get() = legacyNotes + legacyExercises + legacySets + canonicalCategories +
            canonicalExercises + canonicalExerciseAliases + canonicalWorkouts +
            canonicalWorkoutExercises + canonicalWorkoutSets

    companion object {
        fun from(payload: GymTrackBackupPayload) = BackupCounts(
            legacyNotes = payload.legacyNotes.size,
            legacyExercises = payload.legacyExercises.size,
            legacySets = payload.legacySets.size,
            canonicalCategories = payload.canonicalCategories.size,
            canonicalExercises = payload.canonicalExercises.size,
            canonicalExerciseAliases = payload.canonicalExerciseAliases.size,
            canonicalWorkouts = payload.canonicalWorkouts.size,
            canonicalWorkoutExercises = payload.canonicalWorkoutExercises.size,
            canonicalWorkoutSets = payload.canonicalWorkoutSets.size,
        )
    }
}

data class BackupManifest(
    val formatVersion: Int,
    val createdAtEpochMillis: Long,
    val appVersion: String,
    val databaseSchemaVersion: Int,
    val payloadSha256: String,
    val counts: BackupCounts,
)

data class BackupArchiveContents(
    val manifest: BackupManifest,
    val payload: GymTrackBackupPayload,
)

data class BackupWriteResult(
    val manifest: BackupManifest,
    val fileName: String,
)

data class BackupRestoreResult(
    val manifest: BackupManifest,
    val settings: Settings,
)

class InvalidBackupException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)
