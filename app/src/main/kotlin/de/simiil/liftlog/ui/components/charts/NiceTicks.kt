package de.simiil.liftlog.ui.components.charts

import de.simiil.liftlog.domain.format.LocaleFormatters
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** Ascending axis ticks at nice (1/2/5×10ⁿ) steps; first ≤ min, last ≥ max (full coverage). */
fun niceTicks(
    min: Double,
    max: Double,
    targetCount: Int = 5,
): List<Double> {
    val end = if (max > min) max else min + 1.0
    val rawStep = (end - min) / (targetCount - 1)
    val mag = 10.0.pow(floor(log10(rawStep)))
    val fraction = rawStep / mag
    val step =
        mag *
            if (fraction < 1.5) {
                1.0
            } else if (fraction < 3.0) {
                2.0
            } else if (fraction < 7.0) {
                5.0
            } else {
                10.0
            }
    val start = floor(min / step) * step
    return generateSequence(start) { it + step }.takeWhile { it < end + step }.toList()
}

/** Whole ticks render as plain integers; fractional ticks are user-visible weight/volume
 *  values and go through the locale seam (82.5 vs 82,5 — 08-i18n-spec §5). */
fun tickLabel(
    v: Double,
    formatters: LocaleFormatters,
): String {
    val rounded = v.roundToLong()
    return if (abs(v - rounded) < 1e-9) rounded.toString() else formatters.oneDecimal(v)
}
