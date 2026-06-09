package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlanDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ---- helpers ----

    /** Constructs the ViewModel with a [SavedStateHandle] carrying a planId key,
     *  mirroring the pattern in [ActiveSessionViewModelTest] (direct key access in the VM). */
    private fun makeVm(
        planId: String,
        repo: FakePlanRepository = FakePlanRepository(),
    ): PlanDetailViewModel = PlanDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("planId" to planId)),
        planRepository = repo,
    )

    // ---- Tests ----

    @Test
    fun `planName reflects the seeded plan`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("Upper Lower")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("Upper Lower", state.planName)
            assertEquals(false, state.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `days list is empty when no day templates exist`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            val state = awaitItem()
            assertTrue("days list should be empty initially", state.days.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createDay adds a row with the given name`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            awaitItem() // initial empty state

            vm.createDay("Push")

            val state = awaitItem()
            assertEquals(1, state.days.size)
            assertEquals("Push", state.days.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createDay with blank name is a no-op`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createDay("   ") // blank
            vm.createDay("") // empty

            // No new emission expected; days stays empty
            expectNoEvents()
            assertEquals(0, vm.uiState.value.days.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renameDay updates the row name`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createDay("Old Name")
            val afterCreate = awaitItem()
            val dayId = afterCreate.days.first().id

            vm.renameDay(dayId, "New Name")
            val afterRename = awaitItem()

            assertEquals("New Name", afterRename.days.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renameDay with blank name is a no-op`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createDay("Stay")
            val afterCreate = awaitItem()
            val dayId = afterCreate.days.first().id

            vm.renameDay(dayId, "  ") // blank — should be ignored

            expectNoEvents()
            assertEquals("Stay", vm.uiState.value.days.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteDay removes the row`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createDay("To Delete")
            val afterCreate = awaitItem()
            val dayId = afterCreate.days.first().id

            vm.deleteDay(dayId)
            val afterDelete = awaitItem()

            assertTrue("days list should be empty after delete", afterDelete.days.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple days are ordered by position`() = runTest {
        val repo = FakePlanRepository()
        val plan = repo.createPlan("PPL")
        val vm = makeVm(plan.id, repo)

        vm.uiState.test {
            awaitItem() // initial

            vm.createDay("Push")
            awaitItem()
            vm.createDay("Pull")
            awaitItem()
            vm.createDay("Legs")
            val state = awaitItem()

            assertEquals(3, state.days.size)
            assertEquals("Push", state.days[0].name)
            assertEquals("Pull", state.days[1].name)
            assertEquals("Legs", state.days[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `days belong only to the target plan - other plan days not shown`() = runTest {
        val repo = FakePlanRepository()
        val planA = repo.createPlan("Plan A")
        val planB = repo.createPlan("Plan B")

        // Seed a day for Plan B — should NOT appear in Plan A's detail
        repo.createDayTemplate(planB.id, "Plan B Day")

        val vm = makeVm(planA.id, repo)

        vm.uiState.test {
            val state = awaitItem()
            assertTrue("Plan A should have no days from Plan B", state.days.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
