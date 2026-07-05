package com.example.gymtrack.domain.model

/**
 * Pure domain models for the accepted canonical workout design.
 *
 * These are intentionally not Room entities. Persistence mappings and migrations belong to
 * the data layer and will be implemented separately after the migration-safety work is merged.
 */
data class Workout(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long? = null,
    val categoryId: String? = null,
    val title: String = "",
    val learnings: String = "",
    val status: WorkoutStatus = WorkoutStatus.DRAFT,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val legacyCompatibility: LegacyWorkoutCompatibility? = null,
) {
    init {
        require(id.isNotBlank()) { "Workout id must not be blank" }
        require(startedAtEpochMillis >= 0) { "Workout start time must not be negative" }
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis) {
            "Workout end time must not be before its start time"
        }
        require(createdAtEpochMillis >= 0) { "Workout creation time must not be negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "Workout update time must not be before its creation time"
        }
    }
}

enum class WorkoutStatus {
    DRAFT,
    PARTIAL,
    COMPLETED,
}

data class WorkoutExercise(
    val id: String,
    val workoutId: String,
    val exerciseId: String,
    val position: Int,
    val mode: ExerciseMode = ExerciseMode.BILATERAL,
    val modifier: String? = null,
    val equipmentBrand: String? = null,
    val startedAtOffsetSeconds: Int? = null,
    val startedAtEpochMillis: Long? = null,
    val legacyRelativeTimeText: String? = null,
    val legacyAbsoluteTimeText: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Workout exercise id must not be blank" }
        require(workoutId.isNotBlank()) { "Workout id must not be blank" }
        require(exerciseId.isNotBlank()) { "Exercise id must not be blank" }
        require(position >= 0) { "Workout exercise position must not be negative" }
        require(startedAtOffsetSeconds == null || startedAtOffsetSeconds >= 0) {
            "Exercise start offset must not be negative"
        }
        require(startedAtEpochMillis == null || startedAtEpochMillis >= 0) {
            "Exercise start time must not be negative"
        }
    }
}

enum class ExerciseMode {
    BILATERAL,
    UNILATERAL,
    SUPERSET,
}

data class WorkoutSet(
    val id: String,
    val workoutExerciseId: String,
    val position: Int,
    val repetitions: Int? = null,
    val weight: Double? = null,
    val weightUnit: WeightUnit? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val performedAtOffsetSeconds: Int? = null,
    val rpe: Double? = null,
    val rir: Double? = null,
) {
    init {
        require(id.isNotBlank()) { "Workout set id must not be blank" }
        require(workoutExerciseId.isNotBlank()) { "Workout exercise id must not be blank" }
        require(position >= 0) { "Workout set position must not be negative" }
        require(repetitions == null || repetitions > 0) { "Repetitions must be positive" }
        require(weight == null || weight >= 0.0) { "Weight must not be negative" }
        require(durationSeconds == null || durationSeconds > 0) { "Duration must be positive" }
        require(distanceMeters == null || distanceMeters > 0.0) { "Distance must be positive" }
        require(performedAtOffsetSeconds == null || performedAtOffsetSeconds >= 0) {
            "Set time offset must not be negative"
        }
        require(rpe == null || rpe in 0.0..10.0) { "RPE must be between 0 and 10" }
        require(rir == null || rir >= 0.0) { "RIR must not be negative" }
        require(repetitions != null || durationSeconds != null || distanceMeters != null) {
            "A workout set must contain repetitions, duration, or distance"
        }
        require(weight == null || weightUnit != null) {
            "A weight unit is required when weight is present"
        }
    }
}

enum class WeightUnit {
    KILOGRAM,
    POUND,
    UNKNOWN,
}

data class Exercise(
    val id: String,
    val canonicalName: String,
    val parentExerciseId: String? = null,
    val muscleGroup: String? = null,
    val aliases: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "Exercise id must not be blank" }
        require(canonicalName.isNotBlank()) { "Exercise name must not be blank" }
        require(parentExerciseId != id) { "Exercise cannot be its own parent" }
        require(aliases.none { it.isBlank() }) { "Exercise aliases must not be blank" }
    }
}

data class Category(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val position: Int,
    val isBuiltIn: Boolean,
    val isArchived: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Category id must not be blank" }
        require(name.isNotBlank()) { "Category name must not be blank" }
        require(position >= 0) { "Category position must not be negative" }
    }
}

/**
 * Temporary compatibility payload retained while legacy notes are migrated and validated.
 * It is not the canonical source for statistics, history, or export after migration.
 */
data class LegacyWorkoutCompatibility(
    val legacyTimestamp: Long,
    val rawDraftText: String,
    val migrationStatus: LegacyMigrationStatus = LegacyMigrationStatus.PENDING,
    val migrationMessage: String? = null,
) {
    init {
        require(legacyTimestamp >= 0) { "Legacy timestamp must not be negative" }
    }
}

enum class LegacyMigrationStatus {
    PENDING,
    MIGRATED,
    NEEDS_REVIEW,
}

/**
 * Aggregate used by domain services, imports, exports, and tests.
 * It enforces referential and ordering invariants before persistence.
 */
data class WorkoutRecord(
    val workout: Workout,
    val exercises: List<WorkoutExercise>,
    val sets: List<WorkoutSet>,
) {
    init {
        require(exercises.all { it.workoutId == workout.id }) {
            "Every workout exercise must belong to the aggregate workout"
        }
        require(exercises.map { it.id }.distinct().size == exercises.size) {
            "Workout exercise ids must be unique"
        }
        require(exercises.map { it.position }.distinct().size == exercises.size) {
            "Workout exercise positions must be unique"
        }

        val workoutExerciseIds = exercises.map { it.id }.toSet()
        require(sets.all { it.workoutExerciseId in workoutExerciseIds }) {
            "Every set must reference a workout exercise in the aggregate"
        }
        require(sets.map { it.id }.distinct().size == sets.size) {
            "Workout set ids must be unique"
        }

        sets.groupBy { it.workoutExerciseId }.forEach { (_, exerciseSets) ->
            require(exerciseSets.map { it.position }.distinct().size == exerciseSets.size) {
                "Set positions must be unique within each workout exercise"
            }
        }
    }
}
