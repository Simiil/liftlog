package de.simiil.liftlog.ui.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        PlanEditorMode.PLAN ->
            PlanModeContent(
                state = uiState,
                onClose = onClose,
                onSave = { viewModel.save(onSaved) },
                onPlanNameChange = viewModel::setPlanName,
                onAddDay = viewModel::addDay,
                onEditDay = viewModel::editDay,
                onRemoveDay = viewModel::removeDay,
                onReorderDays = viewModel::reorderDays,
                onDeletePlan = { viewModel.deletePlan(onClose) },
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
    onDeletePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Live drag list, synced from upstream when not mid-drag.
    val localDays = remember { mutableStateListOf<EditorDayUi>() }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
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
            modifier =
                Modifier
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
                        dragHandleModifier =
                            Modifier.draggableHandle(
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
            if (!state.isNewPlan) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag(UiTestTags.PLAN_EDITOR_DELETE),
                        ) {
                            Text(
                                text = stringResource(R.string.plan_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.plan_delete_confirm_title)) },
            text = { Text(stringResource(R.string.plan_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlan()
                    },
                    modifier = Modifier.testTag(UiTestTags.PLAN_DELETE_CONFIRM),
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
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
        color =
            if (isDragging) {
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
                modifier =
                    dragHandleModifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .semantics { contentDescription = dragHandleCd },
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onEdit)
                        .semantics(mergeDescendants = true) {}
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
                modifier =
                    Modifier
                        .size(48.dp) // ≥48dp touch target (F-07)
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
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
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
            modifier =
                Modifier
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
                        name = item.name,
                        equipment = item.equipment,
                        muscleGroup = item.muscleGroup,
                        targetSets = item.targetSets,
                        targetRepsMin = item.targetRepsMin,
                        targetRepsMax = item.targetRepsMax,
                        isDragging = isDragging,
                        onRemove = { onRemoveItem(item.key) },
                        onSetTargets = { sets, min, max -> onSetTargets(item.key, sets, min, max) },
                        dragHandleModifier =
                            Modifier.draggableHandle(
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.TEMPLATE_ADD_EXERCISE),
                )
            }
        }
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────

private val previewDayWithExercises =
    EditorDayUi(
        key = "d1",
        name = "Push Day",
        isNewDay = false,
        exercises =
            listOf(
                EditorItemUi("i1", "ex1", "Bench Press", Equipment.BARBELL, MuscleGroup.CHEST, 3, 8, 12),
                EditorItemUi("i2", "ex2", "Overhead Press", Equipment.DUMBBELL, MuscleGroup.SHOULDERS, null, null, null),
            ),
    )

private val previewPlanWithDays =
    PlanEditorUiState(
        mode = PlanEditorMode.PLAN,
        isNewPlan = false,
        planName = "Push Pull Legs",
        days =
            listOf(
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
            onClose = {},
            onSave = {},
            onPlanNameChange = {},
            onAddDay = {},
            onEditDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onDeletePlan = {},
        )
    }
}

@Preview(name = "Plan editor — days (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlanEditorDaysDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlanModeContent(
            state = previewPlanWithDays,
            onClose = {},
            onSave = {},
            onPlanNameChange = {},
            onAddDay = {},
            onEditDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onDeletePlan = {},
        )
    }
}

@Preview(name = "Day editor — exercises (light)", showBackground = true)
@Composable
private fun PreviewDayEditorLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        DayModeContent(
            state =
                PlanEditorUiState(
                    mode = PlanEditorMode.DAY,
                    editingDay = previewDayWithExercises,
                    canDone = true,
                ),
            onBack = {},
            onDone = {},
            onDayNameChange = {},
            onAddExercises = {},
            onRemoveItem = {},
            onReorderItems = {},
            onSetTargets = { _, _, _, _ -> },
        )
    }
}

@Preview(name = "Day editor — exercises (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewDayEditorDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        DayModeContent(
            state =
                PlanEditorUiState(
                    mode = PlanEditorMode.DAY,
                    editingDay = previewDayWithExercises,
                    canDone = true,
                ),
            onBack = {},
            onDone = {},
            onDayNameChange = {},
            onAddExercises = {},
            onRemoveItem = {},
            onReorderItems = {},
            onSetTargets = { _, _, _, _ -> },
        )
    }
}
