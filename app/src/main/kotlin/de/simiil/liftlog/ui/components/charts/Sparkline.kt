package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Tiny 90-day e1RM sparkline (04-analytics-spec §6, chart 1). [color] is `error` when the
 * exercise's trend is down, `primary` otherwise. Renders only with ≥2 points.
 *
 * Drawn with a plain [Canvas] rather than Vico: a sparkline conveys shape, not magnitude, so it
 * normalizes the series to fill the height (Vico's line layer zero-bases the Y axis, which made a
 * rising series read as a near-flat line). A Canvas polyline is also far lighter per `LazyColumn`
 * row than a full chart host.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    Canvas(modifier.width(120.dp).height(34.dp)) {
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val padY = size.height * 0.15f
        val usableH = size.height - 2 * padY
        val stepX = size.width / (values.size - 1)

        // y inverts the normalized value so the highest point sits at the top.
        fun yFor(v: Float) = padY + (1f - (v - min) / span) * usableH

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yFor(v)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // End dot on the latest point.
        drawCircle(color, radius = 2.6.dp.toPx(), center = Offset((values.size - 1) * stepX, yFor(values.last())))
    }
}
