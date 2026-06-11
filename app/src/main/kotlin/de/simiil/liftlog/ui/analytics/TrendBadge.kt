package de.simiil.liftlog.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.ui.theme.LocalLiftLogColors

@Composable
fun TrendBadge(trend: TrendResult, modifier: Modifier = Modifier, large: Boolean = false) {
    val success = LocalLiftLogColors.current.success
    val size: TextUnit = if (large) 15.sp else 13.sp
    val (text, color, weight) = when (trend) {
        is TrendResult.Stale ->
            Triple(stringResource(R.string.trend_stale, trend.weeks), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
        TrendResult.Insufficient ->
            Triple(stringResource(R.string.trend_insufficient), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
        is TrendResult.Ok -> {
            val pct = formatPercent(trend.percent)
            when (trend.direction) {
                TrendDirection.UP -> Triple(stringResource(R.string.trend_up, pct), success, FontWeight.Bold)
                TrendDirection.DOWN -> Triple(stringResource(R.string.trend_down, pct), MaterialTheme.colorScheme.error, FontWeight.Bold)
                TrendDirection.FLAT -> Triple(stringResource(R.string.trend_flat, pct), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Bold)
            }
        }
    }
    Text(text = text, color = color, fontWeight = weight, fontSize = size, modifier = modifier)
}

/** "+4.0" / "-2.1" — one decimal, locale separator, explicit sign for non-negative. */
private fun formatPercent(percent: Double): String =
    String.format(java.util.Locale.getDefault(), "%+.1f", percent)
