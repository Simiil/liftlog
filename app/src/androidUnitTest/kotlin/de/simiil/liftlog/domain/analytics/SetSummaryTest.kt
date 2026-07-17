package de.simiil.liftlog.domain.analytics

import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SetSummaryTest {
    private lateinit var originalLocale: Locale

    // formatSetSummary/Weights.format read the JVM default locale (no locale param); pin it to
    // US so these assertions are deterministic regardless of the environment's default.
    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test fun empty_returnsEmptyString() = assertEquals("", formatSetSummary(emptyList(), WeightUnit.KG))

    @Test fun singleSet() = assertEquals("82.5 kg × 8", formatSetSummary(listOf(SetEntry(82.5, 8)), WeightUnit.KG))

    @Test fun uniformWeight_listsAllReps_noTruncation() {
        // Issue #28 repro 1: four sets of 15 kg — the 4th set must not be dropped.
        val sets = List(4) { SetEntry(15.0, 10) }
        assertEquals("15 kg × 10·10·10·10", formatSetSummary(sets, WeightUnit.KG))
    }

    @Test fun mixedWeights_groupConsecutiveRuns_nonAdjacentEqualWeightsDontMerge() {
        // Issue #28 repro 2: 55×10, 60×9, 60×5, 55×10 must keep weight/reps pairing and order.
        val sets = listOf(SetEntry(55.0, 10), SetEntry(60.0, 9), SetEntry(60.0, 5), SetEntry(55.0, 10))
        assertEquals("55 kg × 10, 60 kg × 9·5, 55 kg × 10", formatSetSummary(sets, WeightUnit.KG))
    }

    @Test fun bodyweight_allZeroWeights_bareRepsList() {
        val sets = listOf(SetEntry(0.0, 12), SetEntry(0.0, 10), SetEntry(0.0, 8))
        assertEquals("12·10·8", formatSetSummary(sets, WeightUnit.KG))
    }

    @Test fun bodyweight_singleSet() = assertEquals("12", formatSetSummary(listOf(SetEntry(0.0, 12)), WeightUnit.KG))

    @Test fun zeroWeightMixedWithWeighted_isNotBodyweight() {
        val sets = listOf(SetEntry(0.0, 10), SetEntry(20.0, 8))
        assertEquals("0 kg × 10, 20 kg × 8", formatSetSummary(sets, WeightUnit.KG))
    }

    @Test fun germanLocale_usesCommaDecimalSeparator() {
        Locale.setDefault(Locale.GERMANY)
        val sets = listOf(SetEntry(82.5, 8), SetEntry(82.5, 8))
        assertEquals("82,5 kg × 8·8", formatSetSummary(sets, WeightUnit.KG))
    }

    @Test fun lbUnit_convertsAndLabels() {
        val kg = Weights.displayToKg(60.0, WeightUnit.LB)
        val sets = listOf(SetEntry(kg, 8), SetEntry(kg, 6))
        assertEquals("60 lb × 8·6", formatSetSummary(sets, WeightUnit.LB))
    }

    @Test fun weightsCollidingAfterDisplayRounding_mergeIntoOneRun() {
        // 27.2155422 kg and 27.216 kg both display as "60" in lb — grouping is at display resolution.
        val sets = listOf(SetEntry(27.2155422, 8), SetEntry(27.216, 6))
        assertEquals("60 lb × 8·6", formatSetSummary(sets, WeightUnit.LB))
    }
}
