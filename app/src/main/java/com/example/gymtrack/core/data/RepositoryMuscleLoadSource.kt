package com.example.gymtrack.core.data

import com.example.gymtrack.core.muscle.MuscleExerciseSets
import com.example.gymtrack.core.muscle.MuscleLoadSource
import com.example.gymtrack.core.muscle.MuscleWeek
import com.example.gymtrack.core.muscle.WeeklyMuscleSummary

/**
 * The real [MuscleLoadSource] (#309 data behind the #311 map): the derived `muscle_week` rows,
 * working sets only, primary 1.0 / secondary 0.5. Unmapped stays unmapped, never zero.
 */
class RepositoryMuscleLoadSource(private val repository: WorkoutRepository) : MuscleLoadSource {
    override suspend fun weekLevels(weekKey: String): MuscleWeek = repository.weeklyMuscleSummary(weekKey).toMuscleWeek()

    override suspend fun workoutLevels(note: NoteLine): MuscleWeek =
        repository.workoutMuscleSummary(note.timestamp).toMuscleWeek()
}

/** The map's view of a [WeeklyMuscleSummary]. */
fun WeeklyMuscleSummary.toMuscleWeek(): MuscleWeek = MuscleWeek(
    levels = levels,
    sets = muscles.mapValues { it.value.effectiveSets }.filterValues { it > 0f },
    exercises = muscles.mapValues { (_, stat) -> stat.exercises.map { MuscleExerciseSets(it.name, it.effectiveSets) } }
        .filterValues { it.isNotEmpty() },
)
