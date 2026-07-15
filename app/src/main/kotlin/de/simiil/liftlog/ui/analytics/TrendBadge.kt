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
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.ui.theme.LocalLiftLogColors
import org.koin.compose.koinInject

@Composable
fun TrendBadge(
    trend: TrendResult,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val formatters = koinInject<LocaleFormatters>()
    val success = LocalLiftLogColors.current.success
    val size: TextUnit = if (large) 15.sp else 13.sp
    val (text, color, weight) =
        when (trend) {
            is TrendResult.Stale ->
                Triple(stringResource(R.string.trend_stale, trend.weeks), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
            TrendResult.Insufficient ->
                Triple(stringResource(R.string.trend_insufficient), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
            is TrendResult.Ok -> {
                val pct = formatters.signedOneDecimal(trend.percent)
                when (trend.direction) {
                    TrendDirection.UP -> Triple(stringResource(R.string.trend_up, pct), success, FontWeight.Bold)
                    TrendDirection.DOWN ->
                        Triple(
                            stringResource(R.string.trend_down, pct),
                            MaterialTheme.colorScheme.error,
                            FontWeight.Bold,
                        )
                    TrendDirection.FLAT ->
                        Triple(
                            stringResource(R.string.trend_flat, pct),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            FontWeight.Bold,
                        )
                }
            }
        }
    Text(text = text, color = color, fontWeight = weight, fontSize = size, modifier = modifier)
}
