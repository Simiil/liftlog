package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.ui.theme.LiftLogTheme
import kotlin.time.Instant

// ─── Previews ─────────────────────────────────────────────────────────────────

private val previewSet =
    LoggedSet(
        id = "s1",
        sessionExerciseId = "se1",
        weightKg = 85.0,
        reps = 8,
        position = 0,
        completedAt = Instant.fromEpochSeconds(0),
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
        deletedAt = null,
    )

@Preview(name = "LoggedSetRow – collapsed", showBackground = true)
@Composable
private fun PreviewLoggedSetRowCollapsed() {
    LiftLogTheme {
        LoggedSetRow(
            index = 1,
            set = previewSet,
            unit = WeightUnit.KG,
            expanded = false,
            onLongPress = {},
            onSave = { _, _ -> },
            onDelete = {},
            onCollapse = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Preview(name = "LoggedSetRow – expanded", showBackground = true)
@Composable
private fun PreviewLoggedSetRowExpanded() {
    LiftLogTheme {
        LoggedSetRow(
            index = 1,
            set = previewSet,
            unit = WeightUnit.KG,
            expanded = true,
            onLongPress = {},
            onSave = { _, _ -> },
            onDelete = {},
            onCollapse = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
