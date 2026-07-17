package de.simiil.liftlog.domain.plan

import de.simiil.liftlog.domain.repository.PlanRepository

/**
 * Enforces the "zero live plans -> create a default" invariant (issue #30): the Plan tab must
 * never be observed with no plans at all. Called on app startup and after a backup import
 * ([ensure]), and whenever a plan is deleted ([deletePlan], which reseeds atomically if that
 * was the last live plan).
 */
class DefaultPlanEnsurer(
    private val planRepository: PlanRepository,
    private val nameProvider: DefaultPlanNameProvider,
) {
    suspend fun ensure() = planRepository.ensureDefaultPlan(nameProvider.defaultPlanName())

    suspend fun deletePlan(planId: String) = planRepository.softDeletePlanAndEnsureDefault(planId, nameProvider.defaultPlanName())
}
