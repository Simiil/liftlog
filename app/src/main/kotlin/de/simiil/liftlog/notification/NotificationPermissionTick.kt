package de.simiil.liftlog.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signal that the notification permission may have just changed (the contextual prompt
 * resolved). The coordinator folds it into its start conditions so a mid-session grant
 * takes effect immediately instead of waiting for the next app foreground.
 */
@Singleton
class NotificationPermissionTick
    @Inject
    constructor() {
        private val _ticks = MutableStateFlow(0)
        val ticks: StateFlow<Int> = _ticks

        fun bump() {
            _ticks.update { it + 1 }
        }
    }
