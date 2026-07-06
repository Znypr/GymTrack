package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.CanonicalWorkoutEntity
import com.example.gymtrack.core.data.CanonicalWorkoutExerciseEntity
import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import org.json.JSONObject

internal fun CanonicalWorkoutEntity.toBackupObject(): JSONObject = JSONObject()
    .put("id", id)
    .putOptional("legacyTimestamp", legacyTimestamp)
    .put("startedAt", startedAt)
    .putOptional("endedAt", endedAt)
    .putOptional("categoryId", categoryId)
    .put("title", title)
    .put("learnings", learnings)
    .put("status", status)
    .putOptional("rawDraftText", rawDraftText)
    .putOptional("legacyMigrationStatus", legacyMigrationStatus)
    .putOptional("legacyMigrationMessage", legacyMigrationMessage)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

internal fun JSONObject.readBackupWorkout(): CanonicalWorkoutEntity = CanonicalWorkoutEntity(
    id = getString("id"),
    legacyTimestamp = optionalLong("legacyTimestamp"),
    startedAt = getLong("startedAt"),
    endedAt = optionalLong("endedAt"),
    categoryId = optionalString("categoryId"),
    title = getString("title"),
    learnings = getString("learnings"),
    status = getString("status"),
    rawDraftText = optionalString("rawDraftText"),
    legacyMigrationStatus = optionalString("legacyMigrationStatus"),
    legacyMigrationMessage = optionalString("legacyMigrationMessage"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

internal fun CanonicalWorkoutExerciseEntity.toBackupObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("workoutId", workoutId)
    .put("exerciseId", exerciseId)
    .put("position", position)
    .put("mode", mode)
    .putOptional("modifier", modifier)
    .putOptional("equipmentBrand", equipmentBrand)
    .putOptional("startedAtOffsetSeconds", startedAtOffsetSeconds)
    .putOptional("startedAt", startedAt)
    .putOptional("legacyRelativeTimeText", legacyRelativeTimeText)
    .putOptional("legacyAbsoluteTimeText", legacyAbsoluteTimeText)

internal fun JSONObject.readBackupWorkoutExercise(): CanonicalWorkoutExerciseEntity =
    CanonicalWorkoutExerciseEntity(
        id = getString("id"),
        workoutId = getString("workoutId"),
        exerciseId = getString("exerciseId"),
        position = getInt("position"),
        mode = getString("mode"),
        modifier = optionalString("modifier"),
        equipmentBrand = optionalString("equipmentBrand"),
        startedAtOffsetSeconds = optionalInt("startedAtOffsetSeconds"),
        startedAt = optionalLong("startedAt"),
        legacyRelativeTimeText = optionalString("legacyRelativeTimeText"),
        legacyAbsoluteTimeText = optionalString("legacyAbsoluteTimeText"),
    )

internal fun CanonicalWorkoutSetEntity.toBackupObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("workoutExerciseId", workoutExerciseId)
    .put("position", position)
    .putOptional("repetitions", repetitions)
    .putOptional("weight", weight)
    .putOptional("weightUnit", weightUnit)
    .putOptional("durationSeconds", durationSeconds)
    .putOptional("distanceMeters", distanceMeters)
    .putOptional("performedAtOffsetSeconds", performedAtOffsetSeconds)
    .putOptional("rpe", rpe)
    .putOptional("rir", rir)

internal fun JSONObject.readBackupWorkoutSet(): CanonicalWorkoutSetEntity = CanonicalWorkoutSetEntity(
    id = getString("id"),
    workoutExerciseId = getString("workoutExerciseId"),
    position = getInt("position"),
    repetitions = optionalInt("repetitions"),
    weight = optionalDouble("weight"),
    weightUnit = optionalString("weightUnit"),
    durationSeconds = optionalInt("durationSeconds"),
    distanceMeters = optionalDouble("distanceMeters"),
    performedAtOffsetSeconds = optionalInt("performedAtOffsetSeconds"),
    rpe = optionalDouble("rpe"),
    rir = optionalDouble("rir"),
)
