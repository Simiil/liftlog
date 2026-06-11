package de.simiil.liftlog.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.simiil.liftlog.domain.analytics.Aggregation
import de.simiil.liftlog.domain.analytics.ExerciseSummary
import de.simiil.liftlog.domain.analytics.SessionPoint
import de.simiil.liftlog.domain.analytics.TrendPoint
import de.simiil.liftlog.domain.analytics.TrendResult
import de.simiil.liftlog.domain.analytics.downsample
import de.simiil.liftlog.domain.analytics.trend
import de.simiil.liftlog.domain.model.WeightUnit
import de.simiil.liftlog.domain.repository.AnalyticsRepository
import de.simiil.liftlog.domain.repository.SettingsRepository
import de.simiil.liftlog.ui.exercises.ExerciseNameResolver
import de.simiil.liftlog.ui.components.charts.ChartPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class Metric { E1RM, TOP_SET, VOLUME, MAX_REPS, TOTAL_REPS }
enum class Range(val days: Long) { D30(30), D90(90), Y1(365), ALL(99_999) }

data class RecentSessionRow(
    val sessionId: String,
    val dateMillis: Long,
    val summary: String,
    val isPr: Boolean,
)

data class ExerciseDetailUiState(
    val name: String = "",
    val summary: ExerciseSummary? = null,
    val metrics: List<Metric> = emptyList(),
    val selectedMetric: Metric = Metric.E1RM,
    val selectedRange: Range = Range.D90,
    val chartPoints: List<ChartPoint> = emptyList(),
    /** True for cumulative metrics (volume / total reps) → zero-based Y; else Y zooms to data. */
    val chartZeroBased: Boolean = false,
    val currentValueLabel: String = "",
    /** Trend over the selected range (primary metric). Shown for weighted exercises only. */
    val trend: TrendResult? = null,
    val recent: List<RecentSessionRow> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
    val notEnoughData: Boolean = false,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analyticsRepository: AnalyticsRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val names: ExerciseNameResolver,
) : ViewModel() {

    private val exerciseId: String = checkNotNull(savedStateHandle["exerciseId"])
    private val selectedMetric = MutableStateFlow<Metric?>(null)
    private val selectedRange = MutableStateFlow(Range.D90)

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        analyticsRepository.observeExerciseSummary(exerciseId),
        analyticsRepository.observeTrainedExercises(),
        selectedMetric,
        selectedRange,
        settingsRepository.weightUnit,
    ) { summary, trained, metricOrNull, range, unit ->
        val trainedRow = trained.firstOrNull { it.id == exerciseId }
        val name = trainedRow?.let { names.displayName(it.id, it.name) } ?: ""
        if (summary == null) {
            return@combine ExerciseDetailUiState(name = name, notEnoughData = true, unit = unit)
        }
        val metrics = if (summary.bodyweight) listOf(Metric.MAX_REPS, Metric.TOTAL_REPS)
            else listOf(Metric.E1RM, Metric.TOP_SET, Metric.VOLUME)
        val metric = metricOrNull?.takeIf { it in metrics } ?: metrics.first()

        val cutoff = nowFallbackCutoff(summary, range)
        val rangeFiltered = summary.sessions.filter { it.timeMillis >= cutoff }
        // The chart needs >=2 points; fall back to the last two when the range is too sparse.
        // The sessions list below uses rangeFiltered directly (honest to the selected window).
        var inRange = rangeFiltered
        if (inRange.size < 2) inRange = summary.sessions.takeLast(2)

        val zeroBased = metric == Metric.VOLUME || metric == Metric.TOTAL_REPS
        val agg = if (zeroBased) Aggregation.SUM else Aggregation.MAX
        val raw = inRange.map { TrendPoint(it.timeMillis, valueOf(it, metric)) }
        val ds = downsample(raw, agg)
        // x = days since the first in-range session: preserves real spacing (sparse-data honesty,
        // 04-analytics-spec §6) while staying small enough for Float (epoch-ms would lose precision).
        val t0 = inRange.first().timeMillis
        fun dayX(t: Long) = ((t - t0) / 86_400_000.0).toFloat()
        val pts = if (ds.size == inRange.size) {
            // 1:1 with sessions — can carry PR flags
            inRange.map { ChartPoint(dayX(it.timeMillis), valueOf(it, metric).toFloat(), prFor(it, metric)) }
        } else {
            ds.map { ChartPoint(dayX(it.timeMillis), it.value.toFloat(), false) }
        }

        // Trend over the selected window (primary metric), so the badge tracks the range pills.
        val rangeTrend = trend(
            summary.sessions.map { TrendPoint(it.timeMillis, it.primary) },
            clock.millis(),
            windowDays = range.days,
        )
        val last = summary.sessions.last()
        ExerciseDetailUiState(
            name = name,
            summary = summary,
            metrics = metrics,
            selectedMetric = metric,
            selectedRange = range,
            chartPoints = pts,
            chartZeroBased = zeroBased,
            currentValueLabel = label(valueOf(last, metric), metric, unit),
            trend = rangeTrend,
            recent = rangeFiltered.reversed().map { sp ->
                RecentSessionRow(
                    sessionId = sp.sessionId,
                    dateMillis = sp.timeMillis,
                    summary = recentSummary(sp, summary.bodyweight, unit),
                    isPr = sp.isPr,
                )
            },
            unit = unit,
            notEnoughData = summary.sessions.size < 2,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun onMetricChange(m: Metric) { selectedMetric.value = m }
    fun onRangeChange(r: Range) { selectedRange.value = r }

    private fun nowFallbackCutoff(summary: ExerciseSummary, range: Range): Long {
        val newest = summary.sessions.maxOf { it.timeMillis }
        return newest - range.days * 86_400_000L
    }

    private fun valueOf(s: SessionPoint, m: Metric): Double = when (m) {
        Metric.E1RM -> s.metrics.e1rmKg
        Metric.TOP_SET -> s.metrics.topSetKg
        Metric.VOLUME -> s.metrics.volumeKg
        Metric.MAX_REPS -> s.metrics.maxReps.toDouble()
        Metric.TOTAL_REPS -> s.metrics.totalReps.toDouble()
    }

    private fun prFor(s: SessionPoint, m: Metric): Boolean = when (m) {
        Metric.TOP_SET -> s.isPrTopSet
        Metric.E1RM -> s.isPrE1rm
        Metric.MAX_REPS -> s.isPrReps
        else -> false
    }

    private fun label(v: Double, m: Metric, unit: WeightUnit): String = when (m) {
        Metric.VOLUME -> "${v.toLong()} ${de.simiil.liftlog.domain.units.Weights.label(unit)}"
        Metric.MAX_REPS, Metric.TOTAL_REPS -> "${v.toInt()} reps"
        else -> "${de.simiil.liftlog.domain.units.Weights.format(v, unit)} ${de.simiil.liftlog.domain.units.Weights.label(unit)}"
    }

    private fun recentSummary(s: SessionPoint, bodyweight: Boolean, unit: WeightUnit): String {
        if (bodyweight) return "${s.metrics.maxReps} reps best"
        val topW = s.sets.maxOfOrNull { it.weightKg } ?: 0.0
        val reps = s.sets.take(3).joinToString("·") { it.reps.toString() }
        return "${de.simiil.liftlog.domain.units.Weights.format(topW, unit)} ${de.simiil.liftlog.domain.units.Weights.label(unit)} × $reps"
    }
}
