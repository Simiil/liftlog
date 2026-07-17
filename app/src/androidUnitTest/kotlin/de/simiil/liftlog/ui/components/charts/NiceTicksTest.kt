package de.simiil.liftlog.ui.components.charts

import de.simiil.liftlog.ui.format.AndroidLocaleFormatters
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NiceTicksTest {
    @Test fun spansRangeWithNiceSteps() {
        val t = niceTicks(72.4, 94.1)
        assertEquals(5.0, t[1] - t[0], 1e-9) // step snaps to 5
        assertTrue(t.first() <= 72.4 && t.last() >= 94.1)
    }

    @Test fun zeroBasedVolume() {
        val t = niceTicks(0.0, 4200.0)
        assertEquals(0.0, t.first(), 1e-9)
        assertEquals(1000.0, t[1] - t[0], 1e-9)
    }

    @Test fun degenerateFlatSeries() {
        val t = niceTicks(50.0, 50.0)
        assertTrue(t.size >= 2) // never collapses to one tick
    }

    @Test fun labels_localeAware() {
        val fmt = AndroidLocaleFormatters(context = null)

        fun <T> withLocale(
            l: Locale,
            block: () -> T,
        ): T {
            val old = Locale.getDefault()
            Locale.setDefault(l)
            try {
                return block()
            } finally {
                Locale.setDefault(old)
            }
        }

        // Whole ticks are locale-independent integers.
        assertEquals("80", withLocale(Locale.US) { tickLabel(80.0, fmt) })
        assertEquals("80", withLocale(Locale.GERMANY) { tickLabel(80.0, fmt) })
        // Fractional ticks use the locale decimal separator (08-i18n-spec §5).
        assertEquals("82.5", withLocale(Locale.US) { tickLabel(82.5, fmt) })
        assertEquals("82,5", withLocale(Locale.GERMANY) { tickLabel(82.5, fmt) })
    }
}
