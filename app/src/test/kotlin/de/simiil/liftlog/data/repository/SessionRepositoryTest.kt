package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.SessionSetCount
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.testing.fakes.FakePrefillDao
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionRepositoryTest {

    private val clockMillis = 5000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneOffset.UTC)
    private val fixedInstant = Instant.ofEpochMilli(clockMillis)

    private lateinit var dao: FakeSessionDao
    private lateinit var prefillDao: FakePrefillDao
    private lateinit var repo: SessionRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeSessionDao()
        prefillDao = FakePrefillDao()
        repo = SessionRepositoryImpl(dao, FakeTransactor(), clock, prefillDao)
    }

    @Test
    fun `startEmptySession inserts session with endedAt null and correct timestamps`() = runTest {
        val result = repo.startEmptySession()

        assertNull(result.endedAt)
        assertEquals(fixedInstant, result.startedAt)
        assertEquals(fixedInstant, result.createdAt)
        assertEquals(fixedInstant, result.updatedAt)
        assertNull(result.deletedAt)
        assertNotNull(UUID.fromString(result.id))

        assertEquals(1, dao.sessions.size)
        assertNull(dao.sessions[result.id]!!.endedAt)
    }

    @Test(expected = IllegalStateException::class)
    fun `startEmptySession throws when a session is already active`() = runTest {
        repo.startEmptySession()
        repo.startEmptySession() // must throw
    }

    @Test
    fun `startEmptySession succeeds after finishing the active session`() = runTest {
        val first = repo.startEmptySession()
        repo.finishSession(first.id)
        val second = repo.startEmptySession() // must not throw
        assertNull(second.endedAt)
        assertEquals(2, dao.sessions.size)
    }

    @Test
    fun `finishSession sets endedAt and bumps updatedAt`() = runTest {
        // Insert a session with old updatedAt to make assertion discriminating
        val oldTs = 1L
        val sessionId = "sess-1"
        dao.sessions[sessionId] = SessionEntity(
            sessionId, null, null,
            startedAt = oldTs, endedAt = null, note = null,
            createdAt = oldTs, updatedAt = oldTs, deletedAt = null,
        )

        repo.finishSession(sessionId)

        val stored = dao.sessions[sessionId]!!
        assertEquals(clockMillis, stored.endedAt)
        assertEquals(clockMillis, stored.updatedAt)
    }

    @Test
    fun `finishSession with missing id is a no-op`() = runTest {
        repo.finishSession("non-existent")
        assertEquals(0, dao.sessions.size)
    }

    @Test
    fun `finishSession is a no-op when session is already ended`() = runTest {
        val oldTs = 1L
        val sessionId = "sess-1"
        // Insert already-finished session
        dao.sessions[sessionId] = SessionEntity(
            sessionId, null, null,
            startedAt = oldTs, endedAt = oldTs, note = null,
            createdAt = oldTs, updatedAt = oldTs, deletedAt = null,
        )

        repo.finishSession(sessionId)

        val stored = dao.sessions[sessionId]!!
        // endedAt and updatedAt must remain the original value (oldTs), not clockMillis
        assertEquals(oldTs, stored.endedAt)
        assertEquals(oldTs, stored.updatedAt)
    }

    @Test
    fun `finishSession sets endedAt when session is live`() = runTest {
        val oldTs = 1L
        val sessionId = "sess-live"
        dao.sessions[sessionId] = SessionEntity(
            sessionId, null, null,
            startedAt = oldTs, endedAt = null, note = null,
            createdAt = oldTs, updatedAt = oldTs, deletedAt = null,
        )

        repo.finishSession(sessionId)

        val stored = dao.sessions[sessionId]!!
        assertEquals(clockMillis, stored.endedAt)
        assertEquals(clockMillis, stored.updatedAt)
    }

    @Test
    fun `softDeleteSession tombstones session, its session-exercises, and their logged-sets`() = runTest {
        val oldTs = 1L

        // Session s1 with 1 session-exercise and 2 logged sets
        val s1Id = "sess-1"
        val se1Id = "se-1"
        val ls1Id = "ls-1"
        val ls2Id = "ls-2"

        dao.sessions[s1Id] = SessionEntity(s1Id, null, null, oldTs, null, null, oldTs, oldTs, null)
        dao.sessionExercises[se1Id] = SessionExerciseEntity(se1Id, s1Id, "ex-1", 0, null, null, null, oldTs, oldTs, null)
        dao.loggedSets[ls1Id] = LoggedSetEntity(ls1Id, se1Id, 100.0, 5, 0, oldTs, null, null, oldTs, oldTs, null)
        dao.loggedSets[ls2Id] = LoggedSetEntity(ls2Id, se1Id, 100.0, 5, 1, oldTs, null, null, oldTs, oldTs, null)

        // Session s2 with its own children — must remain live
        val s2Id = "sess-2"
        val se2Id = "se-2"
        val ls3Id = "ls-3"
        dao.sessions[s2Id] = SessionEntity(s2Id, null, null, oldTs, null, null, oldTs, oldTs, null)
        dao.sessionExercises[se2Id] = SessionExerciseEntity(se2Id, s2Id, "ex-2", 0, null, null, null, oldTs, oldTs, null)
        dao.loggedSets[ls3Id] = LoggedSetEntity(ls3Id, se2Id, 80.0, 8, 0, oldTs, null, null, oldTs, oldTs, null)

        repo.softDeleteSession(s1Id)

        // s1 tombstoned
        assertEquals(clockMillis, dao.sessions[s1Id]!!.deletedAt)
        assertEquals(clockMillis, dao.sessions[s1Id]!!.updatedAt)

        // s1 session exercise tombstoned
        assertEquals(clockMillis, dao.sessionExercises[se1Id]!!.deletedAt)
        assertEquals(clockMillis, dao.sessionExercises[se1Id]!!.updatedAt)

        // s1 logged sets tombstoned
        assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.deletedAt)
        assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.updatedAt)
        assertEquals(clockMillis, dao.loggedSets[ls2Id]!!.deletedAt)
        assertEquals(clockMillis, dao.loggedSets[ls2Id]!!.updatedAt)

        // s2 and its children untouched
        assertNull(dao.sessions[s2Id]!!.deletedAt)
        assertNull(dao.sessionExercises[se2Id]!!.deletedAt)
        assertNull(dao.loggedSets[ls3Id]!!.deletedAt)
    }

    // ─── addExerciseToSession ──────────────────────────────────────────────────

    @Test
    fun `addExerciseToSession inserts with correct position and null targets`() = runTest {
        val sessionId = "sess-1"
        dao.sessions[sessionId] = SessionEntity(sessionId, null, null, 1L, null, null, 1L, 1L, null)

        // Pre-insert an exercise at position 1 so the new one should be at position 2
        val existingSeId = "se-existing"
        dao.sessionExercises[existingSeId] = SessionExerciseEntity(
            existingSeId, sessionId, "ex-a", 1, null, null, null, 1L, 1L, null
        )

        val result = repo.addExerciseToSession(sessionId, "ex-b")

        assertEquals(2, result.position)
        assertNull(result.targetSets)
        assertNull(result.targetRepsMin)
        assertNull(result.targetRepsMax)
        assertEquals(sessionId, result.sessionId)
        assertEquals("ex-b", result.exerciseId)
        assertEquals(fixedInstant, result.createdAt)
        assertEquals(fixedInstant, result.updatedAt)
        assertNull(result.deletedAt)
        assertNotNull(UUID.fromString(result.id))

        // Verify it was stored in the fake
        val stored = dao.sessionExercises[result.id]!!
        assertEquals(2, stored.position)
    }

    @Test
    fun `addExerciseToSession with no existing exercises starts at position 1`() = runTest {
        val sessionId = "sess-empty"
        dao.sessions[sessionId] = SessionEntity(sessionId, null, null, 1L, null, null, 1L, 1L, null)

        val result = repo.addExerciseToSession(sessionId, "ex-a")

        assertEquals(1, result.position)
    }

    // ─── logSet ───────────────────────────────────────────────────────────────

    @Test
    fun `logSet inserts set with correct position and timestamps`() = runTest {
        val seId = "se-1"

        // Pre-insert a set at position 1
        val existingSetId = "ls-existing"
        dao.loggedSets[existingSetId] = LoggedSetEntity(
            existingSetId, seId, 80.0, 5, 1, 1L, null, null, 1L, 1L, null
        )

        val result = repo.logSet(seId, 100.0, 8)

        assertEquals(2, result.position)
        assertEquals(100.0, result.weightKg, 0.0)
        assertEquals(8, result.reps)
        assertEquals(fixedInstant, result.completedAt)
        assertEquals(fixedInstant, result.createdAt)
        assertEquals(fixedInstant, result.updatedAt)
        assertNull(result.rpe)
        assertNull(result.note)
        assertNull(result.deletedAt)
        assertNotNull(UUID.fromString(result.id))

        val stored = dao.loggedSets[result.id]!!
        assertEquals(2, stored.position)
        assertEquals(clockMillis, stored.completedAt)
        assertEquals(clockMillis, stored.createdAt)
        assertEquals(clockMillis, stored.updatedAt)
    }

    @Test
    fun `logSet with no existing sets starts at position 1`() = runTest {
        val result = repo.logSet("se-1", 60.0, 10)
        assertEquals(1, result.position)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `logSet rejects negative weightKg`() = runTest {
        repo.logSet("se-1", -1.0, 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `logSet rejects zero reps`() = runTest {
        repo.logSet("se-1", 100.0, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `logSet rejects negative reps`() = runTest {
        repo.logSet("se-1", 100.0, -1)
    }

    // ─── updateSet ────────────────────────────────────────────────────────────

    @Test
    fun `updateSet writes new weight, reps, rpe, note and bumps updatedAt`() = runTest {
        val setId = "ls-1"
        val seId = "se-1"
        val oldTs = 1L
        dao.loggedSets[setId] = LoggedSetEntity(
            setId, seId, 80.0, 5, 2, oldTs, null, null, oldTs, oldTs, null
        )

        repo.updateSet(setId, 90.0, 6, 8.5, "felt easy")

        val stored = dao.loggedSets[setId]!!
        assertEquals(90.0, stored.weightKg, 0.0)
        assertEquals(6, stored.reps)
        assertEquals(8.5, stored.rpe!!, 0.0)
        assertEquals("felt easy", stored.note)
        assertEquals(clockMillis, stored.updatedAt)
        // Preserved fields
        assertEquals(2, stored.position)
        assertEquals(oldTs, stored.completedAt)
        assertEquals(oldTs, stored.createdAt)
    }

    @Test
    fun `updateSet is a no-op when set does not exist`() = runTest {
        repo.updateSet("non-existent", 90.0, 6, null, null)
        assertEquals(0, dao.loggedSets.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateSet rejects negative weightKg`() = runTest {
        repo.updateSet("ls-1", -1.0, 5, null, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `updateSet rejects zero reps`() = runTest {
        repo.updateSet("ls-1", 100.0, 0, null, null)
    }

    // ─── deleteSet ────────────────────────────────────────────────────────────

    @Test
    fun `deleteSet soft-deletes the set`() = runTest {
        val setId = "ls-1"
        val seId = "se-1"
        val oldTs = 1L
        dao.loggedSets[setId] = LoggedSetEntity(
            setId, seId, 80.0, 5, 1, oldTs, null, null, oldTs, oldTs, null
        )

        repo.deleteSet(setId)

        val stored = dao.loggedSets[setId]!!
        assertEquals(clockMillis, stored.deletedAt)
        assertEquals(clockMillis, stored.updatedAt)
    }

    // ─── removeExercise ───────────────────────────────────────────────────────

    @Test
    fun `removeExercise soft-deletes session exercise and all its logged sets`() = runTest {
        val seId = "se-1"
        val ls1Id = "ls-1"
        val ls2Id = "ls-2"
        val oldTs = 1L

        dao.sessionExercises[seId] = SessionExerciseEntity(
            seId, "sess-1", "ex-a", 1, null, null, null, oldTs, oldTs, null
        )
        dao.loggedSets[ls1Id] = LoggedSetEntity(ls1Id, seId, 80.0, 5, 1, oldTs, null, null, oldTs, oldTs, null)
        dao.loggedSets[ls2Id] = LoggedSetEntity(ls2Id, seId, 80.0, 5, 2, oldTs, null, null, oldTs, oldTs, null)

        repo.removeExercise(seId)

        assertEquals(clockMillis, dao.sessionExercises[seId]!!.deletedAt)
        assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.deletedAt)
        assertEquals(clockMillis, dao.loggedSets[ls2Id]!!.deletedAt)
    }

    // ─── replaceExercise ──────────────────────────────────────────────────────

    @Test
    fun `replaceExercise soft-deletes old exercise and sets, inserts new one at same position`() = runTest {
        val seId = "se-1"
        val ls1Id = "ls-1"
        val oldTs = 1L
        val sessionId = "sess-1"
        val originalPosition = 3

        dao.sessionExercises[seId] = SessionExerciseEntity(
            seId, sessionId, "ex-old", originalPosition, null, null, null, oldTs, oldTs, null
        )
        dao.loggedSets[ls1Id] = LoggedSetEntity(ls1Id, seId, 80.0, 5, 1, oldTs, null, null, oldTs, oldTs, null)

        val result = repo.replaceExercise(seId, "ex-new")

        // Old exercise and its sets soft-deleted
        assertEquals(clockMillis, dao.sessionExercises[seId]!!.deletedAt)
        assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.deletedAt)

        // New exercise has same position and new exerciseId
        assertEquals(originalPosition, result.position)
        assertEquals("ex-new", result.exerciseId)
        assertEquals(sessionId, result.sessionId)
        assertNull(result.deletedAt)
        assertEquals(fixedInstant, result.createdAt)
        assertEquals(fixedInstant, result.updatedAt)
        assertNotNull(UUID.fromString(result.id))

        // Stored in fake
        val stored = dao.sessionExercises[result.id]!!
        assertEquals(originalPosition, stored.position)
        assertEquals("ex-new", stored.exerciseId)
        assertNull(stored.deletedAt)
    }

    @Test(expected = IllegalStateException::class)
    fun `replaceExercise throws when session exercise not found`() = runTest {
        repo.replaceExercise("non-existent", "ex-new")
    }

    // ─── lastPerformance ──────────────────────────────────────────────────────

    @Test
    fun `lastPerformance returns emptyList when no completed session exists`() = runTest {
        prefillDao.lastCompletedSessionId = null

        val result = repo.lastPerformance("ex-1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `lastPerformance returns mapped sets from last completed session`() = runTest {
        val sessionId = "sess-completed"
        val exerciseId = "ex-1"
        val now = 1L

        prefillDao.lastCompletedSessionId = sessionId
        prefillDao.setsBySessionAndExercise[sessionId to exerciseId] = listOf(
            LoggedSetEntity("ls-a", "se-prev", 100.0, 5, 1, now, null, null, now, now, null),
            LoggedSetEntity("ls-b", "se-prev", 100.0, 4, 2, now, null, null, now, now, null),
        )

        val result = repo.lastPerformance(exerciseId)

        assertEquals(2, result.size)
        assertEquals("ls-a", result[0].id)
        assertEquals(100.0, result[0].weightKg, 0.0)
        assertEquals(5, result[0].reps)
        assertEquals("ls-b", result[1].id)
        assertEquals(4, result[1].reps)
    }

    // ─── observeSetCountsBySession ────────────────────────────────────────────

    @Test
    fun `observeSetCountsBySession maps rows to sessionId-to-setCount map`() = runTest {
        dao.setCounts.value = listOf(
            SessionSetCount(sessionId = "sess-1", setCount = 3),
            SessionSetCount(sessionId = "sess-2", setCount = 7),
        )

        val result = repo.observeSetCountsBySession().first()

        assertEquals(mapOf("sess-1" to 3, "sess-2" to 7), result)
    }

    @Test
    fun `observeSetCountsBySession returns empty map when no rows exist`() = runTest {
        dao.setCounts.value = emptyList()

        val result = repo.observeSetCountsBySession().first()

        assertTrue(result.isEmpty())
    }
}
