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
import de.simiil.liftlog.ui.theme.LiftLogTheme

/**
 * A horizontal stepper for reps entry: [ − ]  [ reps (+ optional hint) ]  [ + ].
 *
 * - All interactive targets are ≥ 56 dp (logging-path a11y constraint, 03-ux-spec §7).
 * - `targetHint` is an optional rep-range suggestion, e.g. "8–12" (null in M2).
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
    val decrementCd = stringResource(R.string.cd_decrease_reps)
    val incrementCd = stringResource(R.string.cd_increase_reps)
    val valueCd = stringResource(R.string.cd_reps_value, reps)

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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                .clickable(enabled = enabled, onClick = onValueClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = valueCd },
        ) {
            Text(
                text = reps.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            if (targetHint != null) {
                Text(
                    text = targetHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        }

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
