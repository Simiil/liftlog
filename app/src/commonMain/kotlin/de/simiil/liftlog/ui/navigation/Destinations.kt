package de.simiil.liftlog.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object PlanRoute

@Serializable data object AnalyticsRoute

@Serializable data object HistoryRoute

@Serializable data object SettingsRoute

@Serializable data class ActiveSessionRoute(
    val sessionId: String,
)

@Serializable data class SessionDetailRoute(
    val sessionId: String,
)

/** Exercise picker. [multiSelect] = true reaches the multi-select day-editor flow; false keeps the
 *  single-select Active-Session flow (returns one id). */
@Serializable data class ExercisePickerRoute(
    val multiSelect: Boolean = false,
)

/** DB-backed, single-day autosave editor (issue #30 PR3b). [templateId] is a real, already-created
 *  day; [isNew] only drives the "New day"/"Edit day" title. */
@Serializable data class DayEditorRoute(
    val templateId: String,
    val isNew: Boolean = false,
)

/** Analytics exercise detail. Reached from the Analytics browser; not a top-level tab. */
@Serializable data class AnalyticsExerciseDetailRoute(
    val exerciseId: String,
)

/** Deep-link base for [ActiveSessionRoute] (`liftlog://session/{sessionId}`) — internal-only,
 *  used by the session notification's content PendingIntent; no manifest intent-filter. */
const val SESSION_DEEP_LINK_BASE = "liftlog://session"

/** savedStateHandle key the Exercise Picker writes its single selected exercise id to,
 *  read by the caller (e.g. Active Session) after popBackStack. */
const val PICKED_EXERCISE_ID = "picked_exercise_id"

/** savedStateHandle key the Exercise Picker writes its multi-selected exercise ids to,
 *  read by the Day Editor after popBackStack. */
const val PICKED_EXERCISE_IDS = "picked_exercise_ids"
