package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * A single data point: x = whole minutes since the first charted session, y = the selected metric
 * value.
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
 *
 * Drawn with a plain [Canvas] rather than Vico, following Sparkline.kt/RadarChart.kt's precedent.
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
    val dotColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    val ys = points.map { it.y }
    val dataMin = ys.min()
    val dataMax = ys.max()
    val pad = ((dataMax - dataMin).takeIf { it > 0f } ?: (dataMax.takeIf { it != 0f } ?: 1f)) * 0.12f
    val minY = if (zeroBased) 0f else dataMin - pad
    val maxY = dataMax + pad
    val ticks = niceTicks(minY.toDouble(), maxY.toDouble())

    Canvas(
        modifier
            .fillMaxWidth()
            .height(188.dp)
            // The chart is non-text content; expose a spoken summary so screen-reader
            // users get the trend the line conveys visually (F-06).
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        val tickLayouts = ticks.map { t -> t to textMeasurer.measure(tickLabel(t), labelStyle) }
        val labelWidth = tickLayouts.maxOf { it.second.size.width } + 8.dp.toPx()
        val plot = Rect(labelWidth, 6.dp.toPx(), size.width, size.height - 6.dp.toPx())
        val xMin = points.first().x
        val xSpan = (points.last().x - xMin).takeIf { it > 0f } ?: 1f
        val yFirst = ticks.first()
        val ySpan = (ticks.last() - yFirst).toFloat().takeIf { it > 0f } ?: 1f

        fun px(p: ChartPoint) =
            Offset(
                plot.left + (p.x - xMin) / xSpan * plot.width,
                plot.bottom - (p.y - yFirst.toFloat()) / ySpan * plot.height,
            )

        // Gridlines + labels.
        tickLayouts.forEach { (t, layout) ->
            val y = plot.bottom - (t - yFirst).toFloat() / ySpan * plot.height
            drawLine(axisColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }

        // Polyline, drawn under the dots so each session dot reads as a marker on the line.
        val path =
            Path().apply {
                points.forEachIndexed { i, p ->
                    val o = px(p)
                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                }
            }
        drawPath(path, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Dots: 7dp regular / 11dp PR (diameters — radius = half).
        points.forEach { p -> drawCircle(dotColor, radius = (if (p.isPr) 11.dp else 7.dp).toPx() / 2f, center = px(p)) }
    }
}
