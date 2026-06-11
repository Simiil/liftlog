package de.simiil.liftlog.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.units.Decimals
import de.simiil.liftlog.ui.theme.LiftLogTheme

/**
 * Inline numpad that **replaces the stepper area** — not a dialog (design mockup `.numpad`).
 *
 * - The host card stays visible; this slides in where the steppers were.
 * - The system keyboard is never shown for weight/reps entry (03-ux-spec §4.3).
 * - This composable knows nothing about kg/lb — it edits raw display-unit text; the caller
 *   converts. [unitLabel] is shown next to the running value purely for display ("kg"/"reps").
 * - Numpad keys are ≥ 56 dp (logging-path a11y constraint, 03-ux-spec §7).
 */
@Composable
fun InlineNumpad(
    initialText: String,
    allowDecimal: Boolean,
    quickChips: List<Double>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    unitLabel: String? = null,
) {
    var currentText by remember(initialText) { mutableStateOf(initialText) }
    val confirmCd = stringResource(R.string.cd_confirm)
    // Intentionally not remembered: re-evaluates when a locale change recomposes.
    val sep = Decimals.separator()

    fun appendDigit(digit: Int) {
        currentText = (currentText + digit).replaceFirst(Regex("^0(\\d)"), "$1")
    }

    fun appendDecimal() {
        if (allowDecimal && !currentText.contains(sep)) {
            currentText = if (currentText.isEmpty()) "0$sep" else "$currentText$sep"
        }
    }

    fun backspace() {
        if (currentText.isNotEmpty()) currentText = currentText.dropLast(1)
    }

    fun applyChip(delta: Double) {
        val result = ((Decimals.parse(currentText) ?: 0.0) + delta).coerceAtLeast(0.0)
        currentText = Decimals.format(result)
    }

    Column(modifier = modifier) {
        // ── Head: running value + Cancel ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentText.ifEmpty { "0" },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unitLabel != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.numpad_dismiss))
            }
        }

        // ── Quick-delta chips (weight mode only) ──────────────────────────────
        if (quickChips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quickChips.forEach { delta ->
                    val label = formatDelta(delta)
                    QuickChip(
                        label = label,
                        contentDesc = stringResource(R.string.cd_numpad_add, label),
                        onClick = { applyChip(delta) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── 4×3 grid: [1 2 3] [4 5 6] [7 8 9] [. 0 ⌫] ─────────────────────────
        val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { digit ->
                    NumKey(
                        contentDesc = stringResource(R.string.cd_digit, digit),
                        onClick = { appendDigit(digit) },
                        modifier = Modifier.weight(1f),
                    ) {
                        NumKeyText(digit.toString())
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Decimal cell — present only in weight mode (mockup leaves it empty for reps)
            if (allowDecimal) {
                NumKey(
                    contentDesc = stringResource(R.string.cd_decimal),
                    onClick = { appendDecimal() },
                    modifier = Modifier.weight(1f),
                ) {
                    NumKeyText(sep.toString())
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            NumKey(
                contentDesc = stringResource(R.string.cd_digit, 0),
                onClick = { appendDigit(0) },
                modifier = Modifier.weight(1f),
            ) {
                NumKeyText("0")
            }
            NumKey(
                contentDesc = stringResource(R.string.cd_backspace),
                onClick = { backspace() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Confirm (full width) ──────────────────────────────────────────────
        Button(
            onClick = { onConfirm(currentText) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = confirmCd },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.numpad_confirm), style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun formatDelta(delta: Double): String {
    val abs = Decimals.format(kotlin.math.abs(delta))
    return if (delta >= 0) "+$abs" else "-$abs"
}

@Composable
private fun QuickChip(
    label: String,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun NumKey(
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun NumKeyText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        color = color,
    )
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
            unitLabel = "kg",
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
            unitLabel = "reps",
            modifier = Modifier
                .width(360.dp)
                .padding(8.dp),
        )
    }
}
