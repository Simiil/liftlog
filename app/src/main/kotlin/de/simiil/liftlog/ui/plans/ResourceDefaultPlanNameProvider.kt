package de.simiil.liftlog.ui.plans

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves the default plan's name from the app's current display locale (M6 `localeConfig`). */
@Singleton
class ResourceDefaultPlanNameProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DefaultPlanNameProvider {
        override fun defaultPlanName(): String = context.getString(R.string.default_plan_name)
    }
