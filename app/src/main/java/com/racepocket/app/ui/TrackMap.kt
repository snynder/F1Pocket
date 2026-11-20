package com.racepocket.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racepocket.app.R
import com.racepocket.app.data.TrackGeometry
import com.racepocket.app.data.TrackZones
import com.racepocket.app.data.ZonePos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

val SECTOR_COLORS = listOf(Color(0xFFE8336D), Color(0xFFF5C518), Color(0xFF3D8BFF))
private val ZONE = Color(0xFFFF2A2A)
private val OVERTAKE = Color(0xFF4CD68C)

data class CarDot(val x: Float, val y: Float, val label: String, val color: Color)

private class Layout(
    val pts: List<Offset>,
    val minX: Float, val minY: Float, val w: Float, val h: Float,
    val sectors: List<IntArray>,
    val zones: List<IntArray>,
    val corners: List<Triple<Offset, Offset, String>>,
    val detection: Int?, val activation: Int?,
    val rot: (Float, Float) -> Offset
)

private fun buildLayout(g: TrackGeometry, z: TrackZones?): Layout {
    val a = Math.toRadians(g.rotation.toDouble())
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    val rot = { x: Float, y: Float -> Offset(x * ca - y * sa, -(x * sa + y * ca)) }
    val n = g.xs.size
    val pts = (0 until n).map { rot(g.xs[it], g.ys[it]) }
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val cum = FloatArray(n)
    for (i in 1 until n) cum[i] = cum[i - 1] + hypot(g.xs[i] - g.xs[i - 1], g.ys[i] - g.ys[i - 1])
    val total = cum[n - 1] + hypot(g.xs[0] - g.xs[n - 1], g.ys[0] - g.ys[n - 1])

    fun nearest(x: Float, y: Float): Int = (0 until n).minBy { hypot(g.xs[it] - x, g.ys[it] - y) }
    fun index(p: ZonePos): Int? {
        val c = g.corners.firstOrNull { it.number == p.corner } ?: return null
        val base = nearest(c.x, c.y)
        val target = (((cum[base] + p.meters * 10f) % total) + total) % total
        val i = cum.indexOfFirst { it >= target }
        return if (i < 0) n - 1 else i
    }
    fun span(from: Int, to: Int): IntArray {
        val out = ArrayList<Int>()
        var i = from
        while (true) {
            out += i
            if (i == to || out.size > n) break
            i = (i + 1) % n
        }
        return out.toIntArray()
    }
    val s1 = g.s1.coerceIn(0, n - 1)
    val s2 = g.s2.coerceIn(s1, n - 1)
    val sectors = listOf(span(0, s1), span(s1, s2), span(s2, n - 1) + intArrayOf(0))
    val zones = z?.straights?.mapNotNull { (from, to) ->
        val i = index(from) ?: return@mapNotNull null
        val j = index(to) ?: return@mapNotNull null
        span(i, j)
    } ?: emptyList()
    val corners = g.corners.map { c ->
        val p = rot(c.x, c.y)
        val ang = Math.toRadians(c.angle.toDouble())
        val q = rot(c.x + cos(ang).toFloat() * 500f, c.y + sin(ang).toFloat() * 500f)
        val dx = q.x - p.x
        val dy = q.y - p.y
        val l = hypot(dx, dy).takeIf { it > 0 } ?: 1f
        Triple(p, Offset(dx / l, dy / l), c.label)
    }
    return Layout(
        pts, minX, minY, (maxX - minX).coerceAtLeast(1f), (maxY - minY).coerceAtLeast(1f),
        sectors, zones, corners,
        z?.detection?.let { index(it) }, z?.activation?.let { index(it) }, rot
    )
}

