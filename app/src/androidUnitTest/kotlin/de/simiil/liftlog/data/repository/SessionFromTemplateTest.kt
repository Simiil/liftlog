package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.entity.PlanDayTemplateEntity
import de.simiil.liftlog.data.entity.TemplateExerciseEntity
import de.simiil.liftlog.data.entity.WorkoutPlanEntity
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.InMemoryPreferencesDataStore
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakePrefillDao
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Instant

class SessionFromTemplateTest {
    // Shared FakePlanDao so both planRepo and sessionRepo read from the same store
    private val planDao = FakePlanDao()
    private val sessionDao = FakeSessionDao()
    private val clock = FixedClock(Instant.fromEpochMilliseconds(7000L))
    private val planRepo = PlanRepositoryImpl(planDao, FakeTransactor(), clock, InMemoryPreferencesDataStore())
    private val sessionRepo = SessionRepositoryImpl(sessionDao, FakeTransactor(), clock, FakePrefillDao(), planDao)

    // Seed IDs
    private val planId = "plan-1"
    private val dayId = "day-1"
    private val exId1 = "ex-1"
    private val exId2 = "ex-2"

    @Before
    fun setUp() {
        // Seed a plan and day template into the shared planDao
        planDao.plans[planId] =
            WorkoutPlanEntity(
                id = planId,
                name = "PPL Plan",
                position = 0,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            )
        planDao.dayTemplates[dayId] =
            PlanDayTemplateEntity(
                id = dayId,
                planId = planId,
                name = "Push Day",
                position = 0,
                createdAt = 1L,
                updatedAt = 1L,
                deletedAt = null,
            )
    }

    // ─── snapshot copy ────────────────────────────────────────────────────────

    @Test
    fun `startSessionFromTemplate copies template exercises into session_exercises`() =
        runTest {
            val te1Id = UUID.randomUUID().toString()
            val te2Id = UUID.randomUUID().toString()

            // Two template exercises: one with targets, one with nulls
            planDao.templateExercises[te1Id] =
                TemplateExerciseEntity(
                    id = te1Id,
                    templateId = dayId,
                    exerciseId = exId1,
                    position = 0,
                    targetSets = 3,
                    targetRepsMin = 8,
                    targetRepsMax = 12,
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                )
            planDao.templateExercises[te2Id] =
                TemplateExerciseEntity(
                    id = te2Id,
                    templateId = dayId,
                    exerciseId = exId2,
                    position = 1,
                    targetSets = null,
                    targetRepsMin = null,
                    targetRepsMax = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                )

            val session = sessionRepo.startSessionFromTemplate(dayId)

            // Session has correct templateId and snapshot name
            assertEquals(dayId, session.templateId)
            assertEquals("Push Day", session.templateNameSnapshot)
            assertNull(session.endedAt)
            assertNull(session.deletedAt)

            // Session stored in dao
            assertEquals(1, sessionDao.sessions.size)
            val storedSession = sessionDao.sessions[session.id]!!
            assertEquals(dayId, storedSession.templateId)
            assertEquals("Push Day", storedSession.templateNameSnapshot)

            // Two session_exercises copied
            assertEquals(2, sessionDao.sessionExercises.size)

            val seList =
                sessionDao.sessionExercises.values
                    .filter { it.sessionId == session.id }
                    .sortedBy { it.position }

            assertEquals(2, seList.size)

            // First exercise: exId1 with targets
            val se1 = seList[0]
            assertEquals(exId1, se1.exerciseId)
            assertEquals(session.id, se1.sessionId)
            assertEquals(0, se1.position)
            assertEquals(3, se1.targetSets)
            assertEquals(8, se1.targetRepsMin)
            assertEquals(12, se1.targetRepsMax)
            assertNull(se1.deletedAt)
            // Fresh UUID — different from the template exercise id
            assertNotEquals(te1Id, se1.id)
            assertNotNull(UUID.fromString(se1.id))

            // Second exercise: exId2 with null targets
            val se2 = seList[1]
            assertEquals(exId2, se2.exerciseId)
            assertEquals(session.id, se2.sessionId)
            assertEquals(1, se2.position)
            assertNull(se2.targetSets)
            assertNull(se2.targetRepsMin)
            assertNull(se2.targetRepsMax)
            assertNull(se2.deletedAt)
            assertNotEquals(te2Id, se2.id)
        }

