package de.simiil.liftlog.data.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.newInMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [AnalyticsDao.observeSetsForExercise].
 *
 * Graph setup (FK order must be: exercise → session → sessionExercise → loggedSet):
 *  - Exercise "ex1"
 *  - Session A: completed, startedAt=1000
 *  - Session B: completed, startedAt=2000
 *  - Session C: in-progress (endedAt=null), startedAt=3000
 *  - Each session has one SessionExercise for "ex1"
 *  - Session A's SE has 1 live set
 *  - Session B's SE has 1 live set
 *  - Session C's SE has 1 live set (should be excluded: session not completed)
 *  - Session A's SE also has 1 soft-deleted set (should always be excluded)
 *
 * Note: sessions B (startedAt=2000) and A (startedAt=1000) are inserted in THAT order
 * (later first) so that the ORDER BY s.startedAt clause is exercised; without it the
 * natural insertion order would accidentally produce a passing test. The same trick is
 * used for within-session set order: the ordering tests insert sets with scrambled
 * positions so that the secondary ORDER BY ls.position is load-bearing too (the set
 * summary in analytics relies on it, issue #28).
 *
 * Expected: observeSetsForExercise(ex1, fromMillis=0) returns exactly 2 rows
 *           (A's live set, B's live set) in startedAt ASC order.
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var analyticsDao: AnalyticsDao
    private lateinit var sessionDao: SessionDao
    private lateinit var exerciseDao: ExerciseDao

    // -- builders --

    private fun exercise(id: String) =
        ExerciseEntity(
            id,
            "Exercise $id",
            MuscleGroup.CHEST,
            Equipment.BARBELL,
            isBuiltIn = true,
            isHidden = false,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )

    private fun session(
        id: String,
        startedAt: Long,
        endedAt: Long? = startedAt + 3600_000L,
        deleted: Long? = null,
    ) = SessionEntity(
        id = id,
        templateId = null,
        templateNameSnapshot = null,
        startedAt = startedAt,
        endedAt = endedAt,
        note = null,
        rpe = null,
        createdAt = startedAt,
        updatedAt = startedAt,
        deletedAt = deleted,
    )

    private fun sessionExercise(
        id: String,
        sessionId: String,
        exerciseId: String,
    ) = SessionExerciseEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        position = 1,
        targetSets = null,
        targetRepsMin = null,
        targetRepsMax = null,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
    )

    private fun loggedSet(
        id: String,
        sessionExerciseId: String,
        weightKg: Double = 80.0,
        reps: Int = 8,
        position: Int = 1,
        deleted: Long? = null,
    ) = LoggedSetEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        weightKg = weightKg,
        reps = reps,
        position = position,
        completedAt = 1000L,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deleted,
    )

    @Before fun setUp() {
        db = newInMemoryDb()
        analyticsDao = db.analyticsDao()
        sessionDao = db.sessionDao()
        exerciseDao = db.exerciseDao()
    }

    @After fun tearDown() = db.close()

    /**
     * Insert the full graph described in the class KDoc.
     * Sessions B (startedAt=2000) and A (startedAt=1000) are inserted in reverse chronological
     * order so that ORDER BY s.startedAt is genuinely load-bearing — without the clause the
     * result order would match insertion order only accidentally.
     * Returns the IDs of session exercises for each session.
     */
    private suspend fun insertFullGraph(): Triple<String, String, String> {
        exerciseDao.insert(exercise("ex1"))

        // Session B inserted FIRST (startedAt=2000) — reverses natural insertion order
        sessionDao.insertSession(session("sB", startedAt = 2000L, endedAt = 3000L))
        sessionDao.insertSessionExercise(sessionExercise("seB", "sB", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsB_live", "seB", weightKg = 80.0, reps = 8))

        // Session A inserted SECOND (startedAt=1000) — earlier timestamp, inserted later
        sessionDao.insertSession(session("sA", startedAt = 1000L, endedAt = 2000L))
        sessionDao.insertSessionExercise(sessionExercise("seA", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsA_live", "seA", weightKg = 60.0, reps = 10))
        // Soft-deleted set — must never appear
        sessionDao.insertLoggedSet(loggedSet("lsA_dead", "seA", weightKg = 70.0, reps = 5, deleted = 99L))

        // Session C — in-progress (endedAt=null); its sets must be excluded
        sessionDao.insertSession(session("sC", startedAt = 3000L, endedAt = null))
        sessionDao.insertSessionExercise(sessionExercise("seC", "sC", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsC_live", "seC", weightKg = 90.0, reps = 6))

        return Triple("seA", "seB", "seC")
    }

    @Test fun observeSetsForExercise_excludesInProgressAndDeleted_orderedByStartedAt() =
        runTest {
            insertFullGraph()

            analyticsDao.observeSetsForExercise("ex1", fromMillis = 0L).test {
                val rows = awaitItem()
                // Only 2 rows: sA's live set and sB's live set
                assertEquals(2, rows.size)
                // Ordered by startedAt ASC: sA (1000) before sB (2000).
                // Sessions were inserted B-then-A, so this assertion would fail without ORDER BY.
                assertEquals("sA", rows[0].sessionId)
                assertEquals("sB", rows[1].sessionId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeSetsForExercise_fromMillis_filtersEarlySession() =
        runTest {
            insertFullGraph()

            // fromMillis=1500 excludes sA (startedAt=1000 < 1500)
            analyticsDao.observeSetsForExercise("ex1", fromMillis = 1500L).test {
                val rows = awaitItem()
                assertEquals(1, rows.size)
                assertEquals("sB", rows[0].sessionId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeSetsForExercise_softDeletedSet_isExcluded() =
        runTest {
            insertFullGraph()

            // The soft-deleted set in sA (lsA_dead) must not appear
            analyticsDao.observeSetsForExercise("ex1", fromMillis = 0L).test {
                val rows = awaitItem()
                // Neither row should be the dead set
                assertTrue(rows.none { it.weightKg == 70.0 && it.reps == 5 })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeSetsForExercise_returnsCorrectWeightAndReps() =
        runTest {
            insertFullGraph()

            analyticsDao.observeSetsForExercise("ex1", fromMillis = 0L).test {
                val rows = awaitItem()
                // sA live set (comes first due to ORDER BY startedAt ASC)
                assertEquals(60.0, rows[0].weightKg, 0.001)
                assertEquals(10, rows[0].reps)
                // sB live set
                assertEquals(80.0, rows[1].weightKg, 0.001)
                assertEquals(8, rows[1].reps)
                // exerciseId is projected through the session_exercises join
                assertEquals("ex1", rows[0].exerciseId)
                assertEquals("ex1", rows[1].exerciseId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeSetsForExercise_noSetsForExercise_returnsEmpty() =
        runTest {
            exerciseDao.insert(exercise("ex2"))
            sessionDao.insertSession(session("sX", startedAt = 1000L, endedAt = 2000L))
            sessionDao.insertSessionExercise(sessionExercise("seX", "sX", "ex2"))
            sessionDao.insertLoggedSet(loggedSet("lsX", "seX"))

            // Query for a different exercise that has no rows
            exerciseDao.insert(exercise("exEmpty"))
            analyticsDao.observeSetsForExercise("exEmpty", fromMillis = 0L).test {
                val rows = awaitItem()
                assertTrue(rows.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeAllSetsSince_completedOnly_excludesInProgressAndDeleted() =
        runTest {
            insertFullGraph()
            analyticsDao.observeAllSetsSince(fromMillis = 0L).test {
                val rows = awaitItem()
                // sA live + sB live only (sC in-progress excluded; sA dead set excluded)
                assertEquals(2, rows.size)
                assertTrue(rows.none { it.weightKg == 70.0 && it.reps == 5 }) // dead
                assertTrue(rows.none { it.weightKg == 90.0 }) // sC in-progress
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeAllSetsSince_fromMillis_filtersEarlySession() =
        runTest {
            insertFullGraph()
            analyticsDao.observeAllSetsSince(fromMillis = 1500L).test {
                val rows = awaitItem()
                assertEquals(1, rows.size)
                assertEquals("sB", rows[0].sessionId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeAllSetsSince_attributesSetsToTheirExercises() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            exerciseDao.insert(exercise("ex2"))
            sessionDao.insertSession(session("s1", startedAt = 1000L, endedAt = 2000L))
            sessionDao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))
            sessionDao.insertSessionExercise(sessionExercise("se2", "s1", "ex2"))
            sessionDao.insertLoggedSet(loggedSet("ls1", "se1", weightKg = 60.0, reps = 10))
            sessionDao.insertLoggedSet(loggedSet("ls2", "se2", weightKg = 80.0, reps = 8))

            analyticsDao.observeAllSetsSince(fromMillis = 0L).test {
                val rows = awaitItem()
                assertEquals(2, rows.size)
                assertEquals("ex1", rows.first { it.weightKg == 60.0 }.exerciseId)
                assertEquals("ex2", rows.first { it.weightKg == 80.0 }.exerciseId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * One completed session with 3 sets whose positions (1, 2, 3) are inserted in
     * scrambled order (2, 3, 1) — reps identify each set. Shared by the two
     * within-session ordering tests; separate from [insertFullGraph] because the
     * existing tests assert its exact row counts.
     */
    private suspend fun insertScrambledPositionGraph() {
        exerciseDao.insert(exercise("ex1"))
        sessionDao.insertSession(session("s1", startedAt = 1000L, endedAt = 2000L))
        sessionDao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("ls2", "se1", weightKg = 60.0, reps = 9, position = 2))
        sessionDao.insertLoggedSet(loggedSet("ls3", "se1", weightKg = 60.0, reps = 5, position = 3))
        sessionDao.insertLoggedSet(loggedSet("ls1", "se1", weightKg = 55.0, reps = 10, position = 1))
    }

    @Test fun observeSetsForExercise_ordersWithinSessionByPosition() =
        runTest {
            insertScrambledPositionGraph()
            analyticsDao.observeSetsForExercise("ex1", fromMillis = 0L).test {
                val rows = awaitItem()
                assertEquals(listOf(10, 9, 5), rows.map { it.reps })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeAllSetsSince_ordersWithinSessionByPosition() =
        runTest {
            insertScrambledPositionGraph()
            analyticsDao.observeAllSetsSince(fromMillis = 0L).test {
                val rows = awaitItem()
                assertEquals(listOf(10, 9, 5), rows.map { it.reps })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeTrainedExercises_groupsAndTakesMaxStartedAt() =
        runTest {
            insertFullGraph()
            analyticsDao.observeTrainedExercises().test {
                val rows = awaitItem()
                assertEquals(1, rows.size)
                assertEquals("ex1", rows[0].exerciseId)
                // MAX over completed sessions with live sets: sB (2000) > sA (1000); sC excluded
                assertEquals(2000L, rows[0].lastTrainedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
