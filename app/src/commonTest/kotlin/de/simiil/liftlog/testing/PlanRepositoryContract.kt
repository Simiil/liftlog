package de.simiil.liftlog.testing

import de.simiil.liftlog.domain.repository.PlanRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral contract for [PlanRepository], run against both [FakePlanRepository] (see
 * [FakePlanRepositoryContractTest]) and the real `PlanRepositoryImpl` (see
 * `PlanRepositoryImplContractTest` in `data.repository`) so the fake used by every ViewModel test
 * cannot silently diverge from the real implementation it stands in for.
 *
 * Every test here drives state ONLY through the [PlanRepository] interface — no fixture-specific
 * seams — so both implementations are exercised identically by the same test bodies. Subclasses
 * supply the fixture via [createRepository].
 *
 * **Divergence policy:** if a test here fails against [FakePlanRepository], the fake is wrong —
 * fix it to match the real implementation (the real impl and its dedicated `PlanRepositoryTest`
 * are the source of truth). If a test fails against `PlanRepositoryImpl`, treat that as a
 * potential real bug or a wrong contract, not something to paper over.
 *
 * **Timestamps are out of scope here.** [FakePlanRepository] stamps every write with a fixed
 * `Instant.EPOCH` rather than an injectable clock, while `PlanRepositoryImpl` uses an injected
 * `Clock`. The two fixtures cannot be made to agree on *exact* instants without changing the
 * fake's timestamp source, so this suite asserts structural properties only — identity, ordering,
 * liveness, and round-tripped values. Exact-timestamp assertions stay in the fixture-specific
 * `PlanRepositoryTest`.
 *
 * **Recency-with-sessions is out of scope here.** `observeMostUsedOrFirstPlanId`'s
 * "most-recently-used via workout sessions" branch has no session-writing method on
 * [PlanRepository] — it isn't reachable through this interface — so this suite only covers the
 * "no sessions -> first live plan by position, else null" fallback.
 */
abstract class PlanRepositoryContract {
    protected lateinit var repo: PlanRepository

    abstract fun createRepository(): PlanRepository

    @BeforeTest
    fun setUpContract() {
        repo = createRepository()
    }

    // ── plan CRUD / observation ─────────────────────────────────────────────

    @Test
    fun createPlan_trims_the_name_and_appends_by_position_in_observePlans_order() =
        runTest {
            val first = repo.createPlan("  Push Pull Legs  ")
            val second = repo.createPlan("Upper Lower")

            assertEquals("Push Pull Legs", first.name)
            assertEquals(0, first.position)
            assertEquals(1, second.position)

            val observed = repo.observePlans().first()
            assertEquals(listOf(first.id, second.id), observed.map { it.id })
            assertEquals(listOf("Push Pull Legs", "Upper Lower"), observed.map { it.name })
        }

    @Test
    fun renamePlan_changes_the_observed_name() =
        runTest {
            val plan = repo.createPlan("Old Name")

            repo.renamePlan(plan.id, "  New Name  ")

            assertEquals("New Name", repo.observePlan(plan.id).first()?.name)
        }

    @Test
    fun softDeletePlan_removes_the_plan_and_its_days_from_observePlans_and_observePlansWithDays() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            repo.softDeletePlan(plan.id)

