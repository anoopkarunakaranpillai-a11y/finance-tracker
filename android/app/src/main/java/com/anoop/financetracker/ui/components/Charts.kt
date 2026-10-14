package com.anoop.financetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anoop.financetracker.ui.theme.Ft
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PairPoint(val label: String, val a: Long, val b: Long)

/**
 * Grouped bars (income vs expenses). Tap a group to see its values.
 * Bars grow in once when the data changes (a short, restrained animation).
 */
@Composable
fun PairBarChart(
    points: List<PairPoint>,
    aColor: Color, bColor: Color,
    aName: String, bName: String,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Int = 170,
) {
    var sel by remember(points) { mutableIntStateOf(points.indexOfLast { it.a != 0L || it.b != 0L }) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(points) { grow.snapTo(0f); grow.animateTo(1f, tween(450)) }
    val maxV = max(1L, points.maxOfOrNull { max(it.a, it.b) } ?: 1L)
    val line = Ft.c.line
    val desc = points.joinToString("; ") { "${it.label}: $aName ${format(it.a)}, $bName ${format(it.b)}" }
    Column(modifier) {
        val s = points.getOrNull(sel)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s?.label ?: " ", style = MaterialTheme.typography.labelMedium, color = Ft.c.muted, modifier = Modifier.weight(1f))
            if (s != null) {
                Dot(aColor); Text(" ${format(s.a)}   ", style = MaterialTheme.typography.labelMedium)
                Dot(bColor); Text(" ${format(s.b)}", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().height(height.dp)
                .semantics { contentDescription = "Chart. $desc" }
                .pointerInput(points) {
                    detectTapGestures { o ->
                        if (points.isEmpty()) return@detectTapGestures
                        val gw = size.width / points.size
                        sel = (o.x / gw).toInt().coerceIn(0, points.size - 1)
                    }
                }
        ) {
            val n = points.size.coerceAtLeast(1)
            val gw = size.width / n
            val bw = min(18.dp.toPx(), gw * 0.34f)
            val base = size.height - 2f
            for (i in 0..3) {
                val y = base * i / 3f
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            points.forEachIndexed { i, p ->
                val cx = gw * i + gw / 2
                if (i == sel) drawRoundRect(line.copy(alpha = 0.5f), Offset(gw * i + 2, 0f), Size(gw - 4, size.height), CornerRadius(8f))
                val ha = base * p.a / maxV * grow.value
                val hb = base * p.b / maxV * grow.value
                drawRoundRect(aColor, Offset(cx - bw - 1.5f, base - ha), Size(bw, ha), CornerRadius(4f))
                drawRoundRect(bColor, Offset(cx + 1.5f, base - hb), Size(bw, hb), CornerRadius(4f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val step = max(1, points.size / 7)
            points.forEachIndexed { i, p ->
                Text(if (i % step == 0) p.label else "", style = MaterialTheme.typography.labelSmall, color = Ft.c.muted,
                    maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(aColor, aName); Legend(bColor, bName)
        }
    }
}

@Composable
fun Dot(c: Color, size: Int = 8) = Box(Modifier.size(size.dp).background(c, CircleShape))

@Composable
fun Legend(c: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Dot(c); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium, color = Ft.c.muted)
}

data class Slice(val label: String, val value: Long, val color: Color)

/** Donut with a tappable legend; the centre shows the selected slice or the total. */
@Composable
fun DonutChart(slices: List<Slice>, format: (Long) -> String, centerLabel: String, modifier: Modifier = Modifier) {
    var sel by remember(slices) { mutableIntStateOf(-1) }
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val track = Ft.c.line
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)
                .semantics { contentDescription = "Donut chart. " + slices.joinToString("; ") { "${it.label} ${format(it.value)}" } }
                .pointerInput(slices) {
                    detectTapGestures { o ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val d = o - c
                        val r = sqrt(d.x * d.x + d.y * d.y)
                        if (r < size.width * 0.28f || r > size.width / 2f) { sel = -1; return@detectTapGestures }
                        var ang = (atan2(d.y, d.x) * 180 / PI).toFloat() + 90f
                        if (ang < 0) ang += 360f
                        var acc = 0f
                        slices.forEachIndexed { i, s ->
                            val sw = 360f * s.value / total
                            if (ang >= acc && ang < acc + sw) { sel = i; return@detectTapGestures }
                            acc += sw
                        }
                    }
                }) {
                val stroke = size.width * 0.16f
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sw = 360f * s.value / total
                    val w = if (i == sel) stroke * 1.18f else stroke
                    drawArc(s.color, start, max(0.5f, sw - 1.2f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt))
                    start += sw
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val s = slices.getOrNull(sel)
                Text(s?.let { format(it.value) } ?: format(slices.sumOf { it.value }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(s?.label ?: centerLabel, style = MaterialTheme.typography.labelSmall, color = Ft.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(90.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.take(7).forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth().clickable { sel = if (sel == i) -1 else i }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(s.color)
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Normal)
                    Text("${(s.value * 100 / total)}%", style = MaterialTheme.typography.bodySmall, color = Ft.c.muted)
                }
            }
        }
    }
}

/** Simple line for cash flow (running total). */
@Composable
fun LineChart(values: List<Long>, labels: List<String>, color: Color, format: (Long) -> String, modifier: Modifier = Modifier) {
    var sel by remember(values) { mutableIntStateOf(values.lastIndex) }
    val line = Ft.c.line
    val minV = min(0L, values.minOrNull() ?: 0L)
    val maxV = max(1L, values.maxOrNull() ?: 1L)
    Column(modifier) {
        Row {
            Text(labels.getOrNull(sel) ?: "", style = MaterialTheme.typography.labelMedium, color = Ft.c.muted, modifier = Modifier.weight(1f))
            Text(values.getOrNull(sel)?.let(format) ?: "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp)
            .semantics { contentDescription = "Cash flow line. Ends at ${values.lastOrNull()?.let(format) ?: "nothing"}" }
            .pointerInput(values) {
                detectTapGestures { o ->
                    if (values.size > 1) sel = ((o.x / size.width) * (values.size - 1) + 0.5f).toInt().coerceIn(0, values.lastIndex)
                }
            }) {
            if (values.isEmpty()) return@Canvas
            val span = (maxV - minV).toFloat().coerceAtLeast(1f)
            fun y(v: Long) = size.height - (v - minV) / span * size.height
            fun x(i: Int) = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            drawLine(line, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.5f)
            val p = Path()
            values.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
            drawPath(p, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            values.getOrNull(sel)?.let { drawCircle(color, 5.dp.toPx(), Offset(x(sel), y(it))) }
        }
    }
}

/** Thin progress bar that turns amber at 80% and red at 100%. */
@Composable
fun ProgressBar(ratio: Double, modifier: Modifier = Modifier, color: Color? = null) {
    val c = color ?: when { ratio >= 1.0 -> Ft.c.expense; ratio >= 0.8 -> Ft.c.warn; else -> MaterialTheme.colorScheme.primary }
    val track = Ft.c.line
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        val w = size.width * ratio.toFloat().coerceIn(0f, 1f)
        if (w > 0) drawRoundRect(c, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2))
    }
}
