package de.simiil.liftlog.ui.plans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import de.simiil.liftlog.domain.logging.Targets
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.theme.LiftLogTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    pickedExerciseId: String?,
    onPickedExerciseConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Picker-result consume pattern (mirrors ActiveSessionScreen)
    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.addExercise(pickedExerciseId)
            onPickedExerciseConsumed()
        }
    }

    TemplateEditorContent(
        uiState = uiState,
        onBack = onBack,
        onAddExercise = onAddExercise,
        onRemoveExercise = viewModel::removeExercise,
        onSetTargets = viewModel::setTargets,
        onPersistOrder = viewModel::persistOrder,
        modifier = modifier,
    )
}

// ─── Stateless content (easier to preview) ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorContent(
    uiState: TemplateEditorUiState,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (String) -> Unit,
    onSetTargets: (id: String, sets: Int?, repsMin: Int?, repsMax: Int?) -> Unit,
    onPersistOrder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local mutable list for live drag reordering — synced from uiState.exercises
    // only when not mid-drag (isDragging is tracked via the reorderable state).
    val localList = remember { mutableStateListOf<EditorExerciseUi>() }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // onMove is called for each intermediate drag step — reorder local list live
        val fromIndex = localList.indexOfFirst { it.id == from.key }
        val toIndex = localList.indexOfFirst { it.id == to.key }
        if (fromIndex != -1 && toIndex != -1) {
            localList.add(toIndex, localList.removeAt(fromIndex))
        }
    }

    // Sync from upstream state only when not mid-drag
    LaunchedEffect(uiState.exercises) {
        if (!reorderableState.isAnyItemDragging) {
            localList.clear()
            localList.addAll(uiState.exercises)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.dayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddExercise,
                modifier = Modifier.testTag(UiTestTags.TEMPLATE_ADD_EXERCISE),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.template_add_exercise),
                )
            }
        },
    ) { innerPadding ->
        if (!uiState.loading && localList.isEmpty()) {
            TemplateEditorEmptyState(
                onAddExercise = onAddExercise,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(localList, key = { it.id }) { item ->
                    ReorderableItem(
                        reorderableState,
                        key = item.id,
                    ) { isDragging ->
                        ExerciseEditorRow(
                            item = item,
                            isDragging = isDragging,
                            onRemove = { onRemoveExercise(item.id) },
                            onSetTargets = { sets, repsMin, repsMax ->
                                onSetTargets(item.id, sets, repsMin, repsMax)
                            },
                            dragHandleModifier = Modifier.draggableHandle(
                                onDragStopped = {
                                    // Drag settled — persist the current local order
                                    onPersistOrder(localList.map { it.id })
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.TEMPLATE_EXERCISE_ROW),
                        )
                    }
                }
            }
        }
    }
}

// ─── Exercise editor row ──────────────────────────────────────────────────────

