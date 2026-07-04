package de.simiil.liftlog.ui.plans

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PlanViewModelTest {
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
        ensurer: DefaultPlanEnsurer = DefaultPlanEnsurer(planRepo, DefaultPlanNameProvider { "Default" }),
    ) = PlanViewModel(planRepo, exerciseRepo, sessionRepo, ensurer)

    // ── Shape: fallback plan + its day rows ─────────────────────────────────────

    @Test
    fun `shows the fallback plan with its day rows including empty days`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value = listOf(exercise("ex1", "Bench Press", MuscleGroup.CHEST))
            val plan = planRepo.createPlan("Push Pull Legs")
            val dayWithExercise = planRepo.createDayTemplate(plan.id, "Push Day")
            planRepo.addExerciseToTemplate(dayWithExercise.id, "ex1")
            val emptyDay = planRepo.createDayTemplate(plan.id, "Rest Day")

            val vm = makeVm(planRepo, exerciseRepo)
            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plan == null) state = awaitItem()

                val current = state.plan!!
                assertEquals(plan.id, current.id)
                assertEquals("Push Pull Legs", current.name)
                assertEquals(2, current.days.size)
                val emptyDayUi = current.days.first { it.templateId == emptyDay.id }
                assertEquals(0, emptyDayUi.exerciseCount)
                assertEquals(1, state.planChoices.size)
                assertEquals(plan.id, state.planChoices.first().id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `day rows carry exercise counts and up to three distinct muscle groups`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.all.value =
                listOf(
                    exercise("ex1", "Bench Press", MuscleGroup.CHEST),
                    exercise("ex2", "Incline Press", MuscleGroup.CHEST), // duplicate group → deduped
                    exercise("ex3", "Lateral Raise", MuscleGroup.SHOULDERS),
                )
            val plan = planRepo.createPlan("Push Pull Legs")
            val day = planRepo.createDayTemplate(plan.id, "Push Day")
            planRepo.addExerciseToTemplate(day.id, "ex1")
            planRepo.addExerciseToTemplate(day.id, "ex2")
            planRepo.addExerciseToTemplate(day.id, "ex3")

            val vm = makeVm(planRepo, exerciseRepo)
            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plan == null) state = awaitItem()

                val dayUi = state.plan!!.days.first()
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
                while (state.loading || state.plan == null) state = awaitItem()
                val dayUi = state.plan!!.days.first()
                assertEquals(3, dayUi.muscleGroups.size)
                assertEquals(
                    listOf(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.QUADS),
                    dayUi.muscleGroups,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── startDay (resume-guarded, ported verbatim) ──────────────────────────────

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
                    rpe = null,
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

    // ── Persist-on-change mutations ──────────────────────────────────────────────

    @Test
    fun `addDay creates an empty day on the current plan and emits its id`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val vm = makeVm(planRepo)

            var createdId: String? = null
            vm.addDay { createdId = it }

            assertEquals(1, planRepo.dayTemplates.size)
            val day = planRepo.dayTemplates.values.first()
            assertEquals(plan.id, day.planId)
            assertEquals("", day.name)
            assertEquals(day.id, createdId)
        }

    @Test
    fun `removeDay soft-deletes the day`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Push")
            val vm = makeVm(planRepo)

            vm.removeDay(day.id)

            assertNotNull(planRepo.dayTemplates[day.id]?.deletedAt)
        }

    @Test
    fun `reorderDays persists the new order`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day1 = planRepo.createDayTemplate(plan.id, "A")
            val day2 = planRepo.createDayTemplate(plan.id, "B")
            val vm = makeVm(planRepo)

            vm.reorderDays(listOf(day2.id, day1.id))

            assertEquals(0, planRepo.dayTemplates[day2.id]?.position)
            assertEquals(1, planRepo.dayTemplates[day1.id]?.position)
        }

    @Test
    fun `renamePlan persists the trimmed name`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("Old Name")
            val vm = makeVm(planRepo)

            vm.renamePlan("  New Name  ")

            assertEquals("New Name", planRepo.plans[plan.id]?.name)
        }

    @Test
    fun `deletePlan reseeds a default plan so the tab is never empty`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("Old")
            val ensurer = DefaultPlanEnsurer(planRepo, DefaultPlanNameProvider { "Default" })
            val vm = makeVm(planRepo, ensurer = ensurer)

            vm.deletePlan()

            val live = planRepo.plans.values.filter { it.deletedAt == null }
            assertEquals(1, live.size)
            assertNotEquals(plan.id, live.first().id)
            assertEquals("Default", live.first().name)
        }
}
