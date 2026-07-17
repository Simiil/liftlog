package de.simiil.liftlog.ui.plans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.ui.theme.LiftLogTheme

// ─── Previews ──────────────────────────────────────────────────────────────────

private val previewPlan =
    CurrentPlanUi(
        id = "1",
        name = "Push Pull Legs",
        days =
            listOf(
                PlanDayUi("d1", "Push Day", 5, listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
                PlanDayUi("d2", "Pull Day", 5, listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
                PlanDayUi("d3", "", 0, emptyList()),
            ),
    )

@Preview(name = "Plan — loading (light)", showBackground = true)
@Composable
private fun PreviewPlanLoadingLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanContent(
            state = PlanTabUiState(loading = true, plan = null),
            onOpenDay = {},
            onStartDay = {},
            onAddDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onRenamePlan = {},
            onDeletePlan = {},
            onSelectPlan = {},
            onCreatePlan = {},
        )
    }
}

@Preview(name = "Plan — populated (light)", showBackground = true)
@Composable
private fun PreviewPlanPopulatedLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanContent(
            state = PlanTabUiState(loading = false, plan = previewPlan),
            onOpenDay = {},
            onStartDay = {},
            onAddDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onRenamePlan = {},
            onDeletePlan = {},
            onSelectPlan = {},
            onCreatePlan = {},
        )
    }
}

@Preview(name = "Plan — populated (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlanPopulatedDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlanContent(
            state = PlanTabUiState(loading = false, plan = previewPlan),
            onOpenDay = {},
            onStartDay = {},
            onAddDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onRenamePlan = {},
            onDeletePlan = {},
            onSelectPlan = {},
            onCreatePlan = {},
        )
    }
}
