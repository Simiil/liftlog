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
    const val HOME_RECENT_ROW = "home_recent_row" // applied to EACH recent-workout row
    const val LOG_SET_BUTTON = "log_set_button"
    const val WEIGHT_INCREMENT = "weight_increment"
    const val WEIGHT_VALUE = "weight_value"
    const val LOGGED_SET_ROW = "logged_set_row" // applied to EACH logged-set row
    const val ADD_EXERCISE = "add_exercise"

    // Plan tab (single-plan UI, issue #30 PR3b)
    const val PLAN_OVERFLOW = "plan_overflow" // top-bar "⋮" → rename/delete menu
    const val PLAN_MENU_RENAME = "plan_menu_rename"
    const val PLAN_MENU_DELETE = "plan_menu_delete"
    const val PLAN_RENAME_FIELD = "plan_rename_field" // PlanNameDialog's text field
    const val PLAN_RENAME_CONFIRM = "plan_rename_confirm"
    const val PLAN_DELETE_CONFIRM = "plan_delete_confirm"
    const val PLAN_ADD_DAY = "plan_add_day" // dashed "Add training day" row
    const val PLAN_DAY_START = "plan_day_start" // the play button that starts a day's session
    const val PLAN_DAY_REMOVE = "plan_day_remove" // the X on a day row
    const val PLAN_DAY_REMOVE_CONFIRM = "plan_day_remove_confirm"

    // Multi-plan chrome: title-bar switcher + "New plan" (issue #30 PR4)
    const val PLAN_SWITCHER = "plan_switcher" // clickable title (name + dropdown arrow), shown only with 2+ plans
    const val PLAN_SWITCHER_ITEM = "plan_switcher_item" // applied to EACH item in the switcher dropdown
    const val PLAN_MENU_NEW = "plan_menu_new" // overflow "New plan" menu item
    const val PLAN_NEW_FIELD = "plan_new_field" // PlanNameDialog's text field when creating
    const val PLAN_NEW_CONFIRM = "plan_new_confirm"
    const val DAY_EDITOR_DONE = "day_editor_done"
    const val DAY_NAME_FIELD = "day_name_field"
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
    const val SESSION_DELETE_CONFIRM = "session_delete_confirm"
}
