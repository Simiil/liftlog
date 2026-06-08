package de.simiil.liftlog.ui.home

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ---- helpers ----

    private fun makeSession(
        id: String,
        templateNameSnapshot: String? = null,
        endedAt: Instant? = null,
        startedAt: Instant = Instant.parse("2026-01-01T10:00:00Z"),
    ): Session {
        val now = Instant.parse("2026-01-01T10:00:00Z")
        return Session(
            id = id,
            templateId = null,
            templateNameSnapshot = templateNameSnapshot,
            startedAt = startedAt,
            endedAt = endedAt,
            note = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }

    private fun makeDetails(session: Session, exerciseCount: Int): SessionWithDetails =
        SessionWithDetails(
            session = session,
            exercises = List(exerciseCount) { idx ->
                SessionExerciseWithSets(
                    sessionExercise = de.simiil.liftlog.domain.model.SessionExercise(
                        id = "se-$idx",
                        sessionId = session.id,
                        exerciseId = "ex-$idx",
                        position = idx,
                        targetSets = null,
                        targetRepsMin = null,
                        targetRepsMax = null,
                        createdAt = session.createdAt,
                        updatedAt = session.updatedAt,
                        deletedAt = null,
                    ),
                    sets = emptyList(),
                )
            },
        )

    // ---- Tests ----

    @Test
    fun `resume is null when activeSession is null`() = runTest {
        val repo = FakeSessionRepository()
        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertNull("resume should be null when no active session", state.resume)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume is populated when active session exists`() = runTest {
        val repo = FakeSessionRepository()
        val session = makeSession(id = "sess-1", templateNameSnapshot = "Push day")
        repo.activeSession.value = session
        repo.setSessionDetails("sess-1", makeDetails(session, exerciseCount = 3))

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertNotNull("resume should be non-null with active session", state.resume)
            val resume = state.resume!!
            assertEquals("sess-1", resume.sessionId)
            assertEquals("Push day", resume.name)
            assertEquals(3, resume.exerciseCount)
            assertEquals(session.startedAt, resume.startedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume name is null (not resolved) when templateNameSnapshot is null`() = runTest {
        val repo = FakeSessionRepository()
        val session = makeSession(id = "sess-2", templateNameSnapshot = null)
        repo.activeSession.value = session
        repo.setSessionDetails("sess-2", makeDetails(session, exerciseCount = 0))

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertNotNull(state.resume)
            assertNull("name should remain null in VM, resolved in composable", state.resume!!.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume exerciseCount falls back to 0 when details not yet emitted`() = runTest {
        val repo = FakeSessionRepository()
        val session = makeSession(id = "sess-3")
        repo.activeSession.value = session
        // details flow exists but emits null
        repo.details["sess-3"] // don't set a value — will be null

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertNotNull(state.resume)
            assertEquals(0, state.resume!!.exerciseCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent excludes the live session (endedAt == null)`() = runTest {
        val repo = FakeSessionRepository()
        val active = makeSession(id = "active-1", endedAt = null)
        val finished = makeSession(id = "finished-1", endedAt = Instant.parse("2026-01-01T11:00:00Z"))

        repo.activeSession.value = active
        repo.history.value = listOf(active, finished)

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            val recentIds = state.recent.map { it.sessionId }
            assertTrue("finished session should appear in recent", recentIds.contains("finished-1"))
            assertTrue("live session should NOT appear in recent", !recentIds.contains("active-1"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent is limited to 5 entries`() = runTest {
        val repo = FakeSessionRepository()
        val ended = Instant.parse("2026-01-02T10:00:00Z")
        val sessions = (1..8).map { i ->
            makeSession(id = "s-$i", endedAt = ended)
        }
        repo.history.value = sessions

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("recent should be capped at 5", 5, state.recent.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent set counts are joined from observeSetCountsBySession`() = runTest {
        val repo = FakeSessionRepository()
        val ended = Instant.parse("2026-01-02T10:00:00Z")
        val s1 = makeSession(id = "s-1", endedAt = ended)
        val s2 = makeSession(id = "s-2", endedAt = ended)

        repo.history.value = listOf(s1, s2)
        repo.setCounts.value = mapOf("s-1" to 12, "s-2" to 7)

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            val byId = state.recent.associateBy { it.sessionId }
            assertEquals(12, byId["s-1"]?.setCount)
            assertEquals(7, byId["s-2"]?.setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recent set count defaults to 0 when not in map`() = runTest {
        val repo = FakeSessionRepository()
        val ended = Instant.parse("2026-01-02T10:00:00Z")
        val s1 = makeSession(id = "s-1", endedAt = ended)

        repo.history.value = listOf(s1)
        // no entry for s-1 in setCounts

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.recent.first().setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startOrResume returns existing session id when active session is live`() = runTest {
        val repo = FakeSessionRepository()
        val session = makeSession(id = "existing-session")
        repo.activeSession.value = session
        repo.setSessionDetails("existing-session", makeDetails(session, 0))

        val vm = HomeViewModel(repo)

        // Let uiState settle so resume is populated
        vm.uiState.test {
            awaitItem() // initial
            cancelAndIgnoreRemainingEvents()
        }

        var receivedId: String? = null
        vm.startOrResume { receivedId = it }

        // Give the coroutine a chance to run (UnconfinedTestDispatcher executes eagerly)
        assertEquals("existing-session", receivedId)
        assertEquals(0, repo.startEmptySessionCalls.size)
    }

    @Test
    fun `startOrResume calls startEmptySession and returns new id when no active session`() = runTest {
        val repo = FakeSessionRepository()
        // no active session

        val vm = HomeViewModel(repo)

        vm.uiState.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        var receivedId: String? = null
        vm.startOrResume { receivedId = it }

        assertEquals(1, repo.startEmptySessionCalls.size)
        assertNotNull("should have received the new session id", receivedId)
        assertTrue("id should start with 'new-session'", receivedId!!.startsWith("new-session"))
    }
}
