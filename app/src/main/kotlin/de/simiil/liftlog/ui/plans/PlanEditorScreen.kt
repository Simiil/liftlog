package de.simiil.liftlog.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.components.dashedBorder
import de.simiil.liftlog.ui.exercises.equipmentLabel
import de.simiil.liftlog.ui.exercises.muscleGroupLabel
import de.simiil.liftlog.ui.theme.LiftLogTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun PlanEditorScreen(
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onAddExercises: () -> Unit,
    pickedExerciseIds: List<String>?,
    onPickedConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Consume multi-select picker results (mirrors the single-id consume pattern).
    LaunchedEffect(pickedExerciseIds) {
        if (pickedExerciseIds != null) {
            viewModel.addExercises(pickedExerciseIds)
            onPickedConsumed()
        }
    }

    when (uiState.mode) {
        PlanEditorMode.PLAN -> PlanModeContent(
            state = uiState,
            onClose = onClose,
            onSave = { viewModel.save(onSaved) },
            onPlanNameChange = viewModel::setPlanName,
            onAddDay = viewModel::addDay,
            onEditDay = viewModel::editDay,
            onRemoveDay = viewModel::removeDay,
            onReorderDays = viewModel::reorderDays,
            modifier = modifier,
        )
        PlanEditorMode.DAY -> {
            // System back in day mode returns to the plan editor, not out of the screen.
            BackHandler(onBack = viewModel::closeDayEditor)
            DayModeContent(
                state = uiState,
                onBack = viewModel::closeDayEditor,
                onDone = viewModel::closeDayEditor,
                onDayNameChange = viewModel::setDayName,
                onAddExercises = onAddExercises,
                onRemoveItem = viewModel::removeItem,
                onReorderItems = viewModel::reorderItems,
                onSetTargets = viewModel::setTargets,
                modifier = modifier,
            )
        }
    }
}

