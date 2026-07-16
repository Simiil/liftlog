package de.simiil.liftlog.ui.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.ui.theme.LocalLiftLogColors
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.trend_down
import liftlog.app.generated.resources.trend_flat
import liftlog.app.generated.resources.trend_insufficient
import liftlog.app.generated.resources.trend_stale
import liftlog.app.generated.resources.trend_up
import org.jetbrains.compose.resources.stringResource

@Composable
fun TrendBadge(
    trend: TrendResult,
    formatters: LocaleFormatters,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val success = LocalLiftLogColors.current.success
    val size: TextUnit = if (large) 15.sp else 13.sp
    val (text, color, weight) =
        when (trend) {
            is TrendResult.Stale ->
                Triple(stringResource(Res.string.trend_stale, trend.weeks), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
            TrendResult.Insufficient ->
                Triple(stringResource(Res.string.trend_insufficient), MaterialTheme.colorScheme.onSurfaceVariant, FontWeight.Medium)
            is TrendResult.Ok -> {
                val pct = formatters.signedOneDecimal(trend.percent)
                when (trend.direction) {
                    TrendDirection.UP -> Triple(stringResource(Res.string.trend_up, pct), success, FontWeight.Bold)
                    TrendDirection.DOWN ->
                        Triple(
                            stringResource(Res.string.trend_down, pct),
                            MaterialTheme.colorScheme.error,
                            FontWeight.Bold,
                        )
                    TrendDirection.FLAT ->
                        Triple(
                            stringResource(Res.string.trend_flat, pct),
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            FontWeight.Bold,
                        )
                }
            }
        }
    Text(text = text, color = color, fontWeight = weight, fontSize = size, modifier = modifier)
}
