package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.format.LocaleFormatters
import de.simiil.liftlog.ui.components.PrBadge
import de.simiil.liftlog.ui.components.charts.ProgressLineChart
import kotlinx.datetime.TimeZone
import liftlog.app.generated.resources.Res
import liftlog.app.generated.resources.analytics_need_two
import liftlog.app.generated.resources.analytics_sessions
import liftlog.app.generated.resources.cd_progress_chart
import liftlog.app.generated.resources.metric_e1rm
import liftlog.app.generated.resources.metric_max_reps
import liftlog.app.generated.resources.metric_top_set
import liftlog.app.generated.resources.metric_total_reps
import liftlog.app.generated.resources.metric_volume
import liftlog.app.generated.resources.navigate_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = koinViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val formatters = koinInject<LocaleFormatters>()
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(
            title = { Text(ui.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.navigate_back),
                    )
                }
            },
        )
    }) { inner ->
        // Single LazyColumn: the chips/range/chart/value are header items that scroll away above
        // the full (lazy) session list, so the screen scrolls smoothly with a long history.
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
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
            }
            item {
                RangePills(
                    selected = ui.selectedRange,
                    onChange = viewModel::onRangeChange,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
            item {
                // The empty-data placeholder reuses the chart's exact container (same surface,
                // radius, padding, and 188dp content height) so it reads as a chart-shaped slot
                // with the message centered — not a stray line of text.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.padding(vertical = 14.dp, horizontal = 10.dp)) {
                        if (ui.notEnoughData) {
                            Box(
                                Modifier.fillMaxWidth().height(188.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(Res.string.analytics_need_two),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            ProgressLineChart(
                                ui.chartPoints,
                                zeroBased = ui.chartZeroBased,
                                contentDescription =
                                    stringResource(
                                        Res.string.cd_progress_chart,
                                        metricLabel(ui.selectedMetric),
                                        ui.chartPoints.size,
                                    ),
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(ui.currentValueLabel, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    // Trend tracks the selected range; shown for weighted exercises only. The
                    // Insufficient "need N sessions" badge is suppressed here — the chart slot
                    // already says "need 2+ sessions", so showing it twice is redundant.
                    if (ui.summary?.bodyweight == false) {
                        ui.trend?.takeIf { it !is TrendResult.Insufficient }?.let { TrendBadge(it, formatters, large = true) }
                    }
                }
            }
            item {
                Text(
                    stringResource(Res.string.analytics_sessions),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(ui.recent, key = { it.sessionId }) { row ->
                val dateLabel =
                    formatters.weekdayDayMonth(
                        kotlin.time.Instant.fromEpochMilliseconds(row.dateMillis),
                        TimeZone.currentSystemDefault(),
                    )
                SessionRow(row, dateLabel, onOpenSession)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SessionRow(
    row: RecentSessionRow,
    dateLabel: String,
    onOpenSession: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenSession(row.sessionId) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(dateLabel, fontSize = 14.sp, modifier = Modifier.width(92.dp))
        Text(
            row.summary,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (row.isPr) {
            PrBadge()
        }
    }
}

@Composable
private fun metricLabel(m: Metric) =
    stringResource(
        when (m) {
            Metric.E1RM -> Res.string.metric_e1rm
            Metric.TOP_SET -> Res.string.metric_top_set
            Metric.VOLUME -> Res.string.metric_volume
            Metric.MAX_REPS -> Res.string.metric_max_reps
            Metric.TOTAL_REPS -> Res.string.metric_total_reps
        },
    )
