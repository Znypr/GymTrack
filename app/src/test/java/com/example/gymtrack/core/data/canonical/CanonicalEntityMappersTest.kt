package com.example.gymtrack.core.data.canonical

import com.example.gymtrack.core.data.CanonicalWorkoutSetEntity
import com.example.gymtrack.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CanonicalEntityMappersTest {
    @Test
    fun missingHistoricalUnitMapsToExplicitUnknownAndBackToNull() {
        val entity = CanonicalWorkoutSetEntity(
            id = "set-1",
            workoutExerciseId = "workout-exercise-1",
            position = 0,
            repetitions = 8,
            weight = 80.0,
            weightUnit = null,
            durationSeconds = null,
            distanceMeters = null,
            performedAtOffsetSeconds = null,
            rpe = null,
            rir = null,
        )

        val domain = entity.toDomain()

        assertEquals(WeightUnit.UNKNOWN, domain.weightUnit)
        assertNull(domain.toEntity().weightUnit)
    }

    @Test
    fun unknownStoredUnitFailsMappingInsteadOfBeingGuessed() {
        val entity = CanonicalWorkoutSetEntity(
            id = "set-1",
            workoutExerciseId = "workout-exercise-1",
            position = 0,
            repetitions = 8,
            weight = 80.0,
            weightUnit = "STONE",
            durationSeconds = null,
            distanceMeters = null,
            performedAtOffsetSeconds = null,
            rpe = null,
            rir = null,
        )

        assertThrows(CanonicalMappingException::class.java) {
            entity.toDomain()
        }
    }
}
