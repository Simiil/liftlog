package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PlanEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val names =
        de.simiil.liftlog.ui.exercises
            .ExerciseNameResolver { _, fallback -> fallback }

    private fun exercise(
        id: String,
        name: String,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        isBuiltIn = true,
        isHidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

    private fun makeVm(
        planId: String? = null,
        planRepo: FakePlanRepository = FakePlanRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
    ) = PlanEditorViewModel(
        savedStateHandle = SavedStateHandle(if (planId != null) mapOf("planId" to planId) else emptyMap()),
        planRepository = planRepo,
        exerciseRepository = exerciseRepo,
        names = names,
    )

    // ── New plan, full happy path ────────────────────────────────────────────

    @Test
    fun `new plan - build draft and save produces expected plan, day, items`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench Press"), exercise("ex2", "Fly"))
            val vm = makeVm(planRepo = planRepo, exerciseRepo = exerciseRepo)

            vm.uiState.test {
                awaitItem() // initial empty draft

                vm.setPlanName("Upper / Lower")
                vm.addDay()
                // Read the generated day key from the latest state.
                var state = awaitItemUntil { it.mode == PlanEditorMode.DAY }
                val dayKey = state.editingDay!!.key

                vm.setDayName("Upper A")
                vm.addExercises(listOf("ex1", "ex2"))
                state = awaitItemUntil { it.editingDay?.exercises?.size == 2 }

                val itemKey =
                    state.editingDay!!
                        .exercises
                        .first()
                        .key
                vm.setTargets(itemKey, sets = 4, repsMin = 6, repsMax = 10)
                state =
                    awaitItemUntil {
                        it.editingDay
                            ?.exercises
                            ?.first()
                            ?.targetSets == 4
                    }

                assertTrue("Save should be enabled once a valid day exists", state.canSave)

                var savedId: String? = null
                vm.save { savedId = it }

                // Verify the persisted shape via the fake repository snapshots.
                val plan = planRepo.plans.values.single { it.deletedAt == null }
                assertEquals(savedId, plan.id)
                assertEquals("Upper / Lower", plan.name)

                val day = planRepo.dayTemplates.values.single { it.deletedAt == null }
                assertEquals(plan.id, day.planId)
                assertEquals("Upper A", day.name)

                val items =
                    planRepo.templateExercises.values
                        .filter { it.templateId == day.id && it.deletedAt == null }
                        .sortedBy { it.position }
                assertEquals(listOf("ex1", "ex2"), items.map { it.exerciseId })
                assertEquals(4, items.first().targetSets)
                assertEquals(6, items.first().targetRepsMin)
                assertEquals(10, items.first().targetRepsMax)

                // dayKey was a fresh UUID (not an existing template id)
                assertFalse(dayKey == day.id) // new draft key differs from the persisted id
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Gating ──────────────────────────────────────────────────────────────

    @Test
    fun `canSave requires plan name and at least one valid day`() =
        runTest {
            val vm = makeVm()
            vm.uiState.test {
                assertFalse(awaitItem().canSave) // empty

                vm.setPlanName("PPL")
                assertFalse(awaitItem().canSave) // name but no day

                vm.addDay()
                awaitItemUntil { it.mode == PlanEditorMode.DAY } // day with no name/items → invalid
                assertFalse(vm.uiState.value.canSave)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `canDone requires day name and at least one exercise`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Squat"))
            val vm = makeVm(exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItem()
                vm.addDay()
                awaitItemUntil { it.mode == PlanEditorMode.DAY }
                assertFalse(vm.uiState.value.canDone) // no name, no exercises

                vm.setDayName("Push")
                awaitItemUntil { it.editingDay?.name == "Push" }
                assertFalse(vm.uiState.value.canDone) // name but no exercises

                vm.addExercises(listOf("ex1"))
                awaitItemUntil { it.editingDay?.exercises?.isNotEmpty() == true }
                assertTrue(vm.uiState.value.canDone)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── addExercises dedupe ──────────────────────────────────────────────────

    @Test
    fun `addExercises dedupes ids already present in the day`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"), exercise("ex2", "Row"))
            val vm = makeVm(exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItem()
                vm.addDay()
                awaitItemUntil { it.mode == PlanEditorMode.DAY }

                vm.addExercises(listOf("ex1", "ex2"))
                awaitItemUntil { it.editingDay?.exercises?.size == 2 }

                // Re-adding ex1 (and a fresh ex2) must not duplicate.
                vm.addExercises(listOf("ex1", "ex2"))
                // No size change expected; assert from the snapshot.
                val day = vm.uiState.value.editingDay!!
                assertEquals(2, day.exercises.size)
                assertEquals(listOf("ex1", "ex2"), day.exercises.map { it.exerciseId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Reorder / remove ─────────────────────────────────────────────────────

    @Test
    fun `reorderItems updates draft order`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "A"), exercise("ex2", "B"))
            val vm = makeVm(exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItem()
                vm.addDay()
                awaitItemUntil { it.mode == PlanEditorMode.DAY }
                vm.addExercises(listOf("ex1", "ex2"))
                var state = awaitItemUntil { it.editingDay?.exercises?.size == 2 }

                val keys = state.editingDay!!.exercises.map { it.key }
                vm.reorderItems(listOf(keys[1], keys[0]))
                state =
                    awaitItemUntil {
                        it.editingDay
                            ?.exercises
                            ?.first()
                            ?.key == keys[1]
                    }
                assertEquals(listOf("ex2", "ex1"), state.editingDay!!.exercises.map { it.exerciseId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removeDay drops the day from the draft`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "A"))
            val vm = makeVm(exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItem()
                vm.addDay()
                var state = awaitItemUntil { it.mode == PlanEditorMode.DAY }
                val key = state.editingDay!!.key
                vm.closeDayEditor()
                state = awaitItemUntil { it.mode == PlanEditorMode.PLAN }
                assertEquals(1, state.days.size)

                vm.removeDay(key)
                state = awaitItemUntil { it.days.isEmpty() }
                assertTrue(state.days.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Edit existing plan ───────────────────────────────────────────────────

    @Test
    fun `edit existing - draft loads, reorder days, remove an exercise, save reconciles`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value =
                listOf(
                    exercise("ex1", "Bench"),
                    exercise("ex2", "Fly"),
                    exercise("ex3", "Squat"),
                )

            // Seed an existing plan: 2 days; day A has ex1+ex2, day B has ex3.
            val plan = planRepo.createPlan("PPL")
            val dayA = planRepo.createDayTemplate(plan.id, "Day A")
            val dayB = planRepo.createDayTemplate(plan.id, "Day B")
            val teA1 = planRepo.addExerciseToTemplate(dayA.id, "ex1")
            planRepo.addExerciseToTemplate(dayA.id, "ex2")
            planRepo.addExerciseToTemplate(dayB.id, "ex3")

            val vm = makeVm(planId = plan.id, planRepo = planRepo, exerciseRepo = exerciseRepo)
            vm.uiState.test {
                var state = awaitItemUntil { it.planName == "PPL" && it.days.size == 2 }
                // isNewPlan=false shows the Delete-plan row (PlanEditorScreen gates it on !isNewPlan).
                assertFalse(state.isNewPlan)
                assertEquals(listOf("Day A", "Day B"), state.days.map { it.name })
                // existing day keys == template ids (so reconciliation preserves rows)
                assertEquals(dayA.id, state.days[0].key)

                // Reorder days: B then A.
                vm.reorderDays(listOf(dayB.id, dayA.id))
                state = awaitItemUntil { it.days.first().key == dayB.id }
                assertEquals(listOf("Day B", "Day A"), state.days.map { it.name })

                // Remove ex1 from Day A: open it, remove the item.
                vm.editDay(dayA.id)
                state = awaitItemUntil { it.editingDay?.key == dayA.id }
                val ex1Key =
                    state.editingDay!!
                        .exercises
                        .first { it.exerciseId == "ex1" }
                        .key
                vm.removeItem(ex1Key)
                awaitItemUntil { it.editingDay?.exercises?.size == 1 }
                vm.closeDayEditor()
                awaitItemUntil { it.mode == PlanEditorMode.PLAN }

                vm.save { }

                // Day positions reindexed to the new order.
                val persistedA = planRepo.dayTemplates[dayA.id]!!
                val persistedB = planRepo.dayTemplates[dayB.id]!!
                assertEquals(0, persistedB.position)
                assertEquals(1, persistedA.position)

                // ex1 soft-deleted; ex2 preserved (same id), ex3 untouched.
                assertEquals(planRepo.templateExercises[teA1.id]!!.deletedAt != null, true)
                val liveInA =
                    planRepo.templateExercises.values
                        .filter { it.templateId == dayA.id && it.deletedAt == null }
                        .map { it.exerciseId }
                assertEquals(listOf("ex2"), liveInA)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `edit existing - rename plan persists on save`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val plan = planRepo.createPlan("Old Name")
            val day = planRepo.createDayTemplate(plan.id, "Day")
            planRepo.addExerciseToTemplate(day.id, "ex1")

            val vm = makeVm(planId = plan.id, planRepo = planRepo, exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.planName == "Old Name" }
                vm.setPlanName("New Name")
                awaitItemUntil { it.planName == "New Name" }

                var savedId: String? = null
                vm.save { savedId = it }
                assertEquals(plan.id, savedId)
                assertEquals("New Name", planRepo.plans[plan.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `save drops invalid days`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val vm = makeVm(planRepo = planRepo, exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItem()
                vm.setPlanName("Plan")
                // Day 1: valid
                vm.addDay()
                var state = awaitItemUntil { it.mode == PlanEditorMode.DAY }
                vm.setDayName("Good")
                vm.addExercises(listOf("ex1"))
                awaitItemUntil { it.editingDay?.exercises?.isNotEmpty() == true }
                vm.closeDayEditor()
                awaitItemUntil { it.mode == PlanEditorMode.PLAN }
                // Day 2: invalid (no name, no exercises)
                vm.addDay()
                awaitItemUntil { it.mode == PlanEditorMode.DAY }
                vm.closeDayEditor()
                awaitItemUntil { it.mode == PlanEditorMode.PLAN && it.days.size == 2 }

                vm.save { }

                val liveDays = planRepo.dayTemplates.values.filter { it.deletedAt == null }
                assertEquals(1, liveDays.size)
                assertEquals("Good", liveDays.single().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Delete plan ──────────────────────────────────────────────────────────

    @Test
    fun `deletePlan soft-deletes the edited plan and invokes the callback`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Day A")
            val te = planRepo.addExerciseToTemplate(day.id, "ex1")

            val vm = makeVm(planId = plan.id, planRepo = planRepo, exerciseRepo = exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.planName == "PPL" }
                cancelAndIgnoreRemainingEvents()
            }

            var invoked = false
            vm.deletePlan {
                invoked = true
                // Asserting inside the callback proves it fires only AFTER the soft-delete.
                assertTrue(planRepo.plans[plan.id]!!.deletedAt != null)
                assertTrue(planRepo.dayTemplates[day.id]!!.deletedAt != null)
                assertTrue(planRepo.templateExercises[te.id]!!.deletedAt != null)
            }

            assertTrue("onDeleted callback should have been invoked", invoked)
        }

    @Test
    fun `deletePlan is a no-op for a new plan`() =
        runTest {
            val planRepo = FakePlanRepository()
            val vm = makeVm(planRepo = planRepo)

            var invoked = false
            vm.deletePlan { invoked = true }

            assertFalse("onDeleted must not fire for a never-saved plan", invoked)
            assertTrue("no plan should have been touched", planRepo.plans.isEmpty())
        }

    @Test
    fun `new plan starts empty when no planId`() =
        runTest {
            val vm = makeVm()
            vm.uiState.test {
                val state = awaitItem()
                assertEquals(PlanEditorMode.PLAN, state.mode)
                // isNewPlan=true hides the Delete-plan row (PlanEditorScreen gates it on !isNewPlan).
                assertTrue(state.isNewPlan)
                assertEquals("", state.planName)
                assertTrue(state.days.isEmpty())
                assertNull(state.editingDay)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

/**
 * Drains turbine emissions until [predicate] holds, returning that item. Tolerates the
 * conflated/intermediate states emitted by combine + stateIn.
 */
private suspend fun app.cash.turbine.ReceiveTurbine<PlanEditorUiState>.awaitItemUntil(
    predicate: (PlanEditorUiState) -> Boolean,
): PlanEditorUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
