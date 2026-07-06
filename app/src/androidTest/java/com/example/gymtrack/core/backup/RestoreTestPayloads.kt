package com.example.gymtrack.core.backup

internal fun GymTrackBackupPayload.replacementPayload(): GymTrackBackupPayload = copy(
    settings = settings.copy(darkMode = !settings.darkMode),
    legacyNotes = legacyNotes.map { it.copy(title = "Replacement") },
    canonicalWorkouts = canonicalWorkouts.map { it.copy(title = "Replacement") },
    canonicalWorkoutSets = canonicalWorkoutSets.map { it.copy(weight = 120.0) },
)

internal fun GymTrackBackupPayload.cyclicPayload(): GymTrackBackupPayload {
    val template = canonicalExercises.single()
    return copy(
        settings = settings.copy(darkMode = !settings.darkMode),
        canonicalExercises = listOf(
            template.copy(
                id = "cycle-a",
                canonicalName = "Cycle A",
                normalizedName = "cycle a",
                parentExerciseId = "cycle-b",
            ),
            template.copy(
                id = "cycle-b",
                canonicalName = "Cycle B",
                normalizedName = "cycle b",
                parentExerciseId = "cycle-a",
            ),
        ),
        canonicalExerciseAliases = emptyList(),
        canonicalWorkouts = emptyList(),
        canonicalWorkoutExercises = emptyList(),
        canonicalWorkoutSets = emptyList(),
    )
}
