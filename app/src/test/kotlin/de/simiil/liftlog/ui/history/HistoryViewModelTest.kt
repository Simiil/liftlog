package de.simiil.liftlog.ui.history

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.testing.FakeAnalyticsRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ended = Instant.parse("2026-01-02T10:00:00Z")

    private fun makeSession(
        id: String,
        endedAt: Instant? = ended,
        startedAt: Instant = Instant.parse("2026-01-01T10:00:00Z"),
        templateNameSnapshot: String? = null,
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

    private fun makeVm(
        repo: FakeSessionRepository,
        analytics: FakeAnalyticsRepository = FakeAnalyticsRepository(),
    ) = HistoryViewModel(repo, analytics)

    @Test
    fun `initial state has no sessions`() =
        runTest {
            val repo = FakeSessionRepository()
            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                assertTrue("sessions should be empty initially", state.sessions.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finished sessions are shown`() =
        runTest {
            val repo = FakeSessionRepository()
            val s1 = makeSession("s-1", endedAt = ended)
            val s2 = makeSession("s-2", endedAt = ended)
            repo.history.value = listOf(s1, s2)

            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(2, state.sessions.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `live session (endedAt == null) is excluded`() =
        runTest {
            val repo = FakeSessionRepository()
            val active = makeSession("active-1", endedAt = null)
            val finished = makeSession("finished-1", endedAt = ended)
            repo.history.value = listOf(active, finished)

            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                val ids = state.sessions.map { it.sessionId }
                assertTrue("live session should be excluded", !ids.contains("active-1"))
                assertTrue("finished session should be included", ids.contains("finished-1"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `set counts are joined from observeSetCountsBySession`() =
        runTest {
            val repo = FakeSessionRepository()
            val s1 = makeSession("s-1")
            val s2 = makeSession("s-2")
            repo.history.value = listOf(s1, s2)
            repo.setCounts.value = mapOf("s-1" to 15, "s-2" to 9)

            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                val byId = state.sessions.associateBy { it.sessionId }
                assertEquals(15, byId["s-1"]?.setCount)
                assertEquals(9, byId["s-2"]?.setCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `set count defaults to 0 when session not in counts map`() =
        runTest {
            val repo = FakeSessionRepository()
            val s1 = makeSession("s-1")
            repo.history.value = listOf(s1)
            // no entry in setCounts

            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(0, state.sessions.first().setCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `order is preserved from observeHistory`() =
        runTest {
            val repo = FakeSessionRepository()
            val t1 = Instant.parse("2026-01-03T10:00:00Z")
            val t2 = Instant.parse("2026-01-02T10:00:00Z")
            val t3 = Instant.parse("2026-01-01T10:00:00Z")
            val s1 = makeSession("s-1", startedAt = t1)
            val s2 = makeSession("s-2", startedAt = t2)
            val s3 = makeSession("s-3", startedAt = t3)
            // history already ordered DESC by the repository
            repo.history.value = listOf(s1, s2, s3)

            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(listOf("s-1", "s-2", "s-3"), state.sessions.map { it.sessionId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `session name is preserved (null and non-null)`() =
        runTest {
            val repo = FakeSessionRepository()
            val named = makeSession("s-named", templateNameSnapshot = "Push day")
            val unnamed = makeSession("s-unnamed", templateNameSnapshot = null)
            repo.history.value = listOf(named, unnamed)

            val vm = makeVm(repo)

            vm.uiState.test {
                val state = awaitItem()
                val byId = state.sessions.associateBy { it.sessionId }
                assertEquals("Push day", byId["s-named"]?.name)
                assertEquals(null, byId["s-unnamed"]?.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `session carries PR flag from analytics`() =
        runTest {
            val repo = FakeSessionRepository()
            val analytics = FakeAnalyticsRepository()
            repo.history.value = listOf(makeSession("s-pr"), makeSession("s-plain"))
            analytics.prSessionIds.value = setOf("s-pr")

            val vm = makeVm(repo, analytics)

            vm.uiState.test {
                val byId = awaitItem().sessions.associateBy { it.sessionId }
                assertTrue("s-pr should be flagged", byId.getValue("s-pr").isPr)
                assertFalse("s-plain should not be flagged", byId.getValue("s-plain").isPr)

                // live update: flags must re-stamp when the PR set changes
                analytics.prSessionIds.value = setOf("s-plain")
                val updated = awaitItem().sessions.associateBy { it.sessionId }
                assertFalse("s-pr flag should clear", updated.getValue("s-pr").isPr)
                assertTrue("s-plain should now be flagged", updated.getValue("s-plain").isPr)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
