package de.simiil.liftlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.theme.LiftLogTheme
import java.time.Instant

/**
 * A single logged-set row that supports inline editing (long-press to expand).
 *
 * Collapsed: a `surfaceContainerHighest` pill — numbered chip · "{weight} {unit} × {reps}" ·
 * optional RPE pill / note dot · check (design mockup `.logged-row`). Long-press to edit.
 * Expanded: weight + reps steppers, RPE chip strip, note field, Delete + Save (`.edit-row`).
 *
 * Editing is OFF the hot logging path, so steppers (not numpad) are used.
 */
@Composable
fun LoggedSetRow(
    index: Int,
    set: LoggedSet,
    unit: WeightUnit,
    expanded: Boolean,
    onLongPress: () -> Unit,
    onSave: (weightKg: Double, reps: Int, rpe: Double?, note: String?) -> Unit,
    onDelete: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (expanded) {
        ExpandedSetRow(
            index = index,
            set = set,
            unit = unit,
            onSave = { w, r, rpe, note -> onSave(w, r, rpe, note); onCollapse() },
            onDelete = { onDelete(); onCollapse() },
            onCollapse = onCollapse,
            modifier = modifier,
        )
    } else {
        CollapsedSetRow(
            index = index,
            set = set,
            unit = unit,
            onLongPress = onLongPress,
            modifier = modifier,
        )
    }
}

// ─── Collapsed row ────────────────────────────────────────────────────────────

@Composable
private fun CollapsedSetRow(
    index: Int,
    set: LoggedSet,
    unit: WeightUnit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unitLong = when (unit) {
        WeightUnit.KG -> stringResource(R.string.weight_kilograms)
        WeightUnit.LB -> stringResource(R.string.weight_pounds)
    }
    val weightFormatted = Weights.format(set.weightKg, unit)
    val unitLabel = Weights.label(unit)
    val rpeFormatted = set.rpe?.let { rpe ->
        if (rpe == rpe.toLong().toDouble()) rpe.toLong().toString() else "%.1f".format(rpe)
    }
    val cdBase = stringResource(R.string.cd_set_logged, index, weightFormatted, unitLong, set.reps)
    val cdRpe = rpeFormatted?.let { stringResource(R.string.cd_set_has_rpe, it) } ?: ""
    val cdNote = if (set.note != null) stringResource(R.string.cd_set_has_note) else ""
    val cd = cdBase + cdRpe + cdNote
    val editLabel = stringResource(R.string.cd_edit_set)

    Surface(
        // The clickable merge-root MUST be the same node that carries the LOGGED_SET_ROW
        // testTag (from `modifier`) so the critical UI test's `tag AND text` matcher finds
        // one node with both the tag and the merged "{weight} {unit} × {reps}" text.
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onLongClick = onLongPress,
                onClick = { /* collapsed row: nothing on single tap */ },
            )
            .semantics {
                contentDescription = cd
                customActions = listOf(CustomAccessibilityAction(editLabel) { onLongPress(); true })
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Numbered chip
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = "$weightFormatted $unitLabel × ${set.reps}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (rpeFormatted != null) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                ) {
                    Text(
                        text = stringResource(R.string.rpe_value, rpeFormatted),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (set.note != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
            }
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Expanded row (inline edit) ───────────────────────────────────────────────

private val RPE_VALUES: List<Double> = (12..20).map { it * 0.5 } // 6.0, 6.5 … 10.0

@Composable
private fun ExpandedSetRow(
    index: Int,
    set: LoggedSet,
    unit: WeightUnit,
    onSave: (weightKg: Double, reps: Int, rpe: Double?, note: String?) -> Unit,
    onDelete: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editWeightKg by remember(set.id) { mutableDoubleStateOf(set.weightKg) }
    var editReps by remember(set.id) { mutableIntStateOf(set.reps) }
    var editRpe by remember(set.id) { mutableStateOf(set.rpe) }
    var editNote by rememberSaveable(set.id) { mutableStateOf(set.note ?: "") }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.set_number, index),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            WeightStepper(
                valueKg = editWeightKg,
                unit = unit,
                onDecrement = {
                    val stepKg = Weights.displayToKg(Weights.stepIncrementDisplay(unit), unit)
                    editWeightKg = (editWeightKg - stepKg).coerceAtLeast(0.0)
                },
                onIncrement = {
                    val stepKg = Weights.displayToKg(Weights.stepIncrementDisplay(unit), unit)
                    editWeightKg += stepKg
                },
                onValueClick = { /* no numpad for edit path */ },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            RepsStepper(
                reps = editReps,
                onDecrement = { editReps = (editReps - 1).coerceAtLeast(1) },
                onIncrement = { editReps += 1 },
                onValueClick = { /* no numpad for edit path */ },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.rpe_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = editRpe == null,
                    onClick = { editRpe = null },
                    label = { Text(stringResource(R.string.rpe_none)) },
                )
                RPE_VALUES.forEach { rpe ->
                    val label = if (rpe == rpe.toLong().toDouble()) rpe.toLong().toString() else "%.1f".format(rpe)
                    FilterChip(
                        selected = editRpe == rpe,
                        onClick = { editRpe = if (editRpe == rpe) null else rpe },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editNote,
                onValueChange = { editNote = it },
                label = { Text(stringResource(R.string.set_note)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.set_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(editWeightKg, editReps, editRpe, editNote.trim().takeIf { it.isNotEmpty() })
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.set_save))
                }
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val previewSet = LoggedSet(
    id = "s1",
    sessionExerciseId = "se1",
    weightKg = 85.0,
    reps = 8,
    position = 0,
    completedAt = Instant.ofEpochSecond(0),
    rpe = 8.5,
    note = "Felt strong",
    createdAt = Instant.ofEpochSecond(0),
    updatedAt = Instant.ofEpochSecond(0),
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
            onSave = { _, _, _, _ -> },
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
            onSave = { _, _, _, _ -> },
            onDelete = {},
            onCollapse = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
