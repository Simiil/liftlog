package de.simiil.liftlog.ui.session

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.components.InlineNumpad
import de.simiil.liftlog.ui.components.LoggedSetRow
import de.simiil.liftlog.ui.components.RepsStepper
import de.simiil.liftlog.ui.components.WeightStepper
import de.simiil.liftlog.ui.theme.LiftLogTheme
import java.time.Instant

// ─── ExerciseCard ─────────────────────────────────────────────────────────────

/**
 * Renders a single exercise card in the Active Session screen.
 *
 * State is determined by [card.state]:
 *  - COMPLETED  collapsed summary with set recap
 *  - UPCOMING   collapsed name-only row
 *  - ACTIVE     fully expanded entry area with steppers / numpad + LOG SET
 */
@Composable
fun ExerciseCard(
    card: ExerciseCardUi,
    entry: EntryUi?,           // non-null only when card.state == ACTIVE
    unit: WeightUnit,
    onActivateCard: (seId: String) -> Unit,
    onWeightIncrement: () -> Unit,
    onWeightDecrement: () -> Unit,
    onRepsIncrement: () -> Unit,
    onRepsDecrement: () -> Unit,
    onOpenNumpad: (NumpadTarget) -> Unit,
    onNumpadConfirm: (String) -> Unit,
    onNumpadDismiss: () -> Unit,
    onLogSet: () -> Unit,
    onRequestRemoveExercise: (seId: String) -> Unit,
    onRequestReplaceExercise: (seId: String) -> Unit,
    onAddExercise: () -> Unit,
    onLongPressSet: (setId: String) -> Unit,
    onEditSetSave: (setId: String, weightKg: Double, reps: Int, rpe: Double?, note: String?) -> Unit,
    onDeleteSet: (setId: String) -> Unit,
    onCollapseEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card.state) {
        CardState.COMPLETED -> CompletedCard(card, unit, onActivateCard, modifier)
        CardState.UPCOMING  -> UpcomingCard(card, onActivateCard, modifier)
        CardState.ACTIVE    -> ActiveCard(
            card = card,
            entry = entry,
            unit = unit,
            onWeightIncrement = onWeightIncrement,
            onWeightDecrement = onWeightDecrement,
            onRepsIncrement = onRepsIncrement,
            onRepsDecrement = onRepsDecrement,
            onOpenNumpad = onOpenNumpad,
            onNumpadConfirm = onNumpadConfirm,
            onNumpadDismiss = onNumpadDismiss,
            onLogSet = onLogSet,
            onRequestRemoveExercise = onRequestRemoveExercise,
            onRequestReplaceExercise = onRequestReplaceExercise,
            onAddExercise = onAddExercise,
            onLongPressSet = onLongPressSet,
            onEditSetSave = onEditSetSave,
            onDeleteSet = onDeleteSet,
            onCollapseEdit = onCollapseEdit,
            modifier = modifier,
        )
    }
}

// ─── Collapsed: COMPLETED ─────────────────────────────────────────────────────

