package de.simiil.liftlog.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import de.simiil.liftlog.ui.navigation.ActiveSessionRoute

// singleTop: the notification's deep link arrives via onNewIntent when the activity is already
// alive — Navigation only auto-handles the cold-start intent (#36). Skip when already on the target
// session: handleDeepLink rebuilds the back stack, which would recreate the ViewModel and lose the
// dialed (unlogged) entry values.
@Composable
actual fun DeepLinkEffect(navController: NavHostController) {
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(navController, activity) {
        val listener =
            Consumer<Intent> { intent ->
                val current = navController.currentBackStackEntry
                val alreadyThere =
                    current?.destination?.hasRoute<ActiveSessionRoute>() == true &&
                        current.toRoute<ActiveSessionRoute>().sessionId == intent.data?.lastPathSegment
                if (!alreadyThere) navController.handleDeepLink(intent)
            }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
}
