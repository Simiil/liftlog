package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.Equipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAnalyticsTest {
    private val day = 86_400_000L
    private val now = 1_000_000_000_000L

    @Test fun noSets_returnsNull() = assertNull(summarize(Equipment.BARBELL, emptyList(), now))

    @Test fun singleSessionEver_everyMetricIsPr() {
        val sets = listOf(DatedSet("s1", now - 5 * day, 100.0, 5)) // volume 500
        val s = summarize(Equipment.BARBELL, sets, now)!!
        assertEquals(1, s.sessions.size)
        assertTrue(s.sessions.first().isPr)
        assertTrue(s.sessions.first().isPrVolume)
        assertEquals(500.0, s.currentValue, 0.0) // volume is the headline primary
        assertFalse(s.bodyweight)
    }

    @Test fun pr_isStrictlyGreaterThanAllEarlier() {
        val sets =
            listOf(
                DatedSet("s1", now - 20 * day, 100.0, 5), // volume 500 — PR (first)
                DatedSet("s2", now - 13 * day, 100.0, 5), // volume 500 — equal, NOT a PR (not strictly greater)
                DatedSet("s3", now - 6 * day, 105.0, 5), // volume 525 — PR
            )
        val s = summarize(Equipment.BARBELL, sets, now)!!
        assertTrue(s.sessions[0].isPr)
        assertFalse(s.sessions[1].isPr)
        assertTrue(s.sessions[2].isPr)
        assertEquals(525.0, s.currentValue, 0.0) // last session's volume
    }

    @Test fun bodyweight_swapsToTotalRepMetrics() {
        val sets =
            listOf(
                DatedSet("s1", now - 12 * day, 0.0, 8), // totalReps 8
                DatedSet("s2", now - 5 * day, 0.0, 6), // session s2 has two sets:
                DatedSet("s2", now - 5 * day, 0.0, 6), // totalReps 12, maxReps 6
            )
        val s = summarize(Equipment.BODYWEIGHT, sets, now)!!
        assertTrue(s.bodyweight)
        assertEquals(12.0, s.currentValue, 0.0) // last session's totalReps is the primary
        assertTrue(s.sessions[1].isPr) // total-reps PR (12 > 8), even though maxReps (6) dropped
        assertTrue(s.sessions[1].isPrTotalReps)
    }

    @Test fun weightedBodyweightEntry_usesWeightMetrics() {
        // BODYWEIGHT equipment but added load ⇒ NOT treated as bodyweight
        val sets = listOf(DatedSet("s1", now - 5 * day, 20.0, 5))
        val s = summarize(Equipment.BODYWEIGHT, sets, now)!!
        assertFalse(s.bodyweight)
    }

    @Test fun sessionsGroupedBySessionId() {
        val sets =
            listOf(
                DatedSet("s1", now - 6 * day, 100.0, 5),
                DatedSet("s1", now - 6 * day, 100.0, 5),
                DatedSet("s1", now - 6 * day, 95.0, 8),
            )
        val s = summarize(Equipment.BARBELL, sets, now)!!
        assertEquals(1, s.sessions.size)
        assertEquals(
            3,
            s.sessions
                .first()
                .sets.size,
        )
    }
}