@Composable
private fun CompletedCard(
    card: ExerciseCardUi,
    unit: WeightUnit,
    onActivateCard: (seId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = formatSetsSummary(card.sets, unit)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onActivateCard(card.sessionExerciseId) }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓ ${card.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ─── Collapsed: UPCOMING ──────────────────────────────────────────────────────

@Composable
private fun UpcomingCard(
    card: ExerciseCardUi,
    onActivateCard: (seId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onActivateCard(card.sessionExerciseId) }),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (card.targetSets != null) {
                Text(
                    text = "target ${card.targetSets}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Expanded: ACTIVE ─────────────────────────────────────────────────────────

@Composable
private fun ActiveCard(
    card: ExerciseCardUi,
    entry: EntryUi?,
    unit: WeightUnit,
    onWeightIncrement: () -> Unit,
    onWeightDecrement: () -> Unit,
    onRepsIncrement: () -> Unit,
    onRepsDecrement: () -> Unit,
    onOpenNumpad: (NumpadTarget) -> Unit,
    onNumpadConfirm: (String) -> Unit,
    onNumpadDismiss: () -> Unit,
    onLogSet: () -> Unit,
    onRequestRemoveExercise: (seId: String) -> Unit,
    onRequestReplaceExercise: (seId: String) -> Unit,
    onAddExercise: () -> Unit,
    onLongPressSet: (setId: String) -> Unit,
    onEditSetSave: (setId: String, weightKg: Double, reps: Int, rpe: Double?, note: String?) -> Unit,
    onDeleteSet: (setId: String) -> Unit,
    onCollapseEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                // Progress: "2/—" or "2/5"
                val targetLabel = card.targetSets?.toString() ?: "—"
                Text(
                    text = stringResource(R.string.session_progress, card.sets.size, targetLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )

                // Overflow menu (⋮)
                Box {
                    val overflowCd = stringResource(R.string.session_overflow)
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier.semantics { contentDescription = overflowCd },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_remove_exercise)) },
                            onClick = {
                                overflowExpanded = false
                                onRequestRemoveExercise(card.sessionExerciseId)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.session_replace_exercise)) },
                            onClick = {
                                overflowExpanded = false
                                onRequestReplaceExercise(card.sessionExerciseId)
                                onAddExercise()
                            },
                        )
                    }
                }
            }

            // ── Ghost row (last performance) ─────────────────────────────
            if (card.ghostSets.isNotEmpty()) {
                val ghostSummary = formatSetsSummary(card.ghostSets, unit)
                Text(
                    text = stringResource(R.string.ghost_last, ghostSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ── Logged-set rows ──────────────────────────────────────────
            card.sets.forEachIndexed { index, set ->
                LoggedSetRow(
                    index = index + 1,
                    set = set,
                    unit = unit,
                    expanded = card.editingSetId == set.id,
                    onLongPress = { onLongPressSet(set.id) },
                    onSave = { w, r, rpe, note ->
                        onEditSetSave(set.id, w, r, rpe, note)
                    },
                    onDelete = { onDeleteSet(set.id) },
                    onCollapse = onCollapseEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Entry area (steppers or numpad) ──────────────────────────
            if (entry != null) {
                val numpad = entry.numpad
                if (numpad != null) {
                    InlineNumpad(
                        initialText = numpad.text,
                        allowDecimal = numpad.target == NumpadTarget.WEIGHT,
                        quickChips = if (numpad.target == NumpadTarget.WEIGHT) {
                            listOf(10.0, 5.0, 2.5, -2.5)
                        } else {
                            emptyList()
                        },
                        onConfirm = { onNumpadConfirm(it) },
                        onDismiss = { onNumpadDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    WeightStepper(
                        valueKg = entry.weightKg,
                        unit = unit,
                        onDecrement = onWeightDecrement,
                        onIncrement = onWeightIncrement,
                        onValueClick = { onOpenNumpad(NumpadTarget.WEIGHT) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RepsStepper(
                        reps = entry.reps,
                        onDecrement = onRepsDecrement,
                        onIncrement = onRepsIncrement,
                        onValueClick = { onOpenNumpad(NumpadTarget.REPS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── LOG SET ─────────────────────────────────────────────
                Button(
                    onClick = onLogSet,
                    enabled = entry.weightKg != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.session_log_set),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                // ── Reserved rest-timer slot (v1: empty placeholder) ────
                // DO NOT fill this in v1 — the rest timer lands here in a future milestone.
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

// ─── Formatting helper ────────────────────────────────────────────────────────

/**
 * Compact set summary, e.g. "82.5 kg × 8·8·6".
 * Groups by weight for brevity; falls back to listing all reps.
 */
internal fun formatSetsSummary(sets: List<LoggedSet>, unit: WeightUnit): String {
    if (sets.isEmpty()) return ""
    // Simple format: use the first set's weight + all reps joined by ·
    val weightKg = sets.first().weightKg
    val weightDisplay = "${Weights.format(weightKg, unit)} ${Weights.label(unit)}"
    val repsStr = sets.joinToString("·") { it.reps.toString() }
    return "$weightDisplay × $repsStr"
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val fakeInstant = Instant.ofEpochSecond(0)

private fun fakeSet(id: String, weightKg: Double, reps: Int, rpe: Double? = null, note: String? = null) = LoggedSet(
    id = id,
    sessionExerciseId = "se1",
    weightKg = weightKg,
    reps = reps,
    position = 0,
    completedAt = fakeInstant,
    rpe = rpe,
    note = note,
    createdAt = fakeInstant,
    updatedAt = fakeInstant,
    deletedAt = null,
)

private val fakeGhostSets = listOf(
    fakeSet("g1", 82.5, 8),
    fakeSet("g2", 82.5, 8),
    fakeSet("g3", 82.5, 7),
)

private val fakeLoggedSets = listOf(
    fakeSet("s1", 85.0, 8),
    fakeSet("s2", 85.0, 6, rpe = 8.5),
)

private val fakeActiveCard = ExerciseCardUi(
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

private val fakeEntry = EntryUi(
    sessionExerciseId = "se1",
    weightKg = 87.5,
    reps = 8,
)

@Preview(name = "ExerciseCard – ACTIVE (ghost + 2 sets)", showBackground = true)
@Composable
private fun PreviewActiveCard() {
    LiftLogTheme {
        ExerciseCard(
            card = fakeActiveCard,
            entry = fakeEntry,
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
            onEditSetSave = { _, _, _, _, _ -> },
            onDeleteSet = {},
            onCollapseEdit = {},
        )
    }
}

@Preview(name = "ExerciseCard – ACTIVE with numpad open", showBackground = true)
@Composable
private fun PreviewActiveCardNumpad() {
    LiftLogTheme {
        ExerciseCard(
            card = fakeActiveCard,
            entry = fakeEntry.copy(numpad = NumpadUi(NumpadTarget.WEIGHT, "87.5")),
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
            onEditSetSave = { _, _, _, _, _ -> },
            onDeleteSet = {},
            onCollapseEdit = {},
        )
    }
}

@Preview(name = "ExerciseCard – COMPLETED", showBackground = true)
@Composable
private fun PreviewCompletedCard() {
    LiftLogTheme {
        ExerciseCard(
            card = fakeActiveCard.copy(state = CardState.COMPLETED, ghostSets = emptyList()),
            entry = null,
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
            onEditSetSave = { _, _, _, _, _ -> },
            onDeleteSet = {},
            onCollapseEdit = {},
        )
    }
}

@Preview(name = "ExerciseCard – UPCOMING", showBackground = true)
@Composable
private fun PreviewUpcomingCard() {
    LiftLogTheme {
        ExerciseCard(
            card = fakeActiveCard.copy(
                state = CardState.UPCOMING,
                sets = emptyList(),
                ghostSets = emptyList(),
            ),
            entry = null,
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
            onEditSetSave = { _, _, _, _, _ -> },
            onDeleteSet = {},
            onCollapseEdit = {},
        )
    }
}

@Preview(name = "ExerciseCard – ACTIVE first-ever (weightKg null, LOG SET disabled)", showBackground = true)
@Composable
private fun PreviewActiveCardFirstEver() {
    LiftLogTheme {
        ExerciseCard(
            card = fakeActiveCard.copy(sets = emptyList(), ghostSets = emptyList()),
            entry = fakeEntry.copy(weightKg = null),
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
            onEditSetSave = { _, _, _, _, _ -> },
            onDeleteSet = {},
            onCollapseEdit = {},
        )
    }
}
