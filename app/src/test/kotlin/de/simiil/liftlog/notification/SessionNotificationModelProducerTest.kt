package de.simiil.liftlog.notification

import app.cash.turbine.test
import de.simiil.liftlog.domain.logging.ActiveEntry
import de.simiil.liftlog.domain.logging.ActiveEntryTracker
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
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class SessionNotificationModelProducerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-07-09T10:00:00Z")

    // ---- builders ----

    private fun exercise(
        id: String,
        name: String,
    ): Exercise =
        Exercise(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            isBuiltIn = true,
            isHidden = false,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private fun session(
        id: String = "s1",
        name: String? = null,
        endedAt: Instant? = null,
        deletedAt: Instant? = null,
    ): Session =
        Session(
            id = id,
            templateId = null,
            templateNameSnapshot = name,
            startedAt = now,
            endedAt = endedAt,
            note = null,
            rpe = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = deletedAt,
        )

    private fun entry(
        seId: String,
        exerciseId: String,
        position: Int = 0,
        targetSets: Int? = null,
        sets: List<LoggedSet> = emptyList(),
    ): SessionExerciseWithSets =
        SessionExerciseWithSets(
            sessionExercise =
                SessionExercise(
                    id = seId,
                    sessionId = "s1",
                    exerciseId = exerciseId,
                    position = position,
                    targetSets = targetSets,
                    targetRepsMin = null,
                    targetRepsMax = null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                ),
            sets = sets,
        )

    private fun set(
        seId: String,
        weightKg: Double,
        reps: Int,
        position: Int,
    ): LoggedSet =
        LoggedSet(
            id = "$seId-$position",
            sessionExerciseId = seId,
            weightKg = weightKg,
            reps = reps,
            position = position,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )

    private class Env(
        weightUnit: WeightUnit = WeightUnit.KG,
    ) {
        val sessionRepo = FakeSessionRepository()
        val exerciseRepo = FakeExerciseRepository()
        val settings = FakeSettingsRepository(initialWeightUnit = weightUnit)
        val tracker = ActiveEntryTracker()
        val producer =
            SessionNotificationModelProducer(
                sessionRepository = sessionRepo,
                exerciseRepository = exerciseRepo,
                settingsRepository = settings,
                tracker = tracker,
                names = ExerciseNameResolver { _, fallback -> fallback },
            )
    }

    // ---- tests ----

    @Test
    fun `target case - progress and prefill from last set of entry`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session(),
                    listOf(
                        entry(
                            "se1",
                            "ex1",
                            targetSets = 5,
                            sets = listOf(set("se1", 80.0, 8, 1), set("se1", 80.0, 8, 2)),
                        ),
                    ),
                ),
            )

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals("s1", model.sessionId)
                assertEquals(now, model.startedAt)
                assertEquals("Bench", model.exerciseName)
                assertEquals("se1", model.sessionExerciseId)
                assertEquals(2, model.setsDone)
                assertEquals(5, model.targetSets)
                assertEquals(80.0, model.nextWeightKg!!, 1e-9)
                assertEquals(8, model.nextReps)
                assertEquals(WeightUnit.KG, model.unit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ad-hoc exercise has null targetSets`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session(),
                    listOf(entry("se1", "ex1", targetSets = null, sets = listOf(set("se1", 60.0, 10, 1)))),
                ),
            )

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertNull(model.targetSets)
                assertEquals(1, model.setsDone)
                assertEquals(60.0, model.nextWeightKg!!, 1e-9)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `never performed exercise - no weight, default reps`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.lastPerformanceResult = emptyList()
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(session(), listOf(entry("se1", "ex1"))),
            )

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals("Bench", model.exerciseName)
                assertEquals(0, model.setsDone)
                assertNull(model.nextWeightKg)
                assertEquals(10, model.nextReps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tracker values override prefill for the matching exercise`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session(),
                    listOf(entry("se1", "ex1", targetSets = 5, sets = listOf(set("se1", 80.0, 8, 1)))),
                ),
            )
            env.tracker.update(ActiveEntry("se1", 100.0, 5))

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals(100.0, model.nextWeightKg!!, 1e-9)
                assertEquals(5, model.nextReps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tracker pointing at a completed exercise falls through to next incomplete`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"), exercise("ex2", "Squat"))
            env.sessionRepo.lastPerformanceResult = emptyList()
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session(),
                    listOf(
                        entry("se1", "ex1", position = 0, targetSets = 2, sets = listOf(set("se1", 80.0, 8, 1), set("se1", 80.0, 8, 2))),
                        entry("se2", "ex2", position = 1, targetSets = 3, sets = listOf(set("se2", 90.0, 5, 1))),
                    ),
                ),
            )
            env.tracker.update(ActiveEntry("se1", 80.0, 8))

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals("Squat", model.exerciseName)
                assertEquals("se2", model.sessionExerciseId)
                assertEquals(1, model.setsDone)
                assertEquals(3, model.targetSets)
                // tracker exercise mismatch -> prefill from se2's own last set
                assertEquals(90.0, model.nextWeightKg!!, 1e-9)
                assertEquals(5, model.nextReps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tracker with blanked weight falls back to prefill values`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session(),
                    listOf(entry("se1", "ex1", targetSets = 5, sets = listOf(set("se1", 80.0, 8, 1)))),
                ),
            )
            env.tracker.update(ActiveEntry("se1", null, 12))

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals(80.0, model.nextWeightKg!!, 1e-9)
                assertEquals(8, model.nextReps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `empty tracker falls back to last performance prefill (process death)`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.lastPerformanceResult = listOf(set("old", 70.0, 12, 1))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(session(), listOf(entry("se1", "ex1", targetSets = 3))),
            )

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals(70.0, model.nextWeightKg!!, 1e-9)
                assertEquals(12, model.nextReps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `last performance is fetched once per exercise (ghost cache)`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.lastPerformanceResult = listOf(set("old", 70.0, 12, 1))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(session(), listOf(entry("se1", "ex1", targetSets = 3))),
            )

            env.producer.models("s1").test {
                awaitItem()
                // A new emission for the same exercise must reuse the cached ghost.
                env.sessionRepo.setSessionDetails(
                    "s1",
                    SessionWithDetails(
                        session(),
                        listOf(entry("se1", "ex1", targetSets = 3, sets = listOf(set("se1", 70.0, 12, 1)))),
                    ),
                )
                awaitItem()
                assertEquals(1, env.sessionRepo.lastPerformanceCalls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `weight unit is passed through`() =
        runTest {
            val env = Env(weightUnit = WeightUnit.LB)
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(
                    session(),
                    listOf(entry("se1", "ex1", sets = listOf(set("se1", 80.0, 8, 1)))),
                ),
            )

            env.producer.models("s1").test {
                assertEquals(WeightUnit.LB, awaitItem()!!.unit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `session name and null exercise for empty session`() =
        runTest {
            val env = Env()
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(session(name = "Push Day"), emptyList()),
            )

            env.producer.models("s1").test {
                val model = awaitItem()!!
                assertEquals("Push Day", model.sessionName)
                assertNull(model.exerciseName)
                assertNull(model.sessionExerciseId)
                assertNull(model.nextWeightKg)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when session is finished`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(session(), listOf(entry("se1", "ex1"))),
            )

            env.producer.models("s1").test {
                awaitItem()
                env.sessionRepo.setSessionDetails(
                    "s1",
                    SessionWithDetails(session(endedAt = now), listOf(entry("se1", "ex1"))),
                )
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `emits null when session is discarded`() =
        runTest {
            val env = Env()
            env.exerciseRepo.all.value = listOf(exercise("ex1", "Bench"))
            env.sessionRepo.setSessionDetails(
                "s1",
                SessionWithDetails(session(), listOf(entry("se1", "ex1"))),
            )

            env.producer.models("s1").test {
                awaitItem()
                env.sessionRepo.setSessionDetails("s1", null)
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
