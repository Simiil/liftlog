package de.simiil.liftlog.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object PlansRoute
@Serializable data object AnalyticsRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

@Serializable data class ActiveSessionRoute(val sessionId: String)
@Serializable data class SessionDetailRoute(val sessionId: String)
@Serializable data object ExercisePickerRoute
@Serializable data class PlanDetailRoute(val planId: String)
@Serializable data class TemplateEditorRoute(val templateId: String)

/** savedStateHandle key the Exercise Picker writes its selected exercise id to,
 *  read by the caller (e.g. Active Session) after popBackStack. */
const val PICKED_EXERCISE_ID = "picked_exercise_id"
