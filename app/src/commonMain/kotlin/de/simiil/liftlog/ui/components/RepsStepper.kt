package de.simiil.liftlog.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.cd_decrease_reps
import liftlog.app.generated.resources.cd_increase_reps
import liftlog.app.generated.resources.cd_reps_value
import liftlog.app.generated.resources.reps_label
import org.jetbrains.compose.resources.stringResource

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
        decrementCd = stringResource(Res.string.cd_decrease_reps, (reps - 1).coerceAtLeast(0)),
        incrementCd = stringResource(Res.string.cd_increase_reps, reps + 1),
        valueCd = stringResource(Res.string.cd_reps_value, reps),
        numberText = reps.toString(),
        unitText = targetHint ?: stringResource(Res.string.reps_label),
    )
}
