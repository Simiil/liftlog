package de.simiil.liftlog.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpleyTest {
    @Test fun oneRep_returnsWeight() = assertEquals(102.5, e1rm(102.5, 1)!!, 0.0001)

    @Test fun midRange_appliesEpley() {
        assertEquals(116.6667, e1rm(100.0, 5)!!, 0.001) // 100·(1+5/30)
        assertEquals(120.3333, e1rm(95.0, 8)!!, 0.001) // 95·(1+8/30)
    }

    @Test fun twelveReps_included() = assertEquals(140.0, e1rm(100.0, 12)!!, 0.001) // 100·(1+12/30)

    @Test fun aboveTwelveReps_excluded() = assertNull(e1rm(60.0, 15))

    @Test fun zeroWeight_isZeroNotNull() = assertEquals(0.0, e1rm(0.0, 8)!!, 0.0)
}
