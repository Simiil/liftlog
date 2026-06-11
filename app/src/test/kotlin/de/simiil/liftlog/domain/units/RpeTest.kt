package de.simiil.liftlog.domain.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RpeTest {
    @Test
    fun `increment from unset starts at default 8`() = assertEquals(8.0, Rpe.increment(null), 0.0)

    @Test
    fun `decrement from unset starts at default 8`() = assertEquals(8.0, Rpe.decrement(null), 0.0)

    @Test
    fun `increment steps by half and clamps at 10`() {
        assertEquals(8.5, Rpe.increment(8.0), 0.0)
        assertEquals(10.0, Rpe.increment(10.0), 0.0)
    }

    @Test
    fun `decrement steps by half and clamps at 6`() {
        assertEquals(7.5, Rpe.decrement(8.0), 0.0)
        assertEquals(6.0, Rpe.decrement(6.0), 0.0)
    }

    @Test
    fun `isWhole distinguishes whole from half values`() {
        assertTrue(Rpe.isWhole(8.0))
        assertFalse(Rpe.isWhole(8.5))
    }
}
