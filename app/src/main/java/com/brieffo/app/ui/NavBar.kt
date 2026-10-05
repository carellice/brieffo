package com.brieffo.app.ui

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spazio da lasciare in fondo alle pagine perché l'ultimo contenuto non resti sotto la barra. */
val NavBarSpace = 104.dp

/** Registra ciò che disegna in [layer], così la barra può riusarlo sfocato come fondo di vetro. */
fun Modifier.captureTo(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Barra di navigazione fluttuante in stile vetro: sfoca davvero il contenuto che le scorre sotto
 * (da Android 12; prima resta un velo traslucido) e sposta la pillola di selezione con una molla.
 */
@Composable
fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit, backdrop: GraphicsLayer, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val items = listOf(Icons.Rounded.AutoAwesome to "Brief", Icons.Rounded.Tune to "Impostazioni")
    val itemWidth = 136.dp
    val itemHeight = 52.dp
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }

    val pillX by animateDpAsState(itemWidth * selected, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow), label = "pill")

    Box(
        modifier
            .shadow(22.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(CircleShape)
            .drawBehind {
                if (Build.VERSION.SDK_INT >= 31) {
                    blurred.renderEffect = BlurEffect(48f, 48f, TileMode.Clamp)
                    blurred.record { translate(-origin.x, -origin.y) { drawLayer(backdrop) } }
                    drawLayer(blurred)
                }
                // Velo e riflesso: più chiaro in alto, come una lastra illuminata da sopra.
                val veil = if (p.dark) Color(0xFF1B1D33) else Color.White
                drawRect(veil.copy(alpha = if (Build.VERSION.SDK_INT >= 31) 0.42f else 0.88f))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.14f else 0.45f), Color.Transparent), endY = size.height * 0.6f))
            }
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.34f else 0.95f), Color.White.copy(alpha = 0.10f))), CircleShape)
            .padding(6.dp),
    ) {
        // Pillola di selezione: scivola sotto le voci e si assesta con un rimbalzo appena accennato.
        Box(
            Modifier
                .offset(x = pillX)
                .size(itemWidth, itemHeight)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.30f), p.accent.copy(alpha = 0.18f))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (p.dark) 0.30f else 0.85f), Color.Transparent)), CircleShape),
        )
        Row {
            items.forEachIndexed { i, (icon, label) ->
                val active = i == selected
                val scale by animateFloatAsState(if (active) 1.03f else 1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium), label = "scale")
                val tint = if (active) p.accent else p.sub
                Row(
                    Modifier
                        .size(itemWidth, itemHeight)
                        .clip(CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) }
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(icon, null, Modifier.size(20.dp), tint = tint)
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = if (active) p.text else p.sub)
                }
            }
        }
    }
}
