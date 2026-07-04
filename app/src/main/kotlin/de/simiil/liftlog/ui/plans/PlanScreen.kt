package de.simiil.liftlog.ui.plans

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.exercises.muscleGroupLabel
import de.simiil.liftlog.ui.theme.LiftLogTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun PlanScreen(
    onOpenDay: (String, Boolean) -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlanContent(
        state = uiState,
        onOpenDay = { templateId -> onOpenDay(templateId, false) },
        onStartDay = { templateId -> viewModel.startDay(templateId, onOpenSession) },
        onAddDay = { viewModel.addDay { id -> onOpenDay(id, true) } },
        onRemoveDay = viewModel::removeDay,
        onReorderDays = viewModel::reorderDays,
        onRenamePlan = viewModel::renamePlan,
        onDeletePlan = viewModel::deletePlan,
        modifier = modifier,
    )
}

// ─── Stateless content (easier to preview) ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanContent(
    state: PlanTabUiState,
    onOpenDay: (String) -> Unit,
    onStartDay: (String) -> Unit,
    onAddDay: () -> Unit,
    onRemoveDay: (String) -> Unit,
    onReorderDays: (List<String>) -> Unit,
    onRenamePlan: (String) -> Unit,
    onDeletePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    var overflowExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingRemoveDayId by rememberSaveable { mutableStateOf<String?>(null) }

    // Live drag list, synced from upstream when not mid-drag.
    val localDays = remember { mutableStateListOf<PlanDayUi>() }
    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = localDays.indexOfFirst { it.templateId == from.key }
            val toIndex = localDays.indexOfFirst { it.templateId == to.key }
            if (fromIndex != -1 && toIndex != -1) {
                localDays.add(toIndex, localDays.removeAt(fromIndex))
            }
        }
    LaunchedEffect(plan?.days) {
        if (!reorderableState.isAnyItemDragging) {
            localDays.clear()
            plan?.days?.let(localDays::addAll)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    // While loading/plan == null this is a transient gap, not a real plan with
                    // a blank name — render nothing rather than flashing the "Untitled plan"
                    // fallback, which is reserved for an actually-loaded, actually-blank name.
                    val titleText =
                        if (state.loading || plan == null) {
                            ""
                        } else {
                            plan.name.ifBlank { stringResource(R.string.plan_untitled) }
                        }
                    Text(titleText)
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag(UiTestTags.PLAN_OVERFLOW),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.plan_overflow_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plan_menu_rename)) },
                                onClick = {
                                    overflowExpanded = false
                                    showRenameDialog = true
                                },
                                modifier = Modifier.testTag(UiTestTags.PLAN_MENU_RENAME),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.plan_delete)) },
                                onClick = {
                                    overflowExpanded = false
                                    showDeleteConfirm = true
                                },
                                modifier = Modifier.testTag(UiTestTags.PLAN_MENU_DELETE),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // plan == null only during load / a transient gap: bare scaffold, never an empty-state CTA.
        if (plan != null) {
            LazyColumn(
                state = lazyListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (plan.days.isEmpty()) {
                    item { EditorEmpty(stringResource(R.string.plan_days_empty)) }
                }
                items(localDays, key = { it.templateId }) { day ->
                    ReorderableItem(reorderableState, key = day.templateId) { isDragging ->
                        PlanDayRow(
                            day = day,
                            isDragging = isDragging,
                            onOpen = { onOpenDay(day.templateId) },
                            onStart = { onStartDay(day.templateId) },
                            onRemove = {
                                if (day.exerciseCount > 0) {
                                    pendingRemoveDayId = day.templateId
                                } else {
                                    onRemoveDay(day.templateId)
                                }
                            },
                            dragHandleModifier =
                                Modifier.draggableHandle(
                                    onDragStopped = { onReorderDays(localDays.map { it.templateId }) },
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    AddRow(
                        label = stringResource(R.string.plan_add_day),
                        onClick = onAddDay,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.PLAN_ADD_DAY),
                    )
                }
            }
        }
    }

    if (showRenameDialog && plan != null) {
        PlanNameDialog(
            title = stringResource(R.string.plan_menu_rename),
            initialName = plan.name,
            onConfirm = { name ->
                onRenamePlan(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
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

    pendingRemoveDayId?.let { dayId ->
        AlertDialog(
            onDismissRequest = { pendingRemoveDayId = null },
            title = { Text(stringResource(R.string.plan_day_remove_confirm_title)) },
            text = { Text(stringResource(R.string.plan_day_remove_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveDayId = null
                        onRemoveDay(dayId)
                    },
                    modifier = Modifier.testTag(UiTestTags.PLAN_DAY_REMOVE_CONFIRM),
                ) {
                    Text(
                        text = stringResource(R.string.common_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveDayId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// ─── Day row ──────────────────────────────────────────────────────────────────

@Composable
private fun PlanDayRow(
    day: PlanDayUi,
    isDragging: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val untitled = stringResource(R.string.plan_untitled_day)
    val startCd = stringResource(R.string.plan_start_day_cd, day.name.ifBlank { untitled })
    val removeCd = stringResource(R.string.plan_remove_day)
    val dragHandleCd = stringResource(R.string.template_drag_handle_cd)
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
            modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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
            // main area → open the day editor
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpen)
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 8.dp),
            ) {
                Text(
                    text = day.name.ifBlank { untitled },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = daySubtitle(day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier =
                    Modifier
                        .size(40.dp) // ≥40dp; the Row's own touch padding brings it to the ≥48dp target (F-07)
                        .testTag(UiTestTags.PLAN_DAY_REMOVE)
                        .semantics { contentDescription = removeCd },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            // .row-play — 48dp circular primary/onPrimary play button → start day (F-08)
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onStart)
                        .testTag(UiTestTags.PLAN_DAY_START)
                        .semantics { contentDescription = startCd },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** "N exercises[ · group · group · group]" — mirrors the mockup's plan-row-sub. */
@Composable
private fun daySubtitle(day: PlanDayUi): String {
    val count = pluralStringResource(R.plurals.exercise_count, day.exerciseCount, day.exerciseCount)
    if (day.muscleGroups.isEmpty()) return count
    val groups = day.muscleGroups.map { muscleGroupLabel(it) }.joinToString(" · ")
    return "$count · $groups"
}

// ─── Shared plan-name dialog (rename now; reused by "New plan" in a follow-up PR) ────────────

@Composable
internal fun PlanNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FieldLabel(stringResource(R.string.plan_name_field_label))
                EditorTextField(
                    value = name,
                    onValueChange = { name = it },
                    hint = stringResource(R.string.plan_name_field_hint),
                    modifier = Modifier.testTag(UiTestTags.PLAN_RENAME_FIELD),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(UiTestTags.PLAN_RENAME_CONFIRM),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

// ─── Previews ──────────────────────────────────────────────────────────────────

private val previewPlan =
    CurrentPlanUi(
        id = "1",
        name = "Push Pull Legs",
        days =
            listOf(
                PlanDayUi("d1", "Push Day", 5, listOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)),
                PlanDayUi("d2", "Pull Day", 5, listOf(MuscleGroup.BACK, MuscleGroup.BICEPS)),
                PlanDayUi("d3", "", 0, emptyList()),
            ),
    )

@Preview(name = "Plan — loading (light)", showBackground = true)
@Composable
private fun PreviewPlanLoadingLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanContent(
            state = PlanTabUiState(loading = true, plan = null),
            onOpenDay = {},
            onStartDay = {},
            onAddDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onRenamePlan = {},
            onDeletePlan = {},
        )
    }
}

@Preview(name = "Plan — populated (light)", showBackground = true)
@Composable
private fun PreviewPlanPopulatedLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanContent(
            state = PlanTabUiState(loading = false, plan = previewPlan),
            onOpenDay = {},
            onStartDay = {},
            onAddDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onRenamePlan = {},
            onDeletePlan = {},
        )
    }
}

@Preview(name = "Plan — populated (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlanPopulatedDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlanContent(
            state = PlanTabUiState(loading = false, plan = previewPlan),
            onOpenDay = {},
            onStartDay = {},
            onAddDay = {},
            onRemoveDay = {},
            onReorderDays = {},
            onRenamePlan = {},
            onDeletePlan = {},
        )
    }
}
