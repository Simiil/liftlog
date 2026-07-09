package de.simiil.liftlog.domain.logging

import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.Session
import de.simiil.liftlog.domain.model.SessionExercise
import de.simiil.liftlog.domain.model.SessionExerciseWithSets
import de.simiil.liftlog.domain.model.SessionWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun session() =
    Session(
        id = "s1",
        templateId = null,
        templateNameSnapshot = null,
        startedAt = Instant.EPOCH,
        endedAt = null,
        note = null,
        rpe = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = null,
    )

private fun entry(
    seId: String,
    targetSets: Int?,
    setCount: Int,
    position: Int = 0,
) = SessionExerciseWithSets(
    sessionExercise =
        SessionExercise(
            id = seId,
            sessionId = "s1",
            exerciseId = "ex-$seId",
            position = position,
            targetSets = targetSets,
            targetRepsMin = null,
            targetRepsMax = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            deletedAt = null,
        ),
    sets =
        (1..setCount).map { i ->
            LoggedSet(
                id = "$seId-set$i",
                sessionExerciseId = seId,
                weightKg = 40.0,
                reps = 8,
                position = i,
                completedAt = Instant.EPOCH,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                deletedAt = null,
            )
        },
)

private fun details(vararg entries: SessionExerciseWithSets) = SessionWithDetails(session(), entries.toList())

class ActiveExerciseDefaultsTest {
    @Test fun pickDefault_emptySession_returnsNull() {
        assertNull(ActiveExerciseDefaults.pickDefault(details()))
    }

    @Test fun pickDefault_returnsFirstIncomplete() {
        val d =
            details(
                entry("se1", targetSets = 3, setCount = 3, position = 0),
                entry("se2", targetSets = 3, setCount = 1, position = 1),
                entry("se3", targetSets = 3, setCount = 0, position = 2),
            )
        assertEquals("se2", ActiveExerciseDefaults.pickDefault(d))
    }

    @Test fun pickDefault_allComplete_returnsLast() {
        val d =
            details(
                entry("se1", targetSets = 2, setCount = 2, position = 0),
                entry("se2", targetSets = 3, setCount = 3, position = 1),
            )
        assertEquals("se2", ActiveExerciseDefaults.pickDefault(d))
    }

    @Test fun pickDefault_adHocNullTargets_neverComplete_returnsFirst() {
        val d =
            details(
                entry("se1", targetSets = null, setCount = 5, position = 0),
                entry("se2", targetSets = null, setCount = 0, position = 1),
            )
        assertEquals("se1", ActiveExerciseDefaults.pickDefault(d))
    }

    @Test fun isComplete_nullTarget_isFalse() {
        assertFalse(ActiveExerciseDefaults.isComplete(entry("se1", targetSets = null, setCount = 10)))
    }

    @Test fun isComplete_reachedTarget_isTrue() {
        assertTrue(ActiveExerciseDefaults.isComplete(entry("se1", targetSets = 3, setCount = 3)))
    }

    @Test fun isComplete_belowTarget_isFalse() {
        assertFalse(ActiveExerciseDefaults.isComplete(entry("se1", targetSets = 3, setCount = 2)))
    }
}
