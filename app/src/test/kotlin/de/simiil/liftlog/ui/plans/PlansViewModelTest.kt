package de.simiil.liftlog.ui.plans

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PlansViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun exercise(
        id: String,
        name: String,
        group: MuscleGroup,
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = group,
        equipment = Equipment.BARBELL,
        isBuiltIn = true,
        isHidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

    private fun makeVm(
        planRepo: FakePlanRepository = FakePlanRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        sessionRepo: FakeSessionRepository = FakeSessionRepository(),
    ) = PlansViewModel(planRepo, exerciseRepo, sessionRepo)

    // ── List shape ────────────────────────────────────────────────────────────

    @Test
    fun `empty repo - no plan cards after load`() =
        runTest {
            val vm = makeVm()
            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state.plans.isEmpty())
                assertEquals(false, state.loading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `plans expose day rows with counts and distinct muscle groups`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value =
                listOf(
                    exercise("ex1", "Bench Press", MuscleGroup.CHEST),
                    exercise("ex2", "Incline Press", MuscleGroup.CHEST), // duplicate group → deduped
                    exercise("ex3", "Lateral Raise", MuscleGroup.SHOULDERS),
                )

            // Seed a plan with one day of three exercises via the suspend API.
            val plan = planRepo.createPlan("Push Pull Legs")
            val day = planRepo.createDayTemplate(plan.id, "Push Day")
            planRepo.addExerciseToTemplate(day.id, "ex1")
            planRepo.addExerciseToTemplate(day.id, "ex2")
            planRepo.addExerciseToTemplate(day.id, "ex3")

            val vm = makeVm(planRepo, exerciseRepo)
            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plans.isEmpty()) state = awaitItem()

                assertEquals(1, state.plans.size)
                val card = state.plans.first()
                assertEquals("Push Pull Legs", card.name)
                assertEquals(1, card.days.size)

                val dayUi = card.days.first()
                assertEquals(day.id, dayUi.templateId)
                assertEquals("Push Day", dayUi.name)
                assertEquals(3, dayUi.exerciseCount)
                // CHEST appears twice but is deduped; order preserved.
                assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS), dayUi.muscleGroups)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `muscle groups capped at three distinct labels`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value =
                listOf(
                    exercise("e1", "A", MuscleGroup.CHEST),
                    exercise("e2", "B", MuscleGroup.BACK),
                    exercise("e3", "C", MuscleGroup.QUADS),
                    exercise("e4", "D", MuscleGroup.SHOULDERS),
                )
            val plan = planRepo.createPlan("Full Body")
            val day = planRepo.createDayTemplate(plan.id, "Day 1")
            listOf("e1", "e2", "e3", "e4").forEach { planRepo.addExerciseToTemplate(day.id, it) }

            val vm = makeVm(planRepo, exerciseRepo)
            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plans.isEmpty()) state = awaitItem()
                val dayUi =
                    state.plans
                        .first()
                        .days
                        .first()
                assertEquals(3, dayUi.muscleGroups.size)
                assertEquals(
                    listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS),
                    dayUi.muscleGroups,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── startDay (resume-guarded) ───────────────────────────────────────────────

    @Test
    fun `startDay starts a new session from the template when none active`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val vm = makeVm(sessionRepo = sessionRepo)

            var opened: String? = null
            vm.startDay("template-1") { opened = it }

            assertEquals(listOf("template-1"), sessionRepo.startFromTemplateCalls)
            assertEquals(sessionRepo.activeSession.value?.id, opened)
        }

    @Test
    fun `startDay resumes the active session instead of starting another`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val now = Instant.now()
            sessionRepo.activeSession.value =
                Session(
                    id = "active-1",
                    templateId = null,
                    templateNameSnapshot = "In Progress",
                    startedAt = now,
                    endedAt = null,
                    note = null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            val vm = makeVm(sessionRepo = sessionRepo)

            var opened: String? = null
            vm.startDay("template-1") { opened = it }

            assertEquals("active-1", opened)
            assertTrue("must not start from template while a session is active", sessionRepo.startFromTemplateCalls.isEmpty())
        }
}
