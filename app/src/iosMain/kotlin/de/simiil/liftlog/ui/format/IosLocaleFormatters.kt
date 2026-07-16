package de.simiil.liftlog.ui.format

import de.simiil.liftlog.domain.format.LocaleFormatters
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.TimeZone
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSString
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.date
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.defaultTimeZone
import platform.Foundation.localizedCompare
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

/**
 * NSDateFormatter/NSNumberFormatter/NSRelativeDateTimeFormatter-backed implementation of
 * [LocaleFormatters]. Compile-only in M7 (no Xcode toolchain here to run on a simulator/device —
 * the klib compile is the gate); M8 verifies on-device, including the 24h-time heuristic and
 * relative-date wording across locales (see `docs/09-i18n-german-spot-check.md`'s Android
 * findings for the kind of on-device checks this needs on iOS too).
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocaleFormatters : LocaleFormatters {
    private fun fmt(
        dateStyle: NSDateFormatterStyle,
        timeStyle: NSDateFormatterStyle,
        tz: TimeZone,
    ) = NSDateFormatter().apply {
        this.dateStyle = dateStyle
        this.timeStyle = timeStyle
        this.timeZone = NSTimeZone.timeZoneWithName(tz.id) ?: NSTimeZone.defaultTimeZone
    }

    // NSDate stores fractional seconds; kotlin.time.Instant.epochSeconds truncates to whole
    // seconds, so go through the millisecond count for the extra precision (harmless either way
    // for the display-only granularity these formatters render at).
    private fun Instant.ns(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1_000.0)

    override fun mediumDate(
        instant: Instant,
        timeZone: TimeZone,
    ) = fmt(NSDateFormatterMediumStyle, NSDateFormatterNoStyle, timeZone).stringFromDate(instant.ns())

    override fun mediumDateShortTime(
        instant: Instant,
        timeZone: TimeZone,
    ) = fmt(NSDateFormatterMediumStyle, NSDateFormatterShortStyle, timeZone).stringFromDate(instant.ns())

    override fun weekdayDayMonth(
        instant: Instant,
        timeZone: TimeZone,
    ) = NSDateFormatter()
        .apply {
            setLocalizedDateFormatFromTemplate("EEE d MMM")
            this.timeZone = NSTimeZone.timeZoneWithName(timeZone.id) ?: NSTimeZone.defaultTimeZone
        }.stringFromDate(instant.ns())

    override fun timeHm(
        instant: Instant,
        timeZone: TimeZone,
    ) = NSDateFormatter()
        .apply {
            dateFormat = "HH:mm"
            this.timeZone = NSTimeZone.timeZoneWithName(timeZone.id) ?: NSTimeZone.defaultTimeZone
        }.stringFromDate(instant.ns())

    override fun relativeDate(thenMillis: Long): String =
        NSRelativeDateTimeFormatter().localizedStringForDate(
            NSDate.dateWithTimeIntervalSince1970(thenMillis / 1_000.0),
            relativeToDate = NSDate.date(),
        )

    override fun relativeDay(thenMillis: Long): String {
        // Day-resolution bucketing (was DateUtils.getRelativeTimeSpanString(..., DAY_IN_MILLIS)):
        // normalize both instants to the start of their calendar day first, so the formatter's
        // "days" component — not hours/minutes — drives the wording ("Today"/"Yesterday"/…).
        val calendar = NSCalendar.currentCalendar
        val then = calendar.startOfDayForDate(NSDate.dateWithTimeIntervalSince1970(thenMillis / 1_000.0))
        val now = calendar.startOfDayForDate(NSDate.date())
        return NSRelativeDateTimeFormatter().localizedStringForDate(then, relativeToDate = now)
    }

    override fun prefers24HourTime(): Boolean =
        NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains("a")?.not() ?: true

    private fun oneDecimalFormatter() =
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = 1u
            maximumFractionDigits = 1u
            usesGroupingSeparator = false
        }

    override fun oneDecimal(value: Double): String = oneDecimalFormatter().stringFromNumber(NSNumber(double = value)) ?: value.toString()

    override fun signedOneDecimal(value: Double): String =
        oneDecimalFormatter()
            .apply { positivePrefix = "+" }
            .stringFromNumber(NSNumber(double = value)) ?: value.toString()

    override fun nameComparator(): Comparator<String> =
        Comparator { a, b ->
            // Kotlin String and NSString are toll-free bridged at runtime (same object); the
            // static cast warning is expected and harmless (see IosDocumentIo for precedent).
            @Suppress("CAST_NEVER_SUCCEEDS")
            (a as NSString).localizedCompare(b).toInt()
        }
}
