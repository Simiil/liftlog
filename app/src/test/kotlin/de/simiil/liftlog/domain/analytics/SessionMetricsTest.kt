package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMetricsTest {
    @Test fun weightedSession_matchesFixture() {
        // 100×5, 102.5×3, 95×8
        val m = sessionMetrics(listOf(SetEntry(100.0, 5), SetEntry(102.5, 3), SetEntry(95.0, 8)))
        assertEquals(102.5, m.topSetKg, 0.001)
        assertEquals(120.3333, m.e1rmKg, 0.001)   // best of the three e1RMs (95×8)
        assertEquals(1567.5, m.volumeKg, 0.001)   // 500 + 307.5 + 760
        assertEquals(8, m.maxReps)
        assertEquals(16, m.totalReps)
    }

    @Test fun highRepExcludedFromE1rm_butCountsElsewhere() {
        // 60×15, 80×1
        val m = sessionMetrics(listOf(SetEntry(60.0, 15), SetEntry(80.0, 1)))
        assertEquals(80.0, m.topSetKg, 0.001)
        assertEquals(80.0, m.e1rmKg, 0.001)        // 15-rep set excluded; r=1 → w
        assertEquals(980.0, m.volumeKg, 0.001)     // 900 + 80
        assertEquals(15, m.maxReps)
        assertEquals(16, m.totalReps)
    }

    @Test fun bodyweightSession_zeroWeightMetrics_repMetricsHold() {
        // 0×12, 0×10
        val m = sessionMetrics(listOf(SetEntry(0.0, 12), SetEntry(0.0, 10)))
        assertEquals(0.0, m.topSetKg, 0.0)
        assertEquals(0.0, m.e1rmKg, 0.0)
        assertEquals(12, m.maxReps)
        assertEquals(22, m.totalReps)
    }

    @Test fun empty_isAllZero() {
        val m = sessionMetrics(emptyList())
        assertEquals(0.0, m.topSetKg, 0.0); assertEquals(0.0, m.e1rmKg, 0.0)
        assertEquals(0.0, m.volumeKg, 0.0); assertEquals(0, m.maxReps); assertEquals(0, m.totalReps)
    }
}
