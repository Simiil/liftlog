package de.simiil.liftlog.domain.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Signal that the notification permission may have just changed (the contextual prompt
 * resolved). The coordinator folds it into its start conditions so a mid-session grant
 * takes effect immediately instead of waiting for the next app foreground.
 */
class NotificationPermissionTick {
    private val _ticks = MutableStateFlow(0)
    val ticks: StateFlow<Int> = _ticks

    fun bump() {
        _ticks.update { it + 1 }
    }
}
