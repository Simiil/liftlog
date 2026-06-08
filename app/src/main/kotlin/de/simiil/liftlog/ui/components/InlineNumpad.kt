package de.simiil.liftlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.theme.LiftLogTheme

/**
 * Inline numpad that **replaces the stepper area** — not a dialog.
 *
 * - The host card stays visible; this slides in where the steppers were.
 * - The system keyboard is never shown for weight/reps entry (03-ux-spec §4.3).
 * - This composable knows nothing about kg/lb — it edits raw display-unit text;
 *   the caller is responsible for converting to/from storage units.
 * - All interactive targets are ≥ 56 dp (logging-path a11y constraint, 03-ux-spec §7).
 * - State is held internally seeded from [initialText]; final value reported via [onConfirm].
 */
@Composable
fun InlineNumpad(
    initialText: String,
    allowDecimal: Boolean,
    quickChips: List<Double>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentText by remember(initialText) { mutableStateOf(initialText) }

    val confirmCd = stringResource(R.string.cd_confirm)

    fun appendDigit(digit: Int) {
        currentText += digit.toString()
    }

    fun appendDecimal() {
        if (allowDecimal && !currentText.contains('.')) {
            currentText = if (currentText.isEmpty()) "0." else "$currentText."
        }
    }

    fun backspace() {
        if (currentText.isNotEmpty()) {
            currentText = currentText.dropLast(1)
        }
    }

    fun applyChip(delta: Double) {
        val current = currentText.toDoubleOrNull() ?: 0.0
        val result = (current + delta).coerceAtLeast(0.0)
        // Format: strip trailing ".0", keep up to 2 significant decimal places
        currentText = if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            "%.2f".format(result).trimEnd('0').trimEnd('.')
        }
    }

    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Current value display
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp),
        ) {
            Text(
                text = currentText.ifEmpty { "0" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // Quick-delta chips (weight mode only)
        if (quickChips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickChips.forEach { delta ->
                    val label = if (delta == delta.toLong().toDouble()) {
                        (if (delta >= 0) "+${delta.toLong()}" else delta.toLong().toString())
                    } else {
                        (if (delta >= 0) "+${"%.4g".format(delta).trimEnd('0').trimEnd('.')}"
                        else "%.4g".format(delta).trimEnd('0').trimEnd('.'))
                    }
                    val chipCd = stringResource(R.string.cd_numpad_add, label)
                    FilledTonalButton(
                        onClick = { applyChip(delta) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 56.dp)
                            .semantics { contentDescription = chipCd },
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4×3 digit grid: rows [1,2,3], [4,5,6], [7,8,9], [., 0, ⌫]
        val digitRows = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        )
        digitRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { digit ->
                    val digitCd = stringResource(R.string.cd_digit, digit)
                    OutlinedButton(
                        onClick = { appendDigit(digit) },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 56.dp)
                            .semantics { contentDescription = digitCd },
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Bottom row: decimal / 0 / backspace
        val decimalCd = stringResource(R.string.cd_decimal)
        val backsapceCd = stringResource(R.string.cd_backspace)
        val digitZeroCd = stringResource(R.string.cd_digit, 0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Decimal point — disabled (greyed) when !allowDecimal or already present
            val decimalEnabled = allowDecimal && !currentText.contains('.')
            OutlinedButton(
                onClick = { appendDecimal() },
                enabled = decimalEnabled,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { contentDescription = decimalCd },
            ) {
                Text(text = ".", style = MaterialTheme.typography.titleLarge)
            }

            OutlinedButton(
                onClick = { appendDigit(0) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { contentDescription = digitZeroCd },
            ) {
                Text(text = "0", style = MaterialTheme.typography.titleLarge)
            }

            OutlinedButton(
                onClick = { backspace() },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { contentDescription = backsapceCd },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null, // described by button semantics above
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Confirm + Dismiss
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.numpad_dismiss),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Button(
                onClick = { onConfirm(currentText) },
                modifier = Modifier
                    .weight(2f)
                    .defaultMinSize(minHeight = 56.dp)
                    .semantics { contentDescription = confirmCd },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.numpad_confirm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(name = "InlineNumpad – weight mode (decimal + chips)", showBackground = true)
@Composable
private fun PreviewInlineNumpadWeight() {
    LiftLogTheme {
        InlineNumpad(
            initialText = "82.5",
            allowDecimal = true,
            quickChips = listOf(10.0, 5.0, 2.5, -2.5),
            onConfirm = {},
            onDismiss = {},
            modifier = Modifier
                .width(360.dp)
                .padding(8.dp),
        )
    }
}

@Preview(name = "InlineNumpad – reps mode (no decimal, no chips)", showBackground = true)
@Composable
private fun PreviewInlineNumpadReps() {
    LiftLogTheme {
        InlineNumpad(
            initialText = "8",
            allowDecimal = false,
            quickChips = emptyList(),
            onConfirm = {},
            onDismiss = {},
            modifier = Modifier
                .width(360.dp)
                .padding(8.dp),
        )
    }
}
