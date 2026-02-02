package com.example.gymtrack.core.data

enum class ExerciseFlag {
    BILATERAL,
    UNILATERAL,
    SUPERSET;

    fun next(): ExerciseFlag = when (this) {
        BILATERAL -> UNILATERAL
        UNILATERAL -> SUPERSET
        SUPERSET -> BILATERAL
    }
}
