package de.simiil.liftlog.data.repository

import app.cash.turbine.test
import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.InMemoryPreferencesDataStore
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PlanRepositoryTest {
    private val clockMillis = 5000L
    private val clock = FixedClock(Instant.fromEpochMilliseconds(clockMillis))
    private val fixedInstant = Instant.fromEpochMilliseconds(clockMillis)

    private lateinit var dao: FakePlanDao
    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var repo: PlanRepositoryImpl

    @BeforeTest
    fun setUp() {
        dao = FakePlanDao()
        dataStore = InMemoryPreferencesDataStore()
        repo = PlanRepositoryImpl(dao, FakeTransactor(), clock, dataStore)
    }

    @Test
    fun createPlan_returns_WorkoutPlan_with_trimmed_name_UUID_id_clock_timestamps_position_0() =
        runTest {
            val result = repo.createPlan("  PPL  ")

            assertEquals("PPL", result.name)
            assertEquals(0, result.position)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNull(result.deletedAt)
            assertNotNull(Uuid.parse(result.id))

            assertEquals(1, dao.plans.size)
            assertEquals("PPL", dao.plans[result.id]!!.name)
        }

    @Test
    fun softDeletePlan_tombstones_plan_its_day_templates_and_their_template_exercises() =
        runTest {
            val oldTs = 1L

            // Plan p1 with two day templates and their exercises
            val p1Id = "plan-1"
            val d1Id = "day-1"
            val d2Id = "day-2"
            val te1Id = "te-1"
            val te2Id = "te-2"
            val te3Id = "te-3"

            dao.plans[p1Id] = WorkoutPlanEntity(p1Id, "Plan1", 0, oldTs, oldTs, null)
            dao.dayTemplates[d1Id] = PlanDayTemplateEntity(d1Id, p1Id, "Day A", 0, oldTs, oldTs, null)
            dao.dayTemplates[d2Id] = PlanDayTemplateEntity(d2Id, p1Id, "Day B", 1, oldTs, oldTs, null)
            dao.templateExercises[te1Id] = TemplateExerciseEntity(te1Id, d1Id, "ex-1", 0, null, null, null, oldTs, oldTs, null)
            dao.templateExercises[te2Id] = TemplateExerciseEntity(te2Id, d1Id, "ex-2", 1, null, null, null, oldTs, oldTs, null)
            dao.templateExercises[te3Id] = TemplateExerciseEntity(te3Id, d2Id, "ex-3", 0, null, null, null, oldTs, oldTs, null)

            // Plan p2 with its own children — must remain live
            val p2Id = "plan-2"
            val d3Id = "day-3"
            val te4Id = "te-4"
            dao.plans[p2Id] = WorkoutPlanEntity(p2Id, "Plan2", 1, oldTs, oldTs, null)
            dao.dayTemplates[d3Id] = PlanDayTemplateEntity(d3Id, p2Id, "Day X", 0, oldTs, oldTs, null)
            dao.templateExercises[te4Id] = TemplateExerciseEntity(te4Id, d3Id, "ex-4", 0, null, null, null, oldTs, oldTs, null)

            repo.softDeletePlan(p1Id)

            // p1 plan tombstoned
            assertEquals(clockMillis, dao.plans[p1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.plans[p1Id]!!.updatedAt)

            // p1 day templates tombstoned
            assertEquals(clockMillis, dao.dayTemplates[d1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.dayTemplates[d1Id]!!.updatedAt)
            assertEquals(clockMillis, dao.dayTemplates[d2Id]!!.deletedAt)
            assertEquals(clockMillis, dao.dayTemplates[d2Id]!!.updatedAt)

            // p1 template exercises tombstoned
            assertEquals(clockMillis, dao.templateExercises[te1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.templateExercises[te1Id]!!.updatedAt)
            assertEquals(clockMillis, dao.templateExercises[te2Id]!!.deletedAt)
            assertEquals(clockMillis, dao.templateExercises[te2Id]!!.updatedAt)
            assertEquals(clockMillis, dao.templateExercises[te3Id]!!.deletedAt)
            assertEquals(clockMillis, dao.templateExercises[te3Id]!!.updatedAt)

            // p2 and its children untouched
            assertNull(dao.plans[p2Id]!!.deletedAt)
            assertNull(dao.dayTemplates[d3Id]!!.deletedAt)
            assertNull(dao.templateExercises[te4Id]!!.deletedAt)
        }

    // ── NEW TESTS (Task 2) ────────────────────────────────────────────────

    @Test
    fun createPlan_second_plan_gets_position_1() =
        runTest {
            val first = repo.createPlan("PPL")
            val second = repo.createPlan("Upper Lower")

            assertEquals(0, first.position)
            assertEquals(1, second.position)
        }

    @Test
    fun createDayTemplate_appends_at_max_1_within_its_plan_independent_counters_per_plan() =
        runTest {
            val p1 = repo.createPlan("Plan A")
            val p2 = repo.createPlan("Plan B")

            val dayA1 = repo.createDayTemplate(p1.id, "Push")
            val dayA2 = repo.createDayTemplate(p1.id, "Pull")
            val dayB1 = repo.createDayTemplate(p2.id, "Full Body")

            assertEquals(0, dayA1.position)
            assertEquals(1, dayA2.position)
            // Plan B starts its own counter at 0 — independent of plan A
            assertEquals(0, dayB1.position)

            assertEquals(p1.id, dayA1.planId)
            assertEquals(p2.id, dayB1.planId)
            assertEquals("Push", dayA1.name)
            assertEquals(fixedInstant, dayA1.createdAt)
            assertEquals(fixedInstant, dayA1.updatedAt)
            assertNull(dayA1.deletedAt)
            assertNotNull(Uuid.parse(dayA1.id))
        }

    @Test
    fun addExerciseToTemplate_appends_at_max_1_starts_at_0_null_targets_UUID() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            val te1 = repo.addExerciseToTemplate(day.id, "ex-a")
            val te2 = repo.addExerciseToTemplate(day.id, "ex-b")

            assertEquals(0, te1.position)
            assertEquals(1, te2.position)
            assertNull(te1.targetSets)
            assertNull(te1.targetRepsMin)
            assertNull(te1.targetRepsMax)
            assertEquals("ex-a", te1.exerciseId)
            assertEquals(day.id, te1.templateId)
            assertEquals(fixedInstant, te1.createdAt)
            assertEquals(fixedInstant, te1.updatedAt)
            assertNull(te1.deletedAt)
            assertNotNull(Uuid.parse(te1.id))
        }

    @Test
    fun renamePlan_trims_name_and_bumps_updatedAt_leaves_other_fields_intact() =
        runTest {
            val plan = repo.createPlan("Old Name")
            repo.renamePlan(plan.id, "  New Name  ")

            val stored = dao.plans[plan.id]!!
            assertEquals("New Name", stored.name)
            assertEquals(clockMillis, stored.updatedAt)
            assertEquals(plan.position, stored.position)
            assertEquals(clockMillis, stored.createdAt) // unchanged
        }

    @Test
    fun renamePlan_is_a_no_op_when_id_is_absent() =
        runTest {
            repo.renamePlan("nonexistent-id", "Name")
            // Should not throw; dao remains empty
            assertTrue(dao.plans.isEmpty())
        }

    @Test
    fun renameDayTemplate_trims_name_and_bumps_updatedAt_leaves_other_fields_intact() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Old Day")
            repo.renameDayTemplate(day.id, "  Push Day  ")

            val stored = dao.dayTemplates[day.id]!!
            assertEquals("Push Day", stored.name)
            assertEquals(clockMillis, stored.updatedAt)
            assertEquals(day.position, stored.position)
            assertEquals(plan.id, stored.planId)
            assertEquals(clockMillis, stored.createdAt) // unchanged
        }

    @Test
    fun renameDayTemplate_is_a_no_op_when_id_is_absent() =
        runTest {
            repo.renameDayTemplate("nonexistent-id", "Name")
            assertTrue(dao.dayTemplates.isEmpty())
        }

    @Test
    fun updateTemplateExerciseTargets_writes_three_target_fields_and_updatedAt_preserves_other_fields() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val te = repo.addExerciseToTemplate(day.id, "ex-a")

            repo.updateTemplateExerciseTargets(te.id, targetSets = 3, targetRepsMin = 8, targetRepsMax = 12)

            val stored = dao.templateExercises[te.id]!!
            assertEquals(3, stored.targetSets)
            assertEquals(8, stored.targetRepsMin)
            assertEquals(12, stored.targetRepsMax)
            assertEquals(clockMillis, stored.updatedAt)
            // Preserved fields
            assertEquals(te.position, stored.position)
            assertEquals(te.exerciseId, stored.exerciseId)
            assertEquals(te.templateId, stored.templateId)
            assertEquals(clockMillis, stored.createdAt)
        }

    @Test
    fun updateTemplateExerciseTargets_can_clear_targets_back_to_null() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val te = repo.addExerciseToTemplate(day.id, "ex-a")
            repo.updateTemplateExerciseTargets(te.id, targetSets = 3, targetRepsMin = 8, targetRepsMax = 12)

            // Now clear back to null
            repo.updateTemplateExerciseTargets(te.id, targetSets = null, targetRepsMin = null, targetRepsMax = null)

            val stored = dao.templateExercises[te.id]!!
            assertNull(stored.targetSets)
            assertNull(stored.targetRepsMin)
            assertNull(stored.targetRepsMax)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test
    fun removeTemplateExercise_tombstones_exactly_that_row() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val te1 = repo.addExerciseToTemplate(day.id, "ex-a")
            val te2 = repo.addExerciseToTemplate(day.id, "ex-b")

            repo.removeTemplateExercise(te1.id)

            assertEquals(clockMillis, dao.templateExercises[te1.id]!!.deletedAt)
            assertNull(dao.templateExercises[te2.id]!!.deletedAt)
        }

    @Test
    fun softDeleteDayTemplate_tombstones_day_and_its_exercises_leaves_sibling_day_exercises_live() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day1 = repo.createDayTemplate(plan.id, "Day 1")
            val day2 = repo.createDayTemplate(plan.id, "Day 2") // sibling

            val te1 = repo.addExerciseToTemplate(day1.id, "ex-a")
            val te2 = repo.addExerciseToTemplate(day1.id, "ex-b")
            val te3 = repo.addExerciseToTemplate(day2.id, "ex-c") // sibling's exercise

            repo.softDeleteDayTemplate(day1.id)

            // day1 tombstoned
            assertEquals(clockMillis, dao.dayTemplates[day1.id]!!.deletedAt)
            // day1's exercises tombstoned
            assertEquals(clockMillis, dao.templateExercises[te1.id]!!.deletedAt)
            assertEquals(clockMillis, dao.templateExercises[te2.id]!!.deletedAt)

            // sibling day2 and its exercise remain live
            assertNull(dao.dayTemplates[day2.id]!!.deletedAt)
            assertNull(dao.templateExercises[te3.id]!!.deletedAt)
        }

    @Test
    fun reorderTemplateExercises_reassigns_positions_0_1_2_with_bumped_updatedAt() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            val teA = repo.addExerciseToTemplate(day.id, "ex-a")
            val teB = repo.addExerciseToTemplate(day.id, "ex-b")
            val teC = repo.addExerciseToTemplate(day.id, "ex-c")

            // Original order: A=0, B=1, C=2
            // Reorder to: C, A, B
            repo.reorderTemplateExercises(listOf(teC.id, teA.id, teB.id))

            val storedA = dao.templateExercises[teA.id]!!
            val storedB = dao.templateExercises[teB.id]!!
            val storedC = dao.templateExercises[teC.id]!!

            assertEquals(0, storedC.position)
            assertEquals(1, storedA.position)
            assertEquals(2, storedB.position)

            // updatedAt bumped for all three
            assertEquals(clockMillis, storedA.updatedAt)
            assertEquals(clockMillis, storedB.updatedAt)
            assertEquals(clockMillis, storedC.updatedAt)
        }

    // ── reorderDayTemplates (Task 30/PR2) ──────────────────────────────────

    @Test
    fun reorderDayTemplates_reassigns_positions_0_1_2_with_bumped_updatedAt() =
        runTest {
            val plan = repo.createPlan("Plan")

            val dayA = repo.createDayTemplate(plan.id, "Day A")
            val dayB = repo.createDayTemplate(plan.id, "Day B")
            val dayC = repo.createDayTemplate(plan.id, "Day C")

            // Original order: A=0, B=1, C=2
            // Reorder to: C, A, B
            repo.reorderDayTemplates(listOf(dayC.id, dayA.id, dayB.id))

            val storedA = dao.dayTemplates[dayA.id]!!
            val storedB = dao.dayTemplates[dayB.id]!!
            val storedC = dao.dayTemplates[dayC.id]!!

            assertEquals(0, storedC.position)
            assertEquals(1, storedA.position)
            assertEquals(2, storedB.position)

            assertEquals(clockMillis, storedA.updatedAt)
            assertEquals(clockMillis, storedB.updatedAt)
            assertEquals(clockMillis, storedC.updatedAt)
        }

    // ── addExercisesToTemplate (Task 30/PR2) ───────────────────────────────

    @Test
    fun addExercisesToTemplate_appends_after_current_max_position_preserving_input_order() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val existing = repo.addExerciseToTemplate(day.id, "ex-existing") // position 0

            repo.addExercisesToTemplate(day.id, listOf("ex-b", "ex-a", "ex-c"))

            val live = dao.templateExercises.values.filter { it.templateId == day.id && it.deletedAt == null }
            assertEquals(existing.position, dao.templateExercises[existing.id]!!.position)

            val added = live.filter { it.id != existing.id }.sortedBy { it.position }
            assertEquals(listOf("ex-b", "ex-a", "ex-c"), added.map { it.exerciseId })
            assertEquals(listOf(1, 2, 3), added.map { it.position })
        }

    @Test
    fun addExercisesToTemplate_dedupes_ids_already_live_in_the_template() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            repo.addExerciseToTemplate(day.id, "ex-a") // already live

            repo.addExercisesToTemplate(day.id, listOf("ex-a", "ex-b"))

            val live = dao.templateExercises.values.filter { it.templateId == day.id && it.deletedAt == null }
            // Only one row for ex-a (the pre-existing one) plus the new ex-b — no duplicate ex-a row.
            assertEquals(2, live.size)
            assertEquals(1, live.count { it.exerciseId == "ex-a" })
            assertEquals(1, live.count { it.exerciseId == "ex-b" })
        }

    @Test
    fun addExercisesToTemplate_dedupes_duplicates_within_the_input_list() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            repo.addExercisesToTemplate(day.id, listOf("ex-a", "ex-b", "ex-a"))

            val live = dao.templateExercises.values.filter { it.templateId == day.id && it.deletedAt == null }
            assertEquals(2, live.size)
            assertEquals(1, live.count { it.exerciseId == "ex-a" })
            assertEquals(1, live.count { it.exerciseId == "ex-b" })
        }

    @Test
    fun addExercisesToTemplate_re_adds_an_exercise_whose_earlier_row_was_soft_deleted() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")
            val firstRow = repo.addExerciseToTemplate(day.id, "ex-a")
            repo.removeTemplateExercise(firstRow.id) // soft-delete: no longer "live"

            repo.addExercisesToTemplate(day.id, listOf("ex-a"))

            val live = dao.templateExercises.values.filter { it.templateId == day.id && it.deletedAt == null }
            assertEquals(1, live.size)
            assertEquals("ex-a", live.first().exerciseId)
            assertNotEquals(firstRow.id, live.first().id) // a fresh row, not the tombstoned one
        }

    // ── observeDayTemplate (Task 30/PR2) ───────────────────────────────────

    @Test
    fun observeDayTemplate_emits_the_row_then_null_after_soft_delete() =
        runTest {
            val plan = repo.createPlan("Plan")
            val day = repo.createDayTemplate(plan.id, "Day")

            repo.observeDayTemplate(day.id).test {
                val first = awaitItem()
                assertNotNull(first)
                assertEquals(day.id, first!!.id)

                repo.softDeleteDayTemplate(day.id)
                val afterDelete = awaitItem()
                assertNull(afterDelete, "observeDayTemplate should emit null after soft-delete")

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── ensureDefaultPlan (Task 30/PR1) ────────────────────────────────────

    @Test
    fun ensureDefaultPlan_creates_a_plan_with_trimmed_name_UUID_timestamps_when_none_live() =
        runTest {
            repo.ensureDefaultPlan("  Default  ")

            assertEquals(1, dao.plans.size)
            val created = dao.plans.values.first()
            assertEquals("Default", created.name)
            assertEquals(0, created.position)
            assertEquals(clockMillis, created.createdAt)
            assertEquals(clockMillis, created.updatedAt)
            assertNull(created.deletedAt)
            assertNotNull(Uuid.parse(created.id))
        }

    @Test
    fun ensureDefaultPlan_is_a_no_op_when_a_live_plan_exists() =
        runTest {
            val existing = repo.createPlan("Existing")

            repo.ensureDefaultPlan("Default")

            assertEquals(1, dao.plans.size)
            assertEquals(
                existing.id,
                dao.plans.values
                    .first()
                    .id,
            )
            assertEquals(
                "Existing",
                dao.plans.values
                    .first()
                    .name,
            )
        }

    @Test
    fun ensureDefaultPlan_after_deleting_all_plans_creates_a_fresh_plan_with_a_new_UUID() =
        runTest {
            val first = repo.createPlan("First")
            repo.softDeletePlan(first.id)

            repo.ensureDefaultPlan("Default")

            val live = dao.plans.values.filter { it.deletedAt == null }
            assertEquals(1, live.size)
            assertEquals("Default", live.first().name)
            assertNotEquals(first.id, live.first().id)
        }

    // ── softDeletePlanAndEnsureDefault (Task 30/PR1) ────────────────────────

    @Test
    fun softDeletePlanAndEnsureDefault_tombstones_plan_days_exercises_and_seeds_a_default_when_it_was_the_only_plan() =
        runTest {
            val plan = repo.createPlan("Solo")
            val day = repo.createDayTemplate(plan.id, "Day")
            val te = repo.addExerciseToTemplate(day.id, "ex-1")

            repo.softDeletePlanAndEnsureDefault(plan.id, "Default")

            assertEquals(clockMillis, dao.plans[plan.id]!!.deletedAt)
            assertEquals(clockMillis, dao.dayTemplates[day.id]!!.deletedAt)
            assertEquals(clockMillis, dao.templateExercises[te.id]!!.deletedAt)

            val live = dao.plans.values.filter { it.deletedAt == null }
            assertEquals(1, live.size)
            assertEquals("Default", live.first().name)
            assertNotEquals(plan.id, live.first().id)
        }

    @Test
    fun softDeletePlanAndEnsureDefault_does_not_seed_when_another_live_plan_remains() =
        runTest {
            val toDelete = repo.createPlan("A")
            val remaining = repo.createPlan("B")

            repo.softDeletePlanAndEnsureDefault(toDelete.id, "Default")

            val live = dao.plans.values.filter { it.deletedAt == null }
            assertEquals(1, live.size)
            assertEquals(remaining.id, live.first().id)
        }

    // ── selectPlan / observeSelectedOrFallbackPlanId (Task 30/PR1) ─────────

    @Test
    fun selectPlan_persists_and_observeSelectedOrFallbackPlanId_emits_the_selected_live_plan() =
        runTest {
            repo.createPlan("Plan A")
            val planB = repo.createPlan("Plan B")

            repo.selectPlan(planB.id)

            repo.observeSelectedOrFallbackPlanId().test {
                assertEquals(planB.id, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeSelectedOrFallbackPlanId_falls_back_to_mostUsedOrFirst_when_unset() =
        runTest {
            val planA = repo.createPlan("Plan A")
            repo.createPlan("Plan B")
            // no selectPlan call — selection is unset

            repo.observeSelectedOrFallbackPlanId().test {
                assertEquals(planA.id, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeSelectedOrFallbackPlanId_falls_back_when_the_selected_plan_is_soft_deleted() =
        runTest {
            val planA = repo.createPlan("Plan A")
            val planB = repo.createPlan("Plan B")
            repo.selectPlan(planB.id)

            repo.observeSelectedOrFallbackPlanId().test {
                assertEquals(planB.id, awaitItem())

                repo.softDeletePlan(planB.id)
                assertEquals(planA.id, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeSelectedOrFallbackPlanId_recovers_when_the_stored_id_reappears_import_round_trip() =
        runTest {
            val planA = repo.createPlan("Plan A")
            val planB = repo.createPlan("Plan B")
            repo.selectPlan(planA.id)

            repo.observeSelectedOrFallbackPlanId().test {
                assertEquals(planA.id, awaitItem())

                // planA goes stale (e.g. an import that momentarily doesn't include it) — falls back.
                repo.softDeletePlan(planA.id)
                assertEquals(planB.id, awaitItem())

                // planA "reappears" live with the SAME id (e.g. the import re-creates it). The
                // stored selection was never cleared, so it resumes with no extra write.
                dao.updatePlan(dao.plans[planA.id]!!.copy(deletedAt = null))
                assertEquals(planA.id, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
