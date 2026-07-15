package de.simiil.liftlog.ui.format

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import de.simiil.liftlog.domain.format.LocaleFormatters
import kotlinx.datetime.TimeZone
import java.text.Collator
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant

/**
 * Android implementation of [LocaleFormatters], backed by java.time/java.text and
 * [android.text.format.DateUtils]/[android.text.format.DateFormat]. `context` is nullable so this
 * class is constructible from JVM unit tests, which don't exercise the [DateUtils]/[DateFormat]
 * paths that require a real Android runtime; on-device it is always supplied via Koin
 * (`androidContext()`).
 */
class AndroidLocaleFormatters(
    private val context: Context?,
) : LocaleFormatters {
    private fun Instant.zoned(tz: TimeZone): ZonedDateTime =
        java.time.Instant
            .ofEpochMilli(toEpochMilliseconds())
            .atZone(ZoneId.of(tz.id))

    override fun mediumDate(
        instant: Instant,
        timeZone: TimeZone,
    ) = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(instant.zoned(timeZone))

    override fun mediumDateShortTime(
        instant: Instant,
        timeZone: TimeZone,
    ) = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(instant.zoned(timeZone))

    override fun weekdayDayMonth(
        instant: Instant,
        timeZone: TimeZone,
    ) = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(instant.zoned(timeZone))

    override fun timeHm(
        instant: Instant,
        timeZone: TimeZone,
    ) = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.zoned(timeZone))

    override fun relativeDate(thenMillis: Long): String =
        DateUtils
            .getRelativeTimeSpanString(
                thenMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString()

    override fun prefers24HourTime(): Boolean = context?.let(DateFormat::is24HourFormat) ?: true

    override fun oneDecimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

    override fun signedOneDecimal(value: Double) = String.format(Locale.getDefault(), "%+.1f", value)

    override fun nameComparator(): Comparator<String> {
        val collator = Collator.getInstance() // per-call: picks up runtime locale change
        return Comparator { a, b -> collator.compare(a, b) }
    }
}
