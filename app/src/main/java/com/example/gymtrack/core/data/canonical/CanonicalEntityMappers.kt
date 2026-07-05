package com.example.gymtrack.core.data.canonical

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalExerciseAliasEntity
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.domain.model.Category
import com.example.gymtrack.domain.model.Exercise
import com.example.gymtrack.domain.model.ExerciseMode
import com.example.gymtrack.domain.model.LegacyMigrationStatus
import com.example.gymtrack.domain.model.LegacyWorkoutCompatibility
import com.example.gymtrack.domain.model.WeightUnit
import com.example.gymtrack.domain.model.Workout
import com.example.gymtrack.domain.model.WorkoutExercise
import com.example.gymtrack.domain.model.WorkoutSet
import com.example.gymtrack.domain.model.WorkoutStatus

class CanonicalMappingException(message: String) : IllegalStateException(message)

internal fun CanonicalWorkoutEntity.toDomain(): Workout = Workout(
    id = id,
    startedAtEpochMillis = startedAt,
    endedAtEpochMillis = endedAt,
    categoryId = categoryId,
    title = title,
    learnings = learnings,
    status = parseEnum(status, "workout.status"),
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt,
    legacyCompatibility = legacyTimestamp?.let { timestamp ->
        LegacyWorkoutCompatibility(
            legacyTimestamp = timestamp,
            rawDraftText = rawDraftText.orEmpty(),
            migrationStatus = legacyMigrationStatus
                ?.let { parseEnum(it, "workout.legacyMigrationStatus") }
                ?: LegacyMigrationStatus.PENDING,
            migrationMessage = legacyMigrationMessage,
        )
    },
)

internal fun Workout.toEntity(): CanonicalWorkoutEntity = CanonicalWorkoutEntity(
    id = id,
    legacyTimestamp = legacyCompatibility?.legacyTimestamp,
    startedAt = startedAtEpochMillis,
    endedAt = endedAtEpochMillis,
    categoryId = categoryId,
    title = title,
    learnings = learnings,
    status = status.name,
    rawDraftText = legacyCompatibility?.rawDraftText,
    legacyMigrationStatus = legacyCompatibility?.migrationStatus?.name,
    legacyMigrationMessage = legacyCompatibility?.migrationMessage,
    createdAt = createdAtEpochMillis,
    updatedAt = updatedAtEpochMillis,
)

internal fun CanonicalWorkoutExerciseEntity.toDomain(): WorkoutExercise = WorkoutExercise(
    id = id,
    workoutId = workoutId,
    exerciseId = exerciseId,
    position = position,
    mode = parseEnum(mode, "workoutExercise.mode"),
    modifier = modifier,
    equipmentBrand = equipmentBrand,
    startedAtOffsetSeconds = startedAtOffsetSeconds,
    startedAtEpochMillis = startedAt,
    legacyRelativeTimeText = legacyRelativeTimeText,
    legacyAbsoluteTimeText = legacyAbsoluteTimeText,
)

internal fun WorkoutExercise.toEntity(): CanonicalWorkoutExerciseEntity =
    CanonicalWorkoutExerciseEntity(
        id = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        position = position,
        mode = mode.name,
        modifier = modifier,
        equipmentBrand = equipmentBrand,
        startedAtOffsetSeconds = startedAtOffsetSeconds,
        startedAt = startedAtEpochMillis,
        legacyRelativeTimeText = legacyRelativeTimeText,
        legacyAbsoluteTimeText = legacyAbsoluteTimeText,
    )

internal fun CanonicalWorkoutSetEntity.toDomain(): WorkoutSet {
    val domainUnit = when {
        weight == null && weightUnit == null -> null
        weight != null && weightUnit == null -> WeightUnit.UNKNOWN
        else -> parseEnum(weightUnit.orEmpty(), "workoutSet.weightUnit")
    }

    return WorkoutSet(
        id = id,
        workoutExerciseId = workoutExerciseId,
        position = position,
        repetitions = repetitions,
        weight = weight,
        weightUnit = domainUnit,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        performedAtOffsetSeconds = performedAtOffsetSeconds,
        rpe = rpe,
        rir = rir,
    )
}

internal fun WorkoutSet.toEntity(): CanonicalWorkoutSetEntity = CanonicalWorkoutSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    position = position,
    repetitions = repetitions,
    weight = weight,
    weightUnit = when (weightUnit) {
        null, WeightUnit.UNKNOWN -> null
        else -> weightUnit.name
    },
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    performedAtOffsetSeconds = performedAtOffsetSeconds,
    rpe = rpe,
    rir = rir,
)

internal fun CanonicalExerciseEntity.toDomain(
    aliases: List<CanonicalExerciseAliasEntity>,
): Exercise = Exercise(
    id = id,
    canonicalName = canonicalName,
    parentExerciseId = parentExerciseId,
    muscleGroup = muscleGroup,
    aliases = aliases
        .filter { it.exerciseId == id }
        .map { it.originalAlias }
        .toSet(),
)

internal fun Exercise.toEntity(
    createdAt: Long,
    updatedAt: Long,
): CanonicalExerciseEntity = CanonicalExerciseEntity(
    id = id,
    canonicalName = canonicalName,
    normalizedName = canonicalName.trim().lowercase().replace(Regex("\\s+"), " "),
    parentExerciseId = parentExerciseId,
    muscleGroup = muscleGroup,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun CanonicalCategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    colorArgb = colorArgb,
    position = position,
    isBuiltIn = isBuiltIn,
    isArchived = isArchived,
)

internal fun Category.toEntity(): CanonicalCategoryEntity = CanonicalCategoryEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    position = position,
    isBuiltIn = isBuiltIn,
    isArchived = isArchived,
)

private inline fun <reified T : Enum<T>> parseEnum(value: String, field: String): T =
    enumValues<T>().firstOrNull { it.name == value }
        ?: throw CanonicalMappingException("Unknown $field value: $value")
