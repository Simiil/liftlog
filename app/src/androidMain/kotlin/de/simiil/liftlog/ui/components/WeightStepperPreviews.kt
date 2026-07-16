package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.ui.theme.LiftLogTheme

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
