package de.simiil.liftlog.ui.format

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.time.Instant

class AndroidLocaleFormattersTest {
    private val fmt = AndroidLocaleFormatters(context = null) // JVM test: DateUtils paths not exercised
    private val instant = Instant.parse("2026-06-04T18:30:00Z")

    private fun <T> withLocale(
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

    @Test
    fun mediumDate_localized() {
        val en = withLocale(Locale.US) { fmt.mediumDate(instant, TimeZone.UTC) }
        val de = withLocale(Locale.GERMANY) { fmt.mediumDate(instant, TimeZone.UTC) }
        assertTrue(en.contains("2026"))
        assertTrue(en.contains("Jun"))
        assertTrue(de.contains("2026"))
        assertTrue(de.contains("Juni") || de.contains("06"))
    }

    @Test
    fun timeHm_is24h() {
        assertEquals("18:30", fmt.timeHm(instant, TimeZone.UTC))
    }

    @Test
    fun oneDecimal_localeSeparator() {
        assertEquals("12.5", withLocale(Locale.US) { fmt.oneDecimal(12.5) })
        assertEquals("12,5", withLocale(Locale.GERMANY) { fmt.oneDecimal(12.5) })
        assertEquals("+12,5", withLocale(Locale.GERMANY) { fmt.signedOneDecimal(12.5) })
    }

    @Test
    fun nameComparator_collates() {
        val sorted = withLocale(Locale.GERMANY) { listOf("Übung", "Anfang").sortedWith(fmt.nameComparator()) }
        assertEquals(listOf("Anfang", "Übung"), sorted) // Collator puts Ü after A, before Z-region
    }
}
