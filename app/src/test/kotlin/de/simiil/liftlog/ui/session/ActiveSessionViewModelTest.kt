package de.simiil.liftlog.ui.session

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.testing.FakeExerciseRepository
import de.simiil.liftlog.testing.FakeSessionRepository
import de.simiil.liftlog.testing.FakeSettingsRepository
import de.simiil.liftlog.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-06-08T10:00:00Z")
    private val names =
        de.simiil.liftlog.ui.exercises
            .ExerciseNameResolver { _, fallback -> fallback }

    // ---- builders ----

    private fun exercise(
        id: String,
        name: String,
        equipment: Equipment = Equipment.BARBELL,
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.CHEST,
            equipment = equipment,
            isBuiltIn = true,
            isHidden = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun session(
        id: String,
        name: String? = null,
    ): Session =
        Session(
            id = id,
            templateId = null,
            templateNameSnapshot = name,
            startedAt = now,
            endedAt = null,
            note = null,
            rpe = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun sessionExercise(
        id: String,
        exerciseId: String,
        position: Int = 0,
        targetSets: Int? = null,
        targetRepsMin: Int? = null,
        targetRepsMax: Int? = null,
    ): SessionExercise =
        SessionExercise(
            id = id,
            sessionId = "s1",
            exerciseId = exerciseId,
            position = position,
            targetSets = targetSets,
            targetRepsMin = targetRepsMin,
            targetRepsMax = targetRepsMax,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun loggedSet(
        id: String,
        seId: String,
        weightKg: Double,
        reps: Int,
        position: Int,
    ): LoggedSet =
        LoggedSet(
            id = id,
            sessionExerciseId = seId,
            weightKg = weightKg,
            reps = reps,
            position = position,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    /** A bare last-performance set (only weight/reps matter for prefill). */
    private fun perf(
        weightKg: Double,
        reps: Int,
    ): LoggedSet = loggedSet("perf", "old-se", weightKg, reps, 0)

    private fun createVm(
        session: FakeSessionRepository,
        exercises: FakeExerciseRepository,
        settings: FakeSettingsRepository = FakeSettingsRepository(initialWeightUnit = WeightUnit.KG),
    ): ActiveSessionViewModel =
        ActiveSessionViewModel(
            sessionRepository = session,
            exerciseRepository = exercises,
            settingsRepository = settings,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to "s1")),
            names = names,
        )

    // ---- Tests ----

    @Test
    fun `entry is pre-filled from last performance first set (rule 2)`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult =
                listOf(perf(30.0, 10), perf(30.0, 10), perf(30.0, 8))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session = session("s1"),
                    exercises =
                        listOf(
                            SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList()),
                        ),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals("se1", state.entry?.sessionExerciseId)
                assertEquals(30.0, state.entry?.weightKg!!, 1e-9)
                assertEquals(10, state.entry?.reps)
                // active card is se1
                val active = state.cards.single { it.state == CardState.ACTIVE }
                assertEquals("se1", active.sessionExerciseId)
                assertEquals("Bench", active.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `weight increment adds 2_5 kg and decrement floors at 0`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                assertEquals(30.0, awaitItem().entry?.weightKg!!, 1e-9)
                vm.onWeightIncrement()
                assertEquals(32.5, expectMostRecentItem().entry?.weightKg!!, 1e-9)
                repeat(20) { vm.onWeightDecrement() }
                assertEquals(0.0, expectMostRecentItem().entry?.weightKg!!, 1e-9)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `logSet records the set and re-primes the entry (rule 1)`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10), perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(30.0, state.entry?.weightKg!!, 1e-9)
                assertEquals(10, state.entry?.reps)

                vm.onLogSet()
                // Simulate the repository emitting the newly-logged set back into the details flow.
                sessionRepo.setSessionDetails(
                    "s1",
                    SessionWithDetails(
                        session("s1"),
                        listOf(
                            SessionExerciseWithSets(
                                sessionExercise("se1", "ex1"),
                                listOf(loggedSet("set-1", "se1", 30.0, 10, 1)),
                            ),
                        ),
                    ),
                )

                val after = expectMostRecentItem()
                assertEquals(
                    1,
                    after.cards
                        .single()
                        .sets.size,
                )
                // Rule 1: next entry re-primes from the just-logged set.
                assertEquals(30.0, after.entry?.weightKg!!, 1e-9)
                assertEquals(10, after.entry?.reps)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf(Triple("se1", 30.0, 10)), sessionRepo.logSetCalls)
        }

    @Test
    fun `logSet is a no-op when weight is null (never performed)`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = emptyList() // never performed
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertNull(state.entry?.weightKg)
                assertEquals(10, state.entry?.reps) // default reps
                vm.onLogSet()
                cancelAndIgnoreRemainingEvents()
            }

            assertTrue("logSet must not be called with null weight", sessionRepo.logSetCalls.isEmpty())
        }

    @Test
    fun `auto-advance activates the next card when a target is met`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo =
                FakeExerciseRepository().apply {
                    all.value = listOf(exercise("ex1", "Bench"), exercise("ex2", "Row"))
                }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            // Card se1 has a target of 1 set; se2 is open-ended.
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(
                        SessionExerciseWithSets(sessionExercise("se1", "ex1", 0, targetSets = 1), emptyList()),
                        SessionExerciseWithSets(sessionExercise("se2", "ex2", 1), emptyList()),
                    ),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            // Push a new details value where se1 now has its 1 target set met.
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(
                        SessionExerciseWithSets(
                            sessionExercise("se1", "ex1", 0, targetSets = 1),
                            listOf(loggedSet("set-1", "se1", 30.0, 10, 1)),
                        ),
                        SessionExerciseWithSets(sessionExercise("se2", "ex2", 1), emptyList()),
                    ),
                ),
            )

            vm.uiState.test {
                val state = expectMostRecentItem()
                val active = state.cards.single { it.state == CardState.ACTIVE }
                assertEquals("se2", active.sessionExerciseId)
                // se1 is now completed (has a set, no longer active)
                assertEquals(CardState.COMPLETED, state.cards.single { it.sessionExerciseId == "se1" }.state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `card exposes targetRepsMin and targetRepsMax from session exercise`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Squat")) }
            sessionRepo.lastPerformanceResult = listOf(perf(100.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(
                        SessionExerciseWithSets(
                            sessionExercise("se1", "ex1", targetSets = 3, targetRepsMin = 8, targetRepsMax = 12),
                            emptyList(),
                        ),
                    ),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val card = awaitItem().cards.single()
                assertEquals(8, card.targetRepsMin)
                assertEquals(12, card.targetRepsMax)
                assertEquals(3, card.targetSets)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finish sets finished flag and total set count`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1", name = "Push Day"),
                    listOf(
                        SessionExerciseWithSets(
                            sessionExercise("se1", "ex1"),
                            listOf(
                                loggedSet("set-1", "se1", 30.0, 10, 1),
                                loggedSet("set-2", "se1", 30.0, 9, 2),
                            ),
                        ),
                    ),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                assertEquals("Push Day", awaitItem().name)
                vm.onFinish()
                val finished = expectMostRecentItem()
                assertTrue(finished.finished)
                assertEquals(2, finished.lastFinishedSetCount)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf("s1"), sessionRepo.finishSessionCalls)
        }

    @Test
    fun `uiState exposes session rpe and note`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1").copy(rpe = 8.0, note = "felt good"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(8.0, state.sessionRpe!!, 1e-9)
                assertEquals("felt good", state.sessionNote)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onSessionRpeChange persists immediately`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.onSessionRpeChange(8.5)
            advanceUntilIdle()

            assertEquals(listOf("s1" to 8.5), sessionRepo.updateSessionRpeCalls)
        }

    @Test
    fun `onSessionNoteChange persists after debounce`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.onSessionNoteChange("pump was insane")
            runCurrent()
            assertTrue("no call yet before debounce", sessionRepo.updateSessionNoteCalls.isEmpty())

            advanceTimeBy(600)
            runCurrent()
            assertEquals(listOf("s1" to "pump was insane"), sessionRepo.updateSessionNoteCalls)
        }

    @Test
    fun `onFinish flushes a pending note before finishing`() =
        runTest {
            val sessionRepo = FakeSessionRepository()
            val exerciseRepo = FakeExerciseRepository().apply { all.value = listOf(exercise("ex1", "Bench")) }
            sessionRepo.lastPerformanceResult = listOf(perf(30.0, 10))
            sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session("s1"),
                    listOf(SessionExerciseWithSets(sessionExercise("se1", "ex1"), emptyList())),
                ),
            )

            val vm = createVm(sessionRepo, exerciseRepo)

            vm.onSessionNoteChange("almost lost this")
            vm.onFinish()
            runCurrent()

            assertTrue(
                "flush must have written the note",
                sessionRepo.updateSessionNoteCalls.contains("s1" to "almost lost this"),
            )
            // Both calls happen in the same launched coroutine: if the note was written,
            // finishSession was also called (they're sequential in onFinish's launch body).
            assertTrue(
                "finishSession must have been called after the note flush",
                sessionRepo.finishSessionCalls.contains("s1"),
            )
        }
}
