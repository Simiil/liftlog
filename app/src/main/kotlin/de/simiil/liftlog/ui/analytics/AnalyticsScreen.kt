package de.simiil.liftlog.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simiil.liftlog.R
import de.simiil.liftlog.domain.analytics.TrendDirection
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.TrainedExercise
import de.simiil.liftlog.domain.repository.WeekSummary
import de.simiil.liftlog.domain.units.Weights
import de.simiil.liftlog.ui.components.charts.Sparkline
import de.simiil.liftlog.ui.exercises.exerciseDisplayName
import de.simiil.liftlog.ui.theme.LocalLiftLogColors

@Composable
fun AnalyticsScreen(
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsBrowserViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        ui.week?.let { WeekCard(it, ui.unit) }
        Spacer(Modifier.height(12.dp))
        SearchBar(ui.query, viewModel::onQueryChange)
        if (ui.exercises.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.analytics_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn {
                items(ui.exercises, key = { it.id }) { ex ->
                    ExerciseRow(ex, ui.unit, viewModel, onOpenExercise)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun WeekCard(
    week: WeekSummary,
    unit: WeightUnit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(R.string.analytics_week_head),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stat(week.sessions.toString(), stringResource(R.string.analytics_stat_sessions), Modifier.weight(1f))
                Stat(week.sets.toString(), stringResource(R.string.analytics_stat_sets), Modifier.weight(1f))
                Stat(
                    stringResource(
                        R.string.analytics_stat_volume_value,
                        String.format(java.util.Locale.getDefault(), "%.1f", week.volumeKg / 1000),
                    ),
                    stringResource(R.string.analytics_stat_volume),
                    Modifier.weight(1f),
                )
            }
            val delta =
                if (week.prevVolumeKg > 0) {
                    ((week.volumeKg - week.prevVolumeKg) / week.prevVolumeKg * 100).toInt()
                } else {
                    0
                }
            val sign = if (delta >= 0) "+" else ""
            Text(
                stringResource(R.string.analytics_week_delta, "$sign$delta"),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (delta >= 0) {
                        LocalLiftLogColors.current.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
    }
}

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchBar(
    query: String,
    onChange: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(100.dp)) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.analytics_search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    ex: TrainedExercise,
    unit: WeightUnit,
    viewModel: AnalyticsBrowserViewModel,
    onOpen: (String) -> Unit,
) {
    val summary by viewModel.summary(ex.id).collectAsStateWithLifecycle(initialValue = null)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(ex.id) }
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 16.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(exerciseDisplayName(ex.id, ex.name), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            val s = summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (s != null && s.trend !is TrendResult.Stale) {
                    val metric =
                        if (s.bodyweight) {
                            // Bodyweight headline is now total reps (matches volume = total work).
                            stringResource(R.string.analytics_reps_value, s.currentValue.toInt())
                        } else {
                            // currentValue is volume in canonical kg; shown with the unit label, no
                            // conversion — consistent with ExerciseDetailViewModel.label()'s VOLUME case.
                            stringResource(R.string.analytics_volume_value, s.currentValue.toLong().toString(), Weights.label(unit))
                        }
                    Text(metric, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // In the overview, an insufficient-data row shows just the name + metric —
                // no "need N sessions" badge and (below) no sparkline. (Stale/Ok badges still show.)
                if (s != null && s.trend !is TrendResult.Insufficient) TrendBadge(s.trend)
            }
        }
        val s = summary
        if (s != null && s.trend !is TrendResult.Stale && s.sessions.size >= 2) {
            val isDown = (s.trend as? TrendResult.Ok)?.direction == TrendDirection.DOWN
            Sparkline(
                values = s.sessions.map { it.primary.toFloat() },
                color = if (isDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
