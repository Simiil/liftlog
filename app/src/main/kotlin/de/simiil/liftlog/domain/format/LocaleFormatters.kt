package de.simiil.liftlog.domain.format

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Locale-sensitive rendering only — no date math. Android impl: java.time/java.text/DateUtils;
 * iOS impl (M8): NSDateFormatter/NSNumberFormatter. Bound as a Koin single per platform.
 */
interface LocaleFormatters {
    fun mediumDate(
        instant: Instant,
        timeZone: TimeZone,
    ): String // was ofLocalizedDate(MEDIUM)

    fun mediumDateShortTime(
        instant: Instant,
        timeZone: TimeZone,
    ): String // was ofLocalizedDateTime(MEDIUM, SHORT)

    fun weekdayDayMonth(
        instant: Instant,
        timeZone: TimeZone,
    ): String // was "EEE d MMM"

    fun timeHm(
        instant: Instant,
        timeZone: TimeZone,
    ): String // was "HH:mm"

    fun relativeDate(thenMillis: Long): String // was DateUtils.getRelativeTimeSpanString

    fun prefers24HourTime(): Boolean // was DateFormat.is24HourFormat(context)

    fun oneDecimal(value: Double): String // was String.format("%.1f", …)

    fun signedOneDecimal(value: Double): String // was String.format("%+.1f", …)

    fun nameComparator(): Comparator<String> // was java.text.Collator (per-call, picks up locale changes)
}
