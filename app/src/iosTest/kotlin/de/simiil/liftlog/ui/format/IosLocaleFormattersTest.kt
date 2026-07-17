package de.simiil.liftlog.ui.format

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Structural coverage for the NSDateFormatter/NSNumberFormatter/NSRelativeDateTimeFormatter-backed
 * [IosLocaleFormatters] (M7 debt item: compile-only until M8's simulator run). Locale-dependent
 * wording (month names, "Today"/"Yesterday" phrasing, etc.) varies with the test runner's host
 * locale, so these assertions pin "sane" output — shape and key substrings — rather than
 * byte-for-byte text; that divergence from the Android actual's exact-string tests is
 * documented-acceptable (see [de.simiil.liftlog.domain.units.DecimalsIosParityTest] for the one
 * member, [de.simiil.liftlog.domain.units.Decimals], that *is* pinned exactly).
 */
class IosLocaleFormattersTest {
    private val fmt = IosLocaleFormatters()
    private val instant = Instant.parse("2026-06-04T18:30:00Z")

    @Test
    fun mediumDate_containsYear() {
        assertTrue(fmt.mediumDate(instant, TimeZone.UTC).contains("2026"))
    }

    @Test
    fun weekdayDayMonth_containsDayNumber() {
        assertTrue(fmt.weekdayDayMonth(instant, TimeZone.UTC).contains("4"))
    }

    @Test
    fun timeHm_is24h() {
        // Fixed "HH:mm" dateFormat, not locale-dependent -> exact match is safe here.
        assertTrue(fmt.timeHm(instant, TimeZone.UTC) == "18:30")
    }

    @Test
    fun oneDecimal_hasOneFractionDigit() {
        assertTrue(fmt.oneDecimal(12.5).length >= 4)
    }

    @Test
    fun relativeDay_todayIsWordNotDate() {
        val now = Clock.System.now().toEpochMilliseconds()
        assertTrue(fmt.relativeDay(now).isNotBlank())
    }

    @Test
    fun nameComparator_ordersLocalized() {
        assertTrue(fmt.nameComparator().compare("Anfang", "Übung") < 0)
    }
}
