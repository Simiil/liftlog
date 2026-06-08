package de.simiil.liftlog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.ui.analytics.AnalyticsScreen
import de.simiil.liftlog.ui.exercises.ExercisePickerScreen
import de.simiil.liftlog.ui.history.HistoryScreen
import de.simiil.liftlog.ui.home.HomeScreen
import de.simiil.liftlog.ui.plans.PlanDetailScreen
import de.simiil.liftlog.ui.plans.PlansScreen
import de.simiil.liftlog.ui.session.ActiveSessionScreen
import de.simiil.liftlog.ui.session.SessionDetailScreen
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
        composable<PlansRoute> {
            PlansScreen(onOpenPlan = { id -> navController.navigate(PlanDetailRoute(id)) })
        }
        composable<AnalyticsRoute> { AnalyticsScreen() }
        composable<HistoryRoute> { HistoryScreen(onOpenSessionDetail = { navController.navigate(SessionDetailRoute(it)) }) }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<ActiveSessionRoute> { entry ->
            val pickedId by entry.savedStateHandle
                .getStateFlow<String?>(PICKED_EXERCISE_ID, null)
                .collectAsStateWithLifecycle()
            ActiveSessionScreen(
                onFinished = { navController.popBackStack() },
                onDiscarded = { navController.popBackStack() },
                onAddExercise = { navController.navigate(ExercisePickerRoute) },
                pickedExerciseId = pickedId,
                onPickedExerciseConsumed = { entry.savedStateHandle[PICKED_EXERCISE_ID] = null },
            )
        }
        composable<PlanDetailRoute> {
            PlanDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenTemplate = { id -> navController.navigate(TemplateEditorRoute(id)) },
            )
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(onBack = { navController.popBackStack() })
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
