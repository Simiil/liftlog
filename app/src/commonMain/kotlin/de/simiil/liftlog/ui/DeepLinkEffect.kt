package de.simiil.liftlog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/**
 * Wires the in-workout notification's deep link into [navController] while the app is already alive
 * (issue #36). Navigation only auto-handles the cold-start intent, so the Android actual registers
 * an `onNewIntent` listener and routes it into the graph (skipping the rebuild when already on the
 * target session, which would recreate the ViewModel and lose the dialed entry values). iOS ships no
 * session notification in v1, so its actual is a deliberate no-op.
 */
@Composable
expect fun DeepLinkEffect(navController: NavHostController)
