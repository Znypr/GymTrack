package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalExerciseAliasEntity
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.core.data.Category
import com.example.gymtrack.core.data.ExerciseEntity
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.SetEntity
import com.example.gymtrack.core.data.Settings

internal object BackupFixtures {
    fun payload() = GymTrackBackupPayload(
        settings = Settings(false, 10, true, listOf(Category("Push", 1L), Category("Custom", 2L))),
        legacyNotes = listOf(NoteEntity(100L, "Workout", "Bench 100x5", "Push", 1L, "Good")),
        legacyExercises = listOf(ExerciseEntity(1L, "Bench", null, "Chest", "bp")),
        legacySets = listOf(SetEntity(1L, 100L, 1L, 100f, 5, false)),
        canonicalCategories = listOf(CanonicalCategoryEntity("cat", "Push", 1L, 0, true, false)),
        canonicalExercises = listOf(CanonicalExerciseEntity("ex", "Bench", "bench", null, "Chest", 1L, 2L)),
        canonicalExerciseAliases = listOf(CanonicalExerciseAliasEntity("alias", "ex", "bp", "BP")),
        canonicalWorkouts = listOf(
            CanonicalWorkoutEntity("workout", 100L, 100L, 200L, "cat", "Workout", "Good", "COMPLETED", null, null, null, 100L, 200L),
        ),
        canonicalWorkoutExercises = listOf(
            CanonicalWorkoutExerciseEntity("we", "workout", "ex", 0, "BILATERAL", null, null, 0, 100L, null, null),
        ),
        canonicalWorkoutSets = listOf(
            CanonicalWorkoutSetEntity("set", "we", 0, 5, 100.0, "KG", null, null, 10, 8.0, 2.0),
        ),
    )
}
