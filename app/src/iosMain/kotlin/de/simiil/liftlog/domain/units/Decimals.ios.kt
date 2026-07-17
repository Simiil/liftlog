package de.simiil.liftlog.domain.units

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp

/**
 * NSNumberFormatter-backed actual. Covered by the locale-agnostic commonTest basics
 * ([format]/[parse]/[separator]) plus `DecimalsIosParityTest` (iosTest, M8-PR2), which pins the
 * negative-tie rounding case specifically: `NSNumberFormatterRoundHalfUp` was an open debt item
 * (unverified whether it rounds ties away from zero like Java's `HALF_UP` for negative values) —
 * verified on the iOS simulator to already match, so no reimplementation was needed here.
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
