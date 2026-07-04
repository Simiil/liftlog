package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DayEditorViewModelTest {
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
        templateId: String,
        planRepo: PlanRepository,
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
    ) = DayEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("templateId" to templateId)),
        planRepository = planRepo,
        exerciseRepository = exerciseRepo,
        names = names,
    )

    // ── Load ──────────────────────────────────────────────────────────────────

    @Test
    fun `loads day name and resolved exercises from the repository`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench Press"))
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Push Day")
            planRepo.addExerciseToTemplate(day.id, "ex1")

            val vm = makeVm(day.id, planRepo, exerciseRepo)
            vm.uiState.test {
                val state = awaitItemUntil { !it.loading }
                assertEquals("Push Day", state.dayName)
                assertEquals(listOf("ex1"), state.exercises.map { it.exerciseId })
                assertEquals("Bench Press", state.exercises.single().name)
                assertFalse(state.dayGone)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dayGone stays false while the template has not yet loaded`() =
        runTest {
            val planRepo = FakePlanRepository()
            // Never created: observeDayTemplate emits null from the very first frame.
            val vm = makeVm("missing-template-id", planRepo)
            val state = vm.uiState.value
            assertTrue("should read as still loading, not gone", state.loading)
            assertFalse(state.dayGone)
        }

    // ── Name overlay + debounce ──────────────────────────────────────────────

    @Test
    fun `setDayName shows typed text immediately via overlay`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val vm = makeVm(day.id, planRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.setDayName("New Name")
                val state = awaitItemUntil { it.dayName == "New Name" }
                assertEquals("New Name", state.dayName)
                // The debounce window has not elapsed: nothing persisted yet.
                assertEquals("Old Name", planRepo.dayTemplates[day.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setDayName persists rename after the 400ms debounce`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val vm = makeVm(day.id, planRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.setDayName("New Name")
                runCurrent()
                assertEquals(
                    "no write before the debounce elapses",
                    "Old Name",
                    planRepo.dayTemplates[day.id]!!.name,
                )

                advanceTimeBy(500)
                runCurrent()
                assertEquals("New Name", planRepo.dayTemplates[day.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `rapid retypes collapse to one write with the final value`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val vm = makeVm(day.id, planRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.setDayName("N")
                advanceTimeBy(100)
                vm.setDayName("Ne")
                advanceTimeBy(100)
                vm.setDayName("New")
                advanceTimeBy(500)
                runCurrent()
                assertEquals("New", planRepo.dayTemplates[day.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flushPendingEdits persists a pending rename immediately`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val vm = makeVm(day.id, planRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.setDayName("New Name")
                vm.flushPendingEdits()
                runCurrent() // NO time advance — flush must write without waiting for the debounce
                assertEquals("New Name", planRepo.dayTemplates[day.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flushPendingEdits is a no-op when nothing is pending`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val vm = makeVm(day.id, planRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.flushPendingEdits()
                runCurrent()
                assertEquals("Old Name", planRepo.dayTemplates[day.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Target overlay ────────────────────────────────────────────────────────

    @Test
    fun `setTargets rapid taps do not drop increments`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val fake = FakePlanRepository()
            val plan = fake.createPlan("PPL")
            val day = fake.createDayTemplate(plan.id, "Day")
            val te = fake.addExerciseToTemplate(day.id, "ex1")
            // A write that takes 50ms to land, so the taps below race ahead of any round trip;
            // only a synchronous overlay (not a wait for the DB echo) can keep the UI at 3.
            val slowRepo = DelayedTargetsRepository(fake, delayMs = 50)
            val vm = makeVm(day.id, slowRepo, exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.exercises.isNotEmpty() }
                vm.setTargets(te.id, sets = 1, repsMin = null, repsMax = null)
                vm.setTargets(te.id, sets = 2, repsMin = null, repsMax = null)
                vm.setTargets(te.id, sets = 3, repsMin = null, repsMax = null)

                assertEquals(
                    3,
                    vm.uiState.value.exercises
                        .single()
                        .targetSets,
                )
                assertNull("write must still be in flight", fake.templateExercises[te.id]!!.targetSets)

                advanceTimeBy(200)
                runCurrent()
                assertEquals(3, fake.templateExercises[te.id]!!.targetSets)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setTargets persists targets immediately`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Day")
            val te = planRepo.addExerciseToTemplate(day.id, "ex1")
            val vm = makeVm(day.id, planRepo, exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.exercises.isNotEmpty() }
                vm.setTargets(te.id, sets = 4, repsMin = 6, repsMax = 10)
                runCurrent()
                val persisted = planRepo.templateExercises[te.id]!!
                assertEquals(4, persisted.targetSets)
                assertEquals(6, persisted.targetRepsMin)
                assertEquals(10, persisted.targetRepsMax)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Add / remove / reorder ────────────────────────────────────────────────

    @Test
    fun `addExercises delegates to addExercisesToTemplate`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"), exercise("ex2", "Row"))
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Day")
            val vm = makeVm(day.id, planRepo, exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                // A duplicate id within one call only collapses via addExercisesToTemplate's own
                // dedup; a VM-side per-id loop over the singular method would create two rows.
                vm.addExercises(listOf("ex1", "ex1", "ex2"))
                val state = awaitItemUntil { it.exercises.isNotEmpty() }
                assertEquals(listOf("ex1", "ex2"), state.exercises.map { it.exerciseId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `removeExercise soft-deletes immediately`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Day")
            val te = planRepo.addExerciseToTemplate(day.id, "ex1")
            val vm = makeVm(day.id, planRepo, exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.exercises.isNotEmpty() }
                vm.removeExercise(te.id)
                val state = awaitItemUntil { it.exercises.isEmpty() }
                assertTrue(state.exercises.isEmpty())
                assertTrue(planRepo.templateExercises[te.id]!!.deletedAt != null)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reorderExercises persists order`() =
        runTest {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "A"), exercise("ex2", "B"))
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Day")
            val te1 = planRepo.addExerciseToTemplate(day.id, "ex1")
            val te2 = planRepo.addExerciseToTemplate(day.id, "ex2")
            val vm = makeVm(day.id, planRepo, exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.exercises.size == 2 }
                vm.reorderExercises(listOf(te2.id, te1.id))
                val state = awaitItemUntil { it.exercises.firstOrNull()?.exerciseId == "ex2" }
                assertEquals(listOf("ex2", "ex1"), state.exercises.map { it.exerciseId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── dayGone ───────────────────────────────────────────────────────────────

    @Test
    fun `dayGone flips true when the template is soft-deleted`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Day")
            val vm = makeVm(day.id, planRepo)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                planRepo.softDeleteDayTemplate(day.id)
                val state = awaitItemUntil { it.dayGone }
                assertTrue(state.dayGone)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

/**
 * Delegates every [PlanRepository] call to [delegate] unchanged, except
 * [updateTemplateExerciseTargets], which is delayed by [delayMs] of virtual time to simulate a
 * real (non-instant) round trip — used to prove the pending-edit overlay is synchronous rather
 * than relying on the write completing before the UI reads the next value.
 */
private class DelayedTargetsRepository(
    private val delegate: PlanRepository,
    private val delayMs: Long,
) : PlanRepository by delegate {
    override suspend fun updateTemplateExerciseTargets(
        id: String,
        targetSets: Int?,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
    ) {
        delay(delayMs)
        delegate.updateTemplateExerciseTargets(id, targetSets, targetRepsMin, targetRepsMax)
    }
}

/**
 * Drains turbine emissions until [predicate] holds, returning that item. Tolerates the
 * conflated/intermediate states emitted by combine + stateIn (mirrors PlanEditorViewModelTest).
 */
private suspend fun app.cash.turbine.ReceiveTurbine<DayEditorUiState>.awaitItemUntil(
    predicate: (DayEditorUiState) -> Boolean,
): DayEditorUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
