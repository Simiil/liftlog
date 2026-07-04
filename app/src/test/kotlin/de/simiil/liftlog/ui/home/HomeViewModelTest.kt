package de.simiil.liftlog.ui.home

import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.testing.FakeAnalyticsRepository
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakePlanRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ---- helpers ----

    /** Creates a [HomeViewModel] with optional plan and session repositories. */
    private fun makeVm(
        sessionRepo: FakeSessionRepository = FakeSessionRepository(),
        planRepo: FakePlanRepository = FakePlanRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        analyticsRepo: FakeAnalyticsRepository = FakeAnalyticsRepository(),
    ) = HomeViewModel(sessionRepo, planRepo, exerciseRepo, analyticsRepo)

    private fun exercise(
        id: String,
        group: MuscleGroup,
    ) = Exercise(
        id = id,
        name = id,
        muscleGroup = group,
        equipment = Equipment.BARBELL,
        isBuiltIn = true,
        isHidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

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
            rpe = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
    }

    private fun makeDetails(
        session: Session,
        exerciseCount: Int,
    ): SessionWithDetails =
        SessionWithDetails(
            session = session,
            exercises =
                List(exerciseCount) { idx ->
                    SessionExerciseWithSets(
                        sessionExercise =
                            de.simiil.liftlog.domain.model.SessionExercise(
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
    fun `resume is null when activeSession is null`() =
        runTest {
            val repo = FakeSessionRepository()
            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                assertNull("resume should be null when no active session", state.resume)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resume is populated when active session exists`() =
        runTest {
            val repo = FakeSessionRepository()
            val session = makeSession(id = "sess-1", templateNameSnapshot = "Push day")
            repo.activeSession.value = session
            repo.setSessionDetails("sess-1", makeDetails(session, exerciseCount = 3))

            val vm = makeVm(sessionRepo = repo)

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
    fun `resume name is null (not resolved) when templateNameSnapshot is null`() =
        runTest {
            val repo = FakeSessionRepository()
            val session = makeSession(id = "sess-2", templateNameSnapshot = null)
            repo.activeSession.value = session
            repo.setSessionDetails("sess-2", makeDetails(session, exerciseCount = 0))

            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                assertNotNull(state.resume)
                assertNull("name should remain null in VM, resolved in composable", state.resume!!.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resume exerciseCount falls back to 0 when details not yet emitted`() =
        runTest {
            val repo = FakeSessionRepository()
            val session = makeSession(id = "sess-3")
            repo.activeSession.value = session
            // details flow exists but emits null
            repo.details["sess-3"] // don't set a value — will be null

            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                assertNotNull(state.resume)
                assertEquals(0, state.resume!!.exerciseCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent excludes the live session (endedAt == null)`() =
        runTest {
            val repo = FakeSessionRepository()
            val active = makeSession(id = "active-1", endedAt = null)
            val finished = makeSession(id = "finished-1", endedAt = Instant.parse("2026-01-01T11:00:00Z"))

            repo.activeSession.value = active
            repo.history.value = listOf(active, finished)

            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                val recentIds = state.recent.map { it.sessionId }
                assertTrue("finished session should appear in recent", recentIds.contains("finished-1"))
                assertTrue("live session should NOT appear in recent", !recentIds.contains("active-1"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent is limited to 5 entries`() =
        runTest {
            val repo = FakeSessionRepository()
            val ended = Instant.parse("2026-01-02T10:00:00Z")
            val sessions =
                (1..8).map { i ->
                    makeSession(id = "s-$i", endedAt = ended)
                }
            repo.history.value = sessions

            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals("recent should be capped at 5", 5, state.recent.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent set counts are joined from observeSetCountsBySession`() =
        runTest {
            val repo = FakeSessionRepository()
            val ended = Instant.parse("2026-01-02T10:00:00Z")
            val s1 = makeSession(id = "s-1", endedAt = ended)
            val s2 = makeSession(id = "s-2", endedAt = ended)

            repo.history.value = listOf(s1, s2)
            repo.setCounts.value = mapOf("s-1" to 12, "s-2" to 7)

            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                val byId = state.recent.associateBy { it.sessionId }
                assertEquals(12, byId["s-1"]?.setCount)
                assertEquals(7, byId["s-2"]?.setCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent set count defaults to 0 when not in map`() =
        runTest {
            val repo = FakeSessionRepository()
            val ended = Instant.parse("2026-01-02T10:00:00Z")
            val s1 = makeSession(id = "s-1", endedAt = ended)

            repo.history.value = listOf(s1)
            // no entry for s-1 in setCounts

            val vm = makeVm(sessionRepo = repo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(0, state.recent.first().setCount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recent session carries PR flag from analytics`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val analyticsRepo = FakeAnalyticsRepository()
            val ended = Instant.parse("2026-01-02T10:00:00Z")
            sessionRepo.history.value =
                listOf(
                    makeSession("s-pr", endedAt = ended),
                    makeSession("s-plain", endedAt = ended),
                )
            analyticsRepo.prSessionIds.value = setOf("s-pr")

            val vm = makeVm(sessionRepo = sessionRepo, analyticsRepo = analyticsRepo)

            vm.uiState.test {
                val byId = awaitItem().recent.associateBy { it.sessionId }
                assertTrue("s-pr should be flagged", byId.getValue("s-pr").isPr)
                assertFalse("s-plain should not be flagged", byId.getValue("s-plain").isPr)
                // live update: flags must re-stamp when the PR set changes
                analyticsRepo.prSessionIds.value = setOf("s-plain")
                val updated = awaitItem().recent.associateBy { it.sessionId }
                assertFalse("s-pr flag should clear", updated.getValue("s-pr").isPr)
                assertTrue("s-plain should now be flagged", updated.getValue("s-plain").isPr)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `startOrResume returns existing session id when active session is live`() =
        runTest {
            val repo = FakeSessionRepository()
            val session = makeSession(id = "existing-session")
            repo.activeSession.value = session
            repo.setSessionDetails("existing-session", makeDetails(session, 0))

            val vm = makeVm(sessionRepo = repo)

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
    fun `startOrResume calls startEmptySession and returns new id when no active session`() =
        runTest {
            val repo = FakeSessionRepository()
            // no active session

            val vm = makeVm(sessionRepo = repo)

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

    // ── Template chip tests ───────────────────────────────────────────────────

    @Test
    fun `templates is empty when no plans exist`() =
        runTest {
            val planRepo = FakePlanRepository()
            // no plans seeded
            val vm = makeVm(planRepo = planRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertTrue("templates should be empty when no plans", state.templates.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `templates has chips for each day template of the most-used plan`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            planRepo.createDayTemplate(plan.id, "Push")
            planRepo.createDayTemplate(plan.id, "Pull")

            val vm = makeVm(planRepo = planRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals("should have 2 chips", 2, state.templates.size)
                assertEquals("Push", state.templates[0].name)
                assertEquals("Pull", state.templates[1].name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `template chip carries exercise count and up to 3 distinct muscle groups`() =
        runTest {
            val planRepo = FakePlanRepository()
            val exerciseRepo = FakeExerciseRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Push")
            planRepo.addExerciseToTemplate(day.id, "ex-bench")
            planRepo.addExerciseToTemplate(day.id, "ex-incline")
            planRepo.addExerciseToTemplate(day.id, "ex-ohp")
            // bench + incline are both CHEST → distinct collapses to [CHEST, SHOULDERS]
            exerciseRepo.all.value =
                listOf(
                    exercise("ex-bench", MuscleGroup.CHEST),
                    exercise("ex-incline", MuscleGroup.CHEST),
                    exercise("ex-ohp", MuscleGroup.SHOULDERS),
                )

            val vm = makeVm(planRepo = planRepo, exerciseRepo = exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(1, state.templates.size)
                val chip = state.templates.first()
                assertEquals("Push", chip.name)
                assertEquals(3, chip.exerciseCount)
                assertEquals(listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS), chip.muscleGroups)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hasPlanContent is false when plans have only empty days`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            planRepo.createDayTemplate(plan.id, "Push") // no exercises
            val vm = makeVm(planRepo = planRepo)

            vm.uiState.test {
                assertTrue(
                    "hasPlanContent should be false when no day has an exercise",
                    !awaitItem().hasPlanContent,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hasPlanContent is true once any day has an exercise`() =
        runTest {
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Push")
            planRepo.addExerciseToTemplate(day.id, "ex-1")
            val vm = makeVm(planRepo = planRepo)

            vm.uiState.test {
                assertTrue(
                    "hasPlanContent should be true once a day has an exercise",
                    awaitItem().hasPlanContent,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `startFromTemplate with no active session calls startSessionFromTemplate and invokes onOpenSession`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Push")

            val vm = makeVm(sessionRepo = sessionRepo, planRepo = planRepo)

            // Settle uiState
            vm.uiState.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            var receivedId: String? = null
            vm.startFromTemplate(day.id) { receivedId = it }

            assertEquals("should have called startSessionFromTemplate once", 1, sessionRepo.startFromTemplateCalls.size)
            assertEquals(day.id, sessionRepo.startFromTemplateCalls[0])
            assertNotNull("onOpenSession should be invoked with the new session id", receivedId)
            assertTrue("session id should be the new one", receivedId!!.startsWith("new-session"))
        }

    @Test
    fun `startFromTemplate with active session reuses active session and does not start a new one`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val planRepo = FakePlanRepository()
            val plan = planRepo.createPlan("PPL")
            val day = planRepo.createDayTemplate(plan.id, "Push")

            // Seed an active session
            val activeSession = makeSession(id = "active-sess")
            sessionRepo.activeSession.value = activeSession
            sessionRepo.setSessionDetails("active-sess", makeDetails(activeSession, 2))

            val vm = makeVm(sessionRepo = sessionRepo, planRepo = planRepo)

            // Settle uiState
            vm.uiState.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            var receivedId: String? = null
            vm.startFromTemplate(day.id) { receivedId = it }

            assertTrue(
                "startSessionFromTemplate should NOT be called when a session is live",
                sessionRepo.startFromTemplateCalls.isEmpty(),
            )
            assertEquals(
                "onOpenSession should receive the active session id",
                "active-sess",
                receivedId,
            )
        }
}
