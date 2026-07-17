package de.simiil.liftlog.domain.units

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity coverage for the NSNumberFormatter-backed iOS [Decimals] actual against the
 * java.time-era DecimalFormat("0.##", HALF_UP) reference semantics the Android actual
 * implements: away-from-zero rounding on ties, at most 2 fraction digits, trailing zeros
 * stripped (M7 debt item — negative-tie rounding was never verified on-device before M8).
 * Expected strings are built from [Decimals.separator] rather than pinned to '.'/',' so
 * this holds under whatever locale the simulator/host provides.
 */
class DecimalsIosParityTest {
    @Test
    fun format_positive() {
        assertEquals("82" + Decimals.separator() + "5", Decimals.format(82.5))
    }

    @Test
    fun format_stripsTrailingZeros() {
        assertEquals("30", Decimals.format(30.0))
    }

    @Test
    fun format_negativeTie_awayFromZero() {
        // Java HALF_UP rounds -2.345 -> -2.35 (away from zero, not toward-zero/half-even).
        // NSNumberFormatterRoundHalfUp is documented to round ties away from zero too, but the
        // negative-tie case was never exercised on-device before this test (M7 debt item) —
        // this pins the iOS actual to the same behavior.
        assertEquals("-2" + Decimals.separator() + "35", Decimals.format(-2.345))
    }

    @Test
    fun parse_roundTrips() {
        assertEquals(82.5, Decimals.parse(Decimals.format(82.5))!!, absoluteTolerance = 1e-9)
    }
}
