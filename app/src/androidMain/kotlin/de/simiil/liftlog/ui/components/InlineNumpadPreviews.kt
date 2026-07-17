package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.ui.theme.LiftLogTheme

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "InlineNumpad – weight mode (decimal + chips)", showBackground = true)
@Composable
private fun PreviewInlineNumpadWeight() {
    LiftLogTheme {
        InlineNumpad(
            initialText = "82.5",
            allowDecimal = true,
            quickChips = listOf(10.0, 5.0, 2.5, -2.5),
            onConfirm = {},
            onDismiss = {},
            unitLabel = "kg",
            modifier =
                Modifier
                    .width(360.dp)
                    .padding(8.dp),
        )
    }
}

@Preview(name = "InlineNumpad – reps mode (no decimal, no chips)", showBackground = true)
@Composable
private fun PreviewInlineNumpadReps() {
    LiftLogTheme {
        InlineNumpad(
            initialText = "8",
            allowDecimal = false,
            quickChips = emptyList(),
            onConfirm = {},
            onDismiss = {},
            unitLabel = "reps",
            modifier =
                Modifier
                    .width(360.dp)
                    .padding(8.dp),
        )
    }
}
