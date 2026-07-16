package de.simiil.liftlog.ui.session

import androidx.compose.runtime.Composable

/**
 * Contextual POST_NOTIFICATIONS prompt (issue #36): plain system dialog, no rationale UI. Android
 * actual fires it once per [de.simiil.liftlog.ui.session.ActiveSessionScreen] entry when not
 * already granted (SDK 33+); iOS actual is a deliberate no-op — v1 ships no session notification
 * on iOS, so there is no equivalent permission to request.
 */
@Composable
expect fun NotificationPermissionEffect(onResult: () -> Unit)
