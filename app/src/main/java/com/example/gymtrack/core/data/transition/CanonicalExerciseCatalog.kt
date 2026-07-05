package com.example.gymtrack.core.data.transition

import com.example.gymtrack.core.data.CanonicalExerciseAliasEntity
import com.example.gymtrack.core.data.CanonicalExerciseEntity
import com.example.gymtrack.core.data.ExerciseEntity

internal class CanonicalExerciseCatalog(
    legacyExercises: List<ExerciseEntity>,
) {
    private val exercises = linkedMapOf<String, CanonicalExerciseEntity>()
    private val aliases = linkedMapOf<String, CanonicalExerciseAliasEntity>()
    private val keyByNormalizedName = mutableMapOf<String, String>()

    init {
        val keyByLegacyId = legacyExercises.associate { legacy ->
            legacy.exerciseId to CanonicalKeys.namedExercise(displayName(legacy))
        }

        legacyExercises.sortedBy { it.exerciseId }.forEach { legacy ->
            val name = displayName(legacy)
            val key = keyByLegacyId.getValue(legacy.exerciseId)
            val normalizedName = CanonicalKeys.normalize(name)
            val parentKey = legacy.parentId?.let(keyByLegacyId::get)
            val current = exercises[key]
            exercises[key] = CanonicalExerciseEntity(
                id = key,
                canonicalName = current?.canonicalName ?: name,
                normalizedName = normalizedName,
                parentExerciseId = current?.parentExerciseId ?: parentKey,
                muscleGroup = current?.muscleGroup ?: legacy.muscleGroup,
                createdAt = current?.createdAt ?: 0L,
                updatedAt = 0L,
            )
            keyByNormalizedName.putIfAbsent(normalizedName, key)

            parseAliases(legacy.aliases).forEach { alias ->
                val normalizedAlias = CanonicalKeys.normalize(alias)
                if (normalizedAlias.isNotEmpty()) {
                    keyByNormalizedName.putIfAbsent(normalizedAlias, key)
                    val aliasKey = CanonicalKeys.alias(key, alias)
                    aliases[aliasKey] = CanonicalExerciseAliasEntity(
                        id = aliasKey,
                        exerciseId = key,
                        normalizedAlias = normalizedAlias,
                        originalAlias = alias,
                    )
                }
            }
        }
    }

    fun resolve(name: String): CanonicalExerciseEntity {
        val displayName = name.trim().ifEmpty { "Unknown exercise" }
        val normalizedName = CanonicalKeys.normalize(displayName)
        val existingKey = keyByNormalizedName[normalizedName]
        if (existingKey != null) return exercises.getValue(existingKey)

        val key = CanonicalKeys.namedExercise(displayName)
        val exercise = CanonicalExerciseEntity(
            id = key,
            canonicalName = displayName,
            normalizedName = normalizedName,
            parentExerciseId = null,
            muscleGroup = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        exercises[key] = exercise
        keyByNormalizedName[normalizedName] = key
        return exercise
    }

    fun exerciseEntities(): List<CanonicalExerciseEntity> = exercises.values.toList()

    fun aliasEntities(): List<CanonicalExerciseAliasEntity> = aliases.values.toList()

    private fun displayName(legacy: ExerciseEntity): String =
        legacy.name.trim().ifEmpty { "Unknown exercise" }

    private fun parseAliases(raw: String): List<String> = raw
        .split(',', ';', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(CanonicalKeys::normalize)
}
