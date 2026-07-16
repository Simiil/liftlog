package de.simiil.liftlog.domain.units

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp

/**
 * NSNumberFormatter-backed actual. Behavior verified on-device in M8 (no Xcode toolchain on the
 * machine that authored this — compile-gated only); unit-covered by the commonTest basics that
 * exercise [format]/[parse]/[separator] once the simulator run is available.
 */
actual object Decimals {
    actual fun separator(): Char = NSNumberFormatter().decimalSeparator.first()

    actual fun format(value: Double): String {
        val formatter =
            NSNumberFormatter().apply {
                numberStyle = NSNumberFormatterDecimalStyle
                maximumFractionDigits = 2u
                minimumFractionDigits = 0u
                roundingMode = NSNumberFormatterRoundHalfUp
                usesGroupingSeparator = false
            }
        return formatter.stringFromNumber(NSNumber(double = value)) ?: value.toString()
    }

    actual fun parse(text: String): Double? = text.replace(separator(), '.').replace(',', '.').toDoubleOrNull()
}