            assertNull(repo.observePlan(plan.id).first())
            assertTrue(repo.observePlans().first().none { it.id == plan.id })
            assertTrue(repo.observePlansWithDays().first().none { it.id == plan.id })
            assertTrue(repo.observeDayTemplates(plan.id).first().none { it.id == day.id })
        }

    // ── day template CRUD / observation ─────────────────────────────────────

    @Test
    fun createDayTemplate_appends_a_day_observable_via_observeDayTemplates_and_observePlansWithDays_with_exerciseCount_0() =
        runTest {
            val plan = repo.createPlan("Plan")

            val day = repo.createDayTemplate(plan.id, "  Push Day  ")

            assertEquals("Push Day", day.name)
            assertEquals(0, day.position)
            assertEquals(listOf(day.id), repo.observeDayTemplates(plan.id).first().map { it.id })

            val planWithDays = repo.observePlansWithDays().first().first { it.id == plan.id }
            assertEquals(1, planWithDays.days.size)
            assertEquals(day.id, planWithDays.days[0].templateId)
            assertEquals(0, planWithDays.days[0].exerciseCount)
        }

    @Test
    fun renameDayTemplate_changes_the_observed_day_name() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Old Day")

            repo.renameDayTemplate(day.id, "  New Day  ")

            val observed = repo.observeDayTemplates(plan.id).first().first { it.id == day.id }
            assertEquals("New Day", observed.name)
        }

    @Test
    fun softDeleteDayTemplate_removes_the_day_from_observation() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            repo.softDeleteDayTemplate(day.id)

            assertTrue(repo.observeDayTemplates(plan.id).first().none { it.id == day.id })
        }

    // ── template exercise CRUD / observation ────────────────────────────────

    @Test
    fun addExerciseToTemplate_appends_order_preserved_across_multiple_adds_reflected_in_DaySummary_exerciseCount() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            val te1 = repo.addExerciseToTemplate(day.id, "ex-a")
            val te2 = repo.addExerciseToTemplate(day.id, "ex-b")

            val observed = repo.observeTemplateExercises(day.id).first()
            assertEquals(listOf(te1.id, te2.id), observed.map { it.id })
            assertEquals(listOf("ex-a", "ex-b"), observed.map { it.exerciseId })

            val summary =
                repo
                    .observePlansWithDays()
                    .first()
                    .first { it.id == plan.id }
                    .days
                    .first { it.templateId == day.id }
            assertEquals(2, summary.exerciseCount)
            assertEquals(listOf("ex-a", "ex-b"), summary.exerciseIds)
        }

    @Test
    fun updateTemplateExerciseTargets_round_trips_via_observeTemplateExercises_including_clearing_back_to_null() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val te = repo.addExerciseToTemplate(day.id, "ex-a")

            repo.updateTemplateExerciseTargets(te.id, targetSets = 3, targetRepsMin = 8, targetRepsMax = 12)

            val withTargets = repo.observeTemplateExercises(day.id).first().first { it.id == te.id }
            assertEquals(3, withTargets.targetSets)
            assertEquals(8, withTargets.targetRepsMin)
            assertEquals(12, withTargets.targetRepsMax)

            repo.updateTemplateExerciseTargets(te.id, targetSets = null, targetRepsMin = null, targetRepsMax = null)

            val cleared = repo.observeTemplateExercises(day.id).first().first { it.id == te.id }
            assertNull(cleared.targetSets)
            assertNull(cleared.targetRepsMin)
            assertNull(cleared.targetRepsMax)
        }

    @Test
    fun removeTemplateExercise_removes_the_row_from_observation() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val te1 = repo.addExerciseToTemplate(day.id, "ex-a")
            val te2 = repo.addExerciseToTemplate(day.id, "ex-b")

            repo.removeTemplateExercise(te1.id)

            assertEquals(listOf(te2.id), repo.observeTemplateExercises(day.id).first().map { it.id })
        }

    @Test
    fun reorderTemplateExercises_reorders_observeTemplateExercises_to_match_the_given_id_order() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val teA = repo.addExerciseToTemplate(day.id, "ex-a")
            val teB = repo.addExerciseToTemplate(day.id, "ex-b")
            val teC = repo.addExerciseToTemplate(day.id, "ex-c")

            repo.reorderTemplateExercises(listOf(teC.id, teA.id, teB.id))

            val observed = repo.observeTemplateExercises(day.id).first()
            assertEquals(listOf(teC.id, teA.id, teB.id), observed.map { it.id })
        }

    // ── Home quick-start fallback ───────────────────────────────────────────

    @Test
    fun observeMostUsedOrFirstPlanId_falls_back_to_the_first_live_plan_by_position_null_when_no_plans() =
        runTest {
            assertNull(repo.observeMostUsedOrFirstPlanId().first())

            val first = repo.createPlan("A")
            repo.createPlan("B")

            assertEquals(first.id, repo.observeMostUsedOrFirstPlanId().first())
        }

    // ── ensureDefaultPlan (issue #30 PR1) ────────────────────────────────────

    @Test
    fun ensureDefaultPlan_creates_a_single_plan_with_the_trimmed_name_when_none_live() =
        runTest {
            repo.ensureDefaultPlan("  Default  ")

            val plans = repo.observePlans().first()
            assertEquals(1, plans.size)
            assertEquals("Default", plans[0].name)
        }

    @Test
    fun ensureDefaultPlan_is_a_no_op_when_a_live_plan_exists() =
        runTest {
            val existing = repo.createPlan("Existing")

            repo.ensureDefaultPlan("Default")

            val plans = repo.observePlans().first()
            assertEquals(listOf(existing.id), plans.map { it.id })
            assertEquals(listOf("Existing"), plans.map { it.name })
        }

    // ── softDeletePlanAndEnsureDefault (issue #30 PR1) ───────────────────────

    @Test
    fun softDeletePlanAndEnsureDefault_on_the_last_plan_tombstones_it_and_seeds_a_fresh_default() =
        runTest {
            val plan = repo.createPlan("Solo")

            repo.softDeletePlanAndEnsureDefault(plan.id, "Default")

            assertNull(repo.observePlan(plan.id).first())
            val plans = repo.observePlans().first()
            assertEquals(1, plans.size)
            assertEquals("Default", plans[0].name)
            assertNotEquals(plan.id, plans[0].id)
        }

    @Test
    fun softDeletePlanAndEnsureDefault_does_not_seed_a_default_when_another_live_plan_remains() =
        runTest {
            val toDelete = repo.createPlan("A")
            val remaining = repo.createPlan("B")

            repo.softDeletePlanAndEnsureDefault(toDelete.id, "Default")

            assertEquals(listOf(remaining.id), repo.observePlans().first().map { it.id })
        }

    // ── selectPlan / observeSelectedOrFallbackPlanId (issue #30 PR1) ────────

    @Test
    fun selectPlan_makes_observeSelectedOrFallbackPlanId_emit_the_selected_live_plan_s_id() =
        runTest {
            repo.createPlan("Plan A")
            val planB = repo.createPlan("Plan B")

            repo.selectPlan(planB.id)

            assertEquals(planB.id, repo.observeSelectedOrFallbackPlanId().first())
        }

    @Test
    fun observeSelectedOrFallbackPlanId_falls_back_to_the_first_plan_by_position_when_selection_is_unset() =
        runTest {
            val planA = repo.createPlan("Plan A")
            repo.createPlan("Plan B")
            // no selectPlan call — selection is unset

            assertEquals(planA.id, repo.observeSelectedOrFallbackPlanId().first())
        }

    @Test
    fun observeSelectedOrFallbackPlanId_falls_back_when_the_selected_plan_is_soft_deleted() =
        runTest {
            val planA = repo.createPlan("Plan A")
            val planB = repo.createPlan("Plan B")
            repo.selectPlan(planB.id)
            assertEquals(planB.id, repo.observeSelectedOrFallbackPlanId().first())

            repo.softDeletePlan(planB.id)

            assertEquals(planA.id, repo.observeSelectedOrFallbackPlanId().first())
        }

    /**
     * The impl-specific `PlanRepositoryTest` pins a stronger "self-heals" property by resurrecting
     * a soft-deleted plan through direct DAO access with its *original* id, proving the stored
     * selection is never overwritten on fallback. That seam isn't reachable through
     * [PlanRepository] — [PlanRepository.createPlan] always mints a fresh UUID, so a deleted plan's
     * id can never reappear via this interface alone. As the reachable equivalent, this test pins
     * that the fallback keeps being *recomputed fresh* from current liveness across a chain of
     * further mutations, rather than sticking to whichever id it first fell back to.
     */
    @Test
    fun observeSelectedOrFallbackPlanId_recomputes_the_fallback_across_a_chain_of_deletions() =
        runTest {
            val planA = repo.createPlan("Plan A")
            val planB = repo.createPlan("Plan B")
            val planC = repo.createPlan("Plan C")
            repo.selectPlan(planC.id)
            assertEquals(planC.id, repo.observeSelectedOrFallbackPlanId().first())

            repo.softDeletePlan(planC.id)
            assertEquals(planA.id, repo.observeSelectedOrFallbackPlanId().first())

            repo.softDeletePlan(planA.id)
            assertEquals(planB.id, repo.observeSelectedOrFallbackPlanId().first())
        }

    // ── day reorder / multi-add / single-day observe (issue #30 PR2) ────────

    @Test
    fun reorderDayTemplates_reorders_observeDayTemplates_to_match_the_given_id_order() =
        runTest {
            val plan = repo.createPlan("Plan")
            val dayA = repo.createDayTemplate(plan.id, "Day A")
            val dayB = repo.createDayTemplate(plan.id, "Day B")
            val dayC = repo.createDayTemplate(plan.id, "Day C")

            repo.reorderDayTemplates(listOf(dayC.id, dayA.id, dayB.id))

            val observed = repo.observeDayTemplates(plan.id).first()
            assertEquals(listOf(dayC.id, dayA.id, dayB.id), observed.map { it.id })
        }

    @Test
    fun addExercisesToTemplate_appends_in_input_order_after_existing_rows() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val existing = repo.addExerciseToTemplate(day.id, "ex-existing")

            repo.addExercisesToTemplate(day.id, listOf("ex-b", "ex-a", "ex-c"))

            val observed = repo.observeTemplateExercises(day.id).first()
            assertEquals(existing.id, observed.first().id)
            assertEquals(listOf("ex-existing", "ex-b", "ex-a", "ex-c"), observed.map { it.exerciseId })
        }

    @Test
    fun addExercisesToTemplate_dedupes_against_live_rows_and_within_the_input() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            repo.addExerciseToTemplate(day.id, "ex-a") // already live

            repo.addExercisesToTemplate(day.id, listOf("ex-a", "ex-b", "ex-b"))

            val observed = repo.observeTemplateExercises(day.id).first()
            assertEquals(2, observed.size)
            assertEquals(1, observed.count { it.exerciseId == "ex-a" })
            assertEquals(1, observed.count { it.exerciseId == "ex-b" })
        }

    @Test
    fun addExercisesToTemplate_re_adds_an_exercise_whose_earlier_row_was_soft_deleted_as_a_fresh_row() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val firstRow = repo.addExerciseToTemplate(day.id, "ex-a")
            repo.removeTemplateExercise(firstRow.id)

            repo.addExercisesToTemplate(day.id, listOf("ex-a"))

            val observed = repo.observeTemplateExercises(day.id).first()
            assertEquals(1, observed.size)
            assertEquals("ex-a", observed.first().exerciseId)
            assertNotEquals(firstRow.id, observed.first().id)
        }

    @Test
    fun observeDayTemplate_emits_the_day_then_null_after_softDeleteDayTemplate() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            assertEquals(day.id, repo.observeDayTemplate(day.id).first()?.id)

            repo.softDeleteDayTemplate(day.id)

            assertNull(repo.observeDayTemplate(day.id).first())
        }
}
