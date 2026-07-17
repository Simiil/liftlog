package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.simiil.liftlog.domain.analytics.MuscleBalance
import de.simiil.liftlog.domain.analytics.RadarGroup
import de.simiil.liftlog.domain.analytics.TARGET_SETS_PER_WEEK
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.ui.components.charts.RadarChart
import de.simiil.liftlog.ui.components.charts.RadarSpoke
import de.simiil.liftlog.ui.theme.LocalLiftLogColors
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.balance_cd_down
import liftlog.app.generated.resources.balance_cd_flat
import liftlog.app.generated.resources.balance_cd_group
import liftlog.app.generated.resources.balance_cd_no_trend
import liftlog.app.generated.resources.balance_cd_up
import liftlog.app.generated.resources.balance_empty
import liftlog.app.generated.resources.balance_group_arms
import liftlog.app.generated.resources.balance_group_core
import liftlog.app.generated.resources.balance_group_hams_glutes
import liftlog.app.generated.resources.balance_legend_down
import liftlog.app.generated.resources.balance_legend_flat
import liftlog.app.generated.resources.balance_legend_no_trend
import liftlog.app.generated.resources.balance_legend_target
import liftlog.app.generated.resources.balance_legend_up
import liftlog.app.generated.resources.balance_title
import liftlog.app.generated.resources.balance_unclassified
import liftlog.app.generated.resources.muscle_back
import liftlog.app.generated.resources.muscle_calves
import liftlog.app.generated.resources.muscle_chest
import liftlog.app.generated.resources.muscle_quads
import liftlog.app.generated.resources.muscle_shoulders
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Chart display order: chest at 12 o'clock, clockwise; push right, pull/posterior left. */
private val displayOrder =
    listOf(
        RadarGroup.CHEST,
        RadarGroup.SHOULDERS,
        RadarGroup.ARMS,
        RadarGroup.QUADS,
        RadarGroup.CALVES,
        RadarGroup.HAMS_GLUTES,
        RadarGroup.CORE,
        RadarGroup.BACK,
    )

@Composable
fun MuscleBalanceCard(
    state: MuscleBalanceUiState,
    onRangeChange: (Range) -> Unit,
    modifier: Modifier = Modifier,
) {
    val balance = state.balance ?: return
    val formatters = koinInject<LocaleFormatters>()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(Res.string.balance_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            RangePills(state.selectedRange, onRangeChange)
            if (balance.isEmpty) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.balance_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                RadarChart(
                    spokes = radarSpokes(balance),
                    targetFraction = balance.targetFraction.toFloat(),
                    contentDescription = balanceDescription(balance, formatters),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Legend()
            }
            if (balance.unclassifiedSets > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(
                        Res.plurals.balance_unclassified,
                        balance.unclassifiedSets,
                        balance.unclassifiedSets,
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun radarSpokes(balance: MuscleBalance): List<RadarSpoke> {
    val byGroup = balance.groups.associateBy { it.group }
    val success = LocalLiftLogColors.current.success
    return displayOrder.map { g ->
        val gb = byGroup.getValue(g)
        RadarSpoke(
            label = stringResource(groupLabel(g)),
            fraction = gb.fraction.toFloat(),
            vertexColor =
                when (gb.direction) {
                    TrendDirection.UP -> success
                    TrendDirection.DOWN -> MaterialTheme.colorScheme.error
                    TrendDirection.FLAT, null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            vertexFilled = gb.direction != null,
        )
    }
}

private fun groupLabel(g: RadarGroup) =
    when (g) {
        RadarGroup.CHEST -> Res.string.muscle_chest
        RadarGroup.BACK -> Res.string.muscle_back
        RadarGroup.SHOULDERS -> Res.string.muscle_shoulders
        RadarGroup.ARMS -> Res.string.balance_group_arms
        RadarGroup.QUADS -> Res.string.muscle_quads
        RadarGroup.HAMS_GLUTES -> Res.string.balance_group_hams_glutes
        RadarGroup.CALVES -> Res.string.muscle_calves
        RadarGroup.CORE -> Res.string.balance_group_core
    }

/**
 * Spoken summary for TalkBack (chart is non-text content; ProgressLineChart convention).
 * NOTE: composable calls must stay inside the `map` (inline) — `joinToString`'s lambda is not
 * inline, so `stringResource` inside it would not compile.
 */
@Composable
private fun balanceDescription(
    balance: MuscleBalance,
    formatters: LocaleFormatters,
): String {
    val byGroup = balance.groups.associateBy { it.group }
    val parts =
        displayOrder.map { g ->
            val gb = byGroup.getValue(g)
            stringResource(
                Res.string.balance_cd_group,
                stringResource(groupLabel(g)),
                formatters.oneDecimal(gb.setsPerWeek),
                stringResource(
                    when (gb.direction) {
                        TrendDirection.UP -> Res.string.balance_cd_up
                        TrendDirection.DOWN -> Res.string.balance_cd_down
                        TrendDirection.FLAT -> Res.string.balance_cd_flat
                        null -> Res.string.balance_cd_no_trend
                    },
                ),
            )
        }
    return parts.joinToString(", ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendDot(LocalLiftLogColors.current.success, filled = true, stringResource(Res.string.balance_legend_up))
        LegendDot(MaterialTheme.colorScheme.error, filled = true, stringResource(Res.string.balance_legend_down))
        LegendDot(neutral, filled = true, stringResource(Res.string.balance_legend_flat))
        LegendDot(neutral, filled = false, stringResource(Res.string.balance_legend_no_trend))
        LegendDash(neutral, stringResource(Res.string.balance_legend_target, TARGET_SETS_PER_WEEK.toInt()))
    }
}

@Composable
private fun LegendDot(
    color: Color,
    filled: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) {
            if (filled) drawCircle(color) else drawCircle(color, style = Stroke(1.5.dp.toPx()))
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDash(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.width(14.dp).height(8.dp)) {
            drawLine(
                color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