// ─── Plan mode ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanModeContent(
    state: PlanEditorUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onPlanNameChange: (String) -> Unit,
    onAddDay: () -> Unit,
    onEditDay: (String) -> Unit,
    onRemoveDay: (String) -> Unit,
    onReorderDays: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Live drag list, synced from upstream when not mid-drag.
    val localDays = remember { mutableStateListOf<EditorDayUi>() }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = localDays.indexOfFirst { it.key == from.key }
        val toIndex = localDays.indexOfFirst { it.key == to.key }
        if (fromIndex != -1 && toIndex != -1) {
            localDays.add(toIndex, localDays.removeAt(fromIndex))
        }
    }
    LaunchedEffect(state.days) {
        if (!reorderableState.isAnyItemDragging) {
            localDays.clear()
            localDays.addAll(state.days)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            EditorHeader(
                title = stringResource(if (state.isNewPlan) R.string.plan_editor_new else R.string.plan_editor_edit),
                onClose = onClose,
                closeIsBack = false,
                closeTag = UiTestTags.PLAN_EDITOR_CANCEL,
                actionLabel = stringResource(R.string.editor_save),
                actionEnabled = state.canSave,
                onAction = onSave,
                actionTag = UiTestTags.PLAN_EDITOR_SAVE,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FieldLabel(stringResource(R.string.plan_name_field_label))
            }
            item {
                EditorTextField(
                    value = state.planName,
                    onValueChange = onPlanNameChange,
                    hint = stringResource(R.string.plan_name_field_hint),
                )
            }
            item {
                FieldLabelWithCount(
                    label = stringResource(R.string.plan_training_days),
                    count = state.days.size,
                    topSpacing = 16.dp,
                )
            }
            if (state.days.isEmpty()) {
                item { EditorEmpty(stringResource(R.string.plan_days_empty)) }
            }
            items(localDays, key = { it.key }) { day ->
                ReorderableItem(reorderableState, key = day.key) { isDragging ->
                    DayRow(
                        day = day,
                        isDragging = isDragging,
                        onEdit = { onEditDay(day.key) },
                        onRemove = { onRemoveDay(day.key) },
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = { onReorderDays(localDays.map { it.key }) },
                        ),
                        modifier = Modifier.fillMaxWidth().testTag(UiTestTags.PLAN_DAY_ROW),
                    )
                }
            }
            item {
                AddRow(
                    label = stringResource(R.string.plan_add_day),
                    onClick = onAddDay,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DayRow(
    day: EditorDayUi,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val dragHandleCd = stringResource(R.string.template_drag_handle_cd)
    val removeCd = stringResource(R.string.plan_remove_day)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics { contentDescription = dragHandleCd },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEdit)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = day.name.ifBlank { stringResource(R.string.plan_untitled_day) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.exercise_count, day.exercises.size, day.exercises.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
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
    }
}

// ─── Day mode ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayModeContent(
    state: PlanEditorUiState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onDayNameChange: (String) -> Unit,
    onAddExercises: () -> Unit,
    onRemoveItem: (String) -> Unit,
    onReorderItems: (List<String>) -> Unit,
    onSetTargets: (key: String, sets: Int?, repsMin: Int?, repsMax: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val day = state.editingDay ?: return
    val localItems = remember { mutableStateListOf<EditorItemUi>() }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = localItems.indexOfFirst { it.key == from.key }
        val toIndex = localItems.indexOfFirst { it.key == to.key }
        if (fromIndex != -1 && toIndex != -1) {
            localItems.add(toIndex, localItems.removeAt(fromIndex))
        }
    }
    LaunchedEffect(day.exercises) {
        if (!reorderableState.isAnyItemDragging) {
            localItems.clear()
            localItems.addAll(day.exercises)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            EditorHeader(
                title = stringResource(if (day.isNewDay) R.string.day_editor_new else R.string.day_editor_edit),
                onClose = onBack,
                closeIsBack = true,
                closeTag = null,
                actionLabel = stringResource(R.string.editor_done),
                actionEnabled = state.canDone,
                onAction = onDone,
                actionTag = UiTestTags.DAY_EDITOR_DONE,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { FieldLabel(stringResource(R.string.day_name_field_label)) }
            item {
                EditorTextField(
                    value = day.name,
                    onValueChange = onDayNameChange,
                    hint = stringResource(R.string.day_name_field_hint),
                )
            }
            item {
                FieldLabelWithCount(
                    label = stringResource(R.string.day_exercises),
                    count = day.exercises.size,
                    topSpacing = 16.dp,
                )
            }
            if (day.exercises.isEmpty()) {
                item { EditorEmpty(stringResource(R.string.day_exercises_empty)) }
            }
            items(localItems, key = { it.key }) { item ->
                ReorderableItem(reorderableState, key = item.key) { isDragging ->
                    ExerciseEditorRow(
                        item = item,
                        isDragging = isDragging,
                        onRemove = { onRemoveItem(item.key) },
                        onSetTargets = { sets, min, max -> onSetTargets(item.key, sets, min, max) },
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = { onReorderItems(localItems.map { it.key }) },
                        ),
                        modifier = Modifier.fillMaxWidth().testTag(UiTestTags.TEMPLATE_EXERCISE_ROW),
                    )
                }
            }
            item {
                AddRow(
                    label = stringResource(R.string.template_add_exercise),
                    onClick = onAddExercises,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.TEMPLATE_ADD_EXERCISE),
                )
            }
        }
    }
}

