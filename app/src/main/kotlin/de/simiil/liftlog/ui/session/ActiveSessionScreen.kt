package de.simiil.liftlog.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.domain.model.Equipment
import de.simiil.liftlog.domain.model.LoggedSet
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.ui.theme.LiftLogTheme
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    onFinished: () -> Unit,
    onDiscarded: () -> Unit,
    onAddExercise: () -> Unit,
    pickedExerciseId: String?,
    onPickedExerciseConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Consume picked exercise id
    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.onPickedExercise(pickedExerciseId)
            onPickedExerciseConsumed()
        }
    }

    // Navigate on finish (show snackbar briefly, then navigate)
    val finishMessage = stringResource(
        R.string.session_finish_summary,
        uiState.name ?: stringResource(R.string.session_untitled),
        uiState.lastFinishedSetCount,
    )
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            launch { snackbarHostState.showSnackbar(finishMessage, duration = SnackbarDuration.Short) }
            delay(1500)
            onFinished()
        }
    }

    // Navigate on discard
    LaunchedEffect(uiState.discarded) {
        if (uiState.discarded) {
            onDiscarded()
        }
    }

    // Elapsed timer state (updates each second)
    val elapsedSeconds = remember(uiState.startedAt) {
        androidx.compose.runtime.mutableLongStateOf(
            uiState.startedAt?.let { Instant.now().epochSecond - it.epochSecond } ?: 0L,
        )
    }
    LaunchedEffect(uiState.startedAt) {
        while (true) {
            delay(1_000)
            elapsedSeconds.longValue =
                uiState.startedAt?.let { Instant.now().epochSecond - it.epochSecond } ?: 0L
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SessionTopBar(
                name = uiState.name,
                elapsedSeconds = elapsedSeconds.longValue,
                onCloseClick = { showDiscardDialog = true },
                onFinishClick = { viewModel.onFinish() },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.cards, key = { it.sessionExerciseId }) { card ->
                ExerciseCard(
                    card = card,
                    entry = uiState.entry?.takeIf { it.sessionExerciseId == card.sessionExerciseId },
                    unit = uiState.unit,
                    onActivateCard = viewModel::onActivateCard,
                    onWeightIncrement = viewModel::onWeightIncrement,
                    onWeightDecrement = viewModel::onWeightDecrement,
                    onRepsIncrement = viewModel::onRepsIncrement,
                    onRepsDecrement = viewModel::onRepsDecrement,
                    onOpenNumpad = viewModel::onOpenNumpad,
                    onNumpadConfirm = viewModel::onNumpadConfirm,
                    onNumpadDismiss = viewModel::onNumpadDismiss,
                    onLogSet = viewModel::onLogSet,
                    onRequestRemoveExercise = viewModel::onRequestRemoveExercise,
                    onRequestReplaceExercise = viewModel::onRequestReplaceExercise,
                    onAddExercise = onAddExercise,
                    onLongPressSet = viewModel::onLongPressSet,
                    onEditSetSave = viewModel::onEditSetSave,
                    onDeleteSet = viewModel::onDeleteSet,
                    onCollapseEdit = viewModel::onCollapseEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                AddExerciseRow(onClick = onAddExercise)
            }
        }
    }

    // Discard confirm dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.session_discard_confirm_title)) },
            text = { Text(stringResource(R.string.session_discard_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.onDiscard()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.session_discard_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(
    name: String?,
    elapsedSeconds: Long,
    onCloseClick: () -> Unit,
    onFinishClick: () -> Unit,
) {
    val title = name ?: stringResource(R.string.session_untitled)
    val timerText = formatElapsed(elapsedSeconds)
    val closeCd = stringResource(R.string.cd_close_session)
    val finishCd = stringResource(R.string.cd_finish_session)

    TopAppBar(
        navigationIcon = {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.semantics { contentDescription = closeCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(
                onClick = onFinishClick,
                modifier = Modifier.semantics { contentDescription = finishCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

// ─── Add exercise row ─────────────────────────────────────────────────────────

@Composable
private fun AddExerciseRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(UiTestTags.ADD_EXERCISE),
    ) {
        Text(
            text = stringResource(R.string.session_add_exercise),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── Elapsed timer formatting ─────────────────────────────────────────────────

internal fun formatElapsed(seconds: Long): String {
    val absSeconds = seconds.coerceAtLeast(0)
    val h = absSeconds / 3600
    val m = (absSeconds % 3600) / 60
    val s = absSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val fakeInstant = Instant.ofEpochSecond(0)

private fun fakeSet(id: String, weightKg: Double, reps: Int) = LoggedSet(
    id = id,
    sessionExerciseId = "se1",
    weightKg = weightKg,
    reps = reps,
    position = 0,
    completedAt = fakeInstant,
    rpe = null,
    note = null,
    createdAt = fakeInstant,
    updatedAt = fakeInstant,
    deletedAt = null,
)

private val previewCards = listOf(
    ExerciseCardUi(
        sessionExerciseId = "se0",
        exerciseId = "ex0",
        name = "Squat",
        equipment = Equipment.BARBELL,
        targetSets = null,
        state = CardState.COMPLETED,
        sets = listOf(fakeSet("s0", 100.0, 5), fakeSet("s1", 100.0, 5)),
        ghostSets = emptyList(),
    ),
    ExerciseCardUi(
        sessionExerciseId = "se1",
        exerciseId = "ex1",
        name = "Bench Press",
        equipment = Equipment.BARBELL,
        targetSets = null,
        state = CardState.ACTIVE,
        sets = listOf(fakeSet("s2", 85.0, 8)),
        ghostSets = listOf(fakeSet("g1", 82.5, 8), fakeSet("g2", 82.5, 8)),
        editingSetId = null,
    ),
    ExerciseCardUi(
        sessionExerciseId = "se2",
        exerciseId = "ex2",
        name = "Row",
        equipment = Equipment.CABLE,
        targetSets = null,
        state = CardState.UPCOMING,
        sets = emptyList(),
        ghostSets = emptyList(),
    ),
)

private val previewEntry = EntryUi(
    sessionExerciseId = "se1",
    weightKg = 87.5,
    reps = 8,
)

@Preview(name = "ActiveSession – normal state", showBackground = true)
@Composable
private fun PreviewActiveSession() {
    LiftLogTheme {
        // Render the body content directly (without VM) for preview
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                SessionTopBar(
                    name = "Push Day",
                    elapsedSeconds = 1830,
                    onCloseClick = {},
                    onFinishClick = {},
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(previewCards, key = { it.sessionExerciseId }) { card ->
                    ExerciseCard(
                        card = card,
                        entry = previewEntry.takeIf { it.sessionExerciseId == card.sessionExerciseId },
                        unit = WeightUnit.KG,
                        onActivateCard = {},
                        onWeightIncrement = {},
                        onWeightDecrement = {},
                        onRepsIncrement = {},
                        onRepsDecrement = {},
                        onOpenNumpad = {},
                        onNumpadConfirm = {},
                        onNumpadDismiss = {},
                        onLogSet = {},
                        onRequestRemoveExercise = {},
                        onRequestReplaceExercise = {},
                        onAddExercise = {},
                        onLongPressSet = {},
                        onEditSetSave = { _, _, _, _, _ -> },
                        onDeleteSet = {},
                        onCollapseEdit = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { AddExerciseRow(onClick = {}) }
            }
        }
    }
}
