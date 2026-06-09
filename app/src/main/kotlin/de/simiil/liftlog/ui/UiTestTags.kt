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

    // Plans screens (M3)
    const val PLANS_CREATE = "plans_create"
    const val PLAN_ROW = "plan_row"
    const val PLAN_DETAIL_ADD_DAY = "plan_detail_add_day"
    const val DAY_TEMPLATE_ROW = "day_template_row"
    const val TEMPLATE_ADD_EXERCISE = "template_add_exercise"
    const val TEMPLATE_EXERCISE_ROW = "template_exercise_row"
    const val HOME_TEMPLATE_CHIP = "home_template_chip"

    // Exercise Picker multi-select
    const val PICKER_ADD_SELECTED = "picker_add_selected"
}
