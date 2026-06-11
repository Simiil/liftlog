package de.simiil.liftlog.domain.units

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Locale-aware decimal entry/format helpers (08-i18n-spec §5.2). Pure; no Android deps. */
object Decimals {
    fun separator(locale: Locale = Locale.getDefault()): Char =
        DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /** Up to 2 decimals, trailing zeros stripped, locale decimal separator. */
    fun format(value: Double, locale: Locale = Locale.getDefault()): String =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale))
            .apply { roundingMode = RoundingMode.HALF_UP }
            .format(value)

    /**
     * Parse user-entered text that may use the locale decimal separator.
     * Lenient: '.' is always accepted too (toDoubleOrNull understands it), so
     * "8.5" parses as 8.5 under any locale and "1.000" is 1.0, never 1000.
     * Safe because input comes from the in-app numpad, which controls the buffer.
     */
    fun parse(text: String, locale: Locale = Locale.getDefault()): Double? =
        text.replace(separator(locale), '.').toDoubleOrNull()
}
