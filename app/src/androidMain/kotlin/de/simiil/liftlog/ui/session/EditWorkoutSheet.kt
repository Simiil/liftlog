package de.simiil.liftlog.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.components.RpeStepper
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Instant

private enum class PickField { START, END }

private enum class PickStage { DATE, TIME }

/**
 * Combines an M3 DatePicker selection with a picked time-of-day into an instant.
 *
 * [pickedUtcDateMillis] follows the DatePicker contract (UTC midnight of the picked calendar
 * day; null when the text-input mode cleared the field) — null falls back to [current]'s
 * LOCAL calendar date in [zone]. The combination applies [zone]'s rules of the picked date;
 * DST-gap times are shifted forward by kotlinx-datetime's `toInstant` (02:30 → 03:30 on
 * spring-forward), matching java.time's SMART resolver semantics.
 */
internal fun combineDateAndTime(
    pickedUtcDateMillis: Long?,
    hour: Int,
    minute: Int,
    current: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Instant {
    val date =
        pickedUtcDateMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
            ?: current.toLocalDateTime(zone).date
    return LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)
}

/**
 * "Edit workout" bottom sheet (2026-06-11 spec §2): start/end date-times, RPE, note.
 * Save is disabled while end <= start. Transient state only — process death just
 * closes the sheet (acceptable per spec §3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutSheet(
    startedAt: Instant,
    endedAt: Instant,
    rpe: Double?,
    note: String?,
    onSave: (Instant, Instant, Double?, String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formatters = koinInject<LocaleFormatters>()
    val zone = TimeZone.currentSystemDefault()
    var startMillis by remember { mutableLongStateOf(startedAt.toEpochMilliseconds()) }
    var endMillis by remember { mutableLongStateOf(endedAt.toEpochMilliseconds()) }
    var editRpe by remember { mutableStateOf(rpe) }
    var noteDraft by remember { mutableStateOf(note ?: "") }
    var pickField by remember { mutableStateOf<PickField?>(null) }
    var pickStage by remember { mutableStateOf(PickStage.DATE) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val valid = endMillis > startMillis
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun animateDismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.session_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DateTimeField(
                label = stringResource(R.string.session_edit_start),
                instant = Instant.fromEpochMilliseconds(startMillis),
                formatters = formatters,
                onClick = {
                    pickField = PickField.START
                    pickStage = PickStage.DATE
                },
            )
            DateTimeField(
                label = stringResource(R.string.session_edit_end),
                instant = Instant.fromEpochMilliseconds(endMillis),
                formatters = formatters,
                onClick = {
                    pickField = PickField.END
                    pickStage = PickStage.DATE
                },
                modifier = Modifier.padding(top = 8.dp),
            )
            RpeStepper(
                value = editRpe,
                onValueChange = { editRpe = it },
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                label = { Text(stringResource(R.string.workout_note)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                maxLines = 3,
            )
            if (!valid) {
                Text(
                    text = stringResource(R.string.session_edit_error_end_before_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.testTag(UiTestTags.SESSION_EDIT_DELETE),
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = ::animateDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        onSave(
                            Instant.fromEpochMilliseconds(startMillis),
                            Instant.fromEpochMilliseconds(endMillis),
                            editRpe,
                            noteDraft.trim().takeIf { it.isNotEmpty() },
                        )
                        animateDismiss()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.set_save))
                }
            }
        }
    }

    val editingField = pickField
    if (editingField != null && pickStage == PickStage.DATE) {
        val current =
            Instant.fromEpochMilliseconds(
                if (editingField == PickField.START) startMillis else endMillis,
            )
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    current
                        .toLocalDateTime(zone)
                        .date
                        .atStartOfDayIn(TimeZone.UTC)
                        .toEpochMilliseconds(),
            )
        DatePickerDialog(
            onDismissRequest = { pickField = null },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        pendingDateMillis = datePickerState.selectedDateMillis
                        pickStage = PickStage.TIME
                    },
                ) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickField = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (editingField != null && pickStage == PickStage.TIME) {
        val current =
            Instant.fromEpochMilliseconds(
                if (editingField == PickField.START) startMillis else endMillis,
            )
        val currentLocal = current.toLocalDateTime(zone)
        val timeState =
            rememberTimePickerState(
                initialHour = currentLocal.hour,
                initialMinute = currentLocal.minute,
                is24Hour = formatters.prefers24HourTime(),
            )
        AlertDialog(
            onDismissRequest = {
                pickField = null
                pickStage = PickStage.DATE
            },
            confirmButton = {
                TextButton(onClick = {
                    val combined =
                        combineDateAndTime(pendingDateMillis, timeState.hour, timeState.minute, current, zone)
                            .toEpochMilliseconds()
                    if (editingField == PickField.START) startMillis = combined else endMillis = combined
                    pickField = null
                    pickStage = PickStage.DATE
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pickField = null
                    pickStage = PickStage.DATE
                }) { Text(stringResource(R.string.common_cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.session_delete_confirm_title)) },
            text = { Text(stringResource(R.string.session_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    modifier = Modifier.testTag(UiTestTags.SESSION_DELETE_CONFIRM),
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
private fun DateTimeField(
    label: String,
    instant: Instant,
    formatters: LocaleFormatters,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text =
        formatters.mediumDateShortTime(
            instant,
            TimeZone.currentSystemDefault(),
        )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
