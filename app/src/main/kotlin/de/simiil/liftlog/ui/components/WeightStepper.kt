package de.simiil.liftlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.theme.LiftLogTheme

/**
 * A horizontal stepper for weight entry: [ − ]  [ value + unit ]  [ + ].
 *
 * - `valueKg = null` means "never performed / empty" — shows a placeholder and still
 *   allows tapping the center area to open the numpad.
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
) {
    val unitLong = when (unit) {
        WeightUnit.KG -> stringResource(R.string.weight_kilograms)
        WeightUnit.LB -> stringResource(R.string.weight_pounds)
    }
    val stepDisplay = Weights.stepIncrementDisplay(unit)
    val stepText = Weights.format(stepDisplay, WeightUnit.KG).let {
        // stepIncrementDisplay returns a display-unit value; format it without conversion
        when (unit) {
            WeightUnit.KG -> "%.4g".format(stepDisplay).trimEnd('0').trimEnd('.')
            WeightUnit.LB -> "%.4g".format(stepDisplay).trimEnd('0').trimEnd('.')
        }
    }

    val decrementCd = stringResource(R.string.cd_decrease_weight, stepText, unitLong)
    val incrementCd = stringResource(R.string.cd_increase_weight, stepText, unitLong)

    val valueText: String
    val valueCd: String
    if (valueKg != null) {
        val formatted = Weights.format(valueKg, unit)
        val unitShort = Weights.label(unit)
        valueText = "$formatted $unitShort"
        valueCd = stringResource(R.string.cd_weight_value, formatted, unitShort)
    } else {
        valueText = stringResource(R.string.weight_placeholder)
        valueCd = stringResource(R.string.cd_weight_value, "—", Weights.label(unit))
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Decrement button
        FilledTonalIconButton(
            onClick = onDecrement,
            enabled = enabled,
            modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = decrementCd,
            )
        }

        // Value area — clickable to open numpad
        Text(
            text = valueText,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                .clickable(enabled = enabled, onClick = onValueClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = valueCd },
        )

        // Increment button
        FilledTonalIconButton(
            onClick = onIncrement,
            enabled = enabled,
            modifier = Modifier.defaultMinSize(minWidth = 56.dp, minHeight = 56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = incrementCd,
            )
        }
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
