package de.simiil.liftlog.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.ThemePreference
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.theme.LiftLogTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // ── dialog state ──────────────────────────────────────────────────────
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by rememberSaveable { mutableStateOf<DayRowUi?>(null) }
    var deleteTarget by rememberSaveable { mutableStateOf<DayRowUi?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.planName) },
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
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag(UiTestTags.PLAN_DETAIL_ADD_DAY),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.plan_detail_add_day),
                )
            }
        },
    ) { innerPadding ->
        if (!uiState.loading && uiState.days.isEmpty()) {
            PlanDetailEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.days, key = { it.id }) { day ->
                    DayTemplateCard(
                        day = day,
                        onClick = { onOpenTemplate(day.id) },
                        onRename = { renameTarget = day },
                        onDelete = { deleteTarget = day },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.DAY_TEMPLATE_ROW),
                    )
                }
            }
        }
    }

    // ── Create dialog ─────────────────────────────────────────────────────
    if (showCreateDialog) {
        NameInputDialog(
            title = stringResource(R.string.plan_detail_add_day),
            hint = stringResource(R.string.day_name_hint),
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { name ->
                viewModel.createDay(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    // ── Rename dialog ─────────────────────────────────────────────────────
    renameTarget?.let { target ->
        NameInputDialog(
            title = stringResource(R.string.day_rename),
            hint = stringResource(R.string.day_name_hint),
            initialValue = target.name,
            confirmLabel = stringResource(R.string.dialog_save),
            onConfirm = { name ->
                viewModel.renameDay(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    // ── Delete confirm dialog ─────────────────────────────────────────────
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.day_delete_confirm_title)) },
            text = { Text(stringResource(R.string.day_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDay(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.day_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

// ── Day template card ─────────────────────────────────────────────────────────

@Composable
private fun DayTemplateCard(
    day: DayRowUi,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.day_rename)) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.day_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun PlanDetailEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.plan_detail_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.plan_detail_empty_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }
}

// ── Reusable name-input dialog ────────────────────────────────────────────────

@Composable
private fun NameInputDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
) {
    var text by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (text.isNotBlank()) onConfirm(text) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned { focusRequester.requestFocus() },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(name = "Plan Detail — empty (light)", showBackground = true)
@Composable
private fun PreviewPlanDetailEmptyLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanDetailScreenPreviewContent(planName = "Push Pull Legs", days = emptyList())
    }
}

@Preview(name = "Plan Detail — empty (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlanDetailEmptyDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlanDetailScreenPreviewContent(planName = "Push Pull Legs", days = emptyList())
    }
}

@Preview(name = "Plan Detail — populated (light)", showBackground = true)
@Composable
private fun PreviewPlanDetailPopulatedLight() {
    LiftLogTheme(themePreference = ThemePreference.LIGHT, dynamicColor = false) {
        PlanDetailScreenPreviewContent(
            planName = "Push Pull Legs",
            days = listOf(
                DayRowUi("1", "Push — Chest & Shoulders"),
                DayRowUi("2", "Pull — Back & Biceps"),
                DayRowUi("3", "Legs"),
            ),
        )
    }
}

@Preview(name = "Plan Detail — populated (dark)", showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun PreviewPlanDetailPopulatedDark() {
    LiftLogTheme(themePreference = ThemePreference.DARK, dynamicColor = false) {
        PlanDetailScreenPreviewContent(
            planName = "Push Pull Legs",
            days = listOf(
                DayRowUi("1", "Push — Chest & Shoulders"),
                DayRowUi("2", "Pull — Back & Biceps"),
                DayRowUi("3", "Legs"),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDetailScreenPreviewContent(planName: String, days: List<DayRowUi>) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(planName) },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
    ) { innerPadding ->
        if (days.isEmpty()) {
            PlanDetailEmptyState(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days, key = { it.id }) { day ->
                    DayTemplateCard(
                        day = day,
                        onClick = {},
                        onRename = {},
                        onDelete = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
