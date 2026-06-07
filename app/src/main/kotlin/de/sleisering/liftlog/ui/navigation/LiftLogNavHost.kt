package de.sleisering.liftlog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.sleisering.liftlog.R
import de.sleisering.liftlog.ui.analytics.AnalyticsScreen
import de.sleisering.liftlog.ui.components.PlaceholderScreen
import de.sleisering.liftlog.ui.history.HistoryScreen
import de.sleisering.liftlog.ui.home.HomeScreen
import de.sleisering.liftlog.ui.plans.PlansScreen

@Composable
fun LiftLogNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = {
                    navController.navigate(SettingsRoute) { launchSingleTop = true }
                },
            )
        }
        composable<PlansRoute> { PlansScreen() }
        composable<AnalyticsRoute> { AnalyticsScreen() }
        composable<HistoryRoute> { HistoryScreen() }
        composable<SettingsRoute> {
            PlaceholderScreen(title = stringResource(R.string.settings_title))
        }
    }
}
