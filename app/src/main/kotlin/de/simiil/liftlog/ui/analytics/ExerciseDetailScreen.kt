package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.ui.components.charts.ProgressLineChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(
            title = { Text(ui.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            },
        )
    }) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Metric chips
            Row(
                Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ui.metrics.forEach { m ->
                    FilterChip(
                        selected = m == ui.selectedMetric,
                        onClick = { viewModel.onMetricChange(m) },
                        label = { Text(metricLabel(m)) },
                    )
                }
            }
            // Range selector row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Range.entries.forEach { r ->
                    val selected = r == ui.selectedRange
                    Surface(
                        onClick = { viewModel.onRangeChange(r) },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                rangeLabel(r),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            // Chart or insufficient-data message
            if (ui.notEnoughData) {
                Text(
                    stringResource(R.string.analytics_need_two),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Box(Modifier.padding(vertical = 14.dp, horizontal = 10.dp)) {
                        ProgressLineChart(ui.chartPoints, zeroBased = ui.chartZeroBased)
                    }
                }
            }
            // Current value + trend badge
            Row(
                Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(ui.currentValueLabel, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                ui.summary?.let { if (!it.bodyweight) TrendBadge(it.trend, large = true) }
            }
            // Recent sessions section header
            Text(
                stringResource(R.string.analytics_recent_sessions),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            // Recent session rows
            val fmt = rememberDateFormat()
            ui.recent.forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSession(row.sessionId) }
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        fmt.format(Date(row.dateMillis)),
                        fontSize = 14.sp,
                        modifier = Modifier.width(92.dp),
                    )
                    Text(
                        row.summary,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.isPr) {
                        Text(
                            stringResource(R.string.analytics_pr),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun rememberDateFormat() = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

@Composable
private fun metricLabel(m: Metric) = stringResource(
    when (m) {
        Metric.E1RM -> R.string.metric_e1rm
        Metric.TOP_SET -> R.string.metric_top_set
        Metric.VOLUME -> R.string.metric_volume
        Metric.MAX_REPS -> R.string.metric_max_reps
        Metric.TOTAL_REPS -> R.string.metric_total_reps
    }
)

@Composable
private fun rangeLabel(r: Range) = stringResource(
    when (r) {
        Range.D30 -> R.string.range_30d
        Range.D90 -> R.string.range_90d
        Range.Y1 -> R.string.range_1y
        Range.ALL -> R.string.range_all
    }
)
