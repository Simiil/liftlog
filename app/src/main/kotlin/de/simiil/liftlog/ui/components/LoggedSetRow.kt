package de.simiil.liftlog.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Collapsed: index + "{weight} {unit} × {reps} ✓"; long-press to edit, tap to collapse.
 * Expanded:  weight + reps steppers, RPE chip strip, note field, Delete + Save buttons.
 *
 * This is OFF the hot logging path, so steppers (not numpad) are used for editing.
 * All interactive targets ≥ 48 dp (non-logging-path floor per 03-ux-spec §7).
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
            onSave = { w, r, rpe, note ->
                onSave(w, r, rpe, note)
                onCollapse()
            },
            onDelete = {
                onDelete()
                onCollapse()
            },
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
    val cd = stringResource(R.string.cd_set_logged, index, weightFormatted, unitLong, set.reps)

    Row(
        modifier = modifier
            .combinedClickable(
                onLongClick = onLongPress,
                onClick = { /* collapsed row: nothing on single tap */ },
            )
            .padding(vertical = 4.dp)
            .semantics { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text = "$weightFormatted $unitLabel × ${set.reps}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Small RPE/note indicator
        if (set.rpe != null || set.note != null) {
            val indicator = buildString {
                if (set.rpe != null) {
                    append("RPE ${set.rpe}")
                }
                if (set.note != null) {
                    if (isNotEmpty()) append(" · ")
                    append("📝") // 📝
                }
            }
            Text(
                text = indicator,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
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
    // Local edit state seeded from the set — intentionally NOT rememberSaveable since
    // the editing session is transient and tied to the expanded flag driven by the VM.
    var editWeightKg by remember(set.id) { mutableDoubleStateOf(set.weightKg) }
    var editReps by remember(set.id) { mutableIntStateOf(set.reps) }
    var editRpe by remember(set.id) { mutableStateOf(set.rpe) }
    var editNote by rememberSaveable(set.id) { mutableStateOf(set.note ?: "") }

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Set $index",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Weight stepper
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

        Spacer(modifier = Modifier.height(8.dp))

        // Reps stepper
        RepsStepper(
            reps = editReps,
            onDecrement = { editReps = (editReps - 1).coerceAtLeast(1) },
            onIncrement = { editReps = editReps + 1 },
            onValueClick = { /* no numpad for edit path */ },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // RPE chips (6.0 … 10.0, step 0.5) + clear
        Text(
            text = stringResource(R.string.rpe_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // "—" clear chip
            FilterChip(
                selected = editRpe == null,
                onClick = { editRpe = null },
                label = { Text(stringResource(R.string.rpe_none)) },
            )
            RPE_VALUES.forEach { rpe ->
                val label = if (rpe == rpe.toLong().toDouble()) {
                    rpe.toLong().toString()
                } else {
                    "%.1f".format(rpe)
                }
                FilterChip(
                    selected = editRpe == rpe,
                    onClick = { editRpe = if (editRpe == rpe) null else rpe },
                    label = { Text(label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Note field
        OutlinedTextField(
            value = editNote,
            onValueChange = { editNote = it },
            label = { Text(stringResource(R.string.set_note)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Delete + Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDelete,
            ) {
                Text(
                    text = stringResource(R.string.set_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSave(
                        editWeightKg,
                        editReps,
                        editRpe,
                        editNote.trim().takeIf { it.isNotEmpty() },
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.set_save))
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
