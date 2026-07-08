package com.example.gymtrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIdentityResolverTest {

    @Test
    fun tricepsPushdownBarUsesCommonCanonicalNameAndAttachmentVariant() {
        val identity = ExerciseIdentityResolver.resolve(
            rawName = "tricep pushdown bar",
            parsedName = "Tricep pushdown",
            modifier = "Straight Bar",
            brand = null,
            isUnilateral = false,
        )

        assertEquals("Triceps Pushdown", identity.canonicalName)
        assertEquals(ExerciseEquipment.CABLE, identity.equipment)
        assertEquals(ExerciseAttachment.STRAIGHT_BAR, identity.attachment)
        assertTrue("raw wording should be kept as alias", "tricep pushdown bar" in identity.aliases)
        assertTrue("straight-bar chip should be displayable", "Straight bar" in identity.variantLabels())
    }

    @Test
    fun machineBrandChangesStrictComparisonButKeepsBaseExercise() {
        val prime = ExerciseIdentityResolver.resolve(
            rawName = "tricep extension prime",
            parsedName = "Tricep extension",
        )
        val atlantis = ExerciseIdentityResolver.resolve(
            rawName = "tricep extension at",
            parsedName = "Tricep extension",
        )

        assertEquals("Triceps Extension", prime.canonicalName)
        assertEquals("Prime", prime.brand)
        assertEquals("Atlantis", atlantis.brand)
        assertEquals(prime.baseComparisonKey, atlantis.baseComparisonKey)
        assertNotEquals(prime.strictComparisonKey, atlantis.strictComparisonKey)
    }

    @Test
    fun rightLeftSuffixDoesNotPolluteCanonicalExerciseName() {
        val identity = ExerciseIdentityResolver.resolve(
            rawName = "leg extension rl",
            parsedName = "Leg extension",
            isUnilateral = true,
        )

        assertEquals("Leg Extension", identity.canonicalName)
        assertEquals(ExerciseSideMode.UNILATERAL, identity.sideMode)
        assertTrue("raw rl wording should be retained as alias", "leg extension rl" in identity.aliases)
        assertTrue("unilateral chip should be displayable", "Unilateral" in identity.variantLabels())
    }

    @Test
    fun backupCorpusAliasesResolveToUsefulCanonicalNames() {
        val examples = mapOf(
            "latpulldown rl" to "Lat Pulldown",
            "diag rowing" to "Diagonal Row",
            "seated hamstring" to "Seated Leg Curl",
            "calve machine" to "Calf Raise",
            "situp l6" to "Sit-Up",
            "rear delt" to "Rear Delt Fly",
            "tbar rowing" to "T-Bar Row",
        )

        examples.forEach { (raw, expected) ->
            assertEquals(expected, ExerciseIdentityResolver.resolve(rawName = raw).canonicalName)
        }
    }

    @Test
    fun parserAttachesExerciseIdentityWithoutChangingRawNoteText() {
        val sets = WorkoutParser().parseWorkout(
            rawText = """
                tricep pushdown bar
                10x 30kg
            """.trimIndent(),
        )

        assertEquals(1, sets.size)
        assertEquals("Triceps Pushdown", sets.single().exerciseIdentity.canonicalName)
        assertEquals(ExerciseAttachment.STRAIGHT_BAR, sets.single().exerciseIdentity.attachment)
    }
}
