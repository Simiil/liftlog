package de.simiil.liftlog.data.mapper

import de.simiil.liftlog.data.dao.SessionExerciseWithSetsRelation
import de.simiil.liftlog.data.dao.SessionWithDetailsRelation
import de.simiil.liftlog.data.entity.LoggedSetEntity
import de.simiil.liftlog.data.entity.SessionEntity
import de.simiil.liftlog.data.entity.SessionExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionWithDetailsMapperTest {

    // ── fixture helpers ──────────────────────────────────────────────────────

    private fun session(id: String = "s1") = SessionEntity(
        id = id,
        templateId = null,
        templateNameSnapshot = null,
        startedAt = 1_000L,
        endedAt = null,
        note = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
    )

    private fun exercise(
        id: String,
        sessionId: String = "s1",
        position: Int,
        deletedAt: Long? = null,
    ) = SessionExerciseEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = "ex-$id",
        position = position,
        targetSets = null,
        targetRepsMin = null,
        targetRepsMax = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = deletedAt,
    )

    private fun loggedSet(
        id: String,
        sessionExerciseId: String,
        position: Int,
        deletedAt: Long? = null,
    ) = LoggedSetEntity(
        id = id,
        sessionExerciseId = sessionExerciseId,
        weightKg = 100.0,
        reps = 5,
        position = position,
        completedAt = 1_000L,
        rpe = null,
        note = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = deletedAt,
    )

    // ── tests ────────────────────────────────────────────────────────────────

    /**
     * The session id field is mapped through correctly.
     */
    @Test
    fun `session id is mapped correctly`() {
        val relation = SessionWithDetailsRelation(
            session = session("s1"),
            exercises = emptyList(),
        )
        val result = relation.toDomain()
        assertEquals("s1", result.session.id)
    }

    /**
     * A session-exercise with deletedAt != null must not appear in the result.
     */
    @Test
    fun `tombstoned session-exercise is excluded`() {
        val liveEx = exercise("live-ex", position = 1, deletedAt = null)
        val deadEx = exercise("dead-ex", position = 3, deletedAt = 99L)

        val relation = SessionWithDetailsRelation(
            session = session(),
            exercises = listOf(
                SessionExerciseWithSetsRelation(sessionExercise = deadEx, sets = emptyList()),
                SessionExerciseWithSetsRelation(sessionExercise = liveEx, sets = emptyList()),
            ),
        )
        val result = relation.toDomain()

        assertEquals(1, result.exercises.size)
        val ids = result.exercises.map { it.sessionExercise.id }
        assertFalse(ids.contains("dead-ex"))
        assertEquals("live-ex", ids[0])
    }

    /**
     * Session-exercises are sorted ascending by position, regardless of
     * insertion order.
     */
    @Test
    fun `session-exercises are sorted ascending by position`() {
        // inserted in NON-ascending order: position 2, 1, 3-tombstoned
        val exPos2 = exercise("ex-pos2", position = 2, deletedAt = null)
        val exPos1 = exercise("ex-pos1", position = 1, deletedAt = null)
        val exPos3Dead = exercise("ex-pos3-dead", position = 3, deletedAt = 99L)

        val relation = SessionWithDetailsRelation(
            session = session(),
            exercises = listOf(
                SessionExerciseWithSetsRelation(sessionExercise = exPos2, sets = emptyList()),
                SessionExerciseWithSetsRelation(sessionExercise = exPos1, sets = emptyList()),
                SessionExerciseWithSetsRelation(sessionExercise = exPos3Dead, sets = emptyList()),
            ),
        )
        val result = relation.toDomain()

        assertEquals(listOf(1, 2), result.exercises.map { it.sessionExercise.position })
    }

    /**
     * A logged-set with deletedAt != null must not appear in the sets of its
     * parent session-exercise.
     */
    @Test
    fun `tombstoned logged-set is excluded`() {
        val ex = exercise("ex1", position = 1, deletedAt = null)
        val liveSet = loggedSet("set-live", sessionExerciseId = "ex1", position = 1, deletedAt = null)
        val deadSet = loggedSet("set-dead", sessionExerciseId = "ex1", position = 3, deletedAt = 99L)

        val relation = SessionWithDetailsRelation(
            session = session(),
            exercises = listOf(
                SessionExerciseWithSetsRelation(
                    sessionExercise = ex,
                    sets = listOf(deadSet, liveSet),
                ),
            ),
        )
        val result = relation.toDomain()

        val setIds = result.exercises[0].sets.map { it.id }
        assertEquals(1, setIds.size)
        assertFalse(setIds.contains("set-dead"))
        assertEquals("set-live", setIds[0])
    }

    /**
     * Logged-sets within a kept exercise are sorted ascending by position,
     * regardless of insertion order, and tombstoned sets are excluded.
     */
    @Test
    fun `logged-sets are sorted ascending by position with tombstones excluded`() {
        val ex = exercise("ex1", position = 1, deletedAt = null)
        // inserted as: position 2 (live), position 1 (live), position 3 (tombstoned)
        val setPos2 = loggedSet("set-pos2", sessionExerciseId = "ex1", position = 2, deletedAt = null)
        val setPos1 = loggedSet("set-pos1", sessionExerciseId = "ex1", position = 1, deletedAt = null)
        val setPos3Dead = loggedSet("set-pos3-dead", sessionExerciseId = "ex1", position = 3, deletedAt = 99L)

        val relation = SessionWithDetailsRelation(
            session = session(),
            exercises = listOf(
                SessionExerciseWithSetsRelation(
                    sessionExercise = ex,
                    sets = listOf(setPos2, setPos1, setPos3Dead),
                ),
            ),
        )
        val result = relation.toDomain()

        assertEquals(listOf(1, 2), result.exercises[0].sets.map { it.position })
    }

    /**
     * Combined scenario: out-of-order exercises with one tombstoned, and the
     * live position-1 exercise carrying out-of-order sets with one tombstoned.
     * Asserts both filter+sort constraints simultaneously.
     */
    @Test
    fun `combined - filters and sorts exercises and sets correctly`() {
        val exPos2 = exercise("ex-pos2", position = 2, deletedAt = null)
        val exPos1 = exercise("ex-pos1", position = 1, deletedAt = null)
        val exPos3Dead = exercise("ex-pos3-dead", position = 3, deletedAt = 99L)

        val setPos2 = loggedSet("set-pos2", sessionExerciseId = "ex-pos1", position = 2, deletedAt = null)
        val setPos1 = loggedSet("set-pos1", sessionExerciseId = "ex-pos1", position = 1, deletedAt = null)
        val setPos3Dead = loggedSet("set-pos3-dead", sessionExerciseId = "ex-pos1", position = 3, deletedAt = 99L)

        val relation = SessionWithDetailsRelation(
            session = session("s1"),
            exercises = listOf(
                SessionExerciseWithSetsRelation(sessionExercise = exPos2, sets = emptyList()),
                SessionExerciseWithSetsRelation(
                    sessionExercise = exPos1,
                    sets = listOf(setPos2, setPos1, setPos3Dead),
                ),
                SessionExerciseWithSetsRelation(sessionExercise = exPos3Dead, sets = emptyList()),
            ),
        )
        val result = relation.toDomain()

        // session id passes through
        assertEquals("s1", result.session.id)

        // only two live exercises, sorted by position
        assertEquals(listOf(1, 2), result.exercises.map { it.sessionExercise.position })

        // tombstoned exercise absent
        assertFalse(result.exercises.map { it.sessionExercise.id }.contains("ex-pos3-dead"))

        // sets on the position-1 exercise: live ones only, sorted ascending
        val setsOfEx1 = result.exercises.first { it.sessionExercise.id == "ex-pos1" }.sets
        assertEquals(listOf(1, 2), setsOfEx1.map { it.position })
    }
}
