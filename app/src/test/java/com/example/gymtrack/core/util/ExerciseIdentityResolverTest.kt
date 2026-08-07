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
    fun bbTokenResolvesAsBootyBuilderMachineBrandNotBarbell() {
        val identity = ExerciseIdentityResolver.resolve(rawName = "BB Hip Thrust")

        assertEquals("Hip Thrust", identity.canonicalName)
        assertEquals("BootyBuilder", identity.brand)
        assertEquals(ExerciseEquipment.MACHINE, identity.equipment)
        assertEquals(null, identity.attachment)
        assertTrue("BootyBuilder chip should be displayable", "BootyBuilder" in identity.variantLabels())
        assertFalse("BB must no longer create a barbell chip", "Barbell" in identity.variantLabels())
    }

    @Test
    fun parserTreatsBbAsBootyBuilderBrand() {
        val set = WorkoutParser().parseWorkout(
            rawText = """
                BB Hip Thrust
                10x 50kg
            """.trimIndent(),
        ).single()

        assertEquals("BootyBuilder", set.brand)
        assertEquals(null, set.modifier)
        assertEquals("BootyBuilder", set.exerciseIdentity.brand)
        assertEquals(ExerciseEquipment.MACHINE, set.exerciseIdentity.equipment)
        assertFalse("parser BB must not leak a barbell modifier", "Barbell" in set.exerciseIdentity.variantLabels())
    }

    @Test
    fun barPrefixResolvesAsBarbellWithoutStraightBarAttachment() {
        val identity = ExerciseIdentityResolver.resolve(rawName = "BAR Bench Press")

        assertEquals("Bench Press", identity.canonicalName)
        assertEquals(null, identity.brand)
        assertEquals(ExerciseEquipment.BARBELL, identity.equipment)
        assertEquals(null, identity.attachment)
        assertTrue("BAR should create a barbell chip", "Barbell" in identity.variantLabels())
        assertFalse("BAR equipment should not also create a straight-bar attachment", "Straight bar" in identity.variantLabels())
    }

    @Test
    fun parserTreatsLeadingBarAsBarbellButKeepsTrailingBarAsAttachment() {
        val barbellSet = WorkoutParser().parseWorkout(
            rawText = """
                BAR Bench Press
                8x 80kg
            """.trimIndent(),
        ).single()
        val pushdownSet = WorkoutParser().parseWorkout(
            rawText = """
                tricep pushdown bar
                10x 30kg
            """.trimIndent(),
        ).single()

        assertEquals("Barbell", barbellSet.modifier)
        assertEquals(ExerciseEquipment.BARBELL, barbellSet.exerciseIdentity.equipment)
        assertEquals(null, barbellSet.exerciseIdentity.attachment)
        assertEquals("Straight Bar", pushdownSet.modifier)
        assertEquals(ExerciseEquipment.CABLE, pushdownSet.exerciseIdentity.equipment)
        assertEquals(ExerciseAttachment.STRAIGHT_BAR, pushdownSet.exerciseIdentity.attachment)
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
        assertFalse("T-bar must not be mistaken for the BAR abbreviation", "Barbell" in identity.variantLabels())
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
