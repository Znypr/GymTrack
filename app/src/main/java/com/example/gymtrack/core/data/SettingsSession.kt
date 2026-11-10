package com.example.gymtrack.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the app's settings for one Activity and guards persisted settings against the
 * startup race in #291.
 *
 * - [settings] stays `null` until the first [load] returns, so the UI never renders
 *   (or saves) default values in place of stored ones.
 * - Nothing is saved on load. [save] only runs for explicit user changes made through
 *   [update] after loading has finished; updates before that are ignored.
 */
class SettingsSession(
    private val scope: CoroutineScope,
    private val load: suspend () -> Settings,
    private val save: suspend (Settings) -> Unit,
    private val onLoadError: (Throwable) -> Unit = {},
) {
    private val _settings = MutableStateFlow<Settings?>(null)
    val settings: StateFlow<Settings?> = _settings.asStateFlow()

    private val saveMutex = Mutex()
    private var loadJob: Job? = null

    fun start(): Job = loadJob ?: scope.launch {
        val loaded = try {
            load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A failed read keeps the stored file untouched: defaults are shown, but nothing
            // is written until the user changes a setting.
            onLoadError(error)
            Settings()
        }
        _settings.value = loaded
    }.also { loadJob = it }

    /** Applies a user change and persists it. Ignored until the first load has completed. */
    fun update(newSettings: Settings): Boolean {
        if (_settings.value == null) return false
        if (_settings.value == newSettings) return true
        _settings.value = newSettings
        scope.launch {
            saveMutex.withLock { save(newSettings) }
        }
        return true
    }
}
