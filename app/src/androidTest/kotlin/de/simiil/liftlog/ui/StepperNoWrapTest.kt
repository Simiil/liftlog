package de.simiil.liftlog.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.components.WeightStepper
import de.simiil.liftlog.ui.theme.LiftLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the stepper value wrapping (e.g. "102,5" breaking onto two lines
 * and pushing the unit label out of the fixed-height shell). The value column inside
 * [WeightStepper] only gets `stepper width − 112dp (two 56dp side buttons) − padding`,
 * so on a phone-width screen (~170dp per stepper) a 5-character weight must shrink to
 * fit a single line rather than wrap.
 */
@RunWith(AndroidJUnit4::class)
class StepperNoWrapTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Lays out a [WeightStepper] at phone-realistic width and returns the value's text layout. */
    private fun layoutWeightValue(valueKg: Double): TextLayoutResult {
        composeRule.setContent {
            LiftLogTheme {
                Box(Modifier.width(170.dp)) {
                    WeightStepper(
                        valueKg = valueKg,
                        unit = WeightUnit.KG,
                        onDecrement = {},
                        onIncrement = {},
                        onValueClick = {},
                    )
                }
            }
        }
        val expected = Weights.format(valueKg, WeightUnit.KG) // locale-correct, e.g. "102,5"
        // The stepper's clickable value area merges descendants — query the unmerged tree.
        val node = composeRule.onNodeWithText(expected, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    @Test
    fun fiveCharWeight_staysOnOneLine_withoutClipping() {
        val layout = layoutWeightValue(102.5)
        assertEquals("weight value must not wrap", 1, layout.lineCount)
        assertFalse("weight value must not be clipped", layout.didOverflowWidth)
    }

    @Test
    fun shortWeight_keepsFullHeadlineSize() {
        val layout = layoutWeightValue(80.0)
        assertEquals("short values must not shrink", 24.sp, layout.layoutInput.style.fontSize)
        assertEquals(1, layout.lineCount)
    }
}
