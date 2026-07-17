package de.simiil.liftlog.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.components.dashedBorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.cd_close_session
import liftlog.app.generated.resources.cd_finish_session
import liftlog.app.generated.resources.common_cancel
import liftlog.app.generated.resources.session_add_exercise
import liftlog.app.generated.resources.session_discard_confirm
import liftlog.app.generated.resources.session_discard_confirm_message
import liftlog.app.generated.resources.session_discard_confirm_title
import liftlog.app.generated.resources.session_finish_summary
import liftlog.app.generated.resources.session_untitled
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

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
    viewModel: ActiveSessionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }

    NotificationPermissionEffect(viewModel::onNotificationPermissionResult)

    LaunchedEffect(pickedExerciseId) {
        if (pickedExerciseId != null) {
            viewModel.onPickedExercise(pickedExerciseId)
            onPickedExerciseConsumed()
        }
    }

    val finishMessage =
        stringResource(
            Res.string.session_finish_summary,
            uiState.name ?: stringResource(Res.string.session_untitled),
            uiState.lastFinishedSetCount,
        )
    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            launch { snackbarHostState.showSnackbar(finishMessage, duration = SnackbarDuration.Short) }
            delay(1500)
            onFinished()
        }
    }

    LaunchedEffect(uiState.discarded) {
        if (uiState.discarded) onDiscarded()
    }

    val elapsedSeconds =
        remember(uiState.startedAt) {
            androidx.compose.runtime.mutableLongStateOf(
                uiState.startedAt?.let { Clock.System.now().epochSeconds - it.epochSeconds } ?: 0L,
            )
        }
    LaunchedEffect(uiState.startedAt) {
        while (true) {
            delay(1_000)
            elapsedSeconds.longValue =
                uiState.startedAt?.let { Clock.System.now().epochSeconds - it.epochSeconds } ?: 0L
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

            item { AddExerciseRow(onClick = onAddExercise) }

            item(key = "session_meta") {
                SessionMetaRow(
                    rpe = uiState.sessionRpe,
                    note = uiState.sessionNote,
                    onRpeChange = viewModel::onSessionRpeChange,
                    onNoteChange = viewModel::onSessionNoteChange,
                    onNoteFlush = viewModel::onNoteFlush,
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(Res.string.session_discard_confirm_title)) },
            text = { Text(stringResource(Res.string.session_discard_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.onDiscard()
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.session_discard_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// internal (not private) so the androidMain @Preview file (ActiveSessionScreenPreviews) can use it.
internal fun SessionTopBar(
    name: String?,
    elapsedSeconds: Long,
    onCloseClick: () -> Unit,
    onFinishClick: () -> Unit,
) {
    val title = name ?: stringResource(Res.string.session_untitled)
    val timerText = formatElapsed(elapsedSeconds)
    val closeCd = stringResource(Res.string.cd_close_session)
    val finishCd = stringResource(Res.string.cd_finish_session)

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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            Text(
                text = timerText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onFinishClick,
                modifier =
                    Modifier
                        .size(48.dp)
                        .semantics { contentDescription = finishCd },
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            }
            Spacer(Modifier.width(4.dp))
        },
    )
}

// ─── Add exercise row (dashed) ─────────────────────────────────────────────────

@Composable
// internal (not private) so the androidMain @Preview file (ActiveSessionScreenPreviews) can use it.
internal fun AddExerciseRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .dashedBorder(color = MaterialTheme.colorScheme.outline, width = 1.5.dp, cornerRadius = 20.dp)
                .clickable(onClick = onClick)
                .testTag(UiTestTags.ADD_EXERCISE),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.session_add_exercise),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Elapsed timer formatting ─────────────────────────────────────────────────

internal fun formatElapsed(seconds: Long): String {
    val absSeconds = seconds.coerceAtLeast(0)
    val h = absSeconds / 3600
    val m = (absSeconds % 3600) / 60
    val s = absSeconds % 60

    // String.format is JVM-only; padStart is the common-Kotlin equivalent (identical output).
    fun pad2(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "${pad2(m)}:${pad2(s)}"
}
