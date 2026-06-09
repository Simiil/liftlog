package de.simiil.liftlog.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a dashed rounded-rect border (Compose has no first-class dashed border).
 * Shared by the Home empty-session tile, the Active Session "add exercise" row, and the
 * Plans editor's dashed "add" rows — all of which mirror the mockup's dashed affordances
 * (`.template-chip.empty`, `.add-exercise`, `.add-row`).
 */
internal fun Modifier.dashedBorder(
    color: Color,
    width: Dp,
    cornerRadius: Dp,
    on: Dp = 6.dp,
    off: Dp = 4.dp,
): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())),
        ),
    )
}
