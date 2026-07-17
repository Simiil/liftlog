package de.simiil.liftlog.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.ui.theme.LiftLogTheme
import kotlin.time.Instant

// ─── Previews ─────────────────────────────────────────────────────────────────

private val fakeInstant = Instant.fromEpochSeconds(0)

private fun fakeSet(
    id: String,
    weightKg: Double,
    reps: Int,
) = LoggedSet(
    id = id,
    sessionExerciseId = "se1",
    weightKg = weightKg,
    reps = reps,
    position = 0,
    completedAt = fakeInstant,
    createdAt = fakeInstant,
    updatedAt = fakeInstant,
    deletedAt = null,
)

private val previewCards =
    listOf(
        ExerciseCardUi(
            sessionExerciseId = "se0",
            exerciseId = "ex0",
            name = "Squat",
            equipment = Equipment.BARBELL,
            targetSets = null,
            state = CardState.COMPLETED,
            sets = listOf(fakeSet("s0", 100.0, 5), fakeSet("s1", 100.0, 5)),
            ghostSets = emptyList(),
        ),
        ExerciseCardUi(
            sessionExerciseId = "se1",
            exerciseId = "ex1",
            name = "Bench Press",
            equipment = Equipment.BARBELL,
            targetSets = null,
            state = CardState.ACTIVE,
            sets = listOf(fakeSet("s2", 85.0, 8)),
            ghostSets = listOf(fakeSet("g1", 82.5, 8), fakeSet("g2", 82.5, 8)),
            editingSetId = null,
        ),
        ExerciseCardUi(
            sessionExerciseId = "se2",
            exerciseId = "ex2",
            name = "Row",
            equipment = Equipment.CABLE,
            targetSets = null,
            state = CardState.UPCOMING,
            sets = emptyList(),
            ghostSets = emptyList(),
        ),
    )

private val previewEntry = EntryUi(sessionExerciseId = "se1", weightKg = 87.5, reps = 8)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "ActiveSession – normal state", showBackground = true)
@Composable
private fun PreviewActiveSession() {
    LiftLogTheme {
        Scaffold(
            topBar = {
                SessionTopBar(
                    name = "Push Day",
                    elapsedSeconds = 1830,
                    onCloseClick = {},
                    onFinishClick = {},
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(previewCards, key = { it.sessionExerciseId }) { card ->
                    ExerciseCard(
                        card = card,
                        entry = previewEntry.takeIf { it.sessionExerciseId == card.sessionExerciseId },
                        unit = WeightUnit.KG,
                        onActivateCard = {},
                        onWeightIncrement = {},
                        onWeightDecrement = {},
                        onRepsIncrement = {},
                        onRepsDecrement = {},
                        onOpenNumpad = {},
                        onNumpadConfirm = {},
                        onNumpadDismiss = {},
                        onLogSet = {},
                        onRequestRemoveExercise = {},
                        onRequestReplaceExercise = {},
                        onAddExercise = {},
                        onLongPressSet = {},
                        onEditSetSave = { _, _, _ -> },
                        onDeleteSet = {},
                        onCollapseEdit = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { AddExerciseRow(onClick = {}) }
            }
        }
    }
}
