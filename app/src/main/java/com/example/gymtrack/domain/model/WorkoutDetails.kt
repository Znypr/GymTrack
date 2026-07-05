package com.example.gymtrack.domain.model

data class WorkoutDetails(
    val record: WorkoutRecord,
    val exerciseDefinitions: Map<String, Exercise>,
    val category: Category? = null,
) {
    init {
        val referencedExerciseIds = record.exercises.map { it.exerciseId }.toSet()
        require(referencedExerciseIds.all(exerciseDefinitions::containsKey)) {
            "Every workout exercise must have an exercise definition"
        }
        require(record.workout.categoryId == category?.id) {
            "Workout category reference must match the supplied category"
        }
    }
}
