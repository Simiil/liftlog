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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.exercises.muscleGroupLabel
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.common_cancel
import liftlog.app.generated.resources.common_create
import liftlog.app.generated.resources.common_delete
import liftlog.app.generated.resources.common_remove
import liftlog.app.generated.resources.common_save
import liftlog.app.generated.resources.exercise_count
import liftlog.app.generated.resources.plan_add_day
import liftlog.app.generated.resources.plan_day_remove_confirm_message
import liftlog.app.generated.resources.plan_day_remove_confirm_title
import liftlog.app.generated.resources.plan_days_empty
import liftlog.app.generated.resources.plan_delete
import liftlog.app.generated.resources.plan_delete_confirm_message
import liftlog.app.generated.resources.plan_delete_confirm_title
import liftlog.app.generated.resources.plan_menu_rename
import liftlog.app.generated.resources.plan_name_field_hint
import liftlog.app.generated.resources.plan_name_field_label
import liftlog.app.generated.resources.plan_overflow_cd
import liftlog.app.generated.resources.plan_remove_day
import liftlog.app.generated.resources.plan_start_day_cd
import liftlog.app.generated.resources.plan_switcher_cd
import liftlog.app.generated.resources.plan_untitled
import liftlog.app.generated.resources.plan_untitled_day
import liftlog.app.generated.resources.plans_create
import liftlog.app.generated.resources.template_drag_handle_cd
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

// ─── Screen ─────────────────────────────────────────────────────────────────

@Composable
fun PlanScreen(
    onOpenDay: (String, Boolean) -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = koinViewModel(),
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
        onSelectPlan = viewModel::selectPlan,
        onCreatePlan = viewModel::createPlan,
        modifier = modifier,
    )
}

