package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.CanonicalCategoryEntity
import com.example.gymtrack.core.data.CanonicalExerciseAliasEntity
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import org.json.JSONObject

internal fun CanonicalCategoryEntity.toBackupObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("colorArgb", colorArgb)
    .put("position", position)
    .put("isBuiltIn", isBuiltIn)
    .put("isArchived", isArchived)

internal fun JSONObject.readBackupCategory(): CanonicalCategoryEntity = CanonicalCategoryEntity(
    id = getString("id"),
    name = getString("name"),
    colorArgb = getLong("colorArgb"),
    position = getInt("position"),
    isBuiltIn = getBoolean("isBuiltIn"),
    isArchived = getBoolean("isArchived"),
)

internal fun CanonicalExerciseEntity.toBackupObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("canonicalName", canonicalName)
    .put("normalizedName", normalizedName)
    .putOptional("parentExerciseId", parentExerciseId)
    .putOptional("muscleGroup", muscleGroup)
    .put("createdAt", createdAt)
    .put("updatedAt", updatedAt)

internal fun JSONObject.readBackupExercise(): CanonicalExerciseEntity = CanonicalExerciseEntity(
    id = getString("id"),
    canonicalName = getString("canonicalName"),
    normalizedName = getString("normalizedName"),
    parentExerciseId = optionalString("parentExerciseId"),
    muscleGroup = optionalString("muscleGroup"),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

internal fun CanonicalExerciseAliasEntity.toBackupObject(): JSONObject = JSONObject()
    .put("id", id)
    .put("exerciseId", exerciseId)
    .put("normalizedAlias", normalizedAlias)
    .put("originalAlias", originalAlias)

internal fun JSONObject.readBackupExerciseAlias(): CanonicalExerciseAliasEntity = CanonicalExerciseAliasEntity(
    id = getString("id"),
    exerciseId = getString("exerciseId"),
    normalizedAlias = getString("normalizedAlias"),
    originalAlias = getString("originalAlias"),
)
