package com.momobook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.momobook.ui.theme.Brand

/** The MomoBook mark, drawn from the same geometry as the launcher icon (logo.svg). */
@Composable
fun MomoLogo(size: Dp = 40.dp, modifier: Modifier = Modifier, withBackground: Boolean = true) {
    Canvas(modifier.size(size)) {
        // Map the icon's 72-unit safe zone (18..90 of 108) onto this canvas.
        val u = this.size.minDimension / 72f
        fun p(x: Float, y: Float) = Offset((x - 18f) * u, (y - 18f) * u)
        if (withBackground) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Brand.Navy, Color(0xFF0A0E16)), Offset.Zero, Offset(this.size.width, this.size.height)),
                cornerRadius = CornerRadius(17f * u),
            )
        }
        drawCircle(Color.Black.copy(alpha = .2f), 27f * u, p(55.5f, 56f))
        drawCircle(Brand.Yellow, 27f * u, p(54f, 54f))
        drawCircle(Brand.YellowDeep, 22.5f * u, p(54f, 54f), style = Stroke(1.6f * u))
        val stroke = Stroke(5.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val m = Path().apply {
            p(40f, 66f).let { moveTo(it.x, it.y) }
            p(40f, 45f).let { lineTo(it.x, it.y) }
            p(52f, 57f).let { lineTo(it.x, it.y) }
            p(66f, 43f).let { lineTo(it.x, it.y) }
            p(57.5f, 43f).let { moveTo(it.x, it.y) }
            p(66f, 43f).let { lineTo(it.x, it.y) }
            p(66f, 51.5f).let { lineTo(it.x, it.y) }
        }
        drawPath(m, Brand.Ink, style = stroke)
    }
}