    // ─── isolation ───────────────────────────────────────────────────────────

    @Test
    fun `session_exercises are unaffected when template is mutated or deleted after snapshot`() =
        runTest {
            val te1Id = UUID.randomUUID().toString()
            val te2Id = UUID.randomUUID().toString()

            planDao.templateExercises[te1Id] =
                TemplateExerciseEntity(
                    id = te1Id,
                    templateId = dayId,
                    exerciseId = exId1,
                    position = 0,
                    targetSets = 3,
                    targetRepsMin = 8,
                    targetRepsMax = 12,
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                )
            planDao.templateExercises[te2Id] =
                TemplateExerciseEntity(
                    id = te2Id,
                    templateId = dayId,
                    exerciseId = exId2,
                    position = 1,
                    targetSets = null,
                    targetRepsMin = null,
                    targetRepsMax = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                    deletedAt = null,
                )

            // Take the snapshot
            val session = sessionRepo.startSessionFromTemplate(dayId)

            // Capture the session_exercises IDs before mutation
            val seIds = sessionDao.sessionExercises.keys.toList()
            assertEquals(2, seIds.size)

            // Mutate the template via planRepo: change targets of te1, remove te2, soft-delete the day
            planRepo.updateTemplateExerciseTargets(te1Id, targetSets = 5, targetRepsMin = 6, targetRepsMax = 10)
            planRepo.removeTemplateExercise(te2Id)
            planRepo.softDeleteDayTemplate(dayId)

            // Session exercises must be completely unchanged
            val seAfter =
                sessionDao.sessionExercises.values
                    .filter { it.sessionId == session.id }
                    .sortedBy { it.position }

            // Still two rows (both live — not tombstoned)
            assertEquals(2, seAfter.size)
            seAfter.forEach { assertNull("session_exercise must still be live: ${it.id}", it.deletedAt) }

            // First exercise: still has original targets (3/8/12), not the mutated ones (5/6/10)
            val se1 = seAfter[0]
            assertEquals(exId1, se1.exerciseId)
            assertEquals(3, se1.targetSets)
            assertEquals(8, se1.targetRepsMin)
            assertEquals(12, se1.targetRepsMax)

            // Second exercise: still present with null targets
            val se2 = seAfter[1]
            assertEquals(exId2, se2.exerciseId)
            assertNull(se2.targetSets)
        }

    // ─── active-session guard ─────────────────────────────────────────────────

    @Test(expected = IllegalStateException::class)
    fun `startSessionFromTemplate throws when a session is already active`() =
        runTest {
            // Start an active session first
            sessionRepo.startEmptySession()

            // Should throw because there is already an active session
            sessionRepo.startSessionFromTemplate(dayId)
        }

    // ─── empty template ───────────────────────────────────────────────────────

    @Test
    fun `startSessionFromTemplate with empty template creates session with zero session_exercises`() =
        runTest {
            // No template exercises seeded — dayId template is empty

            val session = sessionRepo.startSessionFromTemplate(dayId)

            assertEquals(dayId, session.templateId)
            assertEquals("Push Day", session.templateNameSnapshot)
            assertNull(session.endedAt)

            // Session inserted
            assertEquals(1, sessionDao.sessions.size)

            // Zero session_exercises
            val seCount = sessionDao.sessionExercises.values.count { it.sessionId == session.id }
            assertEquals(0, seCount)
            assertTrue(sessionDao.sessionExercises.isEmpty())
        }
}
