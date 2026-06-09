package de.simiil.liftlog.ui.plans

import app.cash.turbine.test
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlansViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun makeVm(repo: FakePlanRepository = FakePlanRepository()) =
        PlansViewModel(repo)

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun `empty repo - no plan rows after load`() = runTest {
        val vm = makeVm()

        vm.uiState.test {
            val state = awaitItem()
            assertTrue("plans list should be empty", state.plans.isEmpty())
            assertEquals("loading should be false after first emission", false, state.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createPlan adds a row with the given name`() = runTest {
        val repo = FakePlanRepository()
        val vm = makeVm(repo)

        vm.uiState.test {
            awaitItem() // initial empty state

            vm.createPlan("PPL")

            val state = awaitItem()
            assertEquals(1, state.plans.size)
            assertEquals("PPL", state.plans.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createPlan with blank name is a no-op`() = runTest {
        val repo = FakePlanRepository()
        val vm = makeVm(repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createPlan("   ") // blank
            vm.createPlan("") // empty

            // No new emission expected; state stays with zero plans
            expectNoEvents()
            assertEquals(0, vm.uiState.value.plans.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renamePlan updates the row name`() = runTest {
        val repo = FakePlanRepository()
        val vm = makeVm(repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createPlan("Old Name")
            val afterCreate = awaitItem()
            val planId = afterCreate.plans.first().id

            vm.renamePlan(planId, "New Name")
            val afterRename = awaitItem()

            assertEquals("New Name", afterRename.plans.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renamePlan with blank name is a no-op`() = runTest {
        val repo = FakePlanRepository()
        val vm = makeVm(repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createPlan("Stay")
            val afterCreate = awaitItem()
            val planId = afterCreate.plans.first().id

            vm.renamePlan(planId, "  ") // blank — should be ignored

            expectNoEvents()
            assertEquals("Stay", vm.uiState.value.plans.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deletePlan removes the row`() = runTest {
        val repo = FakePlanRepository()
        val vm = makeVm(repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createPlan("To Delete")
            val afterCreate = awaitItem()
            val planId = afterCreate.plans.first().id

            vm.deletePlan(planId)
            val afterDelete = awaitItem()

            assertTrue("plans list should be empty after delete", afterDelete.plans.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple plans are ordered by position`() = runTest {
        val repo = FakePlanRepository()
        val vm = makeVm(repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createPlan("First")
            awaitItem()
            vm.createPlan("Second")
            awaitItem()
            vm.createPlan("Third")
            val state = awaitItem()

            assertEquals(3, state.plans.size)
            assertEquals("First", state.plans[0].name)
            assertEquals("Second", state.plans[1].name)
            assertEquals("Third", state.plans[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
