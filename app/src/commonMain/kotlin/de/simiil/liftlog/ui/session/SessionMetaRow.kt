package de.simiil.liftlog.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.domain.units.Decimals
import de.simiil.liftlog.ui.UiTestTags
import de.simiil.liftlog.ui.components.RpeStepper
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.common_done
import liftlog.app.generated.resources.rpe_value
import liftlog.app.generated.resources.session_meta_add
import liftlog.app.generated.resources.workout_note
import org.jetbrains.compose.resources.stringResource

/**
 * Unobtrusive workout-level note + RPE entry at the END of the Active Session list
 * (2026-06-11 spec §2). Collapsed it is a single quiet row; expanded it shows the note
 * field and the RPE stepper. There is no Save button: RPE persists on every change,
 * the note via the ViewModel's debounced [onNoteChange].
 */
@Composable
fun SessionMetaRow(
    rpe: Double?,
    note: String?,
    onRpeChange: (Double?) -> Unit,
    onNoteChange: (String) -> Unit,
    onNoteFlush: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Draft is seeded from the persisted note on first expand. rememberSaveable restores it
    // across process death; the flush-on-collapse/focus-loss + debounce keep the DB close behind.
    var noteDraft by rememberSaveable { mutableStateOf<String?>(null) }

    if (!expanded) {
        val summary =
            listOfNotNull(
                rpe?.let { stringResource(Res.string.rpe_value, Decimals.format(it)) },
                note?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        Surface(
            onClick = {
                if (noteDraft == null) noteDraft = note.orEmpty()
                expanded = true
            },
            shape = RoundedCornerShape(14.dp),
            color =
                if (summary.isEmpty()) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            modifier = modifier.fillMaxWidth().testTag(UiTestTags.SESSION_META_ROW),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (summary.isEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.session_meta_add),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.fillMaxWidth().testTag(UiTestTags.SESSION_META_ROW),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                RpeStepper(
                    value = rpe,
                    onValueChange = onRpeChange,
                    incrementTestTag = UiTestTags.RPE_INCREMENT,
                )
                OutlinedTextField(
                    value = noteDraft.orEmpty(),
                    onValueChange = {
                        noteDraft = it
                        onNoteChange(it)
                    },
                    label = { Text(stringResource(Res.string.workout_note)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag(UiTestTags.SESSION_META_NOTE)
                            .onFocusChanged { if (!it.isFocused) onNoteFlush() },
                    maxLines = 3,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        expanded = false
                        onNoteFlush()
                    }) {
                        Text(stringResource(Res.string.common_done))
                    }
                }
            }
        }
    }
}
