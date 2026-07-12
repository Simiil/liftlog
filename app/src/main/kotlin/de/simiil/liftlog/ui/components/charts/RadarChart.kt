package de.simiil.liftlog.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One radar axis: label at the spoke tip, spoke length as a 0..1 rim fraction, trend-colored vertex. */
data class RadarSpoke(
    val label: String,
    val fraction: Float,
    val vertexColor: Color,
    /** false → hollow vertex (no trend data); true → filled (up/down/flat). */
    val vertexFilled: Boolean,
)

/**
 * Muscle-balance radar (spec 2026-07-10 §UI). Hand-drawn Canvas — Vico is Cartesian-only;
 * follows Sparkline.kt's precedent. Shape = dose polygon, vertex color = trend, dashed ring =
 * the sets/week target. Spoke 0 sits at 12 o'clock; order proceeds clockwise in list order.
 */
@Composable
fun RadarChart(
    spokes: List<RadarSpoke>,
    targetFraction: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (spokes.size < 3) return
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
    val polygonColor = MaterialTheme.colorScheme.primary
    // Hollow vertices get a hole in the card's surface color so the polygon doesn't show through.
    val holeColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier
            .aspectRatio(1f)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val rim = min(size.width, size.height) / 2f - 34.dp.toPx() // label margin
        val n = spokes.size

        fun angleRad(i: Int) = (-90.0 + i * 360.0 / n) * PI / 180.0

        fun point(
            i: Int,
            fraction: Float,
        ): Offset {
            val a = angleRad(i)
            return center + Offset((cos(a) * rim * fraction).toFloat(), (sin(a) * rim * fraction).toFloat())
        }

        // Grid: 3 faint rings + one spoke line per axis.
        for (ring in 1..3) {
            drawCircle(gridColor, radius = rim * ring / 3f, center = center, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) {
            drawLine(gridColor, center, point(i, 1f), strokeWidth = 1.dp.toPx())
        }

        // Dashed target ring.
        drawCircle(
            ringColor,
            radius = rim * targetFraction.coerceIn(0f, 1f),
            center = center,
            style =
                Stroke(
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                ),
        )

        // Dose polygon: translucent fill + solid stroke.
        val path = Path()
        spokes.forEachIndexed { i, s ->
            val p = point(i, s.fraction.coerceIn(0f, 1f))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, polygonColor.copy(alpha = 0.25f))
        drawPath(path, polygonColor, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))

        // Trend vertices: filled = up/down/flat, hollow = no trend data.
        spokes.forEachIndexed { i, s ->
            val p = point(i, s.fraction.coerceIn(0f, 1f))
            if (s.vertexFilled) {
                drawCircle(s.vertexColor, radius = 4.dp.toPx(), center = p)
            } else {
                drawCircle(holeColor, radius = 4.dp.toPx(), center = p)
                drawCircle(s.vertexColor, radius = 4.dp.toPx(), center = p, style = Stroke(1.5.dp.toPx()))
            }
        }

        // Labels just outside the rim, width-constrained so long labels ("Beinbeuger & Gesäß")
        // wrap instead of overflowing the canvas. The (0.5 − 0.5·cos/sin) factors slide the
        // anchor from left/top-aligned (right/bottom of chart) to right/bottom-aligned (left/top).
        spokes.forEachIndexed { i, s ->
            val a = angleRad(i)
            val anchor =
                center + Offset((cos(a) * (rim + 8.dp.toPx())).toFloat(), (sin(a) * (rim + 8.dp.toPx())).toFloat())
            val measured =
                textMeasurer.measure(
                    s.label,
                    labelStyle,
                    constraints = Constraints(maxWidth = 76.dp.roundToPx()),
                )
            drawText(
                measured,
                topLeft =
                    Offset(
                        anchor.x - measured.size.width * (0.5f - 0.5f * cos(a).toFloat()),
                        anchor.y - measured.size.height * (0.5f - 0.5f * sin(a).toFloat()),
                    ),
            )
        }
    }
}
