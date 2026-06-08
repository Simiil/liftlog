package de.simiil.liftlog.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.Exercise
import de.simiil.liftlog.domain.model.MuscleGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    onSelected: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateForm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.picker_cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Search field
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.picker_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }

            // Muscle group filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MuscleGroup.entries) { muscle ->
                        FilterChip(
                            selected = uiState.muscleFilter == muscle,
                            onClick = {
                                viewModel.onMuscleFilter(
                                    if (uiState.muscleFilter == muscle) null else muscle,
                                )
                            },
                            label = { Text(muscleGroupLabel(muscle)) },
                        )
                    }
                }
            }

            // Equipment filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Equipment.entries) { equipment ->
                        FilterChip(
                            selected = uiState.equipmentFilter == equipment,
                            onClick = {
                                viewModel.onEquipmentFilter(
                                    if (uiState.equipmentFilter == equipment) null else equipment,
                                )
                            },
                            label = { Text(equipmentLabel(equipment)) },
                        )
                    }
                }
            }

            // Recent section (only shown when no query or filters active)
            if (uiState.recent.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.picker_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(uiState.recent, key = { "recent-${it.id}" }) { exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        onClick = { onSelected(exercise.id) },
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // Results list
            items(uiState.results, key = { it.id }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    onClick = { onSelected(exercise.id) },
                )
            }

            // Create exercise affordance
            item {
                Spacer(Modifier.height(8.dp))
                if (!showCreateForm) {
                    TextButton(
                        onClick = { showCreateForm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.picker_create))
                    }
                } else {
                    CreateExerciseForm(
                        createError = uiState.createError,
                        onCreateClicked = { name, muscle, equipment ->
                            viewModel.createCustom(name, muscle, equipment) { id ->
                                onSelected(id)
                            }
                        },
                        onDismiss = { showCreateForm = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${muscleGroupLabel(exercise.muscleGroup)} · ${equipmentLabel(exercise.equipment)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.picker_create),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))

            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.picker_create_name)) },
                isError = createError != null,
                supportingText = if (createError != null) {
                    {
                        Text(
                            text = when (createError) {
                                CreateError.BLANK_NAME -> stringResource(R.string.picker_error_blank)
                                CreateError.DUPLICATE_NAME -> stringResource(R.string.picker_duplicate_name)
                            },
                        )
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { onCreateClicked(name, selectedMuscle, selectedEquipment) },
                ),
            )

            Spacer(Modifier.height(12.dp))

            // Muscle group label
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

            // Equipment label
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
}
