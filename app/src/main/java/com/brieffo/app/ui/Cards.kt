package com.brieffo.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brieffo.app.data.Battery
import com.brieffo.app.data.BriefState
import com.brieffo.app.data.Daypart
import com.brieffo.app.data.Event
import com.brieffo.app.data.HealthState
import com.brieffo.app.data.Market
import com.brieffo.app.data.Moon
import com.brieffo.app.data.NewsItem
import com.brieffo.app.data.NewsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.brieffo.app.data.OnThisDay
import com.brieffo.app.data.UsageState
import com.brieffo.app.data.Weather
import com.brieffo.app.data.WxAlert
import com.brieffo.app.data.aqiText
import com.brieffo.app.data.durationText
import com.brieffo.app.data.wxText
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val hm = DateTimeFormatter.ofPattern("HH:mm")

private fun Double.deg() = "${roundToInt()}°"

@Composable
internal fun Hint(text: String) {
    Text(text, fontSize = 14.sp, color = LocalPalette.current.sub, lineHeight = 20.sp)
}

@Composable
internal fun ActionButton(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
        modifier = Modifier.padding(top = 12.dp),
    ) { Text(text) }
}

/** Righe segnaposto che pulsano al posto del testo, alte quanto le righe del riepilogo. */
@Composable
private fun SummarySkeleton() {
    val p = LocalPalette.current
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.10f, targetValue = 0.24f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "alpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.padding(vertical = 5.dp)) {
        listOf(1f, 0.94f, 0.98f, 0.62f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(16.dp).background(p.text.copy(alpha = pulse), RoundedCornerShape(8.dp)))
        }
    }
}

/** Quante righe del riepilogo di Gemini restano visibili quando è chiuso. */
private const val SUMMARY_PREVIEW = 2

