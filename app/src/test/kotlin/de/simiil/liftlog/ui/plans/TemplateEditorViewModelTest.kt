package de.simiil.liftlog.ui.plans

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TemplateEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun fakeExercise(id: String, name: String, equipment: Equipment = Equipment.BARBELL) =
        Exercise(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.CHEST,
            equipment = equipment,
            isBuiltIn = true,
            isHidden = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            deletedAt = null,
        )

    /**
     * Builds a [TemplateEditorViewModel] backed by the given repos and a
     * [SavedStateHandle] carrying the templateId key.
     */
    private fun makeVm(
        templateId: String,
        planRepo: FakePlanRepository = FakePlanRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
    ): TemplateEditorViewModel = TemplateEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("templateId" to templateId)),
        planRepository = planRepo,
        exerciseRepository = exerciseRepo,
    )

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    fun `exercises list carries joined names and targets in position order`() = runTest {
        val planRepo = FakePlanRepository()
        val exerciseRepo = FakeExerciseRepository()

        val plan = planRepo.createPlan("PPL")
        val day = planRepo.createDayTemplate(plan.id, "Push")

        // Two exercises in the exercise repository
        val ex1 = fakeExercise("ex1", "Bench Press", Equipment.BARBELL)
        val ex2 = fakeExercise("ex2", "Tricep Pushdown", Equipment.CABLE)
        exerciseRepo.all.value = listOf(ex1, ex2)

        // Add them to the template with targets on the first, none on the second
        val te1 = planRepo.addExerciseToTemplate(day.id, ex1.id)
        planRepo.updateTemplateExerciseTargets(te1.id, targetSets = 3, targetRepsMin = 8, targetRepsMax = 12)
        val te2 = planRepo.addExerciseToTemplate(day.id, ex2.id)

        val vm = makeVm(day.id, planRepo, exerciseRepo)

        vm.uiState.test {
            // Skip loading state if any; drain until loading = false
            var state = awaitItem()
            while (state.loading) state = awaitItem()

            assertEquals("Push", state.dayName)
            assertEquals(2, state.exercises.size)

            val first = state.exercises[0]
            assertEquals(te1.id, first.id)
            assertEquals(ex1.id, first.exerciseId)
            assertEquals("Bench Press", first.name)
            assertEquals(Equipment.BARBELL, first.equipment)
            assertEquals(3, first.targetSets)
            assertEquals(8, first.targetRepsMin)
            assertEquals(12, first.targetRepsMax)

            val second = state.exercises[1]
            assertEquals(te2.id, second.id)
            assertEquals(ex2.id, second.exerciseId)
            assertEquals("Tricep Pushdown", second.name)
            assertEquals(Equipment.CABLE, second.equipment)
            assertNull(second.targetSets)
            assertNull(second.targetRepsMin)
            assertNull(second.targetRepsMax)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addExercise appends a new entry`() = runTest {
        val planRepo = FakePlanRepository()
        val exerciseRepo = FakeExerciseRepository()

        val plan = planRepo.createPlan("PPL")
        val day = planRepo.createDayTemplate(plan.id, "Push")
        val ex = fakeExercise("ex1", "Squat")
        exerciseRepo.all.value = listOf(ex)

        val vm = makeVm(day.id, planRepo, exerciseRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertTrue(state.exercises.isEmpty())

            vm.addExercise(ex.id)

            val afterAdd = awaitItem()
            assertEquals(1, afterAdd.exercises.size)
            assertEquals("Squat", afterAdd.exercises.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeExercise soft-deletes the row`() = runTest {
        val planRepo = FakePlanRepository()
        val exerciseRepo = FakeExerciseRepository()

        val plan = planRepo.createPlan("PPL")
        val day = planRepo.createDayTemplate(plan.id, "Push")
        val ex = fakeExercise("ex1", "Bench Press")
        exerciseRepo.all.value = listOf(ex)
        val te = planRepo.addExerciseToTemplate(day.id, ex.id)

        val vm = makeVm(day.id, planRepo, exerciseRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertEquals(1, state.exercises.size)

            vm.removeExercise(te.id)

            val afterRemove = awaitItem()
            assertTrue(afterRemove.exercises.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setTargets updates targets on the specified exercise`() = runTest {
        val planRepo = FakePlanRepository()
        val exerciseRepo = FakeExerciseRepository()

        val plan = planRepo.createPlan("PPL")
        val day = planRepo.createDayTemplate(plan.id, "Push")
        val ex = fakeExercise("ex1", "Overhead Press")
        exerciseRepo.all.value = listOf(ex)
        val te = planRepo.addExerciseToTemplate(day.id, ex.id)

        val vm = makeVm(day.id, planRepo, exerciseRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()

            // Set targets
            vm.setTargets(te.id, sets = 4, repsMin = 5, repsMax = 8)
            val afterSet = awaitItem()
            val exercise = afterSet.exercises.first()
            assertEquals(4, exercise.targetSets)
            assertEquals(5, exercise.targetRepsMin)
            assertEquals(8, exercise.targetRepsMax)

            // Clear targets back to null
            vm.setTargets(te.id, sets = null, repsMin = null, repsMax = null)
            val afterClear = awaitItem()
            val cleared = afterClear.exercises.first()
            assertNull(cleared.targetSets)
            assertNull(cleared.targetRepsMin)
            assertNull(cleared.targetRepsMax)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persistOrder reorders exercises (b,a produces swapped positions)`() = runTest {
        val planRepo = FakePlanRepository()
        val exerciseRepo = FakeExerciseRepository()

        val plan = planRepo.createPlan("PPL")
        val day = planRepo.createDayTemplate(plan.id, "Push")
        val exA = fakeExercise("exA", "Bench Press")
        val exB = fakeExercise("exB", "Incline Press")
        exerciseRepo.all.value = listOf(exA, exB)

        val teA = planRepo.addExerciseToTemplate(day.id, exA.id)
        val teB = planRepo.addExerciseToTemplate(day.id, exB.id)

        val vm = makeVm(day.id, planRepo, exerciseRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            // Initial order: A at 0, B at 1
            assertEquals(teA.id, state.exercises[0].id)
            assertEquals(teB.id, state.exercises[1].id)

            // Reorder to B first, A second
            vm.persistOrder(listOf(teB.id, teA.id))

            val afterReorder = awaitItem()
            assertEquals(2, afterReorder.exercises.size)
            assertEquals(teB.id, afterReorder.exercises[0].id)
            assertEquals("Incline Press", afterReorder.exercises[0].name)
            assertEquals(teA.id, afterReorder.exercises[1].id)
            assertEquals("Bench Press", afterReorder.exercises[1].name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exercises from other templates do not appear`() = runTest {
        val planRepo = FakePlanRepository()
        val exerciseRepo = FakeExerciseRepository()

        val plan = planRepo.createPlan("PPL")
        val dayA = planRepo.createDayTemplate(plan.id, "Push")
        val dayB = planRepo.createDayTemplate(plan.id, "Pull")
        val ex = fakeExercise("ex1", "Pull-Up")
        exerciseRepo.all.value = listOf(ex)
        planRepo.addExerciseToTemplate(dayB.id, ex.id) // added to dayB, NOT dayA

        val vm = makeVm(dayA.id, planRepo, exerciseRepo)

        vm.uiState.test {
            var state = awaitItem()
            while (state.loading) state = awaitItem()
            assertTrue("dayA should show no exercises from dayB", state.exercises.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
