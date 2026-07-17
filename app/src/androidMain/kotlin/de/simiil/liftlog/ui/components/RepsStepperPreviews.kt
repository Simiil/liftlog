package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.ui.theme.LiftLogTheme

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
