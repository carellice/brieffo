package com.brieffo.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brieffo.app.data.Daypart
import kotlinx.coroutines.delay

data class Palette(
    val top: Color,
    val bottom: Color,
    val blobs: List<Color>,
    val text: Color,
    val sub: Color,
    val card: Color,
    val stroke: Color,
    val accent: Color,
    val dark: Boolean,
) {
    /** Colore di testo e icone sopra un fondo nel colore primario. */
    val onAccent get() = onColor(accent)
}

/** Colori primari proposti nelle impostazioni. */
val AccentChoices = listOf(
    "Rosso" to Color(0xFFE5484D), "Arancione" to Color(0xFFF2643D), "Ambra" to Color(0xFFE08A00), "Giallo" to Color(0xFFE2B100),
    "Lime" to Color(0xFF7CB518), "Verde" to Color(0xFF1F9D61), "Menta" to Color(0xFF12B5A0), "Turchese" to Color(0xFF0E9AA7),
    "Azzurro" to Color(0xFF1E9BF0), "Blu" to Color(0xFF2F6BF0), "Indaco" to Color(0xFF5B5BD6), "Viola" to Color(0xFF8B5CF6),
    "Magenta" to Color(0xFFC13FD0), "Rosa" to Color(0xFFE0457B), "Grigio" to Color(0xFF6B7385),
)

/** Colore neutro: bianco con il tema scuro, nero con quello chiaro. Scelto in un tema, si adatta se si passa all'altro. */
fun monoAccent(dark: Boolean) = if (dark) Color.White else Color(0xFF14151F)

fun isMonoAccent(c: Color) = c == Color.White || c == Color(0xFF14151F)

/** Colore leggibile sopra [c]: scuro sui fondi chiari, bianco su quelli scuri. */
fun onColor(c: Color) = if (c.luminance() > 0.55f) Color(0xFF14151F) else Color.White

/**
 * Tavolozza del brief: le tinte seguono il momento della giornata, la luminosità segue il tema scelto.
 * Con [accent] l'utente fissa il colore primario; altrimenti cambia con l'ora.
 */
fun paletteFor(d: Daypart, dark: Boolean, chosen: Color? = null): Palette {
    val accent = chosen?.let { if (isMonoAccent(it)) monoAccent(dark) else it }
    val blobs = when (d) {
        Daypart.MORNING -> listOf(Color(0xFFFFB07C), Color(0xFFFF8FB1), Color(0xFF9CC4FF))
        Daypart.MIDDAY -> listOf(Color(0xFF7DBBFF), Color(0xFFA9F0E0), Color(0xFFC9B8FF))
        Daypart.EVENING -> listOf(Color(0xFFFF7A59), Color(0xFFB84DD6), Color(0xFF3D5BFF))
        Daypart.NIGHT -> listOf(Color(0xFF3848C9), Color(0xFF6A3FB8), Color(0xFF14607A))
    }
    val (top, bottom) = if (dark) when (d) {
        Daypart.MORNING -> Color(0xFF3B2233) to Color(0xFF141226)
        Daypart.MIDDAY -> Color(0xFF10294A) to Color(0xFF0A1020)
        Daypart.EVENING -> Color(0xFF3A1F5C) to Color(0xFF12122E)
        Daypart.NIGHT -> Color(0xFF0E1433) to Color(0xFF05060F)
    } else when (d) {
        Daypart.MORNING -> Color(0xFFFFE3CF) to Color(0xFFDCE9FF)
        Daypart.MIDDAY -> Color(0xFFCDE6FF) to Color(0xFFF1F4FF)
        Daypart.EVENING -> Color(0xFFFFE0D4) to Color(0xFFE9DFFF)
        Daypart.NIGHT -> Color(0xFFDDE3FF) to Color(0xFFF3F1FF)
    }
    val auto = if (dark) when (d) {
        Daypart.MORNING, Daypart.EVENING -> Color(0xFFFFA06B)
        Daypart.MIDDAY -> Color(0xFF6FA8FF)
        Daypart.NIGHT -> Color(0xFF8FA4FF)
    } else when (d) {
        Daypart.MORNING, Daypart.EVENING -> Color(0xFFF2643D)
        Daypart.MIDDAY -> Color(0xFF2F6BF0)
        Daypart.NIGHT -> Color(0xFF5B6BE8)
    }
    return if (dark) Palette(
        top, bottom, blobs,
        text = Color(0xFFF4F2FF), sub = Color(0xFFB9B4D4),
        card = Color.White.copy(alpha = 0.10f), stroke = Color.White.copy(alpha = 0.14f),
        accent = accent ?: auto, dark = true,
    ) else Palette(
        top, bottom, blobs,
        text = Color(0xFF1A1C30), sub = Color(0xFF5A5F78),
        card = Color.White.copy(alpha = 0.62f), stroke = Color.White.copy(alpha = 0.82f),
        accent = accent ?: auto, dark = false,
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(Daypart.MORNING, dark = false) }

/** Sfondo "aurora": gradiente verticale con tre aloni colorati che si muovono lentamente. */
@Composable
fun AuroraBackground(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.top, p.bottom)))) {
        Canvas(Modifier.fillMaxSize()) {
            val alpha = if (p.dark) 0.45f else 0.55f
            val spots = listOf(
                Offset(size.width * (0.05f + 0.25f * t), size.height * 0.08f),
                Offset(size.width * (1.0f - 0.2f * t), size.height * (0.25f + 0.08f * t)),
                Offset(size.width * (0.2f + 0.3f * t), size.height * (0.78f - 0.1f * t)),
            )
            spots.forEachIndexed { i, c ->
                val r = size.width * 0.75f
                drawCircle(Brush.radialGradient(listOf(p.blobs[i].copy(alpha = alpha), Color.Transparent), c, r), r, c)
            }
        }
        CompositionLocalProvider(LocalContentColor provides p.text, content = content)
    }
}

