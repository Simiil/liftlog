package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

/**
 * A single data point: x = whole minutes since the first charted session, y = the selected metric
 * value. x values must sit on a grid no finer than 1e-4 — Vico crashes on finer grids ("The x
 * values are too precise") — and integral minutes also survive Float→Double conversion exactly.
 */
data class ChartPoint(
    val x: Float,
    val y: Float,
    val isPr: Boolean,
)

/**
 * Progress line chart (04-analytics-spec §6, chart 2). A `primary` line with a dot at every
 * session and a larger `tertiary` dot on PR sessions. Y is zoomed to the data for weight/rep
 * metrics (so progress is visible) and zero-based for cumulative metrics ([zeroBased] = volume /
 * total reps). Straight segments between session points — no interpolation/binning of gaps.
 */
@Composable
fun ProgressLineChart(
    points: List<ChartPoint>,
    zeroBased: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (points.size < 2) return

    val lineColor = MaterialTheme.colorScheme.primary
    // Filled with the chart-card color so each regular session reads as a hollow cut-out on the
    // line in both light and dark (PR sessions get the prominent tertiary dot below).
    val regularPoint =
        LineCartesianLayer.Point(
            rememberShapeComponent(fill = Fill(MaterialTheme.colorScheme.tertiary.toArgb()), shape = CorneredShape.Pill),
            sizeDp = 7f,
        )
    val prPoint =
        LineCartesianLayer.Point(
            rememberShapeComponent(fill = Fill(MaterialTheme.colorScheme.tertiary.toArgb()), shape = CorneredShape.Pill),
            sizeDp = 11f,
        )
    val prXs = remember(points) { points.filter { it.isPr }.map { it.x.toDouble() }.toSet() }
    val pointProvider =
        remember(regularPoint, prPoint, prXs) {
            object : LineCartesianLayer.PointProvider {
                override fun getPoint(
                    entry: LineCartesianLayerModel.Entry,
                    seriesIndex: Int,
                    extraStore: ExtraStore,
                ) = if (entry.x in prXs) prPoint else regularPoint

                override fun getLargestPoint(extraStore: ExtraStore) = prPoint
            }
        }

    // Zoom Y to the data (with headroom) for weight/rep metrics; zero-based for cumulative ones.
    val rangeProvider =
        remember(points, zeroBased) {
            val ys = points.map { it.y }
            val dataMin = ys.min().toDouble()
            val dataMax = ys.max().toDouble()
            val pad = ((dataMax - dataMin).takeIf { it > 0.0 } ?: (dataMax.takeIf { it != 0.0 } ?: 1.0)) * 0.12
            if (zeroBased) {
                CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = dataMax + pad)
            } else {
                CartesianLayerRangeProvider.fixed(minY = dataMin - pad, maxY = dataMax + pad)
            }
        }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries { series(points.map { it.x }, points.map { it.y }) }
        }
    }

    // Y axis only: x is minutes-since-first, so a default bottom axis would render raw offsets.
    // Real date labels on x are a deferred refinement (M4 "simple Vico" decision).
    CartesianChartHost(
        // Scrolling off → the host zooms to fit the whole series in the viewport (Zoom.Content).
        // With scrolling on (the default), Vico sizes content at one x-step (= the GCD of the
        // x deltas, often 1 minute) per ~20dp, so all but the first few minutes of the range
        // landed off-screen and the line read as flat.
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider =
                        LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(Fill(lineColor.toArgb())),
                                pointProvider = pointProvider,
                            ),
                        ),
                    rangeProvider = rangeProvider,
                ),
                startAxis = VerticalAxis.rememberStart(),
            ),
        modelProducer = modelProducer,
        // The chart is non-text content; expose a spoken summary so screen-reader users get the
        // trend the line conveys visually (F-06).
        modifier =
            modifier
                .fillMaxWidth()
                .height(188.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
    )
}
