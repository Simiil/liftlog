package de.simiil.liftlog.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
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

private val fakeGhostSets = listOf(fakeSet("g1", 82.5, 8), fakeSet("g2", 82.5, 8), fakeSet("g3", 82.5, 7))
private val fakeLoggedSets = listOf(fakeSet("s1", 85.0, 8), fakeSet("s2", 85.0, 6))

private val fakeActiveCard =
    ExerciseCardUi(
        sessionExerciseId = "se1",
        exerciseId = "ex1",
        name = "Bench Press",
        equipment = Equipment.BARBELL,
        targetSets = null,
        state = CardState.ACTIVE,
        sets = fakeLoggedSets,
        ghostSets = fakeGhostSets,
        editingSetId = null,
    )

private val fakeEntry = EntryUi(sessionExerciseId = "se1", weightKg = 87.5, reps = 8)

private val noopCard: @Composable (ExerciseCardUi, EntryUi?) -> Unit = { card, entry ->
    ExerciseCard(
        card = card,
        entry = entry,
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
    )
}

@Preview(name = "ExerciseCard – ACTIVE (ghost + 2 sets)", showBackground = true)
@Composable
private fun PreviewActiveCard() {
    LiftLogTheme { noopCard(fakeActiveCard, fakeEntry) }
}

@Preview(name = "ExerciseCard – ACTIVE with numpad open", showBackground = true)
@Composable
private fun PreviewActiveCardNumpad() {
    LiftLogTheme { noopCard(fakeActiveCard, fakeEntry.copy(numpad = NumpadUi(NumpadTarget.WEIGHT, "87.5"))) }
}

@Preview(name = "ExerciseCard – COMPLETED", showBackground = true)
@Composable
private fun PreviewCompletedCard() {
    LiftLogTheme { noopCard(fakeActiveCard.copy(state = CardState.COMPLETED, ghostSets = emptyList()), null) }
}

@Preview(name = "ExerciseCard – COMPLETED mixed weights (grouped summary, ellipsis)", showBackground = true)
@Composable
private fun PreviewCompletedCardMixedWeights() {
    val mixed = listOf(fakeSet("m1", 55.0, 10), fakeSet("m2", 60.0, 9), fakeSet("m3", 60.0, 5), fakeSet("m4", 55.0, 10))
    LiftLogTheme { noopCard(fakeActiveCard.copy(state = CardState.COMPLETED, sets = mixed, ghostSets = emptyList()), null) }
}

@Preview(name = "ExerciseCard – UPCOMING", showBackground = true)
@Composable
private fun PreviewUpcomingCard() {
    LiftLogTheme {
        noopCard(fakeActiveCard.copy(state = CardState.UPCOMING, sets = emptyList(), ghostSets = emptyList()), null)
    }
}

@Preview(name = "ExerciseCard – ACTIVE first-ever (weightKg null, LOG SET disabled)", showBackground = true)
@Composable
private fun PreviewActiveCardFirstEver() {
    LiftLogTheme {
        noopCard(fakeActiveCard.copy(sets = emptyList(), ghostSets = emptyList()), fakeEntry.copy(weightKg = null))
    }
}
