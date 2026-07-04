package de.simiil.liftlog.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.UiTestTags
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * DB-backed, single-day autosave editor (2026-06-12 autosave design). Every field commits to
 * [DayEditorViewModel] on change — there is no Save; back and the "Done" pill are both pure
 * navigation. Not yet reachable from any route (wired up in the next task); built on the shared
 * pieces in `EditorComponents.kt` so it renders identically to the legacy day-mode editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(
    isNew: Boolean,
    onClose: () -> Unit,
    onAddExercise: () -> Unit,
    pickedExerciseIds: List<String>?,
    onPickedConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Consume multi-select picker results once (mirrors PlanEditorScreen's consume-once contract).
    LaunchedEffect(pickedExerciseIds) {
        if (pickedExerciseIds != null) {
            viewModel.addExercises(pickedExerciseIds)
            onPickedConsumed()
        }
    }

    // The day was tombstoned (e.g. its plan got deleted elsewhere): close automatically.
    LaunchedEffect(uiState.dayGone) {
        if (uiState.dayGone) onClose()
    }

    // Flush a pending debounced rename on backgrounding (ON_STOP) and on leaving the screen
    // (dispose) — closes the debounce window's data-loss gap (spec §1 "No data loss").
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.flushPendingEdits()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushPendingEdits()
        }
    }

    // Live drag list, synced from upstream when not mid-drag (mirrors PlanEditorScreen's pattern).
    val localItems = remember { mutableStateListOf<DayExerciseUi>() }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = localItems.indexOfFirst { it.id == from.key }
            val toIndex = localItems.indexOfFirst { it.id == to.key }
            if (fromIndex != -1 && toIndex != -1) {
                localItems.add(toIndex, localItems.removeAt(fromIndex))
            }
        }
    LaunchedEffect(uiState.exercises) {
        if (!reorderableState.isAnyItemDragging) {
            localItems.clear()
            localItems.addAll(uiState.exercises)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            EditorHeader(
                title = stringResource(if (isNew) R.string.day_editor_new else R.string.day_editor_edit),
                onClose = onClose,
                closeIsBack = true,
                closeTag = null,
                actionLabel = stringResource(R.string.editor_done),
                actionEnabled = true,
                onAction = onClose,
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
                    value = uiState.dayName,
                    onValueChange = viewModel::setDayName,
                    hint = stringResource(R.string.day_name_field_hint),
                    modifier = Modifier.testTag(UiTestTags.DAY_NAME_FIELD),
                )
            }
            item {
                FieldLabelWithCount(
                    label = stringResource(R.string.day_exercises),
                    count = uiState.exercises.size,
                    topSpacing = 16.dp,
                )
            }
            if (uiState.exercises.isEmpty()) {
                item { EditorEmpty(stringResource(R.string.day_exercises_empty)) }
            }
            items(localItems, key = { it.id }) { exercise ->
                ReorderableItem(reorderableState, key = exercise.id) { isDragging ->
                    ExerciseEditorRow(
                        name = exercise.name,
                        equipment = exercise.equipment,
                        muscleGroup = exercise.muscleGroup,
                        targetSets = exercise.targetSets,
                        targetRepsMin = exercise.targetRepsMin,
                        targetRepsMax = exercise.targetRepsMax,
                        isDragging = isDragging,
                        onRemove = { viewModel.removeExercise(exercise.id) },
                        onSetTargets = { sets, min, max -> viewModel.setTargets(exercise.id, sets, min, max) },
                        dragHandleModifier =
                            Modifier.draggableHandle(
                                onDragStopped = { viewModel.reorderExercises(localItems.map { it.id }) },
                            ),
                        modifier = Modifier.fillMaxWidth().testTag(UiTestTags.TEMPLATE_EXERCISE_ROW),
                    )
                }
            }
            item {
                AddRow(
                    label = stringResource(R.string.template_add_exercise),
                    onClick = onAddExercise,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.TEMPLATE_ADD_EXERCISE),
                )
            }
        }
    }
}
