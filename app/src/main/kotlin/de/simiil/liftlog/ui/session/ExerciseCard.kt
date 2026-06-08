package de.simiil.liftlog.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.UiTestTags
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

private val CARD_SHAPE = RoundedCornerShape(22.dp)

// ─── ExerciseCard ─────────────────────────────────────────────────────────────

@Composable
fun ExerciseCard(
    card: ExerciseCardUi,
    entry: EntryUi?,
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
        shape = CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
        shape = CARD_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {

            // ── Header ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                ProgressPill(setsDone = card.sets.size, target = card.targetSets)
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
                Text(
                    text = stringResource(R.string.ghost_last, formatSetsSummary(card.ghostSets, unit)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
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
                    onSave = { w, r, rpe, note -> onEditSetSave(set.id, w, r, rpe, note) },
                    onDelete = { onDeleteSet(set.id) },
                    onCollapse = onCollapseEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .testTag(UiTestTags.LOGGED_SET_ROW),
                )
            }

            Spacer(Modifier.height(12.dp))

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
                        unitLabel = if (numpad.target == NumpadTarget.WEIGHT) {
                            Weights.label(unit)
                        } else {
                            stringResource(R.string.reps_label)
                        },
                        onConfirm = { onNumpadConfirm(it) },
                        onDismiss = { onNumpadDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        WeightStepper(
                            valueKg = entry.weightKg,
                            unit = unit,
                            onDecrement = onWeightDecrement,
                            onIncrement = onWeightIncrement,
                            onValueClick = { onOpenNumpad(NumpadTarget.WEIGHT) },
                            valueTestTag = UiTestTags.WEIGHT_VALUE,
                            incrementTestTag = UiTestTags.WEIGHT_INCREMENT,
                            modifier = Modifier.weight(1f),
                        )
                        RepsStepper(
                            reps = entry.reps,
                            onDecrement = onRepsDecrement,
                            onIncrement = onRepsIncrement,
                            onValueClick = { onOpenNumpad(NumpadTarget.REPS) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LogSetButton(enabled = entry.weightKg != null, onClick = onLogSet)

                    // Reserved rest-timer slot (v1: empty placeholder) — DO NOT fill.
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ProgressPill(setsDone: Int, target: Int?) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.session_progress, setsDone, target?.toString() ?: "—"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun LogSetButton(enabled: Boolean, onClick: () -> Unit) {
    val container = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .testTag(UiTestTags.LOG_SET_BUTTON),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.session_log_set),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

// ─── Formatting helper ────────────────────────────────────────────────────────

/** Compact set summary, e.g. "82.5 kg × 8·8·6" (first set's weight + all reps). */
internal fun formatSetsSummary(sets: List<LoggedSet>, unit: WeightUnit): String {
    if (sets.isEmpty()) return ""
    val weightDisplay = "${Weights.format(sets.first().weightKg, unit)} ${Weights.label(unit)}"
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

private val fakeGhostSets = listOf(fakeSet("g1", 82.5, 8), fakeSet("g2", 82.5, 8), fakeSet("g3", 82.5, 7))
private val fakeLoggedSets = listOf(fakeSet("s1", 85.0, 8), fakeSet("s2", 85.0, 6, rpe = 8.5))

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
        onEditSetSave = { _, _, _, _, _ -> },
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
