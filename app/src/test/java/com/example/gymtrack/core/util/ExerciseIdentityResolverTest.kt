package com.example.gymtrack.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun editorSuggestionMatchingCanIgnoreMachineBrandWhenSkippingUsedBaseExercise() {
        val typedWithBrand = ExerciseIdentityResolver.resolve(rawName = "pec deck rl")
        val plainSuggestion = ExerciseIdentityResolver.resolve(rawName = "Pec Deck")
        val machineSuggestion = ExerciseIdentityResolver.resolve(rawName = "Pec Deck Machine")

        assertEquals("Pec Deck", typedWithBrand.canonicalName)
        assertEquals("Realleader", typedWithBrand.brand)
        assertEquals(plainSuggestion.baseComparisonKey, typedWithBrand.baseComparisonKey)
        assertEquals(machineSuggestion.baseComparisonKey, typedWithBrand.baseComparisonKey)
        assertNotEquals(plainSuggestion.progressComparisonKey, typedWithBrand.progressComparisonKey)
    }

    @Test
    fun sygTokenResolvesAsGym80MachineBrandLikeG80() {
        val syg = ExerciseIdentityResolver.resolve(rawName = "leg extension syg")
        val g80 = ExerciseIdentityResolver.resolve(rawName = "leg extension g80")

        assertEquals("Leg Extension", syg.canonicalName)
        assertEquals("Gym80", syg.brand)
        assertEquals(ExerciseEquipment.MACHINE, syg.equipment)
        assertEquals(g80.strictComparisonKey, syg.strictComparisonKey)
        assertEquals(g80.progressComparisonKey, syg.progressComparisonKey)
        assertTrue("Gym80 chip should be displayable", "Gym80" in syg.variantLabels())
    }

    @Test
    fun parserNormalizesSygAbbreviationToGym80MachineIdentity() {
        val sets = WorkoutParser().parseWorkout(
            rawText = """
                leg extension syg
                10x 50kg
            """.trimIndent(),
        )

        assertEquals(1, sets.size)
        assertEquals("Gym80", sets.single().brand)
        assertEquals("Gym80", sets.single().exerciseIdentity.brand)
        assertEquals(ExerciseEquipment.MACHINE, sets.single().exerciseIdentity.equipment)
    }

    @Test
    fun rlTokenResolvesAsRealleaderBrandNotUnilateralMarker() {
        val identity = ExerciseIdentityResolver.resolve(rawName = "latpulldown rl")

        assertEquals("Lat Pulldown", identity.canonicalName)
        assertEquals("Realleader", identity.brand)
        assertEquals(ExerciseEquipment.MACHINE, identity.equipment)
        assertEquals(ExerciseSideMode.BILATERAL, identity.sideMode)
        assertTrue("Realleader chip should be displayable", "Realleader" in identity.variantLabels())
        assertFalse("RL alone should not create a unilateral chip", "Unilateral" in identity.variantLabels())
    }

    @Test
    fun progressComparisonSeparatesLoadIncompatibleEquipmentVariants() {
        val dumbbell = ExerciseIdentityResolver.resolve(rawName = "lateral raise db")
        val machine = ExerciseIdentityResolver.resolve(rawName = "lateral raise machine")
        val cable = ExerciseIdentityResolver.resolve(rawName = "lateral raise cable")

        assertEquals("Lateral Raise", dumbbell.canonicalName)
        assertEquals(dumbbell.baseComparisonKey, machine.baseComparisonKey)
        assertEquals(dumbbell.baseComparisonKey, cable.baseComparisonKey)
        assertNotEquals(dumbbell.progressComparisonKey, machine.progressComparisonKey)
        assertNotEquals(machine.progressComparisonKey, cable.progressComparisonKey)
    }

    @Test
    fun tBarRowDoesNotBecomeStraightBarAttachment() {
        val identity = ExerciseIdentityResolver.resolve(rawName = "tbar rows prime")

        assertEquals("T-Bar Row", identity.canonicalName)
        assertEquals("Prime", identity.brand)
        assertFalse("T-bar is the exercise name, not a straight-bar attachment", "Straight bar" in identity.variantLabels())
    }

    @Test
    fun explicitUnilateralFlagStillControlsSideModeWhenBrandIsPresent() {
        val identity = ExerciseIdentityResolver.resolve(
            rawName = "leg extension rl",
            parsedName = "Leg extension",
            isUnilateral = true,
        )

        assertEquals("Leg Extension", identity.canonicalName)
        assertEquals("Realleader", identity.brand)
        assertEquals(ExerciseSideMode.UNILATERAL, identity.sideMode)
        assertTrue("raw rl wording should be retained as alias", "leg extension rl" in identity.aliases)
        assertTrue("unilateral chip should be displayable when the row flag says Uni", "Unilateral" in identity.variantLabels())
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
