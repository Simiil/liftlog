package de.simiil.liftlog.ui.session

import androidx.compose.runtime.Composable

// Deliberate v1 no-op: no session notification ships on iOS, so there is no permission to
// request here (issue #36 is Android-only). Revisit if/when an iOS session notification lands.
@Composable
actual fun NotificationPermissionEffect(onResult: () -> Unit) {
}
