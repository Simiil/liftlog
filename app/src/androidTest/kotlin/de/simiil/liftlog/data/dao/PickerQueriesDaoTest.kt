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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the picker projection queries:
 *  - [ExerciseDao.observeRecentlyUsedExerciseIds]
 *  - [SessionDao.observeSetCountsBySession]
 *
 * Graph overview:
 *  - Exercise "ex1", "ex2"
 *  - Session A: finished, two session-exercises (ex1 @ completedAt=2000, ex2 @ completedAt=1000)
 *  - Session B: finished, one session-exercise (ex2 @ completedAt=3000)
 *  → ex2 was most recently used at t=3000 (session B); ex1 at t=2000 (session A)
 *  → order must be: ex2 first, ex1 second (ex2 is alphabetically later, making ORDER BY load-bearing)
 *
 * Soft-delete discrimination cases are tested in separate methods.
 */
@RunWith(AndroidJUnit4::class)
class PickerQueriesDaoTest {

    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var sessionDao: SessionDao

    // -- builders --

    private fun exercise(id: String) = ExerciseEntity(
        id = id,
        name = "Exercise $id",
        muscleGroup = MuscleGroup.BACK,
        equipment = Equipment.BARBELL,
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
        createdAt = startedAt,
        updatedAt = startedAt,
        deletedAt = deleted,
    )

    private fun sessionExercise(
        id: String,
        sessionId: String,
        exerciseId: String,
        deleted: Long? = null,
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
        deletedAt = deleted,
    )

