package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PlanRepositoryTest {
    private val clockMillis = 5000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneOffset.UTC)
    private val fixedInstant = Instant.ofEpochMilli(clockMillis)

    private lateinit var dao: FakePlanDao
    private lateinit var repo: PlanRepositoryImpl

    @Before
    fun setUp() {
        dao = FakePlanDao()
        repo = PlanRepositoryImpl(dao, FakeTransactor(), clock)
    }

    @Test
    fun `createPlan returns WorkoutPlan with trimmed name, UUID id, clock timestamps, position 0`() =
        runTest {
            val result = repo.createPlan("  PPL  ")

            assertEquals("PPL", result.name)
            assertEquals(0, result.position)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNull(result.deletedAt)
            assertNotNull(UUID.fromString(result.id))

            assertEquals(1, dao.plans.size)
            assertEquals("PPL", dao.plans[result.id]!!.name)
        }

    @Test
    fun `softDeletePlan tombstones plan, its day templates, and their template exercises`() =
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
    fun `createPlan second plan gets position 1`() =
        runTest {
            val first = repo.createPlan("PPL")
            val second = repo.createPlan("Upper Lower")

            assertEquals(0, first.position)
            assertEquals(1, second.position)
        }

    @Test
    fun `createDayTemplate appends at max+1 within its plan, independent counters per plan`() =
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
            assertNotNull(UUID.fromString(dayA1.id))
        }

    @Test
    fun `addExerciseToTemplate appends at max+1, starts at 0, null targets, UUID`() =
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
            assertNotNull(UUID.fromString(te1.id))
        }

    @Test
    fun `renamePlan trims name and bumps updatedAt, leaves other fields intact`() =
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
    fun `renamePlan is a no-op when id is absent`() =
        runTest {
            repo.renamePlan("nonexistent-id", "Name")
            // Should not throw; dao remains empty
            assertTrue(dao.plans.isEmpty())
        }

    @Test
    fun `renameDayTemplate trims name and bumps updatedAt, leaves other fields intact`() =
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
    fun `renameDayTemplate is a no-op when id is absent`() =
        runTest {
            repo.renameDayTemplate("nonexistent-id", "Name")
            assertTrue(dao.dayTemplates.isEmpty())
        }

    @Test
    fun `updateTemplateExerciseTargets writes three target fields and updatedAt, preserves other fields`() =
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
    fun `updateTemplateExerciseTargets can clear targets back to null`() =
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
    fun `removeTemplateExercise tombstones exactly that row`() =
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
    fun `softDeleteDayTemplate tombstones day and its exercises, leaves sibling day exercises live`() =
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
    fun `reorderTemplateExercises reassigns positions 0,1,2 with bumped updatedAt`() =
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
}
