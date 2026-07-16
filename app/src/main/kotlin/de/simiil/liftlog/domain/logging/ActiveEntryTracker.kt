package de.simiil.liftlog.domain.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The active session-exercise and the values LOG SET would log right now. */
data class ActiveEntry(
    val sessionExerciseId: String,
    val weightKg: Double?,
    val reps: Int,
)

/**
 * In-memory bridge between the Active Session screen and the session notification:
 * the ViewModel mirrors its entry state here so the notification shows (and logs)
 * exactly what the app would. Empty after process death — readers fall back to
 * [ActiveExerciseDefaults] + [Prefill].
 */
class ActiveEntryTracker {
    private val _state = MutableStateFlow<ActiveEntry?>(null)
    val state: StateFlow<ActiveEntry?> = _state

    fun update(entry: ActiveEntry?) {
        _state.value = entry
    }

    fun clear() {
        _state.value = null
    }
}
