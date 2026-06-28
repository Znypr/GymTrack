package com.example.gymtrack.core.data

internal inline fun <T> NoteDatabase.use(block: (NoteDatabase) -> T): T {
    return try {
        block(this)
    } finally {
        close()
    }
}
