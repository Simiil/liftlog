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
    fun format_dyadicTie_awayFromZero() {
        // -2.345 is not a real test of tie-breaking: it isn't exactly representable in binary
        // (the actual double is closer to -2.35 already), so HALF_UP and HALF_EVEN silently
        // agree on it and the test never discriminated between rounding modes. -2.125, by
        // contrast, is exactly representable (2.125 == 17/8) and is a genuine tie at the 2nd
        // fraction digit: Java HALF_UP rounds it away from zero to -2.13, while HALF_EVEN would
        // give -2.12. NSNumberFormatterRoundHalfUp is documented to round ties away from zero
        // too; this pins the iOS actual to that same behavior for both signs.
        assertEquals("-2" + Decimals.separator() + "13", Decimals.format(-2.125))
    }

    @Test
    fun format_dyadicTie_positive() {
        assertEquals("2" + Decimals.separator() + "13", Decimals.format(2.125))
    }

    @Test
    fun parse_roundTrips() {
        assertEquals(82.5, Decimals.parse(Decimals.format(82.5))!!, absoluteTolerance = 1e-9)
    }
}
