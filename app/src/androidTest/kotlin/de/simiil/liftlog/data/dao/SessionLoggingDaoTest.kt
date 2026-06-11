package de.simiil.liftlog.data.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.data.entity.ExerciseEntity
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.testing.newInMemoryDb
import de.simiil.liftlog.testing.tombstoneOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Timestamp used for soft-delete calls; differs from row-creation updatedAt=1L so that
 *  an updatedAt==NOW assertion genuinely proves the write occurred. */
private const val NOW = 7000L

@RunWith(AndroidJUnit4::class)
class SessionLoggingDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var dao: SessionDao
    private lateinit var exerciseDao: ExerciseDao

    // -- builders --

    private fun session(id: String) =
        SessionEntity(
            id = id,
            templateId = null,
            templateNameSnapshot = null,
            startedAt = 1000L,
            endedAt = null,
            note = null,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )

    private fun sessionExercise(
        id: String,
        sessionId: String,
        exerciseId: String,
        position: Int = 1,
        deleted: Long? = null,
    ) = SessionExerciseEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        position = position,
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
        position: Int = 1,
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

    private fun exercise(id: String) =
        ExerciseEntity(
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

    @Before fun setUp() {
        db = newInMemoryDb()
        dao = db.sessionDao()
        exerciseDao = db.exerciseDao()
    }

    @After fun tearDown() = db.close()

    // -------------------------------------------------------------------------
    // maxSetPosition
    // -------------------------------------------------------------------------

    @Test fun maxSetPosition_returnsNullWhenNoSetsExist() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))

            val result = dao.maxSetPosition("se1")
            assertNull("maxSetPosition should be null when no sets exist", result)
        }

    @Test fun maxSetPosition_returnsMaxNotInsertionOrder() =
        runTest {
            // Insert out of order: 2, 3, 1 — MAX must be 3, not the last inserted value.
            // Insertion order ends with 1, so a naive "last inserted" approach would return 1;
            // MAX must return 3.
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))
            dao.insertLoggedSet(loggedSet("ls_p2", "se1", position = 2))
            dao.insertLoggedSet(loggedSet("ls_p3", "se1", position = 3))
            dao.insertLoggedSet(loggedSet("ls_p1", "se1", position = 1))

            val result = dao.maxSetPosition("se1")
            assertEquals("MAX(position) should be 3 regardless of insertion order", 3, result)
        }

    // -------------------------------------------------------------------------
    // maxExercisePosition
    // -------------------------------------------------------------------------

    @Test fun maxExercisePosition_returnsNullWhenNoExercisesExist() =
        runTest {
            dao.insertSession(session("s1"))

            val result = dao.maxExercisePosition("s1")
            assertNull("maxExercisePosition should be null when no exercises exist", result)
        }

    @Test fun maxExercisePosition_returnsMaxNotInsertionOrder() =
        runTest {
            // Insert out of order: 2, 3, 1 — MAX must be 3; last inserted is 1 so insertion-order
            // would give 1, but MAX must give 3.
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSessionExercise(sessionExercise("se_p2", "s1", "ex1", position = 2))
            dao.insertSessionExercise(sessionExercise("se_p3", "s1", "ex1", position = 3))
            dao.insertSessionExercise(sessionExercise("se_p1", "s1", "ex1", position = 1))

            val result = dao.maxExercisePosition("s1")
            assertEquals("MAX(position) should be 3 regardless of insertion order", 3, result)
        }

    // -------------------------------------------------------------------------
    // maxSetPosition ignores soft-deleted sets
    // -------------------------------------------------------------------------

    @Test fun maxSetPosition_ignoresSoftDeletedSets() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))
            dao.insertLoggedSet(loggedSet("ls1", "se1", position = 1))
            dao.insertLoggedSet(loggedSet("ls2", "se1", position = 2))
            dao.insertLoggedSet(loggedSet("ls3", "se1", position = 3))

            // Soft-delete the set with the highest position
            dao.softDeleteLoggedSet("ls3", NOW)

            val result = dao.maxSetPosition("se1")
            assertEquals("maxSetPosition should skip soft-deleted sets; max of live sets is 2", 2, result)
        }

    // -------------------------------------------------------------------------
    // softDeleteLoggedSet
    // -------------------------------------------------------------------------

    @Test fun softDeleteLoggedSet_tombstonesBothTimestampsAndLeavesSiblingUntouched() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))
            dao.insertLoggedSet(loggedSet("ls1", "se1", position = 1))
            dao.insertLoggedSet(loggedSet("ls2", "se1", position = 2)) // sibling — must NOT be touched

            dao.softDeleteLoggedSet("ls1", NOW)

            // Target row: deletedAt and updatedAt must both be NOW
            val tombstone = db.tombstoneOf("logged_sets", "ls1")
            assertNotNull("row ls1 must exist", tombstone)
            assertEquals("deletedAt must be NOW", NOW, tombstone!!.first)
            assertEquals("updatedAt must be NOW", NOW, tombstone.second)

            // Sibling row: deletedAt must still be null (live)
            val sibling = db.tombstoneOf("logged_sets", "ls2")
            assertNotNull("row ls2 must exist", sibling)
            assertNull("sibling ls2 deletedAt must remain null", sibling!!.first)
        }

    // -------------------------------------------------------------------------
    // per-exercise cascade: softDeleteLoggedSetsForSessionExercise + softDeleteSessionExercise
    // -------------------------------------------------------------------------

    @Test fun softDeleteLoggedSetsForSessionExercise_cascadesOnlyToTargetExercise() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))

            // Exercise A and its set
            dao.insertSessionExercise(sessionExercise("seA", "s1", "ex1", position = 1))
            dao.insertLoggedSet(loggedSet("a1", "seA", position = 1))

            // Exercise B and its set — sibling, must NOT be touched
            dao.insertSessionExercise(sessionExercise("seB", "s1", "ex1", position = 2))
            dao.insertLoggedSet(loggedSet("b1", "seB", position = 1))

            // Cascade soft-delete sets for exercise A, then soft-delete exercise A itself
            dao.softDeleteLoggedSetsForSessionExercise("seA", NOW)
            dao.softDeleteSessionExercise("seA", NOW)

            // a1 must be tombstoned
            val a1Tombstone = db.tombstoneOf("logged_sets", "a1")
            assertNotNull("row a1 must exist", a1Tombstone)
            assertEquals("a1 deletedAt must be NOW", NOW, a1Tombstone!!.first)
            assertEquals("a1 updatedAt must be NOW", NOW, a1Tombstone.second)

            // b1 must be untouched (sibling exercise B's set)
            val b1Tombstone = db.tombstoneOf("logged_sets", "b1")
            assertNotNull("row b1 must exist", b1Tombstone)
            assertNull("b1 deletedAt must remain null (sibling untouched)", b1Tombstone!!.first)

            // seA must be tombstoned
            val seATombstone = db.tombstoneOf("session_exercises", "seA")
            assertNotNull("row seA must exist", seATombstone)
            assertEquals("seA deletedAt must be NOW", NOW, seATombstone!!.first)
            assertEquals("seA updatedAt must be NOW", NOW, seATombstone.second)

            // seB must be untouched
            val seBTombstone = db.tombstoneOf("session_exercises", "seB")
            assertNotNull("row seB must exist", seBTombstone)
            assertNull("seB deletedAt must remain null (sibling untouched)", seBTombstone!!.first)
        }
}
