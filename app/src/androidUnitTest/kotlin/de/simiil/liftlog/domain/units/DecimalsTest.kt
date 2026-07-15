package de.simiil.liftlog.domain.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DecimalsTest {
    @Test fun separator_isLocaleSpecific() {
        assertEquals('.', Decimals.separator(Locale.US))
        assertEquals(',', Decimals.separator(Locale.GERMANY))
    }

    @Test fun format_usesLocaleSeparator_andStripsTrailingZeros() {
        assertEquals("82.5", Decimals.format(82.5, Locale.US))
        assertEquals("82,5", Decimals.format(82.5, Locale.GERMANY))
        assertEquals("30", Decimals.format(30.0, Locale.US))
        assertEquals("27,22", Decimals.format(27.2155, Locale.GERMANY)) // HALF_UP, 2 dp max
    }

    @Test fun parse_acceptsLocaleSeparator() {
        assertEquals(82.5, Decimals.parse("82,5", Locale.GERMANY)!!, 1e-9)
        assertEquals(82.5, Decimals.parse("82.5", Locale.US)!!, 1e-9)
        assertEquals(0.0, Decimals.parse("0", Locale.GERMANY)!!, 1e-9)
        assertNull(Decimals.parse("abc", Locale.US))
        assertNull(Decimals.parse("", Locale.US))
    }

    @Test fun parse_acceptsBothSeparatorsUnderAnyLocale() {
        // Uniform leniency: a buffer written under one locale parses under another.
        assertEquals(8.5, Decimals.parse("8.5", Locale.GERMANY)!!, 1e-9)
        assertEquals(8.5, Decimals.parse("8,5", Locale.US)!!, 1e-9)
    }

    @Test fun parse_neverTreatsDotAsThousandsSeparator() {
        assertEquals(1.0, Decimals.parse("1.000", Locale.GERMANY)!!, 1e-9)
    }
}
