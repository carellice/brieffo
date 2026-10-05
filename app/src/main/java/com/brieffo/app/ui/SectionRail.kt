package com.brieffo.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brieffo.app.data.CardKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Una scheda del brief vista dall'indice laterale: [top] è dove comincia nella pagina, in pixel. */
class Section(val key: String, val label: String, val icon: ImageVector, val top: Int)

/** Chiave del riepilogo, che non è tra le schede di CardKeys perché non si può nascondere. */
const val SUMMARY_SECTION = "summary"

fun sectionLabel(key: String) = if (key == SUMMARY_SECTION) "Riepilogo" else CardKeys.labels[key]?.substringBefore(" (") ?: key

fun sectionIcon(key: String): ImageVector = when (key) {
    CardKeys.WEATHER -> Icons.Rounded.Place
    CardKeys.AGENDA -> Icons.Rounded.CalendarMonth
    CardKeys.TRAVEL -> Icons.Rounded.Commute
    CardKeys.HEALTH -> Icons.Rounded.SelfImprovement
    CardKeys.USAGE -> Icons.Rounded.Smartphone
    CardKeys.NEWS -> Icons.Rounded.Newspaper
    CardKeys.SPORT -> Icons.Rounded.SportsSoccer
    CardKeys.MARKETS -> Icons.Rounded.QueryStats
    CardKeys.OCCASIONS -> Icons.Rounded.Celebration
    CardKeys.EXTRAS -> Icons.Rounded.TipsAndUpdates
    else -> Icons.Rounded.AutoAwesome
}

private val Slot = 30.dp

/**
 * Indice delle schede sul bordo sinistro. A riposo è una fila di puntini nel margine, con quello della scheda
 * che si sta leggendo più lungo e colorato. Appoggiando il dito si apre in una colonna di icone con il nome
 * della scheda accanto: un tocco porta lì, e scorrendo il dito su e giù si sfoglia tutto il brief.
 */
@Composable
fun SectionRail(sections: List<Section>, scroll: ScrollState, modifier: Modifier = Modifier) {
    if (sections.size < 2) return
    val p = LocalPalette.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(sections)

    // La scheda "attuale" è l'ultima cominciata entro il primo terzo dello schermo; in fondo alla pagina vale l'ultima.
    val reading by remember {
        derivedStateOf {
            val line = scroll.value + scroll.viewportSize / 3
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue - 4) current.lastIndex
            else current.indexOfLast { it.top <= line }.coerceAtLeast(0)
        }
    }
    var open by remember { mutableStateOf(false) }
    var picked by remember { mutableIntStateOf(0) }
    val active = (if (open) picked else reading).coerceIn(0, sections.lastIndex)

    val width by animateDpAsState(if (open) 46.dp else 18.dp, spring(dampingRatio = 0.8f, stiffness = 500f), label = "width")
    val reveal by animateFloatAsState(if (open) 1f else 0f, spring(stiffness = 500f), label = "reveal")
    val bubbleY by animateDpAsState(Slot * active, spring(dampingRatio = 0.85f, stiffness = 700f), label = "bubble")

    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(width)
                .height(Slot * sections.size)
                .graphicsLayer { shadowElevation = 18f * reveal; shape = RoundedCornerShape(23.dp); clip = false }
                .background(p.bottom.copy(alpha = 0.94f * reveal), RoundedCornerShape(23.dp))
                .border(1.dp, p.stroke.copy(alpha = p.stroke.alpha * reveal), RoundedCornerShape(23.dp))
                .semantics { contentDescription = "Indice delle schede" }
                .pointerInput(Unit) {
                    val slot = Slot.toPx()
                    var job: Job? = null
                    fun go(y: Float) {
                        val i = (y / slot).toInt().coerceIn(0, current.lastIndex)
                        if (i == picked && job != null) return
                        if (i != picked) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        picked = i
                        // La scheda si ferma poco sotto il bordo, così si vede che comincia lì.
                        val target = (current[i].top - with(density) { 12.dp.roundToPx() }).coerceIn(0, scroll.maxValue)
                        job?.cancel()
                        job = scope.launch { scroll.animateScrollTo(target, spring(dampingRatio = 0.9f, stiffness = 260f)) }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        job = null
                        picked = reading
                        open = true
                        go(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            go(change.position.y)
                        }
                        open = false
                    }
                },
        ) {
            Column {
                sections.forEachIndexed { i, section ->
                    val on = i == active
                    val dotHeight by animateDpAsState(if (on) 16.dp else 5.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "dot")
                    Box(Modifier.width(width).height(Slot), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .graphicsLayer { alpha = 1f - reveal }
                                .size(5.dp, dotHeight)
                                .background(if (on) p.accent else p.sub.copy(alpha = 0.45f), CircleShape)
                        )
                        Box(
                            Modifier
                                .graphicsLayer { alpha = reveal; scaleX = 0.6f + 0.4f * reveal; scaleY = 0.6f + 0.4f * reveal }
                                .size(26.dp)
                                .background(if (on) p.accent else p.accent.copy(alpha = 0f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(section.icon, null, Modifier.size(16.dp), tint = if (on) p.onAccent else p.sub) }
                    }
                }
            }
        }
        // Il nome della scheda scelta segue il dito accanto all'indice.
        Box(
            Modifier
                .padding(start = 8.dp, top = 6.dp)
                .offset(y = bubbleY)
                .height(Slot)
                .graphicsLayer { alpha = reveal; translationX = (reveal - 1f) * 24f },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.background(p.accent, RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(sections[active].label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = p.onAccent, maxLines = 1, softWrap = false)
            }
        }
    }
}
