package com.momobook.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momobook.data.FlowBucket
import com.momobook.data.Ranked
import com.momobook.ui.components.FitText
import com.momobook.ui.components.Fmt
import com.momobook.ui.theme.Tabular
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class Slice(val label: String, val value: Long, val color: Color)

@Composable
private fun rememberEntrance(key: Any?, durationMs: Int = 900): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value
}

/** Donut with a centred total and a legend showing share of each slice. */
@Composable
fun DonutWithLegend(slices: List<Slice>, centerLabel: String, centerValue: String) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val progress = rememberEntrance(slices)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column {
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp)) {
                val stroke = size.minDimension * 0.15f
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                var start = -90f
                val gap = if (slices.size > 1) 1.6f else 0f
                slices.forEach { s ->
                    val sweep = 360f * s.value / total * progress
                    if (sweep > gap) {
                        drawArc(s.color, start + gap / 2, sweep - gap, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                    }
                    start += sweep
                }
            }
            Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FitText(centerValue, style = MaterialTheme.typography.titleLarge.merge(Tabular))
            }
        }
        Spacer(Modifier.height(12.dp))
        slices.forEach { s ->
            val pct = s.value * 100.0 / total
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(s.color))
                Spacer(Modifier.width(10.dp))
                Text(s.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (pct < 1) "<1%" else "${pct.toInt()}%",
                    style = MaterialTheme.typography.labelLarge.merge(Tabular),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
                FitText(Fmt.num(s.value), Modifier.widthIn(max = 120.dp), style = MaterialTheme.typography.bodyMedium.merge(Tabular), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Ranked horizontal bars: name, total, count and a proportional bar. */
@Composable
fun RankedBars(items: List<Ranked>, color: Color, countNoun: String = "txn", onClick: ((Ranked) -> Unit)? = null) {
    val maxV = items.maxOfOrNull { it.amount }?.coerceAtLeast(1) ?: 1
    var started by remember(items) { mutableStateOf(false) }
    LaunchedEffect(items) { started = true }
    Column(verticalArrangement = Arrangement.spacedBy(if (onClick != null) 4.dp else 14.dp)) {
        items.forEachIndexed { i, r ->
            val frac by animateFloatAsState(
                if (started) r.amount.toFloat() / maxV else 0f,
                tween(700, delayMillis = i * 70, easing = FastOutSlowInEasing), label = "bar",
            )
            Column(
                if (onClick == null) Modifier
                else Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick(r) }.padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(color.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${r.count} $countNoun${if (r.count == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FitText(Fmt.ugx(r.amount), Modifier.widthIn(max = 170.dp), style = MaterialTheme.typography.bodyMedium.merge(Tabular), fontWeight = FontWeight.Bold)
                    if (onClick != null) Icon(
                        Icons.Rounded.ChevronRight, null, Modifier.padding(start = 2.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color.copy(alpha = .65f), color)))
                    )
                }
            }
        }
    }
}

/** Grouped in/out bars per time bucket with a y axis. */
@Composable
fun CashFlowChart(buckets: List<FlowBucket>, inColor: Color, outColor: Color, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val progress = rememberEntrance(buckets)
    Canvas(modifier.fillMaxWidth().height(210.dp)) {
        if (buckets.isEmpty()) return@Canvas
        val maxV = buckets.maxOf { max(it.inAmt, it.outAmt) }.coerceAtLeast(1)
        val left = 40.dp.toPx()
        val bottom = 22.dp.toPx()
        val chartH = size.height - bottom
        val chartW = size.width - left
        for (i in 0..3) {
            val y = chartH - chartH * i / 3f
            drawLine(
                grid, Offset(left, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = if (i == 0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val lay = tm.measure(Fmt.compact(maxV * i / 3), labelStyle)
            drawText(lay, topLeft = Offset(0f, y - lay.size.height / 2f))
        }
        val groupW = chartW / buckets.size
        val barW = min(groupW * 0.3f, 14.dp.toPx())
        val gap = 2.dp.toPx()
        val r = CornerRadius(min(barW / 2, 4.dp.toPx()))
        val labelEvery = ceil(buckets.size / 7f).toInt().coerceAtLeast(1)
        buckets.forEachIndexed { i, b ->
            val cx = left + groupW * i + groupW / 2
            val hIn = chartH * b.inAmt / maxV * progress
            val hOut = chartH * b.outAmt / maxV * progress
            if (hIn > 0) drawRoundRect(inColor, Offset(cx - gap / 2 - barW, chartH - hIn), Size(barW, hIn), r)
            if (hOut > 0) drawRoundRect(outColor, Offset(cx + gap / 2, chartH - hOut), Size(barW, hOut), r)
            if ((buckets.size - 1 - i) % labelEvery == 0) {
                val lay = tm.measure(b.label, labelStyle)
                drawText(lay, topLeft = Offset(cx - lay.size.width / 2f, chartH + 6.dp.toPx()))
            }
        }
    }
}

/** Smooth time-series line with gradient fill. Used for the balance trend and the hero sparkline. */
@Composable
fun TrendLine(
    points: List<Pair<Long, Long>>,
    color: Color,
    modifier: Modifier = Modifier,
    showAxis: Boolean = true,
    fillAlpha: Float = .28f,
) {
    val tm = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val progress = rememberEntrance(points, 1100)
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minT = points.first().first
        val maxT = points.last().first
        val lo = points.minOf { it.second }
        val hi = points.maxOf { it.second }
        val span = (hi - lo).coerceAtLeast(1)
        val left = if (showAxis) 40.dp.toPx() else 0f
        val top = 6.dp.toPx()
        val bottom = if (showAxis) 6.dp.toPx() else 2.dp.toPx()
        val w = size.width - left
        val h = size.height - top - bottom
        fun x(t: Long) = left + if (maxT == minT) 0f else w * (t - minT).toFloat() / (maxT - minT)
        fun y(v: Long) = top + h - h * (v - lo).toFloat() / span

        if (showAxis) {
            listOf(hi, (hi + lo) / 2, lo).forEach { v ->
                val yy = y(v)
                drawLine(grid, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val lay = tm.measure(Fmt.compact(v), labelStyle)
                drawText(lay, topLeft = Offset(0f, yy - lay.size.height / 2f))
            }
        }

        val pts = points.map { Offset(x(it.first), y(it.second)) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]; val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val clipW = left + w * progress
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close()
        }
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, clipW, size.height)
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = fillAlpha), color.copy(alpha = 0f)), startY = top, endY = size.height))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawContext.canvas.restore()
        if (progress > .98f) {
            val last = pts.last()
            drawCircle(surface, 6.dp.toPx(), last)
            drawCircle(color, 4.dp.toPx(), last)
        }
    }
}
