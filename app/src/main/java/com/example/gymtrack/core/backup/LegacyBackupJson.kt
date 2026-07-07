package com.example.gymtrack.core.backup

import com.example.gymtrack.core.data.ExerciseEntity
import com.example.gymtrack.core.data.NoteEntity
import com.example.gymtrack.core.data.SetEntity
import com.example.gymtrack.core.data.WeightUnit
import org.json.JSONObject

internal fun NoteEntity.asBackupJson(): JSONObject = JSONObject()
    .put("timestamp", timestamp)
    .put("title", title)
    .put("text", text)
    .putOptional("categoryName", categoryName)
    .putOptional("categoryColor", categoryColor)
    .putOptional("learnings", learnings)
    .putOptional("rowMetadata", rowMetadata)

internal fun JSONObject.asBackupNoteEntity(): NoteEntity = NoteEntity(
    timestamp = getLong("timestamp"),
    title = getString("title"),
    text = getString("text"),
    categoryName = optionalString("categoryName"),
    categoryColor = optionalLong("categoryColor"),
    learnings = optionalString("learnings"),
    rowMetadata = optionalString("rowMetadata"),
)

internal fun ExerciseEntity.asBackupJson(): JSONObject = JSONObject()
    .put("exerciseId", exerciseId)
    .put("name", name)
    .putOptional("parentId", parentId)
    .putOptional("muscleGroup", muscleGroup)
    .put("aliases", aliases)

internal fun JSONObject.asBackupExerciseEntity(): ExerciseEntity = ExerciseEntity(
    exerciseId = getLong("exerciseId"),
    name = getString("name"),
    parentId = optionalLong("parentId"),
    muscleGroup = optionalString("muscleGroup"),
    aliases = getString("aliases"),
)

internal fun SetEntity.asBackupJson(): JSONObject = JSONObject()
    .put("setId", setId)
    .put("workoutId", workoutId)
    .put("exerciseId", exerciseId)
    .put("weight", weight.toDouble())
    .put("weightUnit", WeightUnit.fromStorage(weightUnit).storageValue)
    .put("reps", reps)
    .put("isUnilateral", isUnilateral)
    .putOptional("modifier", modifier)
    .putOptional("brand", brand)
    .putOptional("relativeTime", relativeTime)
    .putOptional("absoluteTime", absoluteTime)

internal fun JSONObject.asBackupSetEntity(): SetEntity = SetEntity(
    setId = getLong("setId"),
    workoutId = getLong("workoutId"),
    exerciseId = getLong("exerciseId"),
    weight = getDouble("weight").toFloat(),
    reps = getInt("reps"),
    isUnilateral = getBoolean("isUnilateral"),
    modifier = optionalString("modifier"),
    brand = optionalString("brand"),
    relativeTime = optionalString("relativeTime"),
    absoluteTime = optionalString("absoluteTime"),
    weightUnit = WeightUnit.fromStorage(optString("weightUnit", WeightUnit.KG.storageValue)).storageValue,
)
