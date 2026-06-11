package de.simiil.liftlog.ui.session

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.components.RpeStepper
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class PickField { START, END }

private enum class PickStage { DATE, TIME }

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
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    var startMillis by remember { mutableLongStateOf(startedAt.toEpochMilli()) }
    var endMillis by remember { mutableLongStateOf(endedAt.toEpochMilli()) }
    var editRpe by remember { mutableStateOf(rpe) }
    var noteDraft by remember { mutableStateOf(note ?: "") }
    var pickField by remember { mutableStateOf<PickField?>(null) }
    var pickStage by remember { mutableStateOf(PickStage.DATE) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val valid = endMillis > startMillis

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.session_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DateTimeField(
                label = stringResource(R.string.session_edit_start),
                instant = Instant.ofEpochMilli(startMillis),
                zone = zone,
                onClick = {
                    pickField = PickField.START
                    pickStage = PickStage.DATE
                },
            )
            DateTimeField(
                label = stringResource(R.string.session_edit_end),
                instant = Instant.ofEpochMilli(endMillis),
                zone = zone,
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
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = valid,
                    onClick = {
                        onSave(
                            Instant.ofEpochMilli(startMillis),
                            Instant.ofEpochMilli(endMillis),
                            editRpe,
                            noteDraft.trim().takeIf { it.isNotEmpty() },
                        )
                        onDismiss()
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
            Instant
                .ofEpochMilli(if (editingField == PickField.START) startMillis else endMillis)
                .atZone(zone)
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    current
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { pickField = null },
            confirmButton = {
                TextButton(onClick = {
                    pendingDateMillis = datePickerState.selectedDateMillis
                    pickStage = PickStage.TIME
                }) { Text(stringResource(R.string.common_ok)) }
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
            Instant
                .ofEpochMilli(if (editingField == PickField.START) startMillis else endMillis)
                .atZone(zone)
        val timeState =
            rememberTimePickerState(
                initialHour = current.hour,
                initialMinute = current.minute,
                is24Hour = DateFormat.is24HourFormat(LocalContext.current),
            )
        AlertDialog(
            onDismissRequest = {
                pickField = null
                pickStage = PickStage.DATE
            },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker reports UTC-midnight millis for the picked calendar date.
                    val date =
                        Instant
                            .ofEpochMilli(pendingDateMillis ?: current.toInstant().toEpochMilli())
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    val combined =
                        date
                            .atTime(timeState.hour, timeState.minute)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
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
}

@Composable
private fun DateTimeField(
    label: String,
    instant: Instant,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(instant.atZone(zone))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
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
