package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.theme.LiftLogTheme

/**
 * A unified stepper box for reps entry (shares [StepperShell] with [WeightStepper]).
 *
 * - All interactive targets are ≥ 56 dp (logging-path a11y constraint, 03-ux-spec §7).
 * - `targetHint` is an optional rep-range suggestion shown as the unit label (null in M2).
 * - State is fully hoisted; this composable is stateless.
 */
@Composable
fun RepsStepper(
    reps: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onValueClick: () -> Unit,
    modifier: Modifier = Modifier,
    targetHint: String? = null,
    enabled: Boolean = true,
) {
    StepperShell(
        modifier = modifier,
        enabled = enabled,
        onDecrement = onDecrement,
        onIncrement = onIncrement,
        onValueClick = onValueClick,
        decrementCd = stringResource(R.string.cd_decrease_reps),
        incrementCd = stringResource(R.string.cd_increase_reps),
        valueCd = stringResource(R.string.cd_reps_value, reps),
        numberText = reps.toString(),
        unitText = targetHint ?: stringResource(R.string.reps_label),
    )
}

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "RepsStepper – plain", showBackground = true)
@Composable
private fun PreviewRepsStepperPlain() {
    LiftLogTheme {
        RepsStepper(
            reps = 8,
            onDecrement = {},
            onIncrement = {},
            onValueClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "RepsStepper – with targetHint", showBackground = true)
@Composable
private fun PreviewRepsStepperWithHint() {
    LiftLogTheme {
        RepsStepper(
            reps = 10,
            onDecrement = {},
            onIncrement = {},
            onValueClick = {},
            targetHint = "8–12",
            modifier = Modifier.padding(16.dp),
        )
    }
}