/** Entrata scaglionata: ogni card sale e compare con un piccolo ritardo in base all'indice. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        a.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 60f }) { content() }
}

@Composable
fun BriefCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = p.card,
        contentColor = p.text,
        border = BorderStroke(1.dp, p.stroke),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(32.dp).background(p.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(18.dp), tint = p.onAccent) }
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.sub, modifier = Modifier.weight(1f))
                if (trailing != null) Text(trailing, fontSize = 12.sp, color = p.sub)
            }
            Spacer(Modifier.size(14.dp))
            content()
        }
    }
}

/**
 * Sfuma i bordi di una riga che scorre in orizzontale: gli elementi svaniscono invece di essere tagliati di netto.
 * La sfumatura compare solo sul lato da cui c'è ancora qualcosa da scorrere.
 */
fun Modifier.horizontalFade(state: LazyListState, width: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx()
        if (state.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, w), size = Size(w, size.height), blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), size.width - w, size.width),
                topLeft = Offset(size.width - w, 0f), size = Size(w, size.height), blendMode = BlendMode.DstIn,
            )
        }
    }

/** Come [horizontalFade], ma per le pagine che scorrono in verticale: il contenuto svanisce in alto e in basso. */
fun Modifier.verticalFade(state: ScrollState, height: Dp = 36.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        // La sfumatura cresce con i primi pixel di scorrimento, così non compare di colpo.
        val top = (state.value / h).coerceIn(0f, 1f)
        val bottom = ((state.maxValue - state.value) / h).coerceIn(0f, 1f)
        if (top > 0f) {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 1f - top), Color.Black), 0f, h), size = Size(size.width, h), blendMode = BlendMode.DstIn)
        }
        if (bottom > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 1f - bottom)), size.height - h, size.height),
                topLeft = Offset(0f, size.height - h), size = Size(size.width, h), blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val p = LocalPalette.current
    Row(
        modifier.background(p.text.copy(alpha = 0.07f), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = p.accent)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.text)
    }
}

@Composable
fun Stat(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = p.accent)
        Column {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(label, fontSize = 12.sp, color = p.sub)
        }
    }
}
