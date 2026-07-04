package de.simiil.liftlog.domain.plan

import de.simiil.liftlog.testing.FakePlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DefaultPlanEnsurerTest {
    @Test
    fun `ensure passes the provider name through to ensureDefaultPlan`() =
        runTest {
            val repo = FakePlanRepository()
            val ensurer = DefaultPlanEnsurer(repo, DefaultPlanNameProvider { "Default" })

            ensurer.ensure()

            assertEquals(1, repo.plans.size)
            assertEquals(
                "Default",
                repo.plans.values
                    .first()
                    .name,
            )
        }

    @Test
    fun `deletePlan delegates to the atomic repo method with the provider name`() =
        runTest {
            val repo = FakePlanRepository()
            val plan = repo.createPlan("Old")
            val ensurer = DefaultPlanEnsurer(repo, DefaultPlanNameProvider { "Default" })

            ensurer.deletePlan(plan.id)

            // atomic delete+reseed: the old plan is tombstoned, a fresh default takes its place
            val live = repo.plans.values.filter { it.deletedAt == null }
            assertEquals(1, live.size)
            val remaining = live.first()
            assertEquals("Default", remaining.name)
            assertNotEquals(plan.id, remaining.id)
        }
}
