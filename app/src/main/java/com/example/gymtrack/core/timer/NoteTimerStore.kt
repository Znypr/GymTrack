package com.example.gymtrack.core.timer

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.noteTimerDataStore by preferencesDataStore(name = "note_timer")

object NoteTimerStore {
    private val ACTIVE_NOTE = longPreferencesKey("active_note_timestamp")
    private val ACCUMULATED_SECONDS = longPreferencesKey("accumulated_seconds")
    private val STARTED_AT = longPreferencesKey("started_at_epoch_millis")
    private val IS_RUNNING = booleanPreferencesKey("is_running")

    fun observe(context: Context): Flow<NoteTimerState> = context.noteTimerDataStore.data
        .map { preferences -> preferences.toTimerState() }
        .distinctUntilChanged()

    suspend fun startOrRestore(
        context: Context,
        noteTimestamp: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        context.noteTimerDataStore.edit { preferences ->
            val current = preferences.toTimerState()
            if (current.activeNoteTimestamp == noteTimestamp) return@edit
            preferences.write(NoteTimerState.started(noteTimestamp, nowEpochMillis))
        }
    }

    suspend fun pause(
        context: Context,
        noteTimestamp: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        context.noteTimerDataStore.edit { preferences ->
            val current = preferences.toTimerState()
            if (current.activeNoteTimestamp != noteTimestamp) return@edit
            preferences.write(current.pauseAt(nowEpochMillis))
        }
    }

    suspend fun resume(
        context: Context,
        noteTimestamp: Long,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        context.noteTimerDataStore.edit { preferences ->
            val current = preferences.toTimerState()
            preferences.write(current.resumeAt(noteTimestamp, nowEpochMillis))
        }
    }

    suspend fun stop(context: Context, noteTimestamp: Long? = null) {
        context.noteTimerDataStore.edit { preferences ->
            val current = preferences.toTimerState()
            if (noteTimestamp != null && current.activeNoteTimestamp != noteTimestamp) return@edit
            preferences.clear()
        }
    }

    private fun Preferences.toTimerState(): NoteTimerState = NoteTimerState(
        activeNoteTimestamp = this[ACTIVE_NOTE],
        accumulatedSeconds = this[ACCUMULATED_SECONDS] ?: 0L,
        startedAtEpochMillis = this[STARTED_AT],
        isRunning = this[IS_RUNNING] ?: false,
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(state: NoteTimerState) {
        clear()
        state.activeNoteTimestamp?.let { this[ACTIVE_NOTE] = it }
        this[ACCUMULATED_SECONDS] = state.accumulatedSeconds.coerceAtLeast(0L)
        state.startedAtEpochMillis?.let { this[STARTED_AT] = it }
        this[IS_RUNNING] = state.isRunning
    }
}
