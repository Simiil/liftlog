package de.simiil.liftlog.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.ui.components.dashedBorder
import de.simiil.liftlog.ui.exercises.equipmentLabel
import de.simiil.liftlog.ui.exercises.muscleGroupLabel

// Reusable pieces shared by PlanEditorScreen (plan + legacy day mode) and DayEditorScreen
// (the new DB-backed, single-day autosave editor). Extracted verbatim from PlanEditorScreen.kt
// — no behavior change; ExerciseEditorRow alone had its parameters generalized from the old
// editor's EditorItemUi to plain fields so both screens' row models can supply them.

// ─── Header ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorHeader(
    title: String,
    onClose: () -> Unit,
    closeIsBack: Boolean,
    closeTag: String?,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    actionTag: String,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = if (closeTag != null) Modifier.testTag(closeTag) else Modifier,
            ) {
                if (closeIsBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            }
        },
        actions = {
            // .editor-save / Done pill: primary when enabled, surfaceContainerHighest when not.
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .height(40.dp)
                        .testTag(actionTag),
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

// ─── Field labels + text field ───────────────────────────────────────────────

@Composable
internal fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}

@Composable
internal fun FieldLabelWithCount(
    label: String,
    count: Int,
    topSpacing: Dp,
) {
    Row(
        modifier = Modifier.padding(top = topSpacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel(label)
        // .field-count pill: primary 14% bg, primary text.
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
    )
}

// ─── Empty state + dashed add-row ────────────────────────────────────────────

@Composable
internal fun EditorEmpty(
    text: String,
    modifier: Modifier = Modifier,
) {
    // .editor-empty: surfaceContainerLow bg, radius 16dp, centered, onSurfaceVariant.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
        )
    }
}

@Composable
internal fun AddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // .add-row: 1.5dp dashed outline, primary text, radius 16dp, 54dp.
    Box(
        modifier =
            modifier
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .dashedBorder(
                    color = MaterialTheme.colorScheme.outline,
                    width = 1.5.dp,
                    cornerRadius = 16.dp,
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Exercise row with target steppers ───────────────────────────────────────

@Composable
internal fun ExerciseEditorRow(
    name: String,
    equipment: Equipment,
    muscleGroup: MuscleGroup,
    targetSets: Int?,
    targetRepsMin: Int?,
    targetRepsMax: Int?,
    isDragging: Boolean,
    onRemove: () -> Unit,
    onSetTargets: (sets: Int?, repsMin: Int?, repsMax: Int?) -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val dragHandleCd = stringResource(R.string.template_drag_handle_cd)
    val removeCd = stringResource(R.string.template_remove_exercise)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color =
            if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
    ) {
        Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
            // .ex-edit-top: drag handle · name + "{group} · {equip}" · remove
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        dragHandleModifier
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .semantics { contentDescription = dragHandleCd },
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${muscleGroupLabel(muscleGroup)} · ${equipmentLabel(equipment)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .semantics { contentDescription = removeCd },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // .ex-edit-targets: three min/max steppers (Sets / Reps min / Reps max)
            Row(
                modifier = Modifier.padding(start = 44.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TargetStepper(
                    label = stringResource(R.string.template_target_sets),
                    value = targetSets,
                    onDecrement = {
                        val next = (targetSets ?: 1) - 1
                        onSetTargets(next.takeIf { it > 0 }, targetRepsMin, targetRepsMax)
                    },
                    onIncrement = {
                        onSetTargets(((targetSets ?: 0) + 1).coerceAtMost(10), targetRepsMin, targetRepsMax)
                    },
                    modifier = Modifier.weight(1f),
                )
                TargetStepper(
                    label = stringResource(R.string.template_target_reps_min),
                    value = targetRepsMin,
                    onDecrement = {
                        val next = (targetRepsMin ?: 1) - 1
                        onSetTargets(targetSets, next.takeIf { it > 0 }, targetRepsMax)
                    },
                    onIncrement = {
                        onSetTargets(targetSets, ((targetRepsMin ?: 0) + 1).coerceAtMost(50), targetRepsMax)
                    },
                    modifier = Modifier.weight(1f),
                )
                TargetStepper(
                    label = stringResource(R.string.template_target_reps_max),
                    value = targetRepsMax,
                    onDecrement = {
                        val next = (targetRepsMax ?: 1) - 1
                        onSetTargets(targetSets, targetRepsMin, next.takeIf { it > 0 })
                    },
                    onIncrement = {
                        onSetTargets(targetSets, targetRepsMin, ((targetRepsMax ?: 0) + 1).coerceAtMost(50))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A single min/max stepper styled like the mockup's `.target-stepper`
 * (surfaceContainer, radius 12dp, 46dp tall, − / value+label / +).
 */
@Composable
internal fun TargetStepper(
    label: String,
    value: Int?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(48.dp), // ≥48dp tall so the −/+ buttons hit the touch-target min (F-07)
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepperButton(symbol = "−", onClick = onDecrement)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value?.toString() ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StepperButton(symbol = "+", onClick = onIncrement)
        }
    }
}

@Composable
internal fun StepperButton(
    symbol: String,
    onClick: () -> Unit,
) {
    // Fills the 48dp-tall stepper row for a full-height touch target; width stays compact
    // (~36dp) because three steppers share one row — a 48dp-wide button would overflow. (F-07)
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
