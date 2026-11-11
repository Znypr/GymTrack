package com.example.gymtrack.core.muscle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #309: user override → seed → parent walk (cycle-safe) → unmapped. */
class MuscleResolverTest {
    private val exercises = listOf(
        MuscleExerciseRef(1, "Bench Press", null),
        MuscleExerciseRef(2, "My Weird Press", parentId = 1),
        MuscleExerciseRef(3, "Mystery A", parentId = 4),
        MuscleExerciseRef(4, "Mystery B", parentId = 3),
        MuscleExerciseRef(5, "Dead Hang", null),
        MuscleExerciseRef(6, "Grandchild", parentId = 2),
    ).associateBy { it.id }

    @Test
    fun seedIsUsedWithoutAnOverride() {
        val mapping = MuscleResolver(exercises, emptyMap()).resolve(1)!!
        assertEquals(MuscleMappingOrigin.SEED, mapping.origin)
        assertEquals(MuscleMappingSeed.BENCH_BARBELL, mapping.seed)
    }

    @Test
    fun theUsersOverrideWinsAndReplacesTheSeed() {
        val overrides = mapOf(1L to listOf(MuscleOverride(Muscle.CHEST_LOWER, MuscleRole.PRIMARY), MuscleOverride(Muscle.TRICEPS, MuscleRole.SECONDARY)))
        val mapping = MuscleResolver(exercises, overrides).resolve(1)!!
        assertEquals(MuscleMappingOrigin.USER, mapping.origin)
        assertEquals(setOf(Muscle.CHEST_LOWER), mapping.primary)
        assertEquals(setOf(Muscle.TRICEPS), mapping.secondary)
    }

    @Test
    fun anOverrideWithoutAPrimaryIsIgnored() {
        val overrides = mapOf(1L to listOf(MuscleOverride(Muscle.TRICEPS, MuscleRole.SECONDARY)))
        assertEquals(MuscleMappingOrigin.SEED, MuscleResolver(exercises, overrides).resolve(1)!!.origin)
    }

    @Test
    fun unknownExercisesWalkUpToTheirParent() {
        val resolver = MuscleResolver(exercises, emptyMap())
        val child = resolver.resolve(2)!!
        assertEquals(MuscleMappingOrigin.PARENT, child.origin)
        assertEquals(1L, child.fromExerciseId)
        assertEquals(MuscleMappingSeed.BENCH_BARBELL.primary, child.primary)
        assertEquals(1L, resolver.resolve(6)!!.fromExerciseId)
    }

    @Test
    fun aParentsOverrideIsInherited() {
        val overrides = mapOf(1L to listOf(MuscleOverride(Muscle.TRICEPS, MuscleRole.PRIMARY)))
        val child = MuscleResolver(exercises, overrides).resolve(2)!!
        assertEquals(MuscleMappingOrigin.PARENT, child.origin)
        assertEquals(setOf(Muscle.TRICEPS), child.primary)
    }

    @Test
    fun parentCyclesEndUnmapped() {
        assertNull(MuscleResolver(exercises, emptyMap()).resolve(3))
    }

    @Test
    fun unmappedIsNullNeverAnEmptyMapping() {
        val resolver = MuscleResolver(exercises, emptyMap())
        assertNull(resolver.resolve(5))
        assertNull(resolver.resolve(999))
    }

    @Test
    fun theModifierRefinesTheSeed() {
        val resolver = MuscleResolver(exercises, emptyMap())
        assertEquals(MuscleMappingSeed.BENCH_INCLINE, resolver.resolve(1, "Incline")!!.seed)
        assertEquals(MuscleMappingSeed.BENCH_BARBELL, resolver.resolve(1, null)!!.seed)
    }
}
