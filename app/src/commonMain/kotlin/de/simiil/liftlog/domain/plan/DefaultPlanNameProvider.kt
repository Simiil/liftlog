package de.simiil.liftlog.domain.plan

/** Supplies the name used to seed the default plan (issue #30). Locale-aware implementations
 *  resolve it from the app's current display locale (M6 `localeConfig`) at seed time. */
fun interface DefaultPlanNameProvider {
    suspend fun defaultPlanName(): String
}
