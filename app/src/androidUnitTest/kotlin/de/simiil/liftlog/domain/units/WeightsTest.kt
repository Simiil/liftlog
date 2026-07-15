package de.simiil.liftlog.domain.units

import de.simiil.liftlog.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightsTest {
    @Test fun kg_displaysWithoutConversion() = assertEquals("82.5", Weights.format(82.5, WeightUnit.KG))

    @Test fun kg_stripsTrailingZeros() {
        assertEquals("30", Weights.format(30.0, WeightUnit.KG))
        assertEquals("27.5", Weights.format(27.5, WeightUnit.KG))
    }

    @Test fun lb_convertsAndDisplaysCleanly() {
        // 60 lb stored as 27.2155422 kg must re-display as "60"
        val kg = Weights.displayToKg(60.0, WeightUnit.LB)
        assertEquals(27.2155422, kg, 1e-7)
        assertEquals("60", Weights.format(kg, WeightUnit.LB))
    }

    @Test fun roundTripIsStable() {
        val kg = Weights.displayToKg(42.5, WeightUnit.LB)
        assertEquals(42.5, Weights.kgToDisplay(kg, WeightUnit.LB), 1e-9)
    }

    @Test fun stepIncrementIsPerUnit() {
        assertEquals(2.5, Weights.stepIncrementDisplay(WeightUnit.KG), 0.0)
        assertEquals(5.0, Weights.stepIncrementDisplay(WeightUnit.LB), 0.0)
    }

    @Test fun unitLabel() {
        assertEquals("kg", Weights.label(WeightUnit.KG))
        assertEquals("lb", Weights.label(WeightUnit.LB))
    }

    @Test fun fromStorageValue_fallsBackToKg() {
        assertEquals(WeightUnit.KG, WeightUnit.fromStorageValue(null))
        assertEquals(WeightUnit.KG, WeightUnit.fromStorageValue("STONE"))
        assertEquals(WeightUnit.LB, WeightUnit.fromStorageValue("LB"))
    }

    @Test fun format_usesLocaleDecimalSeparator() {
        assertEquals("82.5", Weights.format(82.5, WeightUnit.KG, java.util.Locale.US))
        assertEquals("82,5", Weights.format(82.5, WeightUnit.KG, java.util.Locale.GERMANY))
    }
}
