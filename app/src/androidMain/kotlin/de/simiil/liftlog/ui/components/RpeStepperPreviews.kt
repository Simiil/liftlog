package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.ui.theme.LiftLogTheme

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "RpeStepper – unset", showBackground = true)
@Composable
private fun PreviewRpeStepperUnset() {
    LiftLogTheme { RpeStepper(value = null, onValueChange = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(name = "RpeStepper – whole value", showBackground = true)
@Composable
private fun PreviewRpeStepperWhole() {
    LiftLogTheme { RpeStepper(value = 8.0, onValueChange = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(name = "RpeStepper – half value", showBackground = true)
@Composable
private fun PreviewRpeStepperHalf() {
    LiftLogTheme { RpeStepper(value = 8.5, onValueChange = {}, modifier = Modifier.padding(16.dp)) }
}
