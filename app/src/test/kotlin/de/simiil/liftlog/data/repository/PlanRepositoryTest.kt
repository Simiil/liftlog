package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun `createPlan returns WorkoutPlan with trimmed name, UUID id, clock timestamps, position 0`() = runTest {
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
    fun `softDeletePlan tombstones plan, its day templates, and their template exercises`() = runTest {
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
}
