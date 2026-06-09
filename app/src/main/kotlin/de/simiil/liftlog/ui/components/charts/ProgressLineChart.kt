package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/** A single data point: x = session time (epoch ms), y = the selected metric value. */
data class ChartPoint(val x: Float, val y: Float, val isPr: Boolean)

/**
 * Progress line chart (04-analytics-spec §6, chart 2). Simple Vico line per the M4 decision.
 * Straight segments between session points (no interpolation/binning of gaps).
 */
@Composable
fun ProgressLineChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries { series(points.map { it.x }, points.map { it.y }) }
        }
    }
    // Y axis only for now: x is epoch-ms, so a default bottom axis renders raw millis
    // labels. Real date labels on x are a deferred refinement (M4 "simple Vico" decision).
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(188.dp),
    )
}
