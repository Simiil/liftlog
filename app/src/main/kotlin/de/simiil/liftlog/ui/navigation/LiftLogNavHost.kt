package de.simiil.liftlog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.simiil.liftlog.ui.analytics.AnalyticsScreen
import de.simiil.liftlog.ui.exercises.ExercisePickerScreen
import de.simiil.liftlog.ui.history.HistoryScreen
import de.simiil.liftlog.ui.home.HomeScreen
import de.simiil.liftlog.ui.plans.PlansScreen
import de.simiil.liftlog.ui.settings.SettingsScreen

@Composable
fun LiftLogNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = {
                    navController.navigate(SettingsRoute) { launchSingleTop = true }
                },
                onOpenSession = { id -> navController.navigate(ActiveSessionRoute(id)) },
                onOpenSessionDetail = { id -> navController.navigate(SessionDetailRoute(id)) },
            )
        }
        composable<PlansRoute> { PlansScreen() }
        composable<AnalyticsRoute> { AnalyticsScreen() }
        composable<HistoryRoute> { HistoryScreen() }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<ExercisePickerRoute> {
            ExercisePickerScreen(
                onSelected = { exerciseId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE_ID, exerciseId)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
