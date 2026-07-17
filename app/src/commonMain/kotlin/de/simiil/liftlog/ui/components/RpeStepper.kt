package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.domain.units.Decimals
import de.simiil.liftlog.domain.units.Rpe
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.cd_decrease_rpe
import liftlog.app.generated.resources.cd_increase_rpe
import liftlog.app.generated.resources.cd_rpe_clear
import liftlog.app.generated.resources.cd_rpe_value
import liftlog.app.generated.resources.rpe_clear
import liftlog.app.generated.resources.rpe_descriptor_half
import liftlog.app.generated.resources.rpe_descriptor_whole
import liftlog.app.generated.resources.rpe_detail_10
import liftlog.app.generated.resources.rpe_detail_6
import liftlog.app.generated.resources.rpe_detail_7
import liftlog.app.generated.resources.rpe_detail_8
import liftlog.app.generated.resources.rpe_detail_9
import liftlog.app.generated.resources.rpe_label
import liftlog.app.generated.resources.rpe_short_10
import liftlog.app.generated.resources.rpe_short_6
import liftlog.app.generated.resources.rpe_short_7
import liftlog.app.generated.resources.rpe_short_8
import liftlog.app.generated.resources.rpe_short_9
import liftlog.app.generated.resources.rpe_unset_hint
import liftlog.app.generated.resources.rpe_unset_value
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Workout-level RPE entry (2026-06-11 spec §2): the familiar − / + stepper shell with a
 * plain-language descriptor below. 6.0–10.0 in 0.5 steps; null = not rated. First tap on
 * either button starts at [Rpe.DEFAULT]; "Clear" resets to null. Stateless.
 */
@Composable
fun RpeStepper(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    incrementTestTag: String? = null,
) {
    Column(modifier = modifier) {
        StepperShell(
            onDecrement = { onValueChange(Rpe.decrement(value)) },
            onIncrement = { onValueChange(Rpe.increment(value)) },
            onValueClick = null,
            decrementCd = stringResource(Res.string.cd_decrease_rpe),
            incrementCd = stringResource(Res.string.cd_increase_rpe),
            valueCd =
                stringResource(
                    Res.string.cd_rpe_value,
                    value?.let { Decimals.format(it) } ?: stringResource(Res.string.rpe_unset_value),
                ),
            numberText = value?.let { Decimals.format(it) } ?: stringResource(Res.string.rpe_unset_value),
            unitText = stringResource(Res.string.rpe_label),
            modifier = Modifier.fillMaxWidth(),
            incrementTestTag = incrementTestTag,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rpeDescriptor(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (value != null) {
                val clearCd = stringResource(Res.string.cd_rpe_clear)
                TextButton(
                    onClick = { onValueChange(null) },
                    modifier = Modifier.semantics { contentDescription = clearCd },
                ) {
                    Text(stringResource(Res.string.rpe_clear))
                }
            }
        }
    }
}

/**
 * Whole values render as "label — detail" (e.g. Hard — tough, with some reserve left);
 * halves render as a between-phrase of the two neighbouring labels.
 */
@Composable
private fun rpeDescriptor(value: Double?): String {
    if (value == null) return stringResource(Res.string.rpe_unset_hint)
    return if (Rpe.isWhole(value)) {
        stringResource(Res.string.rpe_descriptor_whole, rpeShort(value.toInt()), rpeDetail(value.toInt()))
    } else {
        stringResource(Res.string.rpe_descriptor_half, rpeShort(floor(value).toInt()), rpeShort(ceil(value).toInt()))
    }
}

@Composable
private fun rpeShort(whole: Int): String =
    stringResource(
        when (whole) {
            6 -> Res.string.rpe_short_6
            7 -> Res.string.rpe_short_7
            8 -> Res.string.rpe_short_8
            9 -> Res.string.rpe_short_9
            else -> Res.string.rpe_short_10
        },
    )

@Composable
private fun rpeDetail(whole: Int): String =
    stringResource(
        when (whole) {
            6 -> Res.string.rpe_detail_6
            7 -> Res.string.rpe_detail_7
            8 -> Res.string.rpe_detail_8
            9 -> Res.string.rpe_detail_9
            else -> Res.string.rpe_detail_10
        },
    )
