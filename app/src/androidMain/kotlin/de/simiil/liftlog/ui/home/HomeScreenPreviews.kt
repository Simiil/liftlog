package de.simiil.liftlog.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.ui.format.AndroidLocaleFormatters
import de.simiil.liftlog.ui.theme.LiftLogTheme

// Previews
// ──────────────────────────────────────────────────────────────────────────────

@Preview(name = "Home – chips + empty card (light)", showBackground = true)
@Composable
private fun PreviewHomeWithChips() {
    LiftLogTheme {
        HomeContent(
            uiState =
                HomeUiState(
                    templates =
                        listOf(
                            TemplateChipUi("t1", "Push", 5, listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
                            TemplateChipUi("t2", "Pull", 5, listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
                            TemplateChipUi("t3", "Legs", 4, listOf(MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)),
                        ),
                    recent = emptyList(),
                ),
            formatters = AndroidLocaleFormatters(context = null),
            onResume = {},
            onStartEmpty = {},
            onStartFromTemplate = {},
            onOpenSessionDetail = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Home – no plans, empty card only (light)", showBackground = true)
@Composable
private fun PreviewHomeNoPlans() {
    LiftLogTheme {
        HomeContent(
            uiState =
                HomeUiState(
                    templates = emptyList(),
                    recent = emptyList(),
                ),
            formatters = AndroidLocaleFormatters(context = null),
            onResume = {},
            onStartEmpty = {},
            onStartFromTemplate = {},
            onOpenSessionDetail = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