    private fun loggedSet(
        id: String,
        sessionExerciseId: String,
        completedAt: Long = 1000L,
        deleted: Long? = null,
    ) = LoggedSetEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        weightKg = 100.0,
        reps = 5,
        position = 1,
        completedAt = completedAt,
        rpe = null,
        note = null,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deleted,
    )

    @Before fun setUp() {
        db = newInMemoryDb()
        exerciseDao = db.exerciseDao()
        sessionDao = db.sessionDao()
    }

    @After fun tearDown() = db.close()

    // =========================================================================
    // observeRecentlyUsedExerciseIds
    // =========================================================================

    /**
     * Base case: two exercises used across two sessions.
     * ex2 last used at t=3000, ex1 last used at t=2000 → ex2 must come first.
     * Crucially, ex2 is alphabetically later than ex1, so a dropped ORDER BY that
     * fell back to key/insertion order would return [ex1, ex2] — the wrong answer.
     * Sessions are also inserted in reverse order (B before A) to ensure ORDER BY
     * on MAX(completedAt) is genuinely load-bearing.
     */
    @Test fun recentlyUsed_orderedByMostRecentCompletedAtDesc() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        // Session B inserted first (most recent for ex2)
        sessionDao.insertSession(session("sB", startedAt = 2000L, endedAt = 3600L))
        val seB_ex2 = sessionExercise("seB_ex2", "sB", "ex2")
        sessionDao.insertSessionExercise(seB_ex2)
        sessionDao.insertLoggedSet(loggedSet("lsB1", "seB_ex2", completedAt = 3000L))

        // Session A inserted second; ex1 last used at t=2000, ex2 at t=1000
        sessionDao.insertSession(session("sA", startedAt = 500L, endedAt = 2500L))
        val seA_ex1 = sessionExercise("seA_ex1", "sA", "ex1")
        val seA_ex2 = sessionExercise("seA_ex2", "sA", "ex2")
        sessionDao.insertSessionExercise(seA_ex1)
        sessionDao.insertSessionExercise(seA_ex2)
        sessionDao.insertLoggedSet(loggedSet("lsA1", "seA_ex1", completedAt = 2000L))
        sessionDao.insertLoggedSet(loggedSet("lsA2", "seA_ex2", completedAt = 1000L))

        exerciseDao.observeRecentlyUsedExerciseIds().test {
            val rows = awaitItem()
            // One row per exercise
            assertEquals(2, rows.size)
            // ex2 most recent (t=3000) → first; ex1 (t=2000) → second
            // (ex2 is alphabetically later, so a missing ORDER BY would give the wrong order)
            assertEquals("ex2", rows[0].exerciseId)
            assertEquals("ex1", rows[1].exerciseId)
            // lastUsed timestamps must match the MAX completedAt for each exercise
            assertEquals(3000L, rows[0].lastUsed)
            assertEquals(2000L, rows[1].lastUsed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * An exercise whose only logged set is soft-deleted must NOT appear.
     * This test would fail if the `ls.deletedAt IS NULL` join condition were removed.
     */
    @Test fun recentlyUsed_exerciseWithOnlySoftDeletedSet_doesNotAppear() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex2", "sA", "ex2"))
        // ex1: live set
        sessionDao.insertLoggedSet(loggedSet("ls_live", "seA_ex1", completedAt = 1000L))
        // ex2: only a soft-deleted set
        sessionDao.insertLoggedSet(loggedSet("ls_dead", "seA_ex2", completedAt = 2000L, deleted = 99L))

        exerciseDao.observeRecentlyUsedExerciseIds().test {
            val rows = awaitItem()
            // Only ex1 appears; ex2's only set is deleted
            assertEquals(1, rows.size)
            assertEquals("ex1", rows[0].exerciseId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Sets under a soft-deleted session_exercise must not count.
     * This test would fail if the `se.deletedAt IS NULL` WHERE condition were removed.
     */
    @Test fun recentlyUsed_setsUnderSoftDeletedSessionExercise_doNotCount() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        sessionDao.insertSession(session("sA", startedAt = 1000L))
        // ex1: live session_exercise with live set → appears
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("ls_live", "seA_ex1", completedAt = 1000L))
        // ex2: soft-deleted session_exercise with live set → must not appear
        sessionDao.insertSessionExercise(
            sessionExercise("seA_ex2", "sA", "ex2", deleted = 99L)
        )
        sessionDao.insertLoggedSet(loggedSet("ls_under_dead_se", "seA_ex2", completedAt = 2000L))

        exerciseDao.observeRecentlyUsedExerciseIds().test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("ex1", rows[0].exerciseId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Sets whose session is soft-deleted must not count.
     * This test would fail if the `s.deletedAt IS NULL` join condition were removed.
     */
    @Test fun recentlyUsed_setsUnderSoftDeletedSession_doNotCount() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        // sA: live session → ex1 appears
        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("ls_live", "seA_ex1", completedAt = 1000L))

        // sB: soft-deleted session → ex2 must not appear
        sessionDao.insertSession(session("sB", startedAt = 2000L, deleted = 99L))
        sessionDao.insertSessionExercise(sessionExercise("seB_ex2", "sB", "ex2"))
        sessionDao.insertLoggedSet(loggedSet("ls_dead_sess", "seB_ex2", completedAt = 3000L))

        exerciseDao.observeRecentlyUsedExerciseIds().test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("ex1", rows[0].exerciseId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // =========================================================================
    // observeSetCountsBySession
    // =========================================================================

    /**
     * A session with N live sets across exercises returns setCount == N.
     */
    @Test fun setCountsBySession_countsAllLiveSetsAcrossExercises() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex2", "sA", "ex2"))
        // 2 sets for ex1 + 1 set for ex2 = 3 total live sets
        sessionDao.insertLoggedSet(loggedSet("ls1", "seA_ex1", completedAt = 1000L))
        sessionDao.insertLoggedSet(loggedSet("ls2", "seA_ex1", completedAt = 1100L))
        sessionDao.insertLoggedSet(loggedSet("ls3", "seA_ex2", completedAt = 1200L))

        sessionDao.observeSetCountsBySession().test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals("sA", rows[0].sessionId)
            assertEquals(3, rows[0].setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Soft-deleted sets are not counted.
     * This test would fail if the `ls.deletedAt IS NULL` join condition were removed.
     */
    @Test fun setCountsBySession_softDeletedSetsNotCounted() = runTest {
        exerciseDao.insert(exercise("ex1"))

        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        // 1 live set + 1 soft-deleted set; count must be 1
        sessionDao.insertLoggedSet(loggedSet("ls_live", "seA_ex1", completedAt = 1000L))
        sessionDao.insertLoggedSet(loggedSet("ls_dead", "seA_ex1", completedAt = 1100L, deleted = 99L))

        sessionDao.observeSetCountsBySession().test {
            val rows = awaitItem()
            assertEquals(1, rows.size)
            assertEquals(1, rows[0].setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A session with zero live sets (all sets soft-deleted, or no sets at all) must be
     * absent from the result — the GROUP BY / JOIN naturally excludes it.
     * This test would fail if we accidentally included rows with setCount==0.
     */
    @Test fun setCountsBySession_sessionWithZeroLiveSets_isAbsent() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        // sA: has 2 live sets → present
        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsA1", "seA_ex1", completedAt = 1000L))
        sessionDao.insertLoggedSet(loggedSet("lsA2", "seA_ex1", completedAt = 1100L))

        // sB: all sets soft-deleted → absent
        sessionDao.insertSession(session("sB", startedAt = 2000L))
        sessionDao.insertSessionExercise(sessionExercise("seB_ex2", "sB", "ex2"))
        sessionDao.insertLoggedSet(loggedSet("lsB1", "seB_ex2", completedAt = 2000L, deleted = 99L))

        sessionDao.observeSetCountsBySession().test {
            val rows = awaitItem()
            // Only sA appears; sB has no live sets
            assertEquals(1, rows.size)
            assertEquals("sA", rows[0].sessionId)
            assertEquals(2, rows[0].setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Multiple sessions each with distinct counts appear as separate rows.
     */
    @Test fun setCountsBySession_multipleSessionsReturnDistinctRows() = runTest {
        exerciseDao.insert(exercise("ex1"))

        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsA1", "seA", completedAt = 1000L))

        sessionDao.insertSession(session("sB", startedAt = 2000L))
        sessionDao.insertSessionExercise(sessionExercise("seB", "sB", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsB1", "seB", completedAt = 2000L))
        sessionDao.insertLoggedSet(loggedSet("lsB2", "seB", completedAt = 2100L))
        sessionDao.insertLoggedSet(loggedSet("lsB3", "seB", completedAt = 2200L))

        sessionDao.observeSetCountsBySession().test {
            val rows = awaitItem()
            assertEquals(2, rows.size)
            val bySession = rows.associateBy { it.sessionId }
            assertEquals(1, bySession["sA"]!!.setCount)
            assertEquals(3, bySession["sB"]!!.setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Sets under a soft-deleted session_exercise must not count toward any session.
     * This test would fail if the `se.deletedAt IS NULL` WHERE clause were removed
     * from [SessionDao.observeSetCountsBySession].
     */
    @Test fun setCountsBySession_setsUnderSoftDeletedSessionExercise_doNotCount() = runTest {
        exerciseDao.insert(exercise("ex1"))
        exerciseDao.insert(exercise("ex2"))

        sessionDao.insertSession(session("sA", startedAt = 1000L))
        // ex1: live session_exercise with a live set → counts
        sessionDao.insertSessionExercise(sessionExercise("seA_ex1", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("ls_live", "seA_ex1", completedAt = 1000L))
        // ex2: soft-deleted session_exercise with a live set → must NOT count
        sessionDao.insertSessionExercise(
            sessionExercise("seA_ex2", "sA", "ex2", deleted = 99L)
        )
        sessionDao.insertLoggedSet(loggedSet("ls_under_dead_se", "seA_ex2", completedAt = 2000L))

        sessionDao.observeSetCountsBySession().test {
            val rows = awaitItem()
            // sA appears, but only the live set under the live session_exercise is counted
            assertEquals(1, rows.size)
            assertEquals("sA", rows[0].sessionId)
            assertEquals(1, rows[0].setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A soft-deleted session must be absent from [SessionDao.observeSetCountsBySession].
     * This test would fail if the `s.deletedAt IS NULL` join condition were removed
     * from the query.
     */
    @Test fun setCountsBySession_softDeletedSession_isAbsent() = runTest {
        exerciseDao.insert(exercise("ex1"))

        // sA: live session with live sets → present
        sessionDao.insertSession(session("sA", startedAt = 1000L))
        sessionDao.insertSessionExercise(sessionExercise("seA", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsA1", "seA", completedAt = 1000L))

        // sB: soft-deleted session with live sets and live session_exercise → must be absent
        sessionDao.insertSession(session("sB", startedAt = 2000L, deleted = 99L))
        sessionDao.insertSessionExercise(sessionExercise("seB", "sB", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsB1", "seB", completedAt = 2000L))

        sessionDao.observeSetCountsBySession().test {
            val rows = awaitItem()
            // Only sA appears; sB is soft-deleted
            assertEquals(1, rows.size)
            assertEquals("sA", rows[0].sessionId)
            assertEquals(1, rows[0].setCount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