@Composable
private fun ExerciseEditorRow(
    item: EditorExerciseUi,
    isDragging: Boolean,
    onRemove: () -> Unit,
    onSetTargets: (sets: Int?, repsMin: Int?, repsMax: Int?) -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(item.id) { mutableStateOf(false) }

    val dragHandleCd = stringResource(R.string.template_drag_handle_cd)
    val removeCd = stringResource(R.string.template_remove_exercise)

    Card(
        onClick = { expanded = !expanded },
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = if (isDragging) CardDefaults.cardElevation(defaultElevation = 8.dp)
        else CardDefaults.cardElevation(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Main row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handle
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null, // semantic set on the modifier below
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .semantics { contentDescription = dragHandleCd },
                )

                // Name + equipment
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = equipmentLabel(item.equipment),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Targets summary or "set targets" hint
                val repsHint = Targets.repRangeHint(item.targetRepsMin, item.targetRepsMax)
                if (item.targetSets != null || repsHint != null) {
                    val setsLabel = item.targetSets?.let {
                        pluralStringResource(R.plurals.set_count, it, it)
                    } ?: "—"
                    val repsLabel = repsHint ?: "—"
                    Text(
                        text = stringResource(R.string.template_targets_summary, setsLabel, repsLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .testTag("template_targets_summary")
                            .padding(end = 4.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.template_set_targets),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .testTag("template_targets_summary")
                            .padding(end = 4.dp),
                    )
                }

                // Remove button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.semantics { contentDescription = removeCd },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Expanded inline target editors ─────────────────────────────
            AnimatedVisibility(visible = expanded) {
                TargetEditors(
                    item = item,
                    onSetTargets = onSetTargets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

// ─── Inline target editors ────────────────────────────────────────────────────

@Composable
private fun TargetEditors(
    item: EditorExerciseUi,
    onSetTargets: (sets: Int?, repsMin: Int?, repsMax: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Sets stepper
        LabeledStepper(
            label = stringResource(R.string.template_target_sets),
            value = item.targetSets,
            onDecrement = {
                val next = (item.targetSets ?: 1) - 1
                onSetTargets(
                    if (next <= 0) null else next,
                    item.targetRepsMin,
                    item.targetRepsMax,
                )
            },
            onIncrement = {
                val next = (item.targetSets ?: 0) + 1
                onSetTargets(
                    next.coerceAtMost(10),
                    item.targetRepsMin,
                    item.targetRepsMax,
                )
            },
            modifier = Modifier.weight(1f),
        )

        // Reps min stepper
        LabeledStepper(
            label = stringResource(R.string.template_target_reps) + " min",
            value = item.targetRepsMin,
            onDecrement = {
                val next = (item.targetRepsMin ?: 1) - 1
                onSetTargets(
                    item.targetSets,
                    if (next <= 0) null else next,
                    item.targetRepsMax,
                )
            },
            onIncrement = {
                val next = (item.targetRepsMin ?: 0) + 1
                onSetTargets(item.targetSets, next.coerceAtMost(50), item.targetRepsMax)
            },
            modifier = Modifier.weight(1f),
        )

        // Reps max stepper
        LabeledStepper(
            label = stringResource(R.string.template_target_reps) + " max",
            value = item.targetRepsMax,
            onDecrement = {
                val next = (item.targetRepsMax ?: 1) - 1
                onSetTargets(
                    item.targetSets,
                    item.targetRepsMin,
                    if (next <= 0) null else next,
                )
            },
            onIncrement = {
                val next = (item.targetRepsMax ?: 0) + 1
                onSetTargets(item.targetSets, item.targetRepsMin, next.coerceAtMost(50))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LabeledStepper(
    label: String,
    value: Int?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            FilledTonalIconButton(
                onClick = onDecrement,
                modifier = Modifier.size(32.dp),
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = value?.toString() ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(28.dp),
            )
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(
                onClick = onIncrement,
                modifier = Modifier.size(32.dp),
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun TemplateEditorEmptyState(
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.day_template_empty),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("day_template_empty"),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAddExercise,
            modifier = Modifier.testTag(UiTestTags.TEMPLATE_ADD_EXERCISE + "_empty"),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.template_add_exercise))
        }
    }
}

// ─── Equipment label helper ───────────────────────────────────────────────────

@Composable
private fun equipmentLabel(equipment: Equipment): String = when (equipment) {
    Equipment.BARBELL -> stringResource(R.string.equipment_barbell)
    Equipment.DUMBBELL -> stringResource(R.string.equipment_dumbbell)
    Equipment.MACHINE -> stringResource(R.string.equipment_machine)
    Equipment.CABLE -> stringResource(R.string.equipment_cable)
    Equipment.BODYWEIGHT -> stringResource(R.string.equipment_bodyweight)
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val previewExercises = listOf(
    EditorExerciseUi(
        id = "te1",
        exerciseId = "ex1",
        name = "Bench Press",
        equipment = Equipment.BARBELL,
        targetSets = 3,
        targetRepsMin = 8,
        targetRepsMax = 12,
    ),
    EditorExerciseUi(
        id = "te2",
        exerciseId = "ex2",
        name = "Incline Dumbbell Press",
        equipment = Equipment.DUMBBELL,
        targetSets = null,
        targetRepsMin = null,
        targetRepsMax = null,
    ),
)

@Preview(name = "Template Editor — empty (light)", showBackground = true)
@Composable
private fun PreviewTemplateEditorEmptyLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        TemplateEditorContent(
            uiState = TemplateEditorUiState(dayName = "Push", exercises = emptyList(), loading = false),
            onBack = {},
            onAddExercise = {},
            onRemoveExercise = {},
            onSetTargets = { _, _, _, _ -> },
            onPersistOrder = {},
        )
    }
}

@Preview(name = "Template Editor — empty (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewTemplateEditorEmptyDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        TemplateEditorContent(
            uiState = TemplateEditorUiState(dayName = "Push", exercises = emptyList(), loading = false),
            onBack = {},
            onAddExercise = {},
            onRemoveExercise = {},
            onSetTargets = { _, _, _, _ -> },
            onPersistOrder = {},
        )
    }
}

@Preview(name = "Template Editor — exercises (light)", showBackground = true)
@Composable
private fun PreviewTemplateEditorPopulatedLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        TemplateEditorContent(
            uiState = TemplateEditorUiState(dayName = "Push", exercises = previewExercises, loading = false),
            onBack = {},
            onAddExercise = {},
            onRemoveExercise = {},
            onSetTargets = { _, _, _, _ -> },
            onPersistOrder = {},
        )
    }
}

@Preview(name = "Template Editor — exercises (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewTemplateEditorPopulatedDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        TemplateEditorContent(
            uiState = TemplateEditorUiState(dayName = "Push", exercises = previewExercises, loading = false),
            onBack = {},
            onAddExercise = {},
            onRemoveExercise = {},
            onSetTargets = { _, _, _, _ -> },
            onPersistOrder = {},
        )
    }
}
