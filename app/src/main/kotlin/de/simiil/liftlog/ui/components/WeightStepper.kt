package de.simiil.liftlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.theme.LiftLogTheme

/**
 * A unified stepper box for weight entry: a single `surfaceContainerHighest` pill,
 * 76 dp tall, with full-height `−` / `+` side buttons and a tappable centered value
 * (number + unit) that opens the inline numpad (03-ux-spec §4.3 / design mockup `.stepper`).
 *
 * - `valueKg = null` means "never performed / empty" — shows a placeholder, still tappable.
 * - All interactive targets are ≥ 56 dp (logging-path a11y constraint, 03-ux-spec §7).
 * - State is fully hoisted; this composable is stateless.
 */
@Composable
fun WeightStepper(
    valueKg: Double?,
    unit: WeightUnit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Test-only handles (null in every production call except the active logging card,
    // so the critical-path UI test can address the increment button + value display).
    valueTestTag: String? = null,
    incrementTestTag: String? = null,
) {
    val unitLong = when (unit) {
        WeightUnit.KG -> stringResource(R.string.weight_kilograms)
        WeightUnit.LB -> stringResource(R.string.weight_pounds)
    }
    val stepDisplay = Weights.stepIncrementDisplay(unit)
    val stepText = if (stepDisplay == stepDisplay.toLong().toDouble()) {
        stepDisplay.toLong().toString()
    } else {
        stepDisplay.toString()
    }
    val decrementCd = stringResource(R.string.cd_decrease_weight, stepText, unitLong)
    val incrementCd = stringResource(R.string.cd_increase_weight, stepText, unitLong)

    val numberText: String
    val valueCd: String
    if (valueKg != null) {
        val formatted = Weights.format(valueKg, unit)
        numberText = formatted
        valueCd = stringResource(R.string.cd_weight_value, formatted, Weights.label(unit))
    } else {
        numberText = stringResource(R.string.weight_placeholder)
        valueCd = stringResource(R.string.cd_weight_value, "—", Weights.label(unit))
    }

    StepperShell(
        modifier = modifier,
        enabled = enabled,
        onDecrement = onDecrement,
        onIncrement = onIncrement,
        onValueClick = onValueClick,
        decrementCd = decrementCd,
        incrementCd = incrementCd,
        valueCd = valueCd,
        numberText = numberText,
        unitText = Weights.label(unit),
        valueTestTag = valueTestTag,
        incrementTestTag = incrementTestTag,
    )
}

/**
 * Shared visual shell for the weight / reps steppers. Internal so both steppers reuse it.
 */
@Composable
internal fun StepperShell(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit,
    decrementCd: String,
    incrementCd: String,
    valueCd: String,
    numberText: String,
    unitText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueTestTag: String? = null,
    incrementTestTag: String? = null,
) {
    Surface(
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepSideButton(
                glyph = "−",
                onClick = onDecrement,
                enabled = enabled,
                contentDesc = decrementCd,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier)
                    .clickable(enabled = enabled, onClick = onValueClick)
                    .semantics { contentDescription = valueCd }
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = numberText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                )
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StepSideButton(
                glyph = "+",
                onClick = onIncrement,
                enabled = enabled,
                contentDesc = incrementCd,
                testTag = incrementTestTag,
            )
        }
    }
}

@Composable
private fun StepSideButton(
    glyph: String,
    onClick: () -> Unit,
    enabled: Boolean,
    contentDesc: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Box(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "WeightStepper – KG with value", showBackground = true)
@Composable
private fun PreviewWeightStepperKgValue() {
    LiftLogTheme {
        WeightStepper(
            valueKg = 82.5,
            unit = WeightUnit.KG,
            onDecrement = {},
            onIncrement = {},
            onValueClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "WeightStepper – empty (null)", showBackground = true)
@Composable
private fun PreviewWeightStepperEmpty() {
    LiftLogTheme {
        WeightStepper(
            valueKg = null,
            unit = WeightUnit.KG,
            onDecrement = {},
            onIncrement = {},
            onValueClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "WeightStepper – LB with value", showBackground = true)
@Composable
private fun PreviewWeightStepperLbValue() {
    LiftLogTheme {
        WeightStepper(
            valueKg = 82.5,
            unit = WeightUnit.LB,
            onDecrement = {},
            onIncrement = {},
            onValueClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