// ─── Stateless content (easier to preview) ───────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// internal (not private) so the androidMain @Preview file (PlanScreenPreviews) can drive it.
internal fun PlanContent(
    state: PlanTabUiState,
    onOpenDay: (String) -> Unit,
    onStartDay: (String) -> Unit,
    onAddDay: () -> Unit,
    onRemoveDay: (String) -> Unit,
    onReorderDays: (List<String>) -> Unit,
    onRenamePlan: (String) -> Unit,
    onDeletePlan: () -> Unit,
    onSelectPlan: (String) -> Unit,
    onCreatePlan: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = state.plan
    var overflowExpanded by remember { mutableStateOf(false) }
    var switcherExpanded by remember { mutableStateOf(false) }
    // The switcher only renders with 2+ choices, but its remembered expanded state survives a
    // 2→1→2 plan-count transition (e.g. delete down to one plan, then create a second): without
    // this reset, a dropdown left open before dropping to 1 plan would silently reopen the
    // moment a 2nd plan reappears.
    LaunchedEffect(state.planChoices.size >= 2) {
        if (state.planChoices.size < 2) switcherExpanded = false
    }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
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
                    if (state.loading || plan == null) {
                        Text("")
                    } else if (state.planChoices.size >= 2) {
                        PlanSwitcherTitle(
                            currentId = plan.id,
                            currentName = plan.name,
                            choices = state.planChoices,
                            expanded = switcherExpanded,
                            onExpandedChange = { switcherExpanded = it },
                            onSelect = { id ->
                                switcherExpanded = false
                                onSelectPlan(id)
                            },
                        )
                    } else {
                        Text(plan.name.ifBlank { stringResource(Res.string.plan_untitled) })
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            modifier = Modifier.testTag(UiTestTags.PLAN_OVERFLOW),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(Res.string.plan_overflow_cd),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.plans_create)) },
                                onClick = {
                                    overflowExpanded = false
                                    showCreateDialog = true
                                },
                                modifier = Modifier.testTag(UiTestTags.PLAN_MENU_NEW),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.plan_menu_rename)) },
                                onClick = {
                                    overflowExpanded = false
                                    showRenameDialog = true
                                },
                                modifier = Modifier.testTag(UiTestTags.PLAN_MENU_RENAME),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.plan_delete)) },
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
                    item { EditorEmpty(stringResource(Res.string.plan_days_empty)) }
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
                        label = stringResource(Res.string.plan_add_day),
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
            title = stringResource(Res.string.plan_menu_rename),
            initialName = plan.name,
            onConfirm = { name ->
                onRenamePlan(name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showCreateDialog) {
        PlanNameDialog(
            title = stringResource(Res.string.plans_create),
            initialName = "",
            confirmLabel = stringResource(Res.string.common_create),
            fieldTag = UiTestTags.PLAN_NEW_FIELD,
            confirmTag = UiTestTags.PLAN_NEW_CONFIRM,
            onConfirm = { name ->
                onCreatePlan(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.plan_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.plan_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlan()
                    },
                    modifier = Modifier.testTag(UiTestTags.PLAN_DELETE_CONFIRM),
                ) {
                    Text(
                        text = stringResource(Res.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    pendingRemoveDayId?.let { dayId ->
        AlertDialog(
            onDismissRequest = { pendingRemoveDayId = null },
            title = { Text(stringResource(Res.string.plan_day_remove_confirm_title)) },
            text = { Text(stringResource(Res.string.plan_day_remove_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveDayId = null
                        onRemoveDay(dayId)
                    },
                    modifier = Modifier.testTag(UiTestTags.PLAN_DAY_REMOVE_CONFIRM),
                ) {
                    Text(
                        text = stringResource(Res.string.common_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveDayId = null }) {
                    Text(stringResource(Res.string.common_cancel))
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
    val untitled = stringResource(Res.string.plan_untitled_day)
    val startCd = stringResource(Res.string.plan_start_day_cd, day.name.ifBlank { untitled })
    val removeCd = stringResource(Res.string.plan_remove_day)
    val dragHandleCd = stringResource(Res.string.template_drag_handle_cd)
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
    val count = pluralStringResource(Res.plurals.exercise_count, day.exerciseCount, day.exerciseCount)
    if (day.muscleGroups.isEmpty()) return count
    val groups = day.muscleGroups.map { muscleGroupLabel(it) }.joinToString(" · ")
    return "$count · $groups"
}

// ─── Shared plan-name dialog (rename and "New plan" both use this) ──────────────

@Composable
internal fun PlanNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(Res.string.common_save),
    fieldTag: String = UiTestTags.PLAN_RENAME_FIELD,
    confirmTag: String = UiTestTags.PLAN_RENAME_CONFIRM,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FieldLabel(stringResource(Res.string.plan_name_field_label))
                EditorTextField(
                    value = name,
                    onValueChange = { name = it },
                    hint = stringResource(Res.string.plan_name_field_hint),
                    modifier = Modifier.testTag(fieldTag),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(confirmTag),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.common_cancel))
            }
        },
    )
}

// ─── Title-bar plan switcher (2+ plans only, issue #30 PR4) ─────────────────────

@Composable
private fun PlanSwitcherTitle(
    currentId: String?,
    currentName: String,
    choices: List<PlanChoiceUi>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val untitled = stringResource(Res.string.plan_untitled)
    val switcherCd = stringResource(Res.string.plan_switcher_cd)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .clickable { onExpandedChange(true) }
                    .testTag(UiTestTags.PLAN_SWITCHER)
                    .semantics { contentDescription = switcherCd },
        ) {
            Text(
                text = currentName.ifBlank { untitled },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            choices.forEach { choice ->
                val isCurrent = choice.id == currentId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = choice.name.ifBlank { untitled },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon =
                        if (isCurrent) {
                            { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    onClick = { onSelect(choice.id) },
                    modifier =
                        Modifier
                            .widthIn(max = 280.dp)
                            .testTag(UiTestTags.PLAN_SWITCHER_ITEM),
                )
            }
        }
    }
}
