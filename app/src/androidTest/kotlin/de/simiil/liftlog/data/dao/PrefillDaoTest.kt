package de.simiil.liftlog.data.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * Tests for [PrefillDao].
 *
 * Graph:
 *  - Exercise "ex1"
 *  - Session A: completed, startedAt=1000 — 2 sets (pos 1 and 2) for "ex1"
 *  - Session B: completed, startedAt=2000 — 3 sets (pos 1, 2, 3) for "ex1" + 1 soft-deleted set
 *  - Session C: in-progress (endedAt=null), startedAt=3000 — 1 set for "ex1"
 *
 * Note: session B's 3 live sets are inserted in OUT-OF-POSITION order (pos 3, then 1, then 2)
 * so that the ORDER BY ls.position clause is genuinely tested; without it the natural insertion
 * order would not match position order and the assertions would fail.
 *
 * Expected:
 *  - lastCompletedSessionIdFor("ex1") == "sB" (most recent completed, ignoring in-progress C)
 *  - setsForExerciseInSession("sB", "ex1") returns 3 sets in position order 1,2,3, excluding deleted set
 */
@RunWith(AndroidJUnit4::class)
class PrefillDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var prefillDao: PrefillDao
    private lateinit var sessionDao: SessionDao
    private lateinit var exerciseDao: ExerciseDao

    // -- builders --

    private fun exercise(id: String) =
        ExerciseEntity(id, "Exercise $id", MuscleGroup.CHEST, Equipment.BARBELL,
            isBuiltIn = true, isHidden = false, createdAt = 1L, updatedAt = 1L, deletedAt = null)

    private fun session(
        id: String,
        startedAt: Long,
        endedAt: Long? = startedAt + 3600_000L,
        deleted: Long? = null
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

    private fun sessionExercise(id: String, sessionId: String, exerciseId: String) =
        SessionExerciseEntity(
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
        position: Int,
        deleted: Long? = null,
    ) = LoggedSetEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        weightKg = 100.0,
        reps = 5,
        position = position,
        completedAt = 1000L,
        rpe = null,
        note = null,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = deleted,
    )

    @Before fun setUp() {
        db = newInMemoryDb()
        prefillDao = db.prefillDao()
        sessionDao = db.sessionDao()
        exerciseDao = db.exerciseDao()
    }

    @After fun tearDown() = db.close()

    /** Insert the full graph described in the class KDoc.
     *  Session B's live sets are inserted in order: position 3, then 1, then 2
     *  (out of ascending order) so that ORDER BY ls.position is genuinely exercised. */
    private suspend fun insertFullGraph() {
        exerciseDao.insert(exercise("ex1"))

        // Session A — completed, startedAt=1000, 2 sets
        sessionDao.insertSession(session("sA", startedAt = 1000L, endedAt = 2000L))
        sessionDao.insertSessionExercise(sessionExercise("seA", "sA", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsA1", "seA", position = 1))
        sessionDao.insertLoggedSet(loggedSet("lsA2", "seA", position = 2))

        // Session B — completed, startedAt=2000, 3 live sets + 1 deleted set.
        // Live sets inserted OUT OF position order (3, then 1, then 2) to make ORDER BY load-bearing.
        sessionDao.insertSession(session("sB", startedAt = 2000L, endedAt = 3000L))
        sessionDao.insertSessionExercise(sessionExercise("seB", "sB", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsB3", "seB", position = 3))   // inserted first
        sessionDao.insertLoggedSet(loggedSet("lsB1", "seB", position = 1))   // inserted second
        sessionDao.insertLoggedSet(loggedSet("lsB2", "seB", position = 2))   // inserted third
        // Soft-deleted set in session B — must be excluded from setsForExerciseInSession
        sessionDao.insertLoggedSet(loggedSet("lsB_dead", "seB", position = 4, deleted = 99L))

        // Session C — in-progress (endedAt=null), must not be returned by lastCompletedSessionIdFor
        sessionDao.insertSession(session("sC", startedAt = 3000L, endedAt = null))
        sessionDao.insertSessionExercise(sessionExercise("seC", "sC", "ex1"))
        sessionDao.insertLoggedSet(loggedSet("lsC1", "seC", position = 1))
    }

    // -------------------------------------------------------------------------
    // lastCompletedSessionIdFor
    // -------------------------------------------------------------------------

    @Test fun lastCompletedSessionIdFor_returnsMostRecentCompletedSession() = runTest {
        insertFullGraph()
        val id = prefillDao.lastCompletedSessionIdFor("ex1")
        assertEquals("sB", id)
    }

    @Test fun lastCompletedSessionIdFor_ignoresInProgressSession() = runTest {
        insertFullGraph()
        // Session C (in-progress, most recent) must be ignored; sB is still the answer
        val id = prefillDao.lastCompletedSessionIdFor("ex1")
        assertNotEquals("sC", id)
        assertEquals("sB", id)
    }

    @Test fun lastCompletedSessionIdFor_returnsNullWhenNoCompletedSessions() = runTest {
        exerciseDao.insert(exercise("ex2"))
        sessionDao.insertSession(session("sX", startedAt = 1000L, endedAt = null))
        sessionDao.insertSessionExercise(sessionExercise("seX", "sX", "ex2"))

        val id = prefillDao.lastCompletedSessionIdFor("ex2")
        assertNull("should return null when no completed session exists for exercise", id)
    }

    @Test fun lastCompletedSessionIdFor_ignoresTombstonedSessions() = runTest {
        exerciseDao.insert(exercise("ex3"))
        sessionDao.insertSession(session("sD", startedAt = 1000L, endedAt = 2000L, deleted = 99L))
        sessionDao.insertSessionExercise(sessionExercise("seD", "sD", "ex3"))

        val id = prefillDao.lastCompletedSessionIdFor("ex3")
        assertNull("tombstoned session should not be returned", id)
    }

    // -------------------------------------------------------------------------
    // setsForExerciseInSession
    // -------------------------------------------------------------------------

    @Test fun setsForExerciseInSession_returnsLiveSetsInPositionOrder() = runTest {
        insertFullGraph()
        val sets = prefillDao.setsForExerciseInSession("sB", "ex1")
        // 3 live sets; the soft-deleted one (lsB_dead) is excluded.
        // Sets were inserted out of order (pos 3, 1, 2) so this assertion is genuinely testing ORDER BY.
        assertEquals(3, sets.size)
        // Ordered by position ASC: 1, 2, 3
        assertEquals(listOf(1, 2, 3), sets.map { it.position })
        assertEquals(listOf("lsB1", "lsB2", "lsB3"), sets.map { it.id })
    }

    @Test fun setsForExerciseInSession_excludesSoftDeletedSets() = runTest {
        insertFullGraph()
        val sets = prefillDao.setsForExerciseInSession("sB", "ex1")
        assertTrue("deleted set should not appear", sets.none { it.id == "lsB_dead" })
    }

    @Test fun setsForExerciseInSession_returnsCorrectCountForSessionA() = runTest {
        insertFullGraph()
        val sets = prefillDao.setsForExerciseInSession("sA", "ex1")
        assertEquals(2, sets.size)
        assertEquals(listOf(1, 2), sets.map { it.position })
    }

    @Test fun setsForExerciseInSession_returnsEmptyForUnrelatedExercise() = runTest {
        insertFullGraph()
        exerciseDao.insert(exercise("ex_other"))
        val sets = prefillDao.setsForExerciseInSession("sB", "ex_other")
        assertTrue(sets.isEmpty())
    }
}
