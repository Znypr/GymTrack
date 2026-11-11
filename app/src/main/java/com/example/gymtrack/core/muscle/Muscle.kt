package com.example.gymtrack.core.muscle

/**
 * The 26 muscle regions of the body map (#309, #311). [id] values are the binding keys from
 * `docs/design/ANATOMY.md`: they are stored (user overrides, derived `muscle_week` rows, backups),
 * so never rename or reuse one. Shapes: `docs/design/reference/Anatomy.dc.html`; mapping and
 * sources: `docs/design/ANATOMY_MAPPING.md`.
 */
enum class Muscle(val id: String, val label: String, val front: Boolean, val back: Boolean) {
    DELT_FRONT("deltFront", "Front delts", front = true, back = false),
    DELT_SIDE("deltSide", "Side delts", front = true, back = true),
    DELT_REAR("deltRear", "Rear delts", front = false, back = true),
    CHEST_UPPER("chestUpper", "Upper chest", front = true, back = false),
    CHEST_LOWER("chestLower", "Lower chest", front = true, back = false),
    SERRATUS("serratus", "Serratus", front = true, back = false),
    TRAPS_UPPER("trapsUpper", "Upper traps", front = true, back = true),
    TRAPS_MID_LOWER("trapsMidLower", "Mid/lower traps", front = false, back = true),
    RHOMBOIDS_TERES("rhomboidsTeres", "Rhomboids & teres", front = false, back = true),
    LATS("lats", "Lats", front = true, back = true),
    ERECTORS("erectors", "Lower back", front = false, back = true),
    BICEPS("biceps", "Biceps", front = true, back = false),
    BRACHIALIS("brachialis", "Brachialis", front = true, back = false),
    TRICEPS("triceps", "Triceps", front = false, back = true),
    FOREARM_FLEXORS("forearmFlexors", "Forearm flexors", front = true, back = false),
    FOREARM_EXTENSORS("forearmExtensors", "Forearm extensors", front = true, back = true),
    ABS("abs", "Abs", front = true, back = false),
    OBLIQUES("obliques", "Obliques", front = true, back = true),
    GLUTE_MAX("gluteMax", "Glutes", front = false, back = true),
    GLUTE_MED("gluteMed", "Side glutes", front = true, back = true),
    QUADS("quads", "Quads", front = true, back = false),
    ADDUCTORS("adductors", "Adductors", front = true, back = true),
    HAMSTRINGS("hamstrings", "Hamstrings", front = false, back = true),
    GASTROCNEMIUS("gastrocnemius", "Calves", front = true, back = true),
    SOLEUS("soleus", "Soleus", front = true, back = true),
    TIBIALIS("tibialis", "Shins", front = true, back = false),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        /** The muscle for a stored id, or null for an unknown id (never guessed). */
        fun fromId(id: String): Muscle? = byId[id]
    }
}

/** Weekly training level of one region (#309): fractional working sets per week. */
enum class MuscleLevel {
    NONE, LOW, MODERATE, HIGH,

    /** No mapping for the exercises that would train it: shown dotted, never treated as zero. */
    UNMAPPED,
    ;

    companion object {
        /** none 0 · low under 5 · moderate 5 to under 10 · high 10+ fractional sets (decision deck). */
        fun forWeeklySets(sets: Float): MuscleLevel = when {
            sets <= 0f -> NONE
            sets < 5f -> LOW
            sets < 10f -> MODERATE
            else -> HIGH
        }
    }
}
