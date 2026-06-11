package de.simiil.liftlog.ui.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup
import de.simiil.liftlog.ui.UiTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    onSelected: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    onSelectedMany: (List<String>) -> Unit = {},
    existingIds: Set<String> = emptySet(),
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateForm by rememberSaveable { mutableStateOf(false) }
    // Multi-select state: survives recomposition and configuration changes
    var selection by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val toggleSelection: (String) -> Unit = { id ->
        selection = if (selection.contains(id)) selection - id else selection + id
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            if (multiSelect) {
                                stringResource(R.string.picker_title_add)
                            } else {
                                stringResource(R.string.picker_title)
                            },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            if (multiSelect) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close,
                            contentDescription =
                                stringResource(
                                    if (multiSelect) R.string.navigate_back else R.string.picker_cancel,
                                ),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // "Add N" footer — only shown in multi-select when ≥1 item is selected
            if (multiSelect && selection.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
                                ).padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Button(
                            onClick = { onSelectedMany(selection.toList()) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(UiTestTags.PICKER_ADD_SELECTED),
                            shape = RoundedCornerShape(100.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.picker_add_selected, selection.size),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Search field — styled as .picker-search pill
            item {
                PickerSearchBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Muscle group filter chips — .filter-chip style
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MuscleGroup.entries) { muscle ->
                        PickerFilterChip(
                            selected = uiState.muscleFilter == muscle,
                            onClick = {
                                viewModel.onMuscleFilter(
                                    if (uiState.muscleFilter == muscle) null else muscle,
                                )
                            },
                            label = muscleGroupLabel(muscle),
                        )
                    }
                }
            }

            // Equipment filter chips — .filter-chip style
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Equipment.entries) { equipment ->
                        PickerFilterChip(
                            selected = uiState.equipmentFilter == equipment,
                            onClick = {
                                viewModel.onEquipmentFilter(
                                    if (uiState.equipmentFilter == equipment) null else equipment,
                                )
                            },
                            label = equipmentLabel(equipment),
                        )
                    }
                }
            }

            // Create exercise affordance — .create-row at top of list
            item {
                CreateRow(
                    query = uiState.query,
                    onClick = { showCreateForm = true },
                )
            }

            // Recent section header — .picker-section
            if (uiState.recent.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.picker_recent),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                items(uiState.recent, key = { "recent-${it.id}" }) { exercise ->
                    PickerExerciseRow(
                        exercise = exercise,
                        multiSelect = multiSelect,
                        checked = selection.contains(exercise.id),
                        added = existingIds.contains(exercise.id),
                        onClick = {
                            if (multiSelect) toggleSelection(exercise.id) else onSelected(exercise.id)
                        },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // Results list
            items(uiState.results, key = { it.id }) { exercise ->
                PickerExerciseRow(
                    exercise = exercise,
                    multiSelect = multiSelect,
                    checked = selection.contains(exercise.id),
                    added = existingIds.contains(exercise.id),
                    onClick = {
                        if (multiSelect) toggleSelection(exercise.id) else onSelected(exercise.id)
                    },
                )
            }
        }
    }

    // Create exercise sheet — shown as overlay
    if (showCreateForm) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .clickable(onClick = { showCreateForm = false }),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                CreateExerciseForm(
                    createError = uiState.createError,
                    onCreateClicked = { name, muscle, equipment ->
                        viewModel.createCustom(name, muscle, equipment) { id ->
                            if (multiSelect) {
                                // In multi-select mode: add to selection, don't auto-return
                                selection = selection + id
                                showCreateForm = false
                            } else {
                                // Single-select mode: return immediately
                                onSelected(id)
                            }
                        }
                    },
                    onDismiss = { showCreateForm = false },
                )
            }
        }
    }
}

/** Pill-shaped search bar matching .picker-search: surfaceContainerHigh bg, radius 100dp. */
@Composable
private fun PickerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(20.dp)
                        .alpha(0.7f),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.picker_search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.picker_clear_search),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Filter chip matching .filter-chip: outline border, radius 10dp, 48dp touch target, selected → secondaryContainer. */
@Composable
private fun PickerFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        },
        modifier = modifier.height(48.dp), // ≥48dp touch target (F-09)
        shape = RoundedCornerShape(10.dp),
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
                borderWidth = 1.dp,
            ),
    )
}

/** Create-row at the top of the list: .create-row with .create-plus circle. */
@Composable
private fun CreateRow(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // .create-plus circle: primaryContainer bg, onPrimaryContainer icon
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text =
                if (query.isNotEmpty()) {
                    stringResource(R.string.picker_create_with_query, query)
                } else {
                    stringResource(R.string.picker_create)
                },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Exercise row matching .pick-row:
 * - In single mode: plain tappable row (unchanged behavior)
 * - In multi mode: shows a .pick-check checkbox (24dp, radius 7dp, outline → primary when checked);
 *   rows with [added]=true show "Added" badge and are disabled.
 */
@Composable
private fun PickerExerciseRow(
    exercise: Exercise,
    multiSelect: Boolean,
    checked: Boolean,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val disabled = multiSelect && added
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (disabled) Modifier.alpha(0.5f) else Modifier,
                ).then(
                    if (!disabled) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).semantics(mergeDescendants = true) {}
                .padding(horizontal = 8.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (multiSelect) {
            // .pick-check: 24dp box, radius 7dp, outline border → primary bg + onPrimary check when selected
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).border(
                            width = 2.dp,
                            color =
                                if (checked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = RoundedCornerShape(7.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        // .pick-main: name + group/equipment sub-line
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = exerciseDisplayName(exercise.id, exercise.name),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${muscleGroupLabel(exercise.muscleGroup)} · ${equipmentLabel(exercise.equipment)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // .pick-added badge for already-added exercises in multi-select
        if (multiSelect && added) {
            Text(
                text = stringResource(R.string.picker_added),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun CreateExerciseForm(
    createError: CreateError?,
    onCreateClicked: (name: String, muscle: MuscleGroup, equipment: Equipment) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf(MuscleGroup.OTHER) }
    var selectedEquipment by remember { mutableStateOf(Equipment.BARBELL) }

    Column(
        modifier =
            modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 28.dp,
            ),
    ) {
        // Sheet handle
        Box(
            modifier =
                Modifier
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.picker_create),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.picker_create_name)) },
            isError = createError != null,
            supportingText =
                if (createError != null) {
                    {
                        Text(
                            text =
                                when (createError) {
                                    CreateError.BLANK_NAME -> stringResource(R.string.picker_error_blank)
                                    CreateError.DUPLICATE_NAME -> stringResource(R.string.picker_duplicate_name)
                                },
                        )
                    }
                } else {
                    null
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = { onCreateClicked(name, selectedMuscle, selectedEquipment) },
                ),
        )

        Spacer(Modifier.height(12.dp))

        // Muscle group
        Text(
            text = stringResource(R.string.picker_create_muscle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MuscleGroup.entries) { muscle ->
                FilterChip(
                    selected = selectedMuscle == muscle,
                    onClick = { selectedMuscle = muscle },
                    label = { Text(muscleGroupLabel(muscle)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Equipment
        Text(
            text = stringResource(R.string.picker_create_equipment),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Equipment.entries) { equipment ->
                FilterChip(
                    selected = selectedEquipment == equipment,
                    onClick = { selectedEquipment = equipment },
                    label = { Text(equipmentLabel(equipment)) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.picker_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onCreateClicked(name, selectedMuscle, selectedEquipment) }) {
                Text(stringResource(R.string.picker_create_confirm))
            }
        }
    }
}