/** Una riga del riepilogo di Gemini: l'emoji con cui comincia diventa l'icona a sinistra. */
@Composable
private fun SummaryLine(line: String) {
    val head = line.substringBefore(' ')
    val hasIcon = head.length in 1..8 && head.length < line.length && !head.first().isLetterOrDigit()
    Row {
        if (hasIcon) Text(head, fontSize = 20.sp, lineHeight = 26.sp, modifier = Modifier.width(36.dp))
        Text(if (hasIcon) line.substringAfter(' ').trim() else line, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SummaryCard(s: BriefState) {
    val p = LocalPalette.current
    BriefCard(
        title = when {
            s.summaryLoading -> "Riepilogo · Gemini sta scrivendo…"
            s.summaryByAi -> "Riepilogo · scritto da Gemini"
            else -> "Riepilogo"
        },
        icon = Icons.Rounded.AutoAwesome,
    ) {
        // Aggiornando un riepilogo già scritto da Gemini resta visibile il testo di prima.
        val skeleton = s.summaryLoading && !s.summaryByAi
        when {
            skeleton -> SummarySkeleton()
            s.summaryByAi -> {
                val lines = s.summary.lines().filter { it.isNotBlank() }
                // Il riepilogo può essere lungo: chiuso mostra solo le prime righe, le più importanti.
                var expanded by rememberSaveable(s.summaryCollapsed) { mutableStateOf(!s.summaryCollapsed) }
                val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { lines.take(SUMMARY_PREVIEW).forEach { SummaryLine(it) } }
                AnimatedVisibility(
                    visible = expanded && lines.size > SUMMARY_PREVIEW,
                    enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                ) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        lines.drop(SUMMARY_PREVIEW).forEach { SummaryLine(it) }
                    }
                }
                if (lines.size > SUMMARY_PREVIEW) Row(
                    Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(turn), tint = p.accent)
                    Text(
                        if (expanded) "Mostra meno" else "Mostra tutto (altre ${lines.size - SUMMARY_PREVIEW} righe)",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.accent, modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            else -> Text(
                text = s.summary.ifEmpty { "Sto preparando il tuo brief…" },
                fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium,
                color = if (s.summary.isEmpty()) p.sub else p.text,
            )
        }
        if (s.aiError != null) {
            Text("Gemini non ha risposto (${s.aiError}): questo è il riepilogo dell'app.", fontSize = 12.sp, color = p.sub, modifier = Modifier.padding(top = 10.dp))
        }
        if (!skeleton && s.summary.isNotEmpty()) {
            val speaker = rememberSpeaker()
            OutlinedButton(onClick = { speaker.toggle(s.summary) }, modifier = Modifier.padding(top = 12.dp)) {
                when (speaker.status) {
                    Speaker.Status.LOADING -> CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
                    Speaker.Status.PLAYING -> Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp), tint = p.accent)
                    Speaker.Status.IDLE -> Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(18.dp), tint = p.accent)
                }
                Text(
                    when (speaker.status) {
                        Speaker.Status.LOADING -> "Preparo la voce…"
                        Speaker.Status.PLAYING -> "Ferma"
                        Speaker.Status.IDLE -> "Ascolta"
                    },
                    color = p.accent, modifier = Modifier.padding(start = 8.dp),
                )
            }
            speaker.note?.let { Text(it, fontSize = 12.sp, color = p.sub, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard(w: Weather?, loaded: Boolean, alerts: List<WxAlert>, showPollen: Boolean, onSetup: () -> Unit) {
    val p = LocalPalette.current
    BriefCard(title = w?.place ?: "Meteo", icon = Icons.Rounded.Place) {
        if (w == null) {
            Hint(if (loaded) "Non riesco a trovare la tua posizione. Consenti l'accesso alla posizione oppure imposta una città." else "Carico il meteo…")
            if (loaded) ActionButton("Imposta", onSetup)
            return@BriefCard
        }
        AlertBanner(alerts)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(w.code, w.isDay, size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Text(w.temp.deg(), fontSize = 64.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(wxText(w.code).replaceFirstChar { it.uppercase() }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Percepiti ${w.feels.deg()}", fontSize = 13.sp, color = p.sub)
                w.today?.let { Text("Max ${it.tMax.deg()} · Min ${it.tMin.deg()}", fontSize = 13.sp, color = p.sub) }
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            w.today?.let { Pill("Pioggia ${it.rainProb}%", icon = Icons.Rounded.Umbrella) }
            Pill("${w.wind.roundToInt()} km/h", icon = Icons.Rounded.Air)
            Pill("Umidità ${w.humidity}%", icon = Icons.Rounded.WaterDrop)
            Pill("UV ${w.uvMax.roundToInt()}", icon = Icons.Rounded.WbSunny)
            w.aqi?.let { Pill("Aria ${aqiText(it)}", icon = Icons.Rounded.Eco) }
            Pill("Alba ${w.sunrise}", icon = Icons.Rounded.WbTwilight)
            Pill("Tramonto ${w.sunset}", icon = Icons.Rounded.NightsStay)
            if (showPollen) {
                if (w.pollen.isEmpty()) Pill("Pollini assenti", icon = Icons.Rounded.LocalFlorist)
                w.pollen.forEach { Pill("${it.name}: ${it.level}", icon = Icons.Rounded.LocalFlorist) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val hours = rememberLazyListState()
        LazyRow(state = hours, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.horizontalFade(hours)) {
            items(w.hourly.take(18)) { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h.time.format(DateTimeFormatter.ofPattern("HH")), fontSize = 12.sp, color = p.sub)
                    WeatherIcon(h.code, h.isDay, size = 28.dp, modifier = Modifier.padding(vertical = 6.dp))
                    Text(h.temp.deg(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (h.rainProb >= 20) "${h.rainProb}%" else " ", fontSize = 11.sp, color = p.accent)
                }
            }
        }
        if (w.daily.size > 1) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = p.text.copy(alpha = 0.08f))
            val lo = w.daily.minOf { it.tMin }
            val hi = w.daily.maxOf { it.tMax }
            val span = (hi - lo).coerceAtLeast(1.0)
            w.daily.forEachIndexed { i, d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    val day = if (i == 0) "Oggi" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                    Text(day, fontSize = 14.sp, modifier = Modifier.width(46.dp))
                    Box(Modifier.width(30.dp)) { WeatherIcon(d.code, size = 24.dp) }
                    Text(if (d.rainProb >= 20) "${d.rainProb}%" else "", fontSize = 11.sp, color = p.accent, modifier = Modifier.width(36.dp))
                    Text(d.tMin.deg(), fontSize = 14.sp, color = p.sub, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                    Canvas(Modifier.weight(1f).padding(horizontal = 10.dp).height(6.dp)) {
                        val r = CornerRadius(size.height / 2)
                        drawRoundRect(p.text.copy(alpha = 0.08f), cornerRadius = r)
                        val x0 = ((d.tMin - lo) / span).toFloat() * size.width
                        val x1 = ((d.tMax - lo) / span).toFloat() * size.width
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(Color(0xFF5AA9FF), Color(0xFFFFB347), Color(0xFFFF6B4A)), 0f, size.width),
                            topLeft = Offset(x0, 0f), size = Size((x1 - x0).coerceAtLeast(size.height), size.height), cornerRadius = r,
                        )
                    }
                    Text(d.tMax.deg(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp))
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: Event, now: LocalDateTime?) {
    val p = LocalPalette.current
    val past = now != null && !e.allDay && e.end.isBefore(now)
    val live = now != null && !e.allDay && !e.start.isAfter(now) && e.end.isAfter(now)
    val color = if (e.color != 0) Color(e.color) else p.accent
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(54.dp)) {
            if (e.allDay) Text("Tutto\nil giorno", fontSize = 11.sp, lineHeight = 13.sp, color = p.sub)
            else {
                Text(e.start.format(hm), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (past) p.sub else p.text)
                Text(e.end.format(hm), fontSize = 12.sp, color = p.sub)
            }
        }
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = if (past) 0.35f else 1f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (past) p.sub else p.text)
            if (e.location.isNotBlank()) Text(e.location, fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (live) Pill("Ora")
    }
}

@Composable
fun AgendaCard(s: BriefState, onGrant: () -> Unit) {
    val p = LocalPalette.current
    val evening = s.daypart == Daypart.EVENING || s.daypart == Daypart.NIGHT
    BriefCard(title = "Agenda", icon = Icons.Rounded.CalendarMonth, trailing = if (s.calendarGranted) "${s.today.size} oggi · ${s.tomorrow.size} domani" else null) {
        if (!s.calendarGranted) {
            Hint("Consenti l'accesso al calendario per vedere qui i tuoi impegni.")
            ActionButton("Consenti", onGrant)
            return@BriefCard
        }
        // Di sera conta di più il domani: lo mostriamo per primo.
        val sections = listOf("Oggi" to s.today, "Domani" to s.tomorrow).let { if (evening) it.reversed() else it }
        sections.forEachIndexed { i, (label, events) ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.text.copy(alpha = 0.08f))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
            if (events.isEmpty()) Text("Nessun impegno", fontSize = 14.sp, color = p.sub, modifier = Modifier.padding(vertical = 6.dp))
            events.take(6).forEach { EventRow(it, if (label == "Oggi") s.now else null) }
            if (events.size > 6) Text("+ altri ${events.size - 6}", fontSize = 12.sp, color = p.sub)
        }
        val sources = when {
            s.calendars.isEmpty() -> "Nessun calendario attivo: sceglili nelle impostazioni."
            else -> "Calendari letti: " + s.calendars.joinToString(", ") +
                if (s.calendarsOff.isEmpty()) "" else "\nEventi non ancora scaricati sul telefono: " + s.calendarsOff.joinToString(", ")
        }
        Text(sources, fontSize = 11.sp, lineHeight = 15.sp, color = p.sub.copy(alpha = 0.8f), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun HealthCard(s: BriefState, onConnect: () -> Unit) {
    val p = LocalPalette.current
    BriefCard(title = "Salute e attività", icon = Icons.Rounded.SelfImprovement) {
        when (val h = s.health) {
            HealthState.Loading -> Hint("Leggo i dati di salute…")
            HealthState.Unavailable -> Hint("Health Connect non è disponibile su questo dispositivo.")
            HealthState.NeedsPermission -> {
                Hint("Collega Health Connect per vedere passi, sonno, battito e calorie. Samsung Health, Google Fit e gli smartwatch sincronizzano lì i loro dati.")
                ActionButton("Collega Health Connect", onConnect)
            }
            is HealthState.Data -> {
                val d = h.health
                // Si mostrano solo le misure che esistono davvero: senza orologio o fascia, sonno e battito non compaiono.
                val stats = listOfNotNull(
                    d.sleepMinutes?.takeIf { it > 0 }?.let { Triple(Icons.Rounded.Bedtime, durationText(it), "Sonno") },
                    d.avgHr?.let { Triple(Icons.Rounded.Favorite, "$it bpm", d.minHr?.let { m -> "Battito · min $m" } ?: "Battito medio") },
                    d.calories?.takeIf { it > 0 }?.let { Triple(Icons.Rounded.LocalFireDepartment, "${it.roundToInt()} kcal", "Calorie") },
                    d.distanceKm?.takeIf { it > 0 }?.let { Triple(Icons.Rounded.Route, String.format(Locale.ITALY, "%.1f km", it), "Distanza") },
                )
                val steps = d.steps
                if (steps == null && stats.isEmpty()) {
                    Hint("Health Connect è collegato ma oggi non contiene ancora dati.")
                    return@BriefCard
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (steps != null) {
                        val progress = (steps.toFloat() / s.stepGoal).coerceIn(0f, 1f)
                        Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(118.dp)) {
                                val stroke = Stroke(11.dp.toPx(), cap = StrokeCap.Round)
                                val inset = stroke.width / 2
                                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                                drawArc(p.text.copy(alpha = 0.08f), -90f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
                                if (progress > 0f) drawArc(p.accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = stroke)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(String.format(Locale.ITALY, "%,d", steps), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("di ${String.format(Locale.ITALY, "%,d", s.stepGoal)} passi", fontSize = 11.sp, color = p.sub)
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (steps != null && stats.isEmpty()) {
                            val left = s.stepGoal - steps
                            Text(if (left > 0) "Ti mancano" else "Obiettivo", fontSize = 12.sp, color = p.sub)
                            Text(
                                if (left > 0) "${String.format(Locale.ITALY, "%,d", left)} passi" else "raggiunto",
                                fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        stats.forEach { (icon, value, label) -> Stat(icon, value, label) }
                    }
                }
            }
        }
    }
}

@Composable
fun UsageCard(s: BriefState, onGrant: () -> Unit, onAppInfo: () -> Unit) {
    val p = LocalPalette.current
    BriefCard(title = "Tempo di utilizzo", icon = Icons.Rounded.Smartphone) {
        when (val u = s.usage) {
            UsageState.Loading -> Hint("Calcolo il tempo di utilizzo…")
            UsageState.NeedsPermission -> {
                Hint("Attiva \"Accesso ai dati di utilizzo\" per Brieffo per sapere quanto tempo passi al telefono e su quali app.")
                ActionButton("Attiva accesso", onGrant)
                // Android blocca questa impostazione alle app installate da file APK finché non la si sblocca a mano.
                Text(
                    "Android dice che l'accesso è negato o l'impostazione è limitata? Succede con le app installate da file APK. " +
                        "Tocca \"Info app\", poi i tre puntini in alto a destra e \"Consenti impostazioni con limitazioni\". " +
                        "Dopo torna qui e tocca di nuovo \"Attiva accesso\".",
                    fontSize = 12.sp, lineHeight = 17.sp, color = p.sub, modifier = Modifier.padding(top = 14.dp),
                )
                OutlinedButton(onClick = onAppInfo, modifier = Modifier.padding(top = 8.dp)) { Text("Info app", color = p.accent) }
            }
            is UsageState.Data -> {
                Text(durationText(u.usage.totalMinutes), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("oggi sullo schermo", fontSize = 13.sp, color = p.sub)
                val max = u.usage.top.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                u.usage.top.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(p.text.copy(alpha = 0.08f))) {
                            Box(Modifier.fillMaxWidth(a.minutes.toFloat() / max).height(8.dp).clip(CircleShape).background(p.accent))
                        }
                        Text(durationText(a.minutes), fontSize = 12.sp, color = p.sub, textAlign = TextAlign.End, modifier = Modifier.width(72.dp))
                    }
                }
            }
        }
    }
}

private fun ago(t: LocalDateTime?): String? {
    if (t == null) return null
    val m = Duration.between(t, LocalDateTime.now()).toMinutes()
    return when {
        m < 1 -> "adesso"
        m < 60 -> "$m min fa"
        m < 1440 -> "${m / 60} h fa"
        else -> "${m / 1440} g fa"
    }
}

/** Ogni fonte ha un colore stabile ricavato dal suo nome, così resta lo stesso a ogni apertura. */
internal fun topicColor(topic: String): Color =
    AccentChoices[(topic.lowercase().hashCode() and 0x7fffffff) % (AccentChoices.size - 1)].second

@Composable
private fun TopicTag(topic: String) {
    Text(
        topic.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Color.White,
        modifier = Modifier.background(topicColor(topic), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Immagine dell'articolo; se manca o non si carica resta visibile il gradiente nel colore dell'argomento. */
@Composable
private fun NewsImage(n: NewsItem, modifier: Modifier, letter: Boolean = true) {
    val c = topicColor(n.topic)
    // Le notizie oltre le prime arrivano senza foto: la si cerca quando la riga entra in scena.
    val image by produceState(n.image, n.link) {
        if (value == null) value = withContext(Dispatchers.IO) { NewsRepo.preview(n.link) }
    }
    Box(modifier.background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f)))), contentAlignment = Alignment.Center) {
        if (image == null) {
            if (letter) Text(n.topic.take(1), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(image).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun NewsHero(n: NewsItem, onOpen: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp)).clickable { onOpen(n.link) }) {
        NewsImage(n, Modifier.matchParentSize(), letter = false)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.86f))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicTag(n.topic)
                ago(n.published)?.let { Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)) }
            }
            Text(
                n.title, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NewsRow(n: NewsItem, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.text.copy(alpha = 0.05f)).clickable { onOpen(n.link) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewsImage(n, Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(topicColor(n.topic), CircleShape))
                Text(
                    listOfNotNull(n.topic, ago(n.published)).joinToString(" · "),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = p.sub, modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                n.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun NewsCard(news: List<NewsItem>, loading: Boolean, configured: Boolean, onSetup: () -> Unit, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    BriefCard(title = "Notizie", icon = Icons.Rounded.Newspaper) {
        if (!configured) {
            Hint("Scegli tu da dove leggere le notizie: aggiungi nelle impostazioni i siti o i feed RSS che preferisci.")
            ActionButton("Aggiungi una fonte", onSetup)
            return@BriefCard
        }
        if (news.isEmpty()) {
            Hint(if (loading) "Carico le notizie…" else "Nessuna notizia recente dalle tue fonti.")
            return@BriefCard
        }
        var filter by rememberSaveable { mutableStateOf<String?>(null) }
        // Quante notizie mostrare sotto quella in evidenza: si parte da 3 e si aggiungono a gruppi.
        var count by rememberSaveable { mutableIntStateOf(3) }
        val topics = news.map { it.topic }.distinct()
        if (filter != null && filter !in topics) filter = null

        if (topics.size > 1) {
            val chips = rememberLazyListState()
            LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp).horizontalFade(chips)) {
                items(listOf<String?>(null) + topics) { t ->
                    val selected = filter == t
                    Text(
                        t ?: "Tutte", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (selected) (if (t == null) p.onAccent else Color.White) else p.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) (t?.let(::topicColor) ?: p.accent) else p.text.copy(alpha = 0.07f))
                            .clickable { filter = t; count = 3 }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val shown = news.filter { filter == null || it.topic == filter }
        // In apertura va la prima notizia con una foto, così il riquadro grande non resta vuoto.
        val hero = shown.firstOrNull { it.image != null } ?: shown.first()
        val rest = shown - hero
        NewsHero(hero, onOpen)
        Column(Modifier.animateContentSize()) {
            rest.take(count).forEach {
                Spacer(Modifier.height(10.dp))
                NewsRow(it, onOpen)
            }
        }
        val left = rest.size - count
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            if (left > 0) NewsMore("Mostra altre ${minOf(6, left)}") { count += 6 }
            if (left > 6) NewsMore("Mostra tutte (${rest.size})") { count = rest.size }
            if (count > 3) NewsMore("Riduci") { count = 3 }
        }
    }
}

@Composable
private fun NewsMore(text: String, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LocalPalette.current.accent,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun MarketsCard(markets: List<Market>) {
    if (markets.isEmpty()) return
    val p = LocalPalette.current
    BriefCard(title = "Mercati", icon = Icons.Rounded.QueryStats) {
        val tiles = rememberLazyListState()
        LazyRow(state = tiles, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalFade(tiles)) {
            items(markets) { m ->
                Column(Modifier.background(p.text.copy(alpha = 0.06f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(m.label, fontSize = 12.sp, color = p.sub)
                    Text(m.value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val c = m.changePct
                    if (c != null) {
                        val up = c >= 0
                        Text(
                            (if (up) "▲ " else "▼ ") + String.format(Locale.ITALY, "%.2f%%", kotlin.math.abs(c)),
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (up) Color(0xFF2EAD6B) else Color(0xFFE5484D),
                        )
                    } else Text("cambio BCE", fontSize = 12.sp, color = p.sub)
                }
            }
        }
    }
}

@Composable
fun ExtrasCard(onThisDay: OnThisDay?, moon: Moon?, battery: Battery?, alarm: LocalDateTime?) {
    val p = LocalPalette.current
    BriefCard(title = "In breve", icon = Icons.Rounded.TipsAndUpdates) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (moon != null) {
                Column(Modifier.weight(1f).background(p.text.copy(alpha = 0.06f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    MoonIcon(moon.phase, size = 34.dp)
                    Text(moon.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text("Illuminata al ${moon.illumination}%", fontSize = 12.sp, color = p.sub)
                }
            }
            if (battery != null) {
                Column(Modifier.weight(1f).background(p.text.copy(alpha = 0.06f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    IconBadge(
                        when {
                            battery.charging -> Icons.Rounded.BatteryChargingFull
                            battery.percent <= 20 -> Icons.Rounded.BatteryAlert
                            else -> Icons.Rounded.BatteryFull
                        },
                        size = 34.dp,
                        tint = if (battery.percent <= 20 && !battery.charging) Color(0xFFE5484D) else p.accent,
                    )
                    Text("${battery.percent}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        when {
                            battery.charging -> "In carica"
                            battery.percent <= 20 -> "Batteria scarica"
                            else -> "Batteria"
                        },
                        fontSize = 12.sp, color = p.sub,
                    )
                }
            }
        }
        if (alarm != null) {
            Spacer(Modifier.height(10.dp))
            AlarmTile(alarm, Modifier.fillMaxWidth())
        }
        if (onThisDay != null) {
            Spacer(Modifier.height(14.dp))
            val today = LocalDate.now()
            Text(
                "Accadde oggi · ${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)} ${onThisDay.year}",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.accent,
            )
            Text(onThisDay.text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
