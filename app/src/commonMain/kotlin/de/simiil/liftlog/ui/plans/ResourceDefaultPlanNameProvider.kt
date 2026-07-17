package de.simiil.liftlog.ui.plans

import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.default_plan_name
import org.jetbrains.compose.resources.getString

/** Resolves the default plan's name from the app's current display locale (M6 `localeConfig`). */
class ResourceDefaultPlanNameProvider : DefaultPlanNameProvider {
    override suspend fun defaultPlanName(): String = getString(Res.string.default_plan_name)
}
