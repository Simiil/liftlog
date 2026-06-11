package de.simiil.liftlog.ui

/**
 * Stable [androidx.compose.ui.platform.testTag] identifiers shared between production
 * screens and instrumented Compose UI tests (M2 critical-path test, 05-roadmap).
 *
 * These are inert in production (a testTag adds an invisible semantics property only)
 * and exist so the headline logging-path test can address nodes by a stable handle
 * instead of brittle, localisation-sensitive text.
 */
object UiTestTags {
    const val HOME_START_EMPTY = "home_start_empty"
    const val HOME_RESUME_CARD = "home_resume_card"
    const val LOG_SET_BUTTON = "log_set_button"
    const val WEIGHT_INCREMENT = "weight_increment"
    const val WEIGHT_VALUE = "weight_value"
    const val LOGGED_SET_ROW = "logged_set_row" // applied to EACH logged-set row
    const val ADD_EXERCISE = "add_exercise"

    // Plans screens (M3 redesign)
    const val PLANS_CREATE = "plans_create"
    const val PLAN_ROW = "plan_row" // plan-group header on the Plans list (→ edit plan)
    const val PLAN_DAY_ROW = "plan_day_row" // a day row (Plans list day rows + PlanEditor day rows)
    const val PLAN_DAY_START = "plan_day_start" // the play button that starts a day's session
    const val PLAN_EDITOR_SAVE = "plan_editor_save"
    const val PLAN_EDITOR_CANCEL = "plan_editor_cancel"
    const val DAY_EDITOR_DONE = "day_editor_done"
    const val TEMPLATE_ADD_EXERCISE = "template_add_exercise"
    const val TEMPLATE_EXERCISE_ROW = "template_exercise_row"
    const val HOME_TEMPLATE_CHIP = "home_template_chip"

    // Exercise Picker multi-select
    const val PICKER_ADD_SELECTED = "picker_add_selected"

    // Workout-level RPE / note (session meta)
    const val RPE_INCREMENT = "rpe_increment"
    const val SESSION_META_ROW = "session_meta_row"
    const val SESSION_META_NOTE = "session_meta_note"
    const val SESSION_EDIT_DELETE = "session_edit_delete"
}
