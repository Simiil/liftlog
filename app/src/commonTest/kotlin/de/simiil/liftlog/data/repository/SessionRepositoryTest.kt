package de.simiil.liftlog.data.repository

import de.simiil.liftlog.data.dao.SessionSetCount
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import de.simiil.liftlog.testing.FixedClock
import de.simiil.liftlog.testing.fakes.FakePlanDao
import de.simiil.liftlog.testing.fakes.FakePrefillDao
import de.simiil.liftlog.testing.fakes.FakeSessionDao
import de.simiil.liftlog.testing.fakes.FakeTransactor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SessionRepositoryTest {
    private val clockMillis = 5000L
    private val clock = FixedClock(Instant.fromEpochMilliseconds(clockMillis))
    private val fixedInstant = Instant.fromEpochMilliseconds(clockMillis)

    private lateinit var dao: FakeSessionDao
    private lateinit var prefillDao: FakePrefillDao
    private lateinit var repo: SessionRepositoryImpl

    @BeforeTest
    fun setUp() {
        dao = FakeSessionDao()
        prefillDao = FakePrefillDao()
        repo = SessionRepositoryImpl(dao, FakeTransactor(), clock, prefillDao, FakePlanDao())
    }

    @Test
    fun startEmptySession_inserts_session_with_endedAt_null_and_correct_timestamps() =
        runTest {
            val result = repo.startEmptySession()

            assertNull(result.endedAt)
            assertEquals(fixedInstant, result.startedAt)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNull(result.deletedAt)
            assertNotNull(Uuid.parse(result.id))

            assertEquals(1, dao.sessions.size)
            assertNull(dao.sessions[result.id]!!.endedAt)
        }

    @Test(expected = IllegalStateException::class)
    fun startEmptySession_throws_when_a_session_is_already_active() =
        runTest {
            repo.startEmptySession()
            repo.startEmptySession() // must throw
        }

    @Test
    fun startEmptySession_succeeds_after_finishing_the_active_session() =
        runTest {
            val first = repo.startEmptySession()
            repo.finishSession(first.id)
            val second = repo.startEmptySession() // must not throw
            assertNull(second.endedAt)
            assertEquals(2, dao.sessions.size)
        }

    @Test
    fun finishSession_sets_endedAt_and_bumps_updatedAt() =
        runTest {
            // Insert a session with old updatedAt to make assertion discriminating
            val oldTs = 1L
            val sessionId = "sess-1"
            dao.sessions[sessionId] =
                SessionEntity(
                    sessionId,
                    null,
                    null,
                    startedAt = oldTs,
                    endedAt = null,
                    note = null,
                    rpe = null,
                    createdAt = oldTs,
                    updatedAt = oldTs,
                    deletedAt = null,
                )

            repo.finishSession(sessionId)

            val stored = dao.sessions[sessionId]!!
            assertEquals(clockMillis, stored.endedAt)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test
    fun finishSession_with_missing_id_is_a_no_op() =
        runTest {
            repo.finishSession("non-existent")
            assertEquals(0, dao.sessions.size)
        }

    @Test
    fun finishSession_is_a_no_op_when_session_is_already_ended() =
        runTest {
            val oldTs = 1L
            val sessionId = "sess-1"
            // Insert already-finished session
            dao.sessions[sessionId] =
                SessionEntity(
                    sessionId,
                    null,
                    null,
                    startedAt = oldTs,
                    endedAt = oldTs,
                    note = null,
                    rpe = null,
                    createdAt = oldTs,
                    updatedAt = oldTs,
                    deletedAt = null,
                )

            repo.finishSession(sessionId)

            val stored = dao.sessions[sessionId]!!
            // endedAt and updatedAt must remain the original value (oldTs), not clockMillis
            assertEquals(oldTs, stored.endedAt)
            assertEquals(oldTs, stored.updatedAt)
        }

    @Test
    fun finishSession_sets_endedAt_when_session_is_live() =
        runTest {
            val oldTs = 1L
            val sessionId = "sess-live"
            dao.sessions[sessionId] =
                SessionEntity(
                    sessionId,
                    null,
                    null,
                    startedAt = oldTs,
                    endedAt = null,
                    note = null,
                    rpe = null,
                    createdAt = oldTs,
                    updatedAt = oldTs,
                    deletedAt = null,
                )

            repo.finishSession(sessionId)

            val stored = dao.sessions[sessionId]!!
            assertEquals(clockMillis, stored.endedAt)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test
    fun softDeleteSession_tombstones_session_its_session_exercises_and_their_logged_sets() =
        runTest {
            val oldTs = 1L

            // Session s1 with 1 session-exercise and 2 logged sets
            val s1Id = "sess-1"
            val se1Id = "se-1"
            val ls1Id = "ls-1"
            val ls2Id = "ls-2"

            dao.sessions[s1Id] = SessionEntity(s1Id, null, null, oldTs, null, null, null, oldTs, oldTs, null)
            dao.sessionExercises[se1Id] = SessionExerciseEntity(se1Id, s1Id, "ex-1", 0, null, null, null, oldTs, oldTs, null)
            dao.loggedSets[ls1Id] = LoggedSetEntity(ls1Id, se1Id, 100.0, 5, 0, oldTs, oldTs, oldTs, null)
            dao.loggedSets[ls2Id] = LoggedSetEntity(ls2Id, se1Id, 100.0, 5, 1, oldTs, oldTs, oldTs, null)

            // Session s2 with its own children — must remain live
            val s2Id = "sess-2"
            val se2Id = "se-2"
            val ls3Id = "ls-3"
            dao.sessions[s2Id] = SessionEntity(s2Id, null, null, oldTs, null, null, null, oldTs, oldTs, null)
            dao.sessionExercises[se2Id] = SessionExerciseEntity(se2Id, s2Id, "ex-2", 0, null, null, null, oldTs, oldTs, null)
            dao.loggedSets[ls3Id] = LoggedSetEntity(ls3Id, se2Id, 80.0, 8, 0, oldTs, oldTs, oldTs, null)

            repo.softDeleteSession(s1Id)

            // s1 tombstoned
            assertEquals(clockMillis, dao.sessions[s1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.sessions[s1Id]!!.updatedAt)

            // s1 session exercise tombstoned
            assertEquals(clockMillis, dao.sessionExercises[se1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.sessionExercises[se1Id]!!.updatedAt)

            // s1 logged sets tombstoned
            assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.updatedAt)
            assertEquals(clockMillis, dao.loggedSets[ls2Id]!!.deletedAt)
            assertEquals(clockMillis, dao.loggedSets[ls2Id]!!.updatedAt)

            // s2 and its children untouched
            assertNull(dao.sessions[s2Id]!!.deletedAt)
            assertNull(dao.sessionExercises[se2Id]!!.deletedAt)
            assertNull(dao.loggedSets[ls3Id]!!.deletedAt)
        }

    // ─── addExerciseToSession ──────────────────────────────────────────────────

    @Test
    fun addExerciseToSession_inserts_with_correct_position_and_null_targets() =
        runTest {
            val sessionId = "sess-1"
            dao.sessions[sessionId] = SessionEntity(sessionId, null, null, 1L, null, null, null, 1L, 1L, null)

            // Pre-insert an exercise at position 1 so the new one should be at position 2
            val existingSeId = "se-existing"
            dao.sessionExercises[existingSeId] =
                SessionExerciseEntity(
                    existingSeId,
                    sessionId,
                    "ex-a",
                    1,
                    null,
                    null,
                    null,
                    1L,
                    1L,
                    null,
                )

            val result = repo.addExerciseToSession(sessionId, "ex-b")

            assertEquals(2, result.position)
            assertNull(result.targetSets)
            assertNull(result.targetRepsMin)
            assertNull(result.targetRepsMax)
            assertEquals(sessionId, result.sessionId)
            assertEquals("ex-b", result.exerciseId)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNull(result.deletedAt)
            assertNotNull(Uuid.parse(result.id))

            // Verify it was stored in the fake
            val stored = dao.sessionExercises[result.id]!!
            assertEquals(2, stored.position)
        }

    @Test
    fun addExerciseToSession_with_no_existing_exercises_starts_at_position_1() =
        runTest {
            val sessionId = "sess-empty"
            dao.sessions[sessionId] = SessionEntity(sessionId, null, null, 1L, null, null, null, 1L, 1L, null)

            val result = repo.addExerciseToSession(sessionId, "ex-a")

            assertEquals(1, result.position)
        }

    // ─── logSet ───────────────────────────────────────────────────────────────

    @Test
    fun logSet_inserts_set_with_correct_position_and_timestamps() =
        runTest {
            val seId = "se-1"

            // Pre-insert a set at position 1
            val existingSetId = "ls-existing"
            dao.loggedSets[existingSetId] =
                LoggedSetEntity(
                    existingSetId,
                    seId,
                    80.0,
                    5,
                    1,
                    1L,
                    1L,
                    1L,
                    null,
                )

            val result = repo.logSet(seId, 100.0, 8)

            assertEquals(2, result.position)
            assertEquals(100.0, result.weightKg, 0.0)
            assertEquals(8, result.reps)
            assertEquals(fixedInstant, result.completedAt)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNull(result.deletedAt)
            assertNotNull(Uuid.parse(result.id))

            val stored = dao.loggedSets[result.id]!!
            assertEquals(2, stored.position)
            assertEquals(clockMillis, stored.completedAt)
            assertEquals(clockMillis, stored.createdAt)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test
    fun logSet_with_no_existing_sets_starts_at_position_1() =
        runTest {
            val result = repo.logSet("se-1", 60.0, 10)
            assertEquals(1, result.position)
        }

    @Test(expected = IllegalArgumentException::class)
    fun logSet_rejects_negative_weightKg() =
        runTest {
            repo.logSet("se-1", -1.0, 5)
        }

    @Test(expected = IllegalArgumentException::class)
    fun logSet_rejects_zero_reps() =
        runTest {
            repo.logSet("se-1", 100.0, 0)
        }

    @Test(expected = IllegalArgumentException::class)
    fun logSet_rejects_negative_reps() =
        runTest {
            repo.logSet("se-1", 100.0, -1)
        }

    // ─── updateSet ────────────────────────────────────────────────────────────

    @Test
    fun updateSet_writes_new_weight_and_reps_bumps_updatedAt() =
        runTest {
            val setId = "ls-1"
            val seId = "se-1"
            val oldTs = 1L
            dao.loggedSets[setId] =
                LoggedSetEntity(
                    setId,
                    seId,
                    80.0,
                    5,
                    2,
                    oldTs,
                    oldTs,
                    oldTs,
                    null,
                )

            repo.updateSet(setId, 90.0, 6)

            val stored = dao.loggedSets[setId]!!
            assertEquals(90.0, stored.weightKg, 0.0)
            assertEquals(6, stored.reps)
            assertEquals(clockMillis, stored.updatedAt)
            // Preserved fields
            assertEquals(2, stored.position)
            assertEquals(oldTs, stored.completedAt)
            assertEquals(oldTs, stored.createdAt)
        }

    @Test
    fun updateSet_is_a_no_op_when_set_does_not_exist() =
        runTest {
            repo.updateSet("non-existent", 90.0, 6)
            assertEquals(0, dao.loggedSets.size)
        }

    @Test(expected = IllegalArgumentException::class)
    fun updateSet_rejects_negative_weightKg() =
        runTest {
            repo.updateSet("ls-1", -1.0, 5)
        }

    @Test(expected = IllegalArgumentException::class)
    fun updateSet_rejects_zero_reps() =
        runTest {
            repo.updateSet("ls-1", 100.0, 0)
        }

    // ─── deleteSet ────────────────────────────────────────────────────────────

    @Test
    fun deleteSet_soft_deletes_the_set() =
        runTest {
            val setId = "ls-1"
            val seId = "se-1"
            val oldTs = 1L
            dao.loggedSets[setId] =
                LoggedSetEntity(
                    setId,
                    seId,
                    80.0,
                    5,
                    1,
                    oldTs,
                    oldTs,
                    oldTs,
                    null,
                )

            repo.deleteSet(setId)

            val stored = dao.loggedSets[setId]!!
            assertEquals(clockMillis, stored.deletedAt)
            assertEquals(clockMillis, stored.updatedAt)
        }

    // ─── removeExercise ───────────────────────────────────────────────────────

    @Test
    fun removeExercise_soft_deletes_session_exercise_and_all_its_logged_sets() =
        runTest {
            val seId = "se-1"
            val ls1Id = "ls-1"
            val ls2Id = "ls-2"
            val oldTs = 1L

            dao.sessionExercises[seId] =
                SessionExerciseEntity(
                    seId,
                    "sess-1",
                    "ex-a",
                    1,
                    null,
                    null,
                    null,
                    oldTs,
                    oldTs,
                    null,
                )
            dao.loggedSets[ls1Id] = LoggedSetEntity(ls1Id, seId, 80.0, 5, 1, oldTs, oldTs, oldTs, null)
            dao.loggedSets[ls2Id] = LoggedSetEntity(ls2Id, seId, 80.0, 5, 2, oldTs, oldTs, oldTs, null)

            repo.removeExercise(seId)

            assertEquals(clockMillis, dao.sessionExercises[seId]!!.deletedAt)
            assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.deletedAt)
            assertEquals(clockMillis, dao.loggedSets[ls2Id]!!.deletedAt)
        }

    // ─── replaceExercise ──────────────────────────────────────────────────────

    @Test
    fun replaceExercise_soft_deletes_old_exercise_and_sets_inserts_new_one_at_same_position() =
        runTest {
            val seId = "se-1"
            val ls1Id = "ls-1"
            val oldTs = 1L
            val sessionId = "sess-1"
            val originalPosition = 3

            dao.sessionExercises[seId] =
                SessionExerciseEntity(
                    seId,
                    sessionId,
                    "ex-old",
                    originalPosition,
                    null,
                    null,
                    null,
                    oldTs,
                    oldTs,
                    null,
                )
            dao.loggedSets[ls1Id] = LoggedSetEntity(ls1Id, seId, 80.0, 5, 1, oldTs, oldTs, oldTs, null)

            val result = repo.replaceExercise(seId, "ex-new")

            // Old exercise and its sets soft-deleted
            assertEquals(clockMillis, dao.sessionExercises[seId]!!.deletedAt)
            assertEquals(clockMillis, dao.loggedSets[ls1Id]!!.deletedAt)

            // New exercise has same position and new exerciseId
            assertEquals(originalPosition, result.position)
            assertEquals("ex-new", result.exerciseId)
            assertEquals(sessionId, result.sessionId)
            assertNull(result.deletedAt)
            assertEquals(fixedInstant, result.createdAt)
            assertEquals(fixedInstant, result.updatedAt)
            assertNotNull(Uuid.parse(result.id))

            // Stored in fake
            val stored = dao.sessionExercises[result.id]!!
            assertEquals(originalPosition, stored.position)
            assertEquals("ex-new", stored.exerciseId)
            assertNull(stored.deletedAt)
        }

    @Test(expected = IllegalStateException::class)
    fun replaceExercise_throws_when_session_exercise_not_found() =
        runTest {
            repo.replaceExercise("non-existent", "ex-new")
        }

    // ─── lastPerformance ──────────────────────────────────────────────────────

    @Test
    fun lastPerformance_returns_emptyList_when_no_completed_session_exists() =
        runTest {
            prefillDao.lastCompletedSessionId = null

            val result = repo.lastPerformance("ex-1")

            assertTrue(result.isEmpty())
        }

    @Test
    fun lastPerformance_returns_mapped_sets_from_last_completed_session() =
        runTest {
            val sessionId = "sess-completed"
            val exerciseId = "ex-1"
            val now = 1L

            prefillDao.lastCompletedSessionId = sessionId
            prefillDao.setsBySessionAndExercise[sessionId to exerciseId] =
                listOf(
                    LoggedSetEntity("ls-a", "se-prev", 100.0, 5, 1, now, now, now, null),
                    LoggedSetEntity("ls-b", "se-prev", 100.0, 4, 2, now, now, now, null),
                )

            val result = repo.lastPerformance(exerciseId)

            assertEquals(2, result.size)
            assertEquals("ls-a", result[0].id)
            assertEquals(100.0, result[0].weightKg, 0.0)
            assertEquals(5, result[0].reps)
            assertEquals("ls-b", result[1].id)
            assertEquals(4, result[1].reps)
        }

    // ─── updateSessionRpe ─────────────────────────────────────────────────────

    private fun storedSession(id: String = "sess-1"): SessionEntity =
        SessionEntity(
            id,
            null,
            null,
            startedAt = 1L,
            endedAt = 2L,
            note = null,
            rpe = null,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
        )

    @Test
    fun updateSessionRpe_sets_rpe_and_bumps_updatedAt() =
        runTest {
            dao.sessions["sess-1"] = storedSession()
            repo.updateSessionRpe("sess-1", 8.5)
            val stored = dao.sessions["sess-1"]!!
            assertEquals(8.5, stored.rpe!!, 0.0)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test
    fun updateSessionRpe_null_clears_the_rating() =
        runTest {
            dao.sessions["sess-1"] = storedSession().copy(rpe = 7.0)
            repo.updateSessionRpe("sess-1", null)
            assertNull(dao.sessions["sess-1"]!!.rpe)
        }

    @Test
    fun updateSessionNote_trims_and_stores_blank_as_null() =
        runTest {
            dao.sessions["sess-1"] = storedSession()
            repo.updateSessionNote("sess-1", "  felt strong  ")
            assertEquals("felt strong", dao.sessions["sess-1"]!!.note)
            assertEquals(clockMillis, dao.sessions["sess-1"]!!.updatedAt)
            repo.updateSessionNote("sess-1", "   ")
            assertNull(dao.sessions["sess-1"]!!.note)
        }

    @Test
    fun updateSessionDetails_updates_times_rpe_note_atomically_and_bumps_updatedAt() =
        runTest {
            dao.sessions["sess-1"] = storedSession()
            repo.updateSessionDetails(
                "sess-1",
                startedAt = Instant.fromEpochMilliseconds(100L),
                endedAt = Instant.fromEpochMilliseconds(200L),
                rpe = 9.0,
                note = "  rough one  ",
            )
            val stored = dao.sessions["sess-1"]!!
            assertEquals(100L, stored.startedAt)
            assertEquals(200L, stored.endedAt)
            assertEquals(9.0, stored.rpe!!, 0.0)
            assertEquals("rough one", stored.note)
            assertEquals(clockMillis, stored.updatedAt)
        }

    @Test(expected = IllegalArgumentException::class)
    fun updateSessionDetails_rejects_end_not_after_start() =
        runTest {
            dao.sessions["sess-1"] = storedSession()
            repo.updateSessionDetails(
                "sess-1",
                startedAt = Instant.fromEpochMilliseconds(200L),
                endedAt = Instant.fromEpochMilliseconds(200L),
                rpe = null,
                note = null,
            )
        }

    @Test(expected = IllegalArgumentException::class)
    fun updateSessionRpe_rejects_out_of_range_rpe() =
        runTest {
            dao.sessions["sess-1"] = storedSession()
            repo.updateSessionRpe("sess-1", 5.5)
        }

    @Test(expected = IllegalArgumentException::class)
    fun updateSessionDetails_rejects_out_of_range_rpe() =
        runTest {
            dao.sessions["sess-1"] = storedSession()
            repo.updateSessionDetails(
                "sess-1",
                startedAt = Instant.fromEpochMilliseconds(100L),
                endedAt = Instant.fromEpochMilliseconds(200L),
                rpe = 10.5,
                note = null,
            )
        }

    @Test
    fun updateSessionRpe_with_missing_id_is_a_no_op() =
        runTest {
            repo.updateSessionRpe("ghost", 8.0)
            assertTrue(dao.sessions.isEmpty())
        }

    @Test
    fun updateSessionNote_with_missing_id_is_a_no_op() =
        runTest {
            repo.updateSessionNote("ghost", "some note")
            assertTrue(dao.sessions.isEmpty())
        }

    @Test
    fun updateSessionDetails_with_missing_id_is_a_no_op() =
        runTest {
            repo.updateSessionDetails(
                "ghost",
                startedAt = Instant.fromEpochMilliseconds(100L),
                endedAt = Instant.fromEpochMilliseconds(200L),
                rpe = null,
                note = null,
            )
            assertTrue(dao.sessions.isEmpty())
        }

    // ─── observeSetCountsBySession ────────────────────────────────────────────

    @Test
    fun observeSetCountsBySession_maps_rows_to_sessionId_to_setCount_map() =
        runTest {
            dao.setCounts.value =
                listOf(
                    SessionSetCount(sessionId = "sess-1", setCount = 3),
                    SessionSetCount(sessionId = "sess-2", setCount = 7),
                )

            val result = repo.observeSetCountsBySession().first()

            assertEquals(mapOf("sess-1" to 3, "sess-2" to 7), result)
        }

    @Test
    fun observeSetCountsBySession_returns_empty_map_when_no_rows_exist() =
        runTest {
            dao.setCounts.value = emptyList()

            val result = repo.observeSetCountsBySession().first()

            assertTrue(result.isEmpty())
        }
}
