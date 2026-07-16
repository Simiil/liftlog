package de.simiil.liftlog.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.simiil.liftlog.ui.navigation.AnalyticsRoute
import de.simiil.liftlog.ui.navigation.HistoryRoute
import de.simiil.liftlog.ui.navigation.HomeRoute
import de.simiil.liftlog.ui.navigation.LiftLogNavHost
import de.simiil.liftlog.ui.navigation.PlanRoute
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.tab_analytics
import liftlog.app.generated.resources.tab_history
import liftlog.app.generated.resources.tab_home
import liftlog.app.generated.resources.tab_plans
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    val labelRes: StringResource,
)

// 03-ux-spec §2: Home · Plan · Analytics · History
private val topLevelDestinations =
    listOf(
        TopLevelDestination(HomeRoute, Icons.Outlined.Home, Res.string.tab_home),
        TopLevelDestination(PlanRoute, Icons.Outlined.FitnessCenter, Res.string.tab_plans),
        TopLevelDestination(AnalyticsRoute, Icons.Outlined.Insights, Res.string.tab_analytics),
        TopLevelDestination(HistoryRoute, Icons.Outlined.History, Res.string.tab_history),
    )

@Composable
fun LiftLogApp() {
    val navController = rememberNavController()

    // Notification deep-link wiring (#36) is platform-specific — see [DeepLinkEffect].
    DeepLinkEffect(navController)

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
