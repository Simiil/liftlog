package de.simiil.liftlog.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CombineDateAndTimeTest {
    // ---- helpers ----

    /** Parse an ISO-8601 instant string. */
    private fun instant(s: String): Instant = Instant.parse(s)

    /** Berlin zone (CEST = +02:00 in summer, CET = +01:00 in winter). */
    private val berlin = ZoneId.of("Europe/Berlin")

    /**
     * Encode a picked calendar date as the M3 DatePicker contract:
     * UTC midnight of the calendar day (e.g. 2026-06-10 → 2026-06-10T00:00:00Z).
     */
    private fun utcMidnightMillis(
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        java.time.LocalDate
            .of(year, month, day)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

    // ---- tests ----

    /**
     * Normal combine: picked date 2026-06-10 (UTC-midnight millis) + 14:30
     * in zone Europe/Berlin, current = 2026-06-11T08:00 Berlin.
     * Expected instant: 2026-06-10T14:30 CEST = 2026-06-10T12:30Z.
     */
    @Test
    fun normalCombine_pickedDateAndTime_returnsCorrectInstant() {
        val current = ZonedDateTime.of(2026, 6, 11, 8, 0, 0, 0, berlin)
        val picked = utcMidnightMillis(2026, 6, 10)

        val result = combineDateAndTime(picked, 14, 30, current)

        assertEquals(instant("2026-06-10T12:30:00Z"), result)
    }

    /**
     * Null fallback uses LOCAL date: current = 2026-06-11T00:30 CEST (Berlin).
     * UTC equivalent would be 2026-06-10T22:30Z → UTC date is 2026-06-10.
     * But null fallback must use the LOCAL date (2026-06-11), so combining
     * with 01:00 gives 2026-06-11T01:00 CEST = 2026-06-10T23:00Z.
     *
     * Assert: result is NOT on calendar day 2026-06-10 in Berlin.
     */
    @Test
    fun nullFallback_usesLocalDate_notUtcDate() {
        // 00:30 CEST is 22:30 UTC the previous day → UTC date = 2026-06-10
        val current = ZonedDateTime.of(2026, 6, 11, 0, 30, 0, 0, berlin)

        val result = combineDateAndTime(null, 1, 0, current)

        val resultedLocalDate = result.atZone(berlin).toLocalDate()
        assertEquals(
            "Fallback must resolve to the LOCAL date of current (2026-06-11), not the UTC date (2026-06-10)",
            java.time.LocalDate.of(2026, 6, 11),
            resultedLocalDate,
        )
        // Extra: also verify the time component
        assertEquals(instant("2026-06-10T23:00:00Z"), result)
    }

    /**
     * DST gap: zone Europe/Berlin, picked date 2026-03-29 (spring-forward at 02:00 → 03:00).
     * Time 02:30 falls in the gap; java.time's SMART resolver shifts it to 03:30+02:00.
     * Expected instant: 2026-03-29T03:30+02:00 = 2026-03-29T01:30Z.
     */
    @Test
    fun dstGap_springForward_shiftedToPostGapTime() {
        // current can be any arbitrary ZonedDateTime in Berlin
        val current = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, berlin)
        val picked = utcMidnightMillis(2026, 3, 29)

        val result = combineDateAndTime(picked, 2, 30, current)

        // The SMART resolver shifts the gap time to 03:30+02:00
        assertEquals(instant("2026-03-29T01:30:00Z"), result)
    }

    /**
     * Cross-midnight / winter offset: picked date 2026-01-15 (CET, +01:00),
     * time 23:45, current is in June (CEST, +02:00).
     * Zone rules of the PICKED date apply → +01:00.
     * Expected instant: 2026-01-15T23:45+01:00 = 2026-01-15T22:45Z.
     */
    @Test
    fun winterOffset_pickedDateInWinter_appliesWinterZoneRules() {
        val current = ZonedDateTime.of(2026, 6, 15, 10, 0, 0, 0, berlin) // CEST (+02:00)
        val picked = utcMidnightMillis(2026, 1, 15)

        val result = combineDateAndTime(picked, 23, 45, current)

        assertEquals(instant("2026-01-15T22:45:00Z"), result)
    }
}
