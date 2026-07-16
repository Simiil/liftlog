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
import kotlin.time.Instant

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
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
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
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
            val planRepo = FakePlanRepository()
            // Never created: observeDayTemplate emits null from the very first frame. A naive
            // `dayGone = (day == null)` would emit a dayGone=true state here; the correct
            // pre-load guard must keep the (only) observed state at loading, not gone.
            val vm = makeVm("missing-template-id", planRepo)
            vm.uiState.test {
                val state = awaitItem()
                assertTrue("should read as still loading, not gone", state.loading)
                assertFalse(state.dayGone)
                runCurrent() // flush the combine upstream; a dayGone state would surface here
                expectNoEvents()
            }
        }

    // ── Name overlay + debounce ──────────────────────────────────────────────

    @Test
    fun `setDayName shows typed text immediately via overlay`() =
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val counting = InstrumentedPlanRepository(planRepo)
            val vm = makeVm(day.id, counting)
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
                assertEquals("intermediate values must never be written", 1, counting.renameCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flushPendingEdits persists a pending rename immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val counting = InstrumentedPlanRepository(planRepo)
            val vm = makeVm(day.id, counting)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.setDayName("New Name")
                vm.flushPendingEdits()
                runCurrent() // NO time advance — flush must write without waiting for the debounce
                assertEquals("New Name", planRepo.dayTemplates[day.id]!!.name)
                // The debounce timer was cancelled, not left running: no second write later.
                advanceTimeBy(500)
                runCurrent()
                assertEquals("flush must also cancel the timer", 1, counting.renameCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `flushPendingEdits is a no-op when nothing is pending`() =
        runTest(mainDispatcherRule.dispatcher) {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Old Name")
            val counting = InstrumentedPlanRepository(planRepo)
            val vm = makeVm(day.id, counting)
            vm.uiState.test {
                awaitItemUntil { !it.loading }
                vm.flushPendingEdits()
                runCurrent()
                assertEquals("no repository write may happen", 0, counting.renameCount)
                assertEquals("Old Name", planRepo.dayTemplates[day.id]!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Target overlay ────────────────────────────────────────────────────────

    @Test
    fun `setTargets rapid taps do not drop increments`() =
        runTest(mainDispatcherRule.dispatcher) {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val fake = FakePlanRepository()
            val plan = fake.createPlan("PPL")
            val day = fake.createDayTemplate(plan.id, "Day")
            val te = fake.addExerciseToTemplate(day.id, "ex1")
            // Writes that take 50ms to land, so the taps below race ahead of any round trip;
            // only a synchronous overlay (not a wait for the DB echo) can keep the UI at 3.
            val slowRepo = InstrumentedPlanRepository(fake, ArrayDeque(listOf(50L, 50L, 50L)))
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
    fun `rapid setTargets taps serialize to one write with the final value`() =
        runTest(mainDispatcherRule.dispatcher) {
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            val fake = FakePlanRepository()
            val plan = fake.createPlan("PPL")
            val day = fake.createDayTemplate(plan.id, "Day")
            val te = fake.addExerciseToTemplate(day.id, "ex1")
            // DECREASING delays: fire-and-forget writes would complete in reverse call order
            // (tap 3 lands first and clears the overlay, then taps 2 and 1 overwrite the row
            // backwards with nothing left to correct it). Serialized writes must instead cancel
            // the in-flight older write on each tap, so exactly one write — the final value —
            // ever reaches the repository.
            val slowRepo = InstrumentedPlanRepository(fake, ArrayDeque(listOf(300L, 200L, 100L)))
            val vm = makeVm(day.id, slowRepo, exerciseRepo)
            vm.uiState.test {
                awaitItemUntil { it.exercises.isNotEmpty() }
                vm.setTargets(te.id, sets = 1, repsMin = null, repsMax = null)
                vm.setTargets(te.id, sets = 2, repsMin = null, repsMax = null)
                vm.setTargets(te.id, sets = 3, repsMin = null, repsMax = null)

                advanceTimeBy(400)
                runCurrent()
                assertEquals("the last tap's value must land last", 3, fake.templateExercises[te.id]!!.targetSets)
                assertEquals("older in-flight writes must be cancelled, not raced", 1, slowRepo.targetWriteCount)
                // Overlay cleared by the confirmed write: the UI now reads straight from the DB.
                assertEquals(
                    3,
                    vm.uiState.value.exercises
                        .single()
                        .targetSets,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setTargets persists targets immediately`() =
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
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
        runTest(mainDispatcherRule.dispatcher) {
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
 * Delegates every [PlanRepository] call to [delegate] unchanged, but counts the rename/target
 * writes that actually reach it and delays each target write by the next entry in
 * [targetWriteDelaysMs] (virtual time) to simulate real, non-instant round trips — with
 * decreasing delays they complete out of call order. Used to prove the pending-edit overlay is
 * synchronous and target writes are serialized per row, rather than relying on the fake's
 * always-instant, always-in-order writes to mask a missing overlay or a write-ordering race.
 */
private class InstrumentedPlanRepository(
    private val delegate: PlanRepository,
    private val targetWriteDelaysMs: ArrayDeque<Long> = ArrayDeque(),
) : PlanRepository by delegate {
    /** Target writes that reached [delegate]; a write cancelled mid-delay never counts. */
    var targetWriteCount = 0
        private set

    /** Rename writes that reached [delegate]. */
    var renameCount = 0
        private set

    override suspend fun renameDayTemplate(
        id: String,
        name: String,
    ) {
        renameCount++
        delegate.renameDayTemplate(id, name)
    }

    override suspend fun updateTemplateExerciseTargets(
        id: String,
        targetSets: Int?,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
    ) {
        targetWriteDelaysMs.removeFirstOrNull()?.let { delay(it) }
        targetWriteCount++
        delegate.updateTemplateExerciseTargets(id, targetSets, targetRepsMin, targetRepsMax)
    }
}

/**
 * Drains turbine emissions until [predicate] holds, returning that item. Tolerates the
 * conflated/intermediate states emitted by combine + stateIn (a pattern shared with
 * PlanViewModelTest).
 */
private suspend fun app.cash.turbine.ReceiveTurbine<DayEditorUiState>.awaitItemUntil(
    predicate: (DayEditorUiState) -> Boolean,
): DayEditorUiState {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
