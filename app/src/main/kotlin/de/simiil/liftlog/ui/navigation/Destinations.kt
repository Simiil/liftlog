package de.simiil.liftlog.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object PlansRoute
@Serializable data object AnalyticsRoute
@Serializable data object HistoryRoute
@Serializable data object SettingsRoute

@Serializable data class ActiveSessionRoute(val sessionId: String)
@Serializable data class SessionDetailRoute(val sessionId: String)

/** Exercise picker. [multiSelect] = true reaches the multi-select day-editor flow; false keeps the
 *  single-select Active-Session flow (returns one id). */
@Serializable data class ExercisePickerRoute(val multiSelect: Boolean = false)

/** Draft plan editor. [planId] null = new plan; else edit the existing plan. */
@Serializable data class PlanEditorRoute(val planId: String? = null)

/** Analytics exercise detail. Reached from the Analytics browser; not a top-level tab. */
@Serializable data class AnalyticsExerciseDetailRoute(val exerciseId: String)

/** savedStateHandle key the Exercise Picker writes its single selected exercise id to,
 *  read by the caller (e.g. Active Session) after popBackStack. */
const val PICKED_EXERCISE_ID = "picked_exercise_id"

/** savedStateHandle key the Exercise Picker writes its multi-selected exercise ids to,
 *  read by the PlanEditor (day mode) after popBackStack. */
const val PICKED_EXERCISE_IDS = "picked_exercise_ids"
