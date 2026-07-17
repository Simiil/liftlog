package de.simiil.liftlog.domain.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locale-agnostic contract for the [Decimals] expect/actual (08-i18n-spec §5.2). Written against
 * the common surface so it runs on every target — the first automated coverage that will exercise
 * the iOS NSNumberFormatter actual once simulator tests run (M8). Assertions avoid pinning a
 * specific separator so they hold under any host default locale.
 */
class DecimalsTest {
    @Test fun separator_isDotOrComma() {
        assertTrue(Decimals.separator() == '.' || Decimals.separator() == ',')
    }

    @Test fun parseOfFormat_roundTrips() {
        assertEquals(82.5, Decimals.parse(Decimals.format(82.5)))
    }

    @Test fun format_stripsTrailingZeros() {
        // Whole numbers carry no decimal separator at all…
        assertFalse(Decimals.format(2.0).contains(Decimals.separator()))
        // …and fractional values keep no trailing zero (e.g. "2,5"/"2.5", never "2,50").
        assertFalse(Decimals.format(2.5).endsWith("0"))
    }

    @Test fun parse_rejectsNonNumeric() {
        assertNull(Decimals.parse("abc"))
    }

    @Test fun parse_acceptsBothSeparators() {
        // Uniformly lenient: '.' and ',' both parse under any locale (numpad buffer stays valid
        // across a mid-entry app-language switch).
        assertEquals(1.5, Decimals.parse("1.5"))
        assertEquals(1.5, Decimals.parse("1,5"))
    }
}
