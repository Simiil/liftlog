package de.simiil.liftlog.ui.plans

import android.content.Context
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider

/** Resolves the default plan's name from the app's current display locale (M6 `localeConfig`). */
class ResourceDefaultPlanNameProvider(
    private val context: Context,
) : DefaultPlanNameProvider {
    override fun defaultPlanName(): String = context.getString(R.string.default_plan_name)
}
