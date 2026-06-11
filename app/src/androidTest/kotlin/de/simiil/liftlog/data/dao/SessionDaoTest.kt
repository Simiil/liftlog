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
class SessionDaoTest {
    private lateinit var db: de.simiil.liftlog.data.db.AppDatabase
    private lateinit var dao: SessionDao
    private lateinit var exerciseDao: ExerciseDao

    // -- builders --

    private fun session(
        id: String,
        startedAt: Long = 1000L,
        endedAt: Long? = null,
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
            id,
            "Exercise $id",
            MuscleGroup.BACK,
            Equipment.BARBELL,
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
    // observeActiveSession + activeSessionId
    // -------------------------------------------------------------------------

    @Test fun observeActiveSession_emitsLiveSession_thenNullWhenEnded() =
        runTest {
            val s = session("s1", startedAt = 1000L, endedAt = null)
            dao.insertSession(s)

            dao.observeActiveSession().test {
                val active = awaitItem()
                assertNotNull(active)
                assertEquals("s1", active!!.id)

                // End the session
                dao.updateSession(s.copy(endedAt = 2000L, updatedAt = 2000L))
                val afterEnd = awaitItem()
                assertNull("active session should be null after endedAt is set", afterEnd)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun activeSessionId_returnsIdWhenActive_nullWhenNone() =
        runTest {
            assertNull(dao.activeSessionId())
            dao.insertSession(session("s1"))
            assertEquals("s1", dao.activeSessionId())
        }

    @Test fun activeSessionId_excludesDeletedSessions() =
        runTest {
            dao.insertSession(session("s1", deleted = 99L))
            assertNull("deleted session should not be active", dao.activeSessionId())
        }

    @Test fun activeSessionId_excludesCompletedSessions() =
        runTest {
            dao.insertSession(session("s1", endedAt = 2000L))
            assertNull("completed session should not be returned as active", dao.activeSessionId())
        }

    // -------------------------------------------------------------------------
    // observeHistory
    // -------------------------------------------------------------------------

    @Test fun observeHistory_excludesTombstones_orderedByStartedAtDesc() =
        runTest {
            dao.insertSession(session("s1", startedAt = 1000L, endedAt = 2000L))
            dao.insertSession(session("s2", startedAt = 3000L, endedAt = 4000L))
            dao.insertSession(session("s3", startedAt = 500L, deleted = 99L))

            dao.observeHistory().test {
                val items = awaitItem()
                assertEquals(2, items.size)
                // DESC order: s2 (3000) first, s1 (1000) second
                assertEquals(listOf("s2", "s1"), items.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeHistory_includesInProgressSessions() =
        runTest {
            // observeHistory does NOT filter endedAt; it shows all non-deleted sessions
            dao.insertSession(session("s1", startedAt = 1000L, endedAt = null))
            dao.observeHistory().test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals("s1", items[0].id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // observeSessionWithDetails
    // -------------------------------------------------------------------------

    @Test fun observeSessionWithDetails_returnsGraphShapeWithExercisesAndSets() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1", position = 1))
            dao.insertSessionExercise(sessionExercise("se2", "s1", "ex1", position = 2))
            dao.insertLoggedSet(loggedSet("ls1", "se1", position = 1))
            dao.insertLoggedSet(loggedSet("ls2", "se1", position = 2))
            dao.insertLoggedSet(loggedSet("ls3", "se2", position = 1))

            dao.observeSessionWithDetails("s1").test {
                val details = awaitItem()
                assertNotNull(details)
                assertEquals("s1", details!!.session.id)
                // @Relation loads ALL children (no deletedAt filter) — verify graph shape
                assertEquals(2, details.exercises.size)
                // The session exercise with id "se1" has 2 sets; "se2" has 1 set
                val se1Details = details.exercises.first { it.sessionExercise.id == "se1" }
                assertEquals(2, se1Details.sets.size)
                val se2Details = details.exercises.first { it.sessionExercise.id == "se2" }
                assertEquals(1, se2Details.sets.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun observeSessionWithDetails_returnsNullForTombstonedSession() =
        runTest {
            dao.insertSession(session("s1", deleted = 99L))
            dao.observeSessionWithDetails("s1").test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // soft-delete cascade: softDeleteLoggedSetsForSession
    // -------------------------------------------------------------------------

    @Test fun softDeleteLoggedSetsForSession_tombstonesAllSetsForSession() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSession(session("s2"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1"))
            dao.insertSessionExercise(sessionExercise("se2", "s2", "ex1"))
            // Inserted with updatedAt=1L (default builder); NOW=7000L differs — so updatedAt==NOW proves the write
            dao.insertLoggedSet(loggedSet("ls1", "se1"))
            dao.insertLoggedSet(loggedSet("ls2", "se1"))
            // Set belonging to another session — must NOT be touched
            dao.insertLoggedSet(loggedSet("ls3", "se2"))

            dao.softDeleteLoggedSetsForSession("s1", NOW)

            // s1's sets must be tombstoned with both timestamps set to NOW
            assertEquals(NOW to NOW, db.tombstoneOf("logged_sets", "ls1"))
            assertEquals(NOW to NOW, db.tombstoneOf("logged_sets", "ls2"))

            // s2's set must NOT be touched — deletedAt should still be null
            assertEquals(null, db.tombstoneOf("logged_sets", "ls3")!!.first)
        }

    // -------------------------------------------------------------------------
    // soft-delete cascade: softDeleteSessionExercisesFor
    // -------------------------------------------------------------------------

    @Test fun softDeleteSessionExercisesFor_tombstonesOnlySessionsExercises() =
        runTest {
            exerciseDao.insert(exercise("ex1"))
            dao.insertSession(session("s1"))
            dao.insertSession(session("s2"))
            dao.insertSessionExercise(sessionExercise("se1", "s1", "ex1", position = 1))
            dao.insertSessionExercise(sessionExercise("se2", "s1", "ex1", position = 2))
            dao.insertSessionExercise(sessionExercise("se3", "s2", "ex1", position = 1))

            dao.softDeleteSessionExercisesFor("s1", NOW)

            // s1's exercises must be tombstoned with both timestamps set to NOW
            assertEquals(NOW to NOW, db.tombstoneOf("session_exercises", "se1"))
            assertEquals(NOW to NOW, db.tombstoneOf("session_exercises", "se2"))

            // s2's exercise must NOT be touched — deletedAt should still be null
            assertEquals(null, db.tombstoneOf("session_exercises", "se3")!!.first)

            // Double-check via @Relation that s2's exercise is still live
            dao.observeSessionWithDetails("s2").test {
                val details = awaitItem()
                assertNotNull(details)
                val liveExercises = details!!.exercises.filter { it.sessionExercise.deletedAt == null }
                assertEquals(1, liveExercises.size)
                assertEquals("se3", liveExercises[0].sessionExercise.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // soft-delete: softDeleteSession
    // -------------------------------------------------------------------------

    @Test fun softDeleteSession_makesSessionInvisibleToObservers() =
        runTest {
            dao.insertSession(session("s1", startedAt = 1000L, endedAt = 2000L))
            dao.insertSession(session("s2", startedAt = 3000L, endedAt = 4000L))

            dao.softDeleteSession("s1", NOW)

            // Tombstoned session must have both timestamps set to NOW
            assertEquals(NOW to NOW, db.tombstoneOf("sessions", "s1"))

            // Sibling session s2 must NOT be touched — deletedAt should still be null
            assertEquals(null, db.tombstoneOf("sessions", "s2")!!.first)

            // observeHistory excludes deleted sessions
            dao.observeHistory().test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals("s2", items[0].id)
                cancelAndIgnoreRemainingEvents()
            }

            // findSession returns null for tombstoned session
            val found = dao.findSession("s1")
            assertNull("tombstoned session should not be returned by findSession", found)
        }
}