@Composable
fun TrackMap(geo: TrackGeometry, zones: TrackZones?, modifier: Modifier = Modifier, cars: List<CarDot> = emptyList(), compact: Boolean = false) {
    val layout = remember(geo, zones) { buildLayout(geo, zones) }
    val tm = rememberTextMeasurer()
    val p = pal()
    val ratio = (layout.w / layout.h).coerceIn(0.7f, 2.2f)
    // compact (grid cells with a fixed height): fill the cell and fit inside it; otherwise keep the track's proportions
    val sized = if (compact) modifier.fillMaxSize() else modifier.fillMaxWidth().aspectRatio(ratio)
    Canvas(sized.clipToBounds()) {
        val pad = (if (compact) 10.dp else 26.dp).toPx()
        val s = min((size.width - 2 * pad) / layout.w, (size.height - 2 * pad) / layout.h)
        val ox = (size.width - layout.w * s) / 2 - layout.minX * s
        val oy = (size.height - layout.h * s) / 2 - layout.minY * s
        fun sc(o: Offset) = Offset(o.x * s + ox, o.y * s + oy)
        fun path(idx: IntArray, close: Boolean = false) = Path().apply {
            idx.forEachIndexed { k, i ->
                val o = sc(layout.pts[i])
                if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
            if (close) close()
        }
        val width = if (compact) 5.dp.toPx() else 10.dp.toPx()
        if (!compact) layout.zones.forEach { z ->
            drawPath(path(z), ZONE, style = Stroke(width = 18.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.dp.toPx()))))
        }
        val all = path(IntArray(layout.pts.size) { it }, close = true)
        drawPath(all, if (p.dark) Color(0xFF3A3A48) else Color(0xFFC9C9D3), style = Stroke(width = width + 3.dp.toPx(), join = StrokeJoin.Round))
        drawPath(all, if (p.dark) Color(0xFF0B0B10) else Color(0xFF1F1F28), style = Stroke(width = width, join = StrokeJoin.Round))
        layout.sectors.forEachIndexed { k, idx ->
            drawPath(path(idx), SECTOR_COLORS[k], style = Stroke(width = if (compact) 1.8.dp.toPx() else 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // start / finish
        if (layout.pts.size > 4) {
            val a = sc(layout.pts[layout.pts.size - 3])
            val b = sc(layout.pts[3])
            val dx = b.x - a.x
            val dy = b.y - a.y
            val l = hypot(dx, dy).takeIf { it > 0 } ?: 1f
            val nx = -dy / l
            val ny = dx / l
            val c = sc(layout.pts[0])
            val half = width * 0.7f
            drawLine(Color.White, Offset(c.x - nx * half, c.y - ny * half), Offset(c.x + nx * half, c.y + ny * half), strokeWidth = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
        }
        if (!compact) {
            layout.corners.forEach { (pos, dir, label) ->
                val c = sc(pos)
                val at = Offset(c.x + dir.x * 17.dp.toPx(), c.y + dir.y * 17.dp.toPx())
                drawCircle(p.card, radius = 8.dp.toPx(), center = at)
                drawCircle(p.muted, radius = 8.dp.toPx(), center = at, style = Stroke(1.dp.toPx()))
                val t = tm.measure(label, TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = p.text))
                drawText(t, topLeft = Offset(at.x - t.size.width / 2f, at.y - t.size.height / 2f))
            }
            layout.detection?.let { i -> drawCircle(OVERTAKE, radius = 4.5.dp.toPx(), center = sc(layout.pts[i])) }
            layout.activation?.let { i ->
                val c = sc(layout.pts[i])
                drawCircle(OVERTAKE, radius = 4.5.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
            }
        }
        cars.forEach { car ->
            val c = sc(layout.rot(car.x, car.y))
            drawCircle(Color(0xFF15151E), radius = 6.dp.toPx(), center = c)
            drawCircle(car.color, radius = 4.5.dp.toPx(), center = c)
            val t = tm.measure(car.label, TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = p.text))
            drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - 7.dp.toPx() - t.size.height))
        }
    }
}

@Composable
fun TrackLegend(hasZones: Boolean, exact: Boolean) {
    val p = pal()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SECTOR_COLORS.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c))
                    Text(stringResource(R.string.sector_n, i + 1), fontSize = 11.sp, color = p.muted)
                }
            }
        }
        if (hasZones) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(16.dp).height(6.dp).background(ZONE.copy(alpha = 0.8f)))
                Text(stringResource(R.string.straight_mode), fontSize = 11.sp, color = p.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(OVERTAKE))
                Text(stringResource(R.string.overtake_detection), fontSize = 11.sp, color = p.muted)
            }
        }
        if (hasZones) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).border(2.dp, OVERTAKE, CircleShape))
                Text(stringResource(R.string.overtake_activation), fontSize = 11.sp, color = p.muted)
            }
        }
        if (!exact) Muted(stringResource(R.string.sectors_approx), 10.sp)
    }
}
