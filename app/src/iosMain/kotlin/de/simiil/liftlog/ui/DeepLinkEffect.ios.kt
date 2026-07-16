package de.simiil.liftlog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

// Deliberate v1 no-op: no session notification ships on iOS, so there is no onNewIntent-style deep
// link to route (issue #36 is Android-only). Cold-start links, if ever added, would be handled by
// the nav graph. Revisit if/when an iOS session notification lands.
@Composable
actual fun DeepLinkEffect(navController: NavHostController) {
}
