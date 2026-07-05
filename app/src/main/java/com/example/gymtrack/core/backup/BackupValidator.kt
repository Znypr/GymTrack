package com.example.gymtrack.core.backup

object BackupValidator {
    fun check(payload: GymTrackBackupPayload) {
        checkUnique("legacy note timestamps", payload.legacyNotes.map { it.timestamp })
        checkUnique("legacy exercise IDs", payload.legacyExercises.map { it.exerciseId })
        checkUnique("legacy set IDs", payload.legacySets.map { it.setId })
        checkUnique("canonical category IDs", payload.canonicalCategories.map { it.id })
        checkUnique("canonical exercise IDs", payload.canonicalExercises.map { it.id })
        checkUnique("canonical alias IDs", payload.canonicalExerciseAliases.map { it.id })
        checkUnique("canonical workout IDs", payload.canonicalWorkouts.map { it.id })
        checkUnique("canonical workout exercise IDs", payload.canonicalWorkoutExercises.map { it.id })
        checkUnique("canonical workout set IDs", payload.canonicalWorkoutSets.map { it.id })

        val legacyExercises = payload.legacyExercises.mapTo(hashSetOf()) { it.exerciseId }
        payload.legacySets.forEach { value ->
            ensure(value.exerciseId in legacyExercises, "Legacy set ${value.setId} references a missing exercise")
        }

        val categories = payload.canonicalCategories.mapTo(hashSetOf()) { it.id }
        val exercises = payload.canonicalExercises.mapTo(hashSetOf()) { it.id }
        payload.canonicalExercises.forEach { value ->
            ensure(value.parentExerciseId == null || value.parentExerciseId in exercises, "Exercise ${value.id} has a missing parent")
            ensure(value.parentExerciseId != value.id, "Exercise ${value.id} cannot parent itself")
        }
        payload.canonicalExerciseAliases.forEach { value ->
            ensure(value.exerciseId in exercises, "Alias ${value.id} references a missing exercise")
        }

        val workouts = payload.canonicalWorkouts.mapTo(hashSetOf()) { it.id }
        payload.canonicalWorkouts.forEach { value ->
            ensure(value.categoryId == null || value.categoryId in categories, "Workout ${value.id} references a missing category")
        }

        val workoutExercises = payload.canonicalWorkoutExercises.mapTo(hashSetOf()) { it.id }
        payload.canonicalWorkoutExercises.forEach { value ->
            ensure(value.workoutId in workouts, "Workout exercise ${value.id} references a missing workout")
            ensure(value.exerciseId in exercises, "Workout exercise ${value.id} references a missing exercise")
        }
        payload.canonicalWorkoutSets.forEach { value ->
            ensure(value.workoutExerciseId in workoutExercises, "Workout set ${value.id} references a missing workout exercise")
        }
    }

    private fun <T> checkUnique(label: String, values: List<T>) {
        if (values.size != values.toSet().size) throw InvalidBackupException("Backup contains duplicate $label")
    }

    private fun ensure(valid: Boolean, message: String) {
        if (!valid) throw InvalidBackupException(message)
    }
}
