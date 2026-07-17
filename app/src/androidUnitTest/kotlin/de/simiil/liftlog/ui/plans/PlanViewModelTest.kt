package de.simiil.liftlog.ui.plans

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.PlanDayTemplate
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.plan.DefaultPlanEnsurer
import de.simiil.liftlog.domain.plan.DefaultPlanNameProvider
import de.simiil.liftlog.domain.repository.PlanRepository
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
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
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
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
            val now = Clock.System.now()
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
    fun `rapid addDay taps create only one day`() =
        runTest {
            val planRepo = FakePlanRepository()
            planRepo.createPlan("PPL")
            // createDayTemplate takes 100ms (virtual time) to land, so two taps fired before
            // either completes race ahead of any DB round trip; only a synchronous in-flight
            // guard (not a wait for the write to land) can keep this at one day.
            val slowRepo = SlowCreateDayPlanRepository(planRepo, delayMs = 100)
            val vm =
                PlanViewModel(
                    planRepository = slowRepo,
                    exerciseRepository = FakeExerciseRepository(),
                    sessionRepository = FakeSessionRepository(),
                    defaultPlanEnsurer = DefaultPlanEnsurer(planRepo, DefaultPlanNameProvider { "Default" }),
                )

            var createdCount = 0
            vm.addDay { createdCount++ }
            vm.addDay { createdCount++ }
            advanceUntilIdle()

            assertEquals("two rapid taps must create only one day", 1, planRepo.dayTemplates.size)
            assertEquals("onCreated must fire exactly once", 1, createdCount)
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

    // ── Multi-plan chrome: switcher + create (issue #30 PR4) ────────────────────

    @Test
    fun `planChoices lists all live plans in position order`() =
        runTest {
            val planRepo = FakePlanRepository()
            val planA = planRepo.createPlan("A")
            val planB = planRepo.createPlan("B")
            val planC = planRepo.createPlan("C")
            val vm = makeVm(planRepo)

            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plan == null) state = awaitItem()

                assertEquals(listOf(planA.id, planB.id, planC.id), state.planChoices.map { it.id })
                assertEquals(listOf("A", "B", "C"), state.planChoices.map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selectPlan persists the selection and the current plan follows`() =
        runTest {
            val planRepo = FakePlanRepository()
            val planA = planRepo.createPlan("A")
            val planB = planRepo.createPlan("B")
            val vm = makeVm(planRepo)

            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plan == null) state = awaitItem()
                assertEquals(planA.id, state.plan!!.id) // fallback: first plan by position

                vm.selectPlan(planB.id)

                state = awaitItem()
                while (state.plan?.id != planB.id) state = awaitItem()
                assertEquals(planB.id, state.plan!!.id)
                assertEquals(planB.id, planRepo.selectedPlanId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `createPlan creates, selects, and shows the new plan`() =
        runTest {
            val planRepo = FakePlanRepository()
            val existing = planRepo.createPlan("Existing")
            val vm = makeVm(planRepo)

            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plan == null) state = awaitItem()
                assertEquals(existing.id, state.plan!!.id)

                vm.createPlan("  New Plan  ")

                state = awaitItem()
                while (state.plan?.name != "New Plan") state = awaitItem()
                assertEquals("New Plan", state.plan!!.name)
                assertEquals(2, planRepo.plans.values.count { it.deletedAt == null })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting the current plan while another live plan exists falls back to it without reseeding a default`() =
        runTest {
            val planRepo = FakePlanRepository()
            val planA = planRepo.createPlan("A")
            val planB = planRepo.createPlan("B")
            val ensurer = DefaultPlanEnsurer(planRepo, DefaultPlanNameProvider { "Default" })
            val vm = makeVm(planRepo, ensurer = ensurer)

            vm.selectPlan(planA.id)
            vm.deletePlan()

            val live = planRepo.plans.values.filter { it.deletedAt == null }
            assertEquals(2, planRepo.plans.values.size) // A tombstoned, not hard-deleted
            assertEquals(1, live.size)
            assertEquals(planB.id, live.first().id)
            assertTrue(live.none { it.name == "Default" })

            vm.uiState.test {
                var state = awaitItem()
                while (state.loading || state.plan == null) state = awaitItem()
                assertEquals(planB.id, state.plan!!.id)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

/**
 * Delegates every [PlanRepository] call to [delegate] unchanged, but delays
 * [createDayTemplate] by [delayMs] (virtual time) to simulate a non-instant round trip — long
 * enough that two rapid taps both reach the repository before either write lands, unless the
 * caller guards against re-entrant calls.
 */
private class SlowCreateDayPlanRepository(
    private val delegate: PlanRepository,
    private val delayMs: Long,
) : PlanRepository by delegate {
    override suspend fun createDayTemplate(
        planId: String,
        name: String,
    ): PlanDayTemplate {
        delay(delayMs)
        return delegate.createDayTemplate(planId, name)
    }
}
