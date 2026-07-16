package de.simiil.liftlog.domain.units

import de.simiil.liftlog.domain.model.WeightUnit

/** kg<->display conversion + formatting (02-data-spec §5). Pure; no platform UI deps. */
object Weights {
    /** Exact factor: 1 lb = 0.45359237 kg. */
    const val KG_PER_LB: Double = 0.45359237

    fun kgToDisplay(
        weightKg: Double,
        unit: WeightUnit,
    ): Double =
        when (unit) {
            WeightUnit.KG -> weightKg
            WeightUnit.LB -> weightKg / KG_PER_LB
        }

    fun displayToKg(
        value: Double,
        unit: WeightUnit,
    ): Double =
        when (unit) {
            WeightUnit.KG -> value
            WeightUnit.LB -> value * KG_PER_LB
        }

    /** Stepper increment expressed in the display unit (03-ux-spec §4.3): 2.5 kg / 5 lb. */
    fun stepIncrementDisplay(unit: WeightUnit): Double =
        when (unit) {
            WeightUnit.KG -> 2.5
            WeightUnit.LB -> 5.0
        }

    fun label(unit: WeightUnit): String =
        when (unit) {
            WeightUnit.KG -> "kg"
            WeightUnit.LB -> "lb"
        }

    /** Display value, <=2 decimals, trailing zeros stripped, locale decimal separator. */
    fun format(
        weightKg: Double,
        unit: WeightUnit,
    ): String = Decimals.format(kgToDisplay(weightKg, unit))
}
