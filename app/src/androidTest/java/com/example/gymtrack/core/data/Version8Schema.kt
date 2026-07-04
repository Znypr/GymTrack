package com.example.gymtrack.core.data

internal val VERSION_8_SCHEMA = listOf(
    """
    CREATE TABLE notes (
        timestamp INTEGER NOT NULL,
        title TEXT NOT NULL,
        text TEXT NOT NULL,
        categoryName TEXT,
        categoryColor INTEGER,
        learnings TEXT,
        PRIMARY KEY(timestamp)
    )
    """.trimIndent(),
    """
    CREATE TABLE exercises (
        exerciseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        name TEXT NOT NULL,
        parentId INTEGER,
        muscleGroup TEXT,
        aliases TEXT NOT NULL
    )
    """.trimIndent(),
    """
    CREATE TABLE sets (
        setId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        workoutId INTEGER NOT NULL,
        exerciseId INTEGER NOT NULL,
        weight REAL NOT NULL,
        reps INTEGER NOT NULL,
        isUnilateral INTEGER NOT NULL,
        modifier TEXT,
        brand TEXT,
        relativeTime TEXT,
        absoluteTime TEXT,
        FOREIGN KEY(exerciseId) REFERENCES exercises(exerciseId)
            ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX index_sets_exerciseId ON sets(exerciseId)",
    "CREATE INDEX index_sets_workoutId ON sets(workoutId)",
)