@Composable
private fun ExerciseEditorRow(
    item: EditorItemUi,
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
        color = if (isDragging) {
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
                    modifier = dragHandleModifier
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .semantics { contentDescription = dragHandleCd },
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${muscleGroupLabel(item.muscleGroup)} · ${equipmentLabel(item.equipment)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
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
                    value = item.targetSets,
                    onDecrement = {
                        val next = (item.targetSets ?: 1) - 1
                        onSetTargets(next.takeIf { it > 0 }, item.targetRepsMin, item.targetRepsMax)
                    },
                    onIncrement = {
                        onSetTargets(((item.targetSets ?: 0) + 1).coerceAtMost(10), item.targetRepsMin, item.targetRepsMax)
                    },
                    modifier = Modifier.weight(1f),
                )
                TargetStepper(
                    label = stringResource(R.string.template_target_reps_min),
                    value = item.targetRepsMin,
                    onDecrement = {
                        val next = (item.targetRepsMin ?: 1) - 1
                        onSetTargets(item.targetSets, next.takeIf { it > 0 }, item.targetRepsMax)
                    },
                    onIncrement = {
                        onSetTargets(item.targetSets, ((item.targetRepsMin ?: 0) + 1).coerceAtMost(50), item.targetRepsMax)
                    },
                    modifier = Modifier.weight(1f),
                )
                TargetStepper(
                    label = stringResource(R.string.template_target_reps_max),
                    value = item.targetRepsMax,
                    onDecrement = {
                        val next = (item.targetRepsMax ?: 1) - 1
                        onSetTargets(item.targetSets, item.targetRepsMin, next.takeIf { it > 0 })
                    },
                    onIncrement = {
                        onSetTargets(item.targetSets, item.targetRepsMin, ((item.targetRepsMax ?: 0) + 1).coerceAtMost(50))
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
private fun TargetStepper(
    label: String,
    value: Int?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(46.dp),
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
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
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

// ─── Shared editor pieces ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorHeader(
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
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(40.dp)
                    .testTag(actionTag),
                shape = RoundedCornerShape(100.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                colors = ButtonDefaults.buttonColors(
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

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun FieldLabelWithCount(label: String, count: Int, topSpacing: androidx.compose.ui.unit.Dp) {
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
private fun EditorTextField(
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

@Composable
private fun EditorEmpty(text: String, modifier: Modifier = Modifier) {
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
private fun AddRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // .add-row: 1.5dp dashed outline, primary text, radius 16dp, 54dp.
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .dashedBorder(
                color = MaterialTheme.colorScheme.outline,
                width = 1.5.dp,
                cornerRadius = 16.dp,
            )
            .clickable(onClick = onClick),
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

// ─── Previews ──────────────────────────────────────────────────────────────────

private val previewDayWithExercises = EditorDayUi(
    key = "d1",
    name = "Push Day",
    isNewDay = false,
    exercises = listOf(
        EditorItemUi("i1", "ex1", "Bench Press", Equipment.BARBELL, MuscleGroup.CHEST, 3, 8, 12),
        EditorItemUi("i2", "ex2", "Overhead Press", Equipment.DUMBBELL, MuscleGroup.SHOULDERS, null, null, null),
    ),
)

private val previewPlanWithDays = PlanEditorUiState(
    mode = PlanEditorMode.PLAN,
    isNewPlan = false,
    planName = "Push Pull Legs",
    days = listOf(
        EditorDayUi("d1", "Push Day", false, previewDayWithExercises.exercises),
        EditorDayUi("d2", "Pull Day", false, emptyList()),
    ),
    canSave = true,
)

@Preview(name = "Plan editor — new (light)", showBackground = true)
@Composable
private fun PreviewPlanEditorNewLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanModeContent(
            state = PlanEditorUiState(mode = PlanEditorMode.PLAN, isNewPlan = true),
            onClose = {}, onSave = {}, onPlanNameChange = {}, onAddDay = {},
            onEditDay = {}, onRemoveDay = {}, onReorderDays = {},
        )
    }
}

@Preview(name = "Plan editor — days (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlanEditorDaysDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlanModeContent(
            state = previewPlanWithDays,
            onClose = {}, onSave = {}, onPlanNameChange = {}, onAddDay = {},
            onEditDay = {}, onRemoveDay = {}, onReorderDays = {},
        )
    }
}

@Preview(name = "Day editor — exercises (light)", showBackground = true)
@Composable
private fun PreviewDayEditorLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        DayModeContent(
            state = PlanEditorUiState(
                mode = PlanEditorMode.DAY,
                editingDay = previewDayWithExercises,
                canDone = true,
            ),
            onBack = {}, onDone = {}, onDayNameChange = {}, onAddExercises = {},
            onRemoveItem = {}, onReorderItems = {}, onSetTargets = { _, _, _, _ -> },
        )
    }
}

@Preview(name = "Day editor — exercises (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewDayEditorDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        DayModeContent(
            state = PlanEditorUiState(
                mode = PlanEditorMode.DAY,
                editingDay = previewDayWithExercises,
                canDone = true,
            ),
            onBack = {}, onDone = {}, onDayNameChange = {}, onAddExercises = {},
            onRemoveItem = {}, onReorderItems = {}, onSetTargets = { _, _, _, _ -> },
        )
    }
}
