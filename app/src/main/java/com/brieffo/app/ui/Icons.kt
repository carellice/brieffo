package com.brieffo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
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

// ---- Meteo animato ---------------------------------------------------------------------------------------------

/** Secondi che scorrono un fotogramma alla volta: si leggono solo mentre si disegna, così non si ricompone nulla. */
@Composable
fun rememberWeatherClock(): FloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    return clock
}

private fun frac(x: Float) = x - floor(x)

/** Numero "a caso" tra 0 e 1, sempre lo stesso per lo stesso [i]: serve a sparpagliare gocce e fiocchi. */
private fun scatter(i: Int, salt: Float) = frac(sin(i * 12.9898f + salt * 78.233f) * 43758.547f)

private fun DrawScope.livingSun(cx: Float, cy: Float, r: Float, t: Float) {
    val s = size.minDimension
    val c = Offset(cx * s, cy * s)
    val breath = sin(t * 1.6f)
    // Alone che respira, raggi che girano piano e si allungano a turno.
    drawCircle(SunColor.copy(alpha = 0.16f + 0.07f * breath), r * (1.75f + 0.14f * breath) * s, c)
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4 + t * 0.35f
        val reach = 1.95f + 0.16f * sin(t * 2.2f + i * 1.3f)
        drawLine(
            SunColor,
            Offset(c.x + cos(a) * r * 1.45f * s, c.y + sin(a) * r * 1.45f * s),
            Offset(c.x + cos(a) * r * reach * s, c.y + sin(a) * r * reach * s),
            strokeWidth = s * 0.06f, cap = StrokeCap.Round,
        )
    }
    drawCircle(SunColor, r * s, c)
    // Ogni tanto un riflesso attraversa il disco.
    val glint = (t % 4.5f) / 0.9f
    if (glint < 1f) {
        val a = sin(PI.toFloat() * glint)
        drawCircle(Color.White.copy(alpha = 0.75f * a), r * 0.26f * s, Offset(c.x + (glint - 0.5f) * r * 1.1f * s, c.y + (glint - 0.6f) * r * 0.7f * s))
    }
}

private fun DrawScope.livingMoon(cx: Float, cy: Float, r: Float, color: Color, t: Float) {
    val s = size.minDimension
    crescent(cx, cy, r, color)
    // Tre stelline che brillano a turno accanto alla luna.
    listOf(Offset(0.42f, -0.34f), Offset(0.62f, 0.08f), Offset(0.30f, 0.40f)).forEachIndexed { i, o ->
        val a = 0.25f + 0.75f * abs(sin(t * 1.3f + i * 2.1f))
        drawCircle(color.copy(alpha = a), s * 0.022f * (0.7f + 0.5f * a), Offset((cx + o.x * r * 2.2f) * s, (cy + o.y * r * 2.2f) * s))
    }
}

