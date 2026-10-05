package com.brieffo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFF5A623)
private val RainColor = Color(0xFF3B8BEB)
private val SnowColor = Color(0xFF7FB5F5)
private val BoltColor = Color(0xFFF2B01E)

/** Icona tonda colorata, lo stesso stile dell'intestazione delle schede. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp, tint: Color = LocalPalette.current.accent) {
    Box(modifier.size(size).background(tint, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.54f), tint = onColor(tint))
    }
}

// Le icone meteo sono disegnate a mano su una griglia 0..1, così restano nitide a ogni dimensione.

private fun DrawScope.sun(cx: Float, cy: Float, r: Float) {
    val s = size.minDimension
    drawCircle(SunColor, r * s, Offset(cx * s, cy * s))
    for (i in 0 until 8) {
        val a = i * PI / 4
        val from = Offset((cx + cos(a).toFloat() * r * 1.45f) * s, (cy + sin(a).toFloat() * r * 1.45f) * s)
        val to = Offset((cx + cos(a).toFloat() * r * 1.95f) * s, (cy + sin(a).toFloat() * r * 1.95f) * s)
        drawLine(SunColor, from, to, strokeWidth = s * 0.06f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.crescent(cx: Float, cy: Float, r: Float, color: Color) {
    val s = size.minDimension
    val full = Path().apply { addOval(Rect(Offset(cx * s, cy * s), r * s)) }
    val bite = Path().apply { addOval(Rect(Offset((cx + r * 0.45f) * s, (cy - r * 0.3f) * s), r * 0.85f * s)) }
    drawPath(Path.combine(PathOperation.Difference, full, bite), color)
}

/** Nuvola larga [w] (in frazione dell'icona) centrata in ([cx], [cy]). */
private fun DrawScope.cloud(cx: Float, cy: Float, w: Float, color: Color) {
    val s = size.minDimension
    drawCircle(color, 0.19f * w * s, Offset((cx - 0.24f * w) * s, (cy + 0.04f * w) * s))
    drawCircle(color, 0.27f * w * s, Offset((cx - 0.01f * w) * s, (cy - 0.10f * w) * s))
    drawCircle(color, 0.20f * w * s, Offset((cx + 0.25f * w) * s, (cy + 0.03f * w) * s))
    drawRoundRect(
        color, Offset((cx - 0.43f * w) * s, (cy + 0.0f * w) * s), Size(0.88f * w * s, 0.23f * w * s),
        CornerRadius(0.115f * w * s),
    )
}

private fun DrawScope.drops(count: Int, long: Boolean) {
    val s = size.minDimension
    val xs = if (count == 2) listOf(0.40f, 0.60f) else listOf(0.32f, 0.50f, 0.68f)
    val len = if (long) 0.17f else 0.09f
    xs.forEach { x ->
        drawLine(RainColor, Offset(x * s, 0.70f * s), Offset((x - len * 0.4f) * s, (0.70f + len) * s), strokeWidth = s * 0.065f, cap = StrokeCap.Round)
    }
}

/** Icona meteo per un codice WMO; di notte il sole diventa una falce di luna. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean = true, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val cloudColor = if (p.dark) Color(0xFFD5DBEA) else Color(0xFF93A1B8)
    val moonColor = if (p.dark) Color(0xFFC9D2FF) else Color(0xFF6F7FD0)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        fun sky(cx: Float, cy: Float, r: Float) = if (isDay) sun(cx, cy, r) else crescent(cx, cy, r * 1.5f, moonColor)
        when (code) {
            0 -> sky(0.5f, 0.5f, 0.2f)
            1, 2 -> {
                sky(0.36f, 0.36f, 0.15f)
                cloud(0.56f, 0.62f, if (code == 1) 0.62f else 0.74f, cloudColor)
            }
            3 -> cloud(0.5f, 0.55f, 0.9f, cloudColor)
            45, 48 -> listOf(0.34f to 0.5f, 0.50f to 0.66f, 0.66f to 0.42f).forEach { (y, w) ->
                drawLine(cloudColor, Offset((0.5f - w / 2) * s, y * s), Offset((0.5f + w / 2) * s, y * s), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            }
            in 51..57 -> { cloud(0.5f, 0.42f, 0.82f, cloudColor); drops(2, long = false) }
            in 61..67, in 80..82 -> { cloud(0.5f, 0.42f, 0.82f, cloudColor); drops(3, long = true) }
            in 71..77, 85, 86 -> {
                cloud(0.5f, 0.42f, 0.82f, cloudColor)
                listOf(0.32f, 0.50f, 0.68f).forEachIndexed { i, x -> drawCircle(SnowColor, s * 0.045f, Offset(x * s, (if (i == 1) 0.84f else 0.74f) * s)) }
            }
            in 95..99 -> {
                cloud(0.5f, 0.40f, 0.82f, cloudColor)
                val bolt = Path().apply {
                    moveTo(0.54f * s, 0.56f * s); lineTo(0.38f * s, 0.78f * s); lineTo(0.49f * s, 0.78f * s)
                    lineTo(0.44f * s, 0.96f * s); lineTo(0.63f * s, 0.70f * s); lineTo(0.52f * s, 0.70f * s); close()
                }
                drawPath(bolt, BoltColor)
            }
            else -> cloud(0.5f, 0.55f, 0.9f, cloudColor)
        }
    }
}

/**
 * Luna con la parte illuminata reale. [phase] va da 0 (nuova) a 0,5 (piena) a 1 (di nuovo nuova):
 * nella prima metà cresce e la luce è a destra, nella seconda cala e la luce è a sinistra.
 */
@Composable
fun MoonIcon(phase: Double, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val dark = p.text.copy(alpha = 0.16f)
    val lit = if (p.dark) Color(0xFFF2F0E6) else Color(0xFF6F7FD0)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(dark, r, c)
        val waxing = phase < 0.5
        val k = cos(2 * PI * phase).toFloat() // +1 nuova, -1 piena
        val rx = r * abs(k)
        val limb = Rect(c, r)
        val terminator = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
        val path = Path().apply {
            // Bordo esterno dal polo nord al polo sud sul lato illuminato, poi ritorno lungo il terminatore.
            arcTo(limb, -90f, if (waxing) 180f else -180f, forceMoveTo = true)
            val crescent = k > 0
            val sweep = if (waxing == crescent) -180f else 180f
            if (rx > 0.5f) arcTo(terminator, 90f, sweep, forceMoveTo = false) else lineTo(c.x, c.y - r)
            close()
        }
        drawPath(path, lit)
    }
}
