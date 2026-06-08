package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionRepositoryTest {

    private val clockMillis = 5000L
    private val clock = Clock.fixed(Instant.ofEpochMilli(clockMillis), ZoneOffset.UTC)
    private val fixedInstant = Instant.ofEpochMilli(clockMillis)

    private lateinit var dao: FakeSessionDao
    private lateinit var repo: SessionRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeSessionDao()
        repo = SessionRepositoryImpl(dao, FakeTransactor(), clock)
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
}
