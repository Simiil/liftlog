package de.simiil.liftlog.domain.logging

import de.simiil.liftlog.domain.model.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private fun set(
    weightKg: Double,
    reps: Int,
    position: Int,
) = LoggedSet(
    id = "s$position",
    sessionExerciseId = "se",
    weightKg = weightKg,
    reps = reps,
    position = position,
    completedAt = Instant.EPOCH,
    rpe = null,
    note = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    deletedAt = null,
)

class PrefillTest {
    @Test fun rule1_previousSetOfThisEntry_wins() {
        val result =
            Prefill.forNextSet(
                setsThisEntry = listOf(set(30.0, 10, 1), set(32.5, 8, 2)),
                lastPerformance = listOf(set(20.0, 12, 1)),
            )
        assertEquals(32.5, result.weightKg!!, 0.0)
        assertEquals(8, result.reps)
    }

    @Test fun rule2_firstSetFromLastSession_whenEntryEmpty() {
        val result =
            Prefill.forNextSet(
                setsThisEntry = emptyList(),
                lastPerformance = listOf(set(30.0, 10, 1), set(30.0, 10, 2), set(30.0, 8, 3)),
            )
        assertEquals(30.0, result.weightKg!!, 0.0)
        assertEquals(10, result.reps)
    }

    @Test fun rule3_neverPerformed_emptyWeightReps10() {
        val result = Prefill.forNextSet(setsThisEntry = emptyList(), lastPerformance = emptyList())
        assertEquals(null, result.weightKg)
        assertEquals(10, result.reps)
    }
}
