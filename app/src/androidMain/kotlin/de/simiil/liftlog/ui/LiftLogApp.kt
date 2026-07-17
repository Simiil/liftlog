package de.simiil.liftlog.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.navigation.ActiveSessionRoute
import de.simiil.liftlog.ui.navigation.AnalyticsRoute
import de.simiil.liftlog.ui.navigation.HistoryRoute
import de.simiil.liftlog.ui.navigation.HomeRoute
import de.simiil.liftlog.ui.navigation.LiftLogNavHost
import de.simiil.liftlog.ui.navigation.PlanRoute

private data class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
)

// 03-ux-spec §2: Home · Plan · Analytics · History
private val topLevelDestinations =
    listOf(
        TopLevelDestination(HomeRoute, Icons.Outlined.Home, R.string.tab_home),
        TopLevelDestination(PlanRoute, Icons.Outlined.FitnessCenter, R.string.tab_plans),
        TopLevelDestination(AnalyticsRoute, Icons.Outlined.Insights, R.string.tab_analytics),
        TopLevelDestination(HistoryRoute, Icons.Outlined.History, R.string.tab_history),
    )

@Composable
fun LiftLogApp() {
    val navController = rememberNavController()

    // singleTop: the notification's deep link arrives via onNewIntent when the activity is
    // already alive — Navigation only auto-handles the cold-start intent (#36). Skip when
    // already on the target session: handleDeepLink rebuilds the back stack, which would
    // recreate the ViewModel and lose the dialed (unlogged) entry values.
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Bottom bar only on top-level tabs; Settings (and later the active
    // session, 03-ux-spec §2) sits above it.
    val onTopLevelTab =
        topLevelDestinations.any { destination ->
            currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
        }

    Scaffold(
        // Inner screens own their insets (their own Scaffold/top bars);
        // the outer Scaffold only contributes the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (onTopLevelTab) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected =
                            currentDestination
                                ?.hierarchy
                                ?.any { it.hasRoute(destination.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = {
                                Text(
                                    stringResource(destination.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LiftLogNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}