private fun DrawScope.fallingRain(t: Float, count: Int, long: Boolean) {
    val s = size.minDimension
    val len = if (long) 0.15f else 0.08f
    val speed = if (long) 1.5f else 0.9f
    for (i in 0 until count) {
        val x = 0.30f + 0.40f * (i + 0.5f) / count + (scatter(i, 1f) - 0.5f) * 0.06f
        val fall = frac(t * speed * (0.85f + 0.3f * scatter(i, 2f)) + scatter(i, 3f))
        val y = 0.62f + fall * 0.34f
        drawLine(
            RainColor.copy(alpha = 1f - fall * fall * fall),
            Offset(x * s, y * s), Offset((x - len * 0.4f) * s, (y + len) * s),
            strokeWidth = s * 0.055f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.fallingSnow(t: Float) {
    val s = size.minDimension
    for (i in 0 until 5) {
        val fall = frac(t * 0.32f * (0.8f + 0.4f * scatter(i, 2f)) + i * 0.21f)
        val x = 0.28f + 0.44f * (i + 0.5f) / 5 + sin(t * 1.4f + i * 1.9f) * 0.035f
        drawCircle(SnowColor.copy(alpha = sin(PI.toFloat() * fall).coerceIn(0f, 1f)), s * 0.042f, Offset(x * s, (0.62f + fall * 0.36f) * s))
    }
}

/** Quanto è acceso il lampo in questo istante: due colpi ravvicinati, poi buio fino al prossimo. */
private fun flash(t: Float): Float {
    val c = t % 3.4f
    return when {
        c < 0.10f -> 1f
        c < 0.18f -> 0.15f
        c < 0.30f -> 1f
        c < 0.85f -> 1f - (c - 0.30f) / 0.55f
        else -> 0f
    }
}

/**
 * Icona meteo viva per il tempo di adesso: il sole gira e ogni tanto manda un riflesso, le nuvole vanno e vengono,
 * pioggia e neve cadono, i temporali lampeggiano. I disegni sono gli stessi di [WeatherIcon], messi in movimento.
 */
@Composable
fun AnimatedWeatherIcon(code: Int, isDay: Boolean, clock: FloatState, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val cloudColor = if (p.dark) Color(0xFFD5DBEA) else Color(0xFF93A1B8)
    val moonColor = if (p.dark) Color(0xFFC9D2FF) else Color(0xFF6F7FD0)
    Canvas(modifier.size(size)) {
        val t = clock.floatValue
        val s = this.size.minDimension
        val drift = sin(t * 0.7f) * 0.03f
        fun sky(cx: Float, cy: Float, r: Float) = if (isDay) livingSun(cx, cy, r, t) else livingMoon(cx, cy, r * 1.5f, moonColor, t)
        when (code) {
            0 -> sky(0.5f, 0.5f, 0.2f)
            1, 2 -> {
                sky(0.36f, 0.36f, 0.15f)
                cloud(0.56f + drift * 1.6f, 0.62f, if (code == 1) 0.62f else 0.74f, cloudColor)
            }
            45, 48 -> listOf(0.34f to 0.5f, 0.50f to 0.66f, 0.66f to 0.42f).forEachIndexed { i, (y, w) ->
                val x = 0.5f + sin(t * 0.6f + i * 1.9f) * 0.07f
                drawLine(cloudColor.copy(alpha = 0.7f + 0.3f * sin(t * 0.9f + i)), Offset((x - w / 2) * s, y * s), Offset((x + w / 2) * s, y * s), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            }
            in 51..57 -> { fallingRain(t, 3, long = false); cloud(0.5f + drift, 0.42f, 0.82f, cloudColor) }
            in 61..67, in 80..82 -> { fallingRain(t, 4, long = true); cloud(0.5f + drift, 0.42f, 0.82f, cloudColor) }
            in 71..77, 85, 86 -> { fallingSnow(t); cloud(0.5f + drift, 0.42f, 0.82f, cloudColor) }
            in 95..99 -> {
                val f = flash(t)
                fallingRain(t, 3, long = true)
                cloud(0.5f + drift, 0.40f, 0.82f, cloudColor)
                // Il lampo illumina anche la nuvola.
                if (f > 0f) cloud(0.5f + drift, 0.40f, 0.82f, Color.White.copy(alpha = 0.45f * f))
                val bolt = Path().apply {
                    moveTo(0.54f * s, 0.56f * s); lineTo(0.38f * s, 0.78f * s); lineTo(0.49f * s, 0.78f * s)
                    lineTo(0.44f * s, 0.96f * s); lineTo(0.63f * s, 0.70f * s); lineTo(0.52f * s, 0.70f * s); close()
                }
                drawPath(bolt, BoltColor.copy(alpha = 0.25f + 0.75f * f))
            }
            else -> cloud(0.5f + drift * 1.5f, 0.55f, 0.9f, cloudColor)
        }
    }
}

/**
 * Atmosfera sopra tutta la scheda del meteo: pioggia o neve che la attraversano, il bagliore del sole in un angolo,
 * stelle che brillano di notte, il lampo dei temporali. È leggera apposta, per non coprire i numeri.
 */
fun Modifier.weatherAtmosphere(code: Int, isDay: Boolean, clock: FloatState, tint: Color): Modifier = this
    .clip(RoundedCornerShape(28.dp))
    .drawWithContent {
        drawContent()
        val t = clock.floatValue
        val w = size.width
        val h = size.height
        // Gocce e fiocchi in proporzione all'altezza: la scheda del meteo è lunga e resterebbero radi.
        val density = (h / 420.dp.toPx()).coerceIn(1f, 4f)
        fun rain(base: Int, speed: Float, length: Float, alpha: Float) {
            val count = (base * density).toInt()
            for (i in 0 until count) {
                val fall = frac(t * speed / density * (0.7f + 0.6f * scatter(i, 4f)) + scatter(i, 5f))
                val x = (scatter(i, 6f) * 1.15f - 0.05f) * w - fall * h * 0.05f
                val y = fall * (h + length) - length
                drawLine(RainColor.copy(alpha = alpha), Offset(x, y), Offset(x - length * 0.12f, y + length), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        when (code) {
            0, 1 -> if (isDay) {
                // Bagliore che respira dall'angolo in alto a destra, come il sole appena fuori dalla scheda.
                val glow = 0.20f + 0.07f * sin(t * 1.1f)
                drawCircle(
                    Brush.radialGradient(listOf(SunColor.copy(alpha = glow), Color.Transparent), Offset(w, 0f), w * 0.75f),
                    w * 0.75f, Offset(w, 0f),
                )
            } else {
                for (i in 0 until 14) {
                    val a = (0.15f + 0.65f * abs(sin(t * (0.5f + scatter(i, 1f)) + i * 2.3f))) * 0.7f
                    drawCircle(tint.copy(alpha = a), (0.8f + 1.2f * scatter(i, 2f)).dp.toPx(), Offset(scatter(i, 3f) * w, scatter(i, 4f) * h * 0.55f))
                }
            }
            2, 3 -> for (i in 0 until 3) {
                // Banchi di nuvole appena accennati, dai bordi sfumati, che attraversano la parte alta della scheda.
                val cw = w * 0.75f
                val ch = 110.dp.toPx()
                val x = frac(t * 0.012f * (1f + i * 0.4f) + i * 0.37f) * (w + cw) - cw
                val center = Offset(x + cw / 2, (40 + 85 * i).dp.toPx())
                withTransform({ scale(1f, ch / cw, center) }) {
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.10f), Color.Transparent), center, cw / 2), cw / 2, center)
                }
            }
            45, 48 -> for (i in 0 until 4) {
                val x = sin(t * 0.25f + i * 1.7f) * w * 0.12f
                drawRoundRect(tint.copy(alpha = 0.05f), Offset(x - w * 0.1f, h * (0.12f + 0.22f * i)), Size(w * 1.2f, h * 0.07f), CornerRadius(h * 0.035f))
            }
            in 51..57 -> rain(18, 0.45f, 12.dp.toPx(), 0.22f)
            in 61..67, in 80..82 -> rain(34, 0.8f, 20.dp.toPx(), 0.26f)
            in 71..77, 85, 86 -> for (i in 0 until (30 * density).toInt()) {
                val fall = frac(t * 0.09f / density * (0.6f + 0.8f * scatter(i, 4f)) + scatter(i, 5f))
                val x = scatter(i, 6f) * w + sin(t * 0.9f + i * 1.7f) * 10.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.55f), (1.5f + 2f * scatter(i, 7f)).dp.toPx(), Offset(x, fall * (h + 20f) - 10f))
            }
            in 95..99 -> {
                rain(38, 0.95f, 22.dp.toPx(), 0.28f)
                val f = flash(t)
                if (f > 0f) drawRect(Color.White.copy(alpha = 0.13f * f))
            }
        }
    }
