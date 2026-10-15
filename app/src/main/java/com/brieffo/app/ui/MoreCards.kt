package com.brieffo.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.brieffo.app.data.BriefState
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.clickable
import com.brieffo.app.data.Commute
import com.brieffo.app.data.SavedPlace
import com.brieffo.app.data.Travel
import com.brieffo.app.data.CardKeys
import com.brieffo.app.data.wxText
import com.brieffo.app.data.Match
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.Sport
import com.brieffo.app.data.TravelRepo
import com.brieffo.app.data.WxAlert
import com.brieffo.app.data.durationText
import com.brieffo.app.notify.MatchReminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

/** "oggi", "domani" oppure "ven 9 ott": le date vicine si leggono meglio a parole. */
private fun dayLabel(d: LocalDate): String = when (ChronoUnit.DAYS.between(LocalDate.now(), d)) {
    0L -> "oggi"
    1L -> "domani"
    -1L -> "ieri"
    else -> d.format(dayMonth)
}

@Composable
fun AlertBanner(alerts: List<WxAlert>) {
    alerts.forEach { a ->
        val color = when (a.level) { 2 -> Color(0xFFE0A100); 3 -> Color(0xFFF2643D); else -> Color(0xFFD92D20) }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(16.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WarningAmber, null, Modifier.size(24.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Allerta ${a.color} · ${a.type}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val span = listOfNotNull(
                    a.from?.let { "da ${dayLabel(it.toLocalDate())} alle ${it.format(hm)}" },
                    a.until?.let { "fino a ${dayLabel(it.toLocalDate())} alle ${it.format(hm)}" },
                ).joinToString(" ").replaceFirstChar { it.uppercase() }
                Text(listOf(span, "MeteoAlarm").filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.sub)
            }
        }
    }
}

/** Una riga di tragitto: icona del mezzo, dettaglio, ora entro cui uscire e durata. */
@Composable
private fun RideRow(t: Travel) {
    val p = LocalPalette.current
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (t.mode) {
                "transit" -> Icons.Rounded.DirectionsBus
                "bike" -> Icons.AutoMirrored.Rounded.DirectionsBike
                "foot" -> Icons.AutoMirrored.Rounded.DirectionsWalk
                else -> Icons.Rounded.DirectionsCar
            },
            null, Modifier.size(22.dp), tint = p.accent,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                (TravelRepo.MODES[t.mode] ?: t.mode) + (t.leaveBy?.let { " · esci entro le ${it.format(hm)}" } ?: ""),
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (t.leaveBy != null) p.accent else p.text,
            )
            Text(t.detail ?: String.format(Locale.ITALY, "%.1f km", t.km), fontSize = 12.sp, lineHeight = 16.sp, color = p.sub)
        }
        Text(durationText(t.minutes), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Il lavoro di oggi o di domani: se l'app non sa ancora a che ora si entra, lo chiede (si risponde con un tocco,
 * l'orario proposto è l'ultimo usato); poi mostra per ogni mezzo scelto quanto ci si mette e quando uscire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommuteBlock(c: Commute, today: LocalDate, lastStart: String, onAnswer: (LocalDate, String?) -> Unit) {
    val p = LocalPalette.current
    val day = if (c.day == today) "oggi" else "domani"
    var picking by remember { mutableStateOf(false) }
    val proposed = remember(c.day, c.start) { (c.start?.toString() ?: lastStart).split(':').mapNotNull { it.toIntOrNull() } }
    val time = rememberTimePickerState(proposed.getOrElse(0) { 9 }, proposed.getOrElse(1) { 0 }, is24Hour = true)
    val chosen = "%02d:%02d".format(time.hour, time.minute)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Lavoro", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (!c.needsAnswer) Text(
            if (c.off) "$day non lavori" else "$day entri alle ${c.start!!.format(hm)}",
            fontSize = 13.sp, color = p.sub,
        )
    }
    when {
        c.needsAnswer -> {
            Text("A che ora entri al lavoro $day? Calcolo io quando uscire, da dove ti trovi, con i mezzi che hai scelto.", fontSize = 13.sp, lineHeight = 18.sp, color = p.sub, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(chosen, color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onAnswer(c.day, chosen) },
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                ) { Text("Conferma") }
                TextButton(onClick = { onAnswer(c.day, "off") }) { Text("Non lavoro", color = p.sub) }
            }
        }
        c.off -> Unit
        c.there -> Text("Sei già al lavoro.", fontSize = 13.sp, color = p.sub, modifier = Modifier.padding(top = 4.dp))
        else -> {
            c.rides.forEach { RideRow(it) }
            if (c.failed.isNotEmpty()) Text(
                "Non riesco a calcolare: " + c.failed.joinToString { (TravelRepo.MODES[it] ?: it).lowercase() } +
                    if ("transit" in c.failed) ". Per i mezzi pubblici può non esserci una corsa adatta a quell'orario." else ".",
                fontSize = 12.sp, lineHeight = 16.sp, color = p.sub, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (!c.needsAnswer) Text(
        "Cambia orario", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.accent,
        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { onAnswer(c.day, null) }.padding(vertical = 4.dp),
    )
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        confirmButton = { TextButton(onClick = { picking = false }) { Text("Fatto", color = p.accent) } },
        title = { Text("Entri al lavoro alle") },
        text = { TimePicker(time) },
    )
}

@Composable
fun TravelCard(s: BriefState, onSetup: () -> Unit, onGrantPrecise: () -> Unit, onWorkStart: (LocalDate, String?) -> Unit) {
    val p = LocalPalette.current
    BriefCard(title = "Spostamenti", icon = Icons.Rounded.Commute) {
        if (!s.travelConfigured) {
            Hint("Imposta casa e lavoro nelle impostazioni: ti dico quanto ci metti, con i mezzi che preferisci. Per gli impegni in agenda con un luogo il tempo si calcola da solo.")
            ActionButton("Imposta", onSetup)
            return@BriefCard
        }
        // Senza la posizione precisa il punto di partenza può sbagliare di uno o due chilometri: lo si dice e si chiede.
        if (!s.preciseLocation) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).background(p.accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.MyLocation, null, Modifier.size(22.dp), tint = p.accent)
                Text(
                    "Per calcolare il tragitto da dove ti trovi davvero mi serve la posizione precisa.",
                    fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                TextButton(onClick = onGrantPrecise) { Text("Consenti", color = p.accent, fontWeight = FontWeight.SemiBold) }
            }
        }
        val c = s.commute
        if (c != null) CommuteBlock(c, s.now.toLocalDate(), Prefs(LocalContext.current).lastWorkStart, onWorkStart)
        // Le altre destinazioni (e il prossimo impegno con un luogo): una alla volta, con una riga per mezzo.
        s.travel.groupBy { it.label }.entries.forEachIndexed { i, (label, rides) ->
            if (i > 0 || c != null) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = p.text.copy(alpha = 0.08f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Le destinazioni salvate hanno la loro icona; il prossimo impegno in agenda quella del calendario.
                Icon(
                    if (label in s.placeIcons) placeIcon(SavedPlace(label, "", icon = s.placeIcons.getValue(label))) else Icons.Rounded.Event,
                    null, Modifier.size(18.dp), tint = p.sub,
                )
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            rides.forEach { RideRow(it) }
        }
        if (c == null && s.travel.isEmpty()) Hint(
            if (s.loading) "Calcolo i tragitti…" else "Nessun tragitto da mostrare adesso: sei già a destinazione, oppure non riesco a calcolare il percorso. Il tragitto per il lavoro compare la sera per il giorno dopo e la mattina fino all'orario d'ingresso."
        )
        Text(
            listOfNotNull(
                s.travelFrom.takeIf { it.isNotBlank() }?.let { "Partenza $it." },
                "Auto, bici e piedi: stima senza traffico. Mezzi pubblici: orari ufficiali, con l'attesa alla fermata.",
            ).joinToString(" "),
            fontSize = 11.sp, lineHeight = 15.sp, color = p.sub.copy(alpha = 0.8f), modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun TeamLogo(url: String?, size: Int) {
    if (url == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
        contentDescription = null, modifier = Modifier.size(size.dp),
    )
}

/** Riquadro grande per la partita che conta adesso: quella in corso oppure la prossima. */
@Composable
private fun MatchHero(label: String, m: Match) {
    val p = LocalPalette.current
    val tint = if (m.live) Color(0xFFE5484D) else p.accent
    Column(
        Modifier.fillMaxWidth().background(tint.copy(alpha = 0.10f), RoundedCornerShape(22.dp)).padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            listOfNotNull(label, m.league.takeIf { it.isNotBlank() }).joinToString(" · ").uppercase(),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = tint,
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                TeamLogo(m.homeLogo, 52)
                Text(m.home, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
                val played = m.homeScore != null && m.awayScore != null
                if (!played) m.date?.let {
                    Text(dayLabel(it.toLocalDate()).replaceFirstChar { c -> c.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = p.sub)
                }
                Text(
                    if (played) "${m.homeScore} – ${m.awayScore}" else m.date?.format(hm) ?: "vs",
                    fontSize = 30.sp, fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                TeamLogo(m.awayLogo, 52)
                Text(m.away, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
        m.venue?.let {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = p.sub)
                Text(it, fontSize = 12.sp, color = p.sub, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (!m.live && m.date?.isAfter(LocalDateTime.now()) == true) ReminderButton(m)
    }
}

/** Accende o toglie il promemoria della partita: una notifica poco prima dell'inizio. */
@Composable
private fun ReminderButton(m: Match) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var reminder by remember { mutableStateOf(prefs.matchReminder) }
    var denied by remember { mutableStateOf(false) }
    val active = reminder == MatchReminder.key(m)
    fun turnOn() {
        MatchReminder.schedule(context, m)
        reminder = prefs.matchReminder
        denied = false
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else denied = true
    }
    // Partita spostata: il promemoria già acceso segue il nuovo orario.
    LaunchedEffect(m.date) { if (MatchReminder.moved(reminder, m)) turnOn() }

    OutlinedButton(
        onClick = {
            val allowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            when {
                active -> {
                    MatchReminder.cancel(context)
                    reminder = ""
                }
                allowed -> turnOn()
                else -> askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Icon(if (active) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone, null, Modifier.size(18.dp), tint = p.accent)
        Text(
            if (active) "Promemoria attivo · tocca per toglierlo" else "Avvisami ${MatchReminder.MINUTES_BEFORE} minuti prima",
            color = p.accent, modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (denied) Text(
        "Per il promemoria consenti le notifiche a Brieffo dalle impostazioni di Android.",
        fontSize = 12.sp, color = p.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
    )
}

/** Riga compatta: stemma dell'avversario, competizione e, a destra, risultato oppure data. */
@Composable
private fun MatchLine(team: String, m: Match, outcome: Char? = null) {
    val p = LocalPalette.current
    val atHome = m.home == team
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { TeamLogo(if (atHome) m.awayLogo else m.homeLogo, 30) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(if (atHome) m.away else m.home, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(m.league.takeIf { it.isNotBlank() }, if (atHome) "in casa" else "in trasferta").joinToString(" · "),
                fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (m.homeScore != null && m.awayScore != null) {
            // Il punteggio è sempre scritto dal punto di vista della propria squadra.
            val mine = if (atHome) m.homeScore else m.awayScore
            val theirs = if (atHome) m.awayScore else m.homeScore
            Text("$mine – $theirs", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (outcome != null) FormDot(outcome, Modifier.padding(start = 10.dp))
        } else m.date?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text(dayLabel(it.toLocalDate()), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(it.format(hm), fontSize = 12.sp, color = p.sub)
            }
        }
    }
}

@Composable
private fun FormDot(r: Char, modifier: Modifier = Modifier, size: Int = 22) {
    val c = outcomeColor(r)
    Text(
        r.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
        modifier = modifier.size(size.dp).background(c, CircleShape).wrapContentHeight(),
    )
}

private fun outcomeColor(r: Char) = when (r) { 'V' -> Color(0xFF1F9D61); 'P' -> Color(0xFFE5484D); else -> Color(0xFF8A8FA3) }

private fun outcomeWord(r: Char) = when (r) { 'V' -> "Vinta"; 'P' -> "Persa"; else -> "Pari" }

/**
 * Andamento recente: una casella per partita con stemma dell'avversario, punteggio ed esito a parole,
 * colorata di verde, grigio o rosso. La più recente è a destra, evidenziata dal bordo.
 */
@Composable
private fun FormStrip(sport: Sport) {
    val p = LocalPalette.current
    val wins = sport.form.count { it == 'V' }
    val draws = sport.form.count { it == 'N' }
    val losses = sport.form.count { it == 'P' }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("ULTIME ${sport.form.size} PARTITE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = p.sub, modifier = Modifier.weight(1f))
        Text("$wins vinte · $draws pari · $losses perse", fontSize = 12.sp, color = p.sub)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sport.form.forEachIndexed { i, r ->
            val m = sport.formMatches.getOrNull(i)
            val c = outcomeColor(r)
            val latest = i == sport.form.lastIndex
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.copy(alpha = 0.14f))
                    .then(if (latest) Modifier.border(1.5.dp, c.copy(alpha = 0.7f), RoundedCornerShape(16.dp)) else Modifier)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (m != null) {
                    val atHome = m.home == sport.team
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { TeamLogo(if (atHome) m.awayLogo else m.homeLogo, 26) }
                    if (m.homeScore != null && m.awayScore != null) {
                        Text(
                            if (atHome) "${m.homeScore}–${m.awayScore}" else "${m.awayScore}–${m.homeScore}",
                            fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                Text(outcomeWord(r), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    // Anche a colpo d'occhio: una barra proporzionale a vittorie, pareggi e sconfitte.
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(CircleShape)) {
        listOf('V' to wins, 'N' to draws, 'P' to losses).filter { it.second > 0 }.forEach { (r, n) ->
            Box(Modifier.weight(n.toFloat()).height(6.dp).background(outcomeColor(r)))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
        color = LocalPalette.current.sub, modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SportCard(s: BriefState, onSetup: () -> Unit) {
    val p = LocalPalette.current
    val sport = s.sport
    BriefCard(title = "La tua squadra", icon = Icons.Rounded.SportsSoccer, trailing = sport?.source?.takeIf { it.isNotEmpty() }) {
        when {
            !s.sportConfigured -> {
                Hint("Scegli la tua squadra nelle impostazioni per vedere classifica, ultimo risultato e prossime partite.")
                ActionButton("Imposta", onSetup)
            }
            sport == null || (sport.last == null && sport.upcoming.isEmpty() && sport.live == null) ->
                Hint(if (s.loading) "Cerco le partite…" else "Non trovo partite per questa squadra: prova a sceglierla di nuovo dalle impostazioni.")
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(sport.logo, 56)
                    Column(Modifier.padding(start = if (sport.logo != null) 14.dp else 0.dp).weight(1f)) {
                        Text(sport.team, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        sport.standing?.let { Text(it.substringBefore(" · "), fontSize = 13.sp, color = p.sub) }
                    }
                }
                sport.standing?.split(" · ")?.drop(1)?.takeIf { it.isNotEmpty() }?.let { parts ->
                    FlowRow(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { parts.forEach { Pill(it) } }
                }
                if (sport.form.isNotEmpty()) FormStrip(sport)

                val featured = sport.live ?: sport.next
                if (featured != null) {
                    Spacer(Modifier.height(14.dp))
                    MatchHero(if (featured.live) "In corso" else "Prossima partita", featured)
                }
                sport.last?.let {
                    SectionLabel("Ultimo risultato")
                    MatchLine(sport.team, it, sport.form.lastOrNull())
                }
                // Se in alto c'è la partita in corso, la prossima scende qui insieme alle altre.
                val later = if (sport.live != null) sport.upcoming.take(2) else sport.upcoming.drop(1)
                if (later.isNotEmpty()) {
                    SectionLabel("In calendario")
                    later.forEach { MatchLine(sport.team, it) }
                }
            }
        }
    }
}

@Composable
private fun OccasionRow(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 36.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(detail, fontSize = 12.sp, color = LocalPalette.current.sub)
        }
    }
}

/**
 * Sguardo a domani, per il brief della sera: che tempo farà, il primo impegno, quando uscire
 * e a che ora conviene puntare la sveglia.
 */
@Composable
fun TomorrowCard(s: BriefState) {
    val p = LocalPalette.current
    BriefCard(title = "Domani", icon = Icons.Rounded.NightsStay, trailing = if (s.calendarGranted) "${s.tomorrow.size} ${if (s.tomorrow.size == 1) "impegno" else "impegni"}" else null) {
        s.weather?.tomorrow?.takeIf { s.shows(CardKeys.WEATHER) }?.let { d ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { WeatherIcon(d.code, size = 32.dp) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(wxText(d.code).replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Tra ${Math.round(d.tMin)}° e ${Math.round(d.tMax)}°" + if (d.rainProb > 0) " · pioggia ${d.rainProb}%" else "", fontSize = 12.sp, color = p.sub)
                }
            }
        }
        val first = s.tomorrow.firstOrNull { !it.allDay }
        if (s.calendarGranted && s.shows(CardKeys.AGENDA)) {
            if (first != null) OccasionRow(Icons.Rounded.Event, first.title, "Primo impegno alle ${first.start.format(hm)}" + if (first.location.isNotBlank()) " · ${first.location}" else "")
            else OccasionRow(Icons.Rounded.Event, if (s.tomorrow.isEmpty()) "Agenda libera" else s.tomorrow.first().title, if (s.tomorrow.isEmpty()) "Nessun impegno in programma" else "Tutto il giorno")
        }
        val work = s.commute?.takeIf { it.day == s.now.toLocalDate().plusDays(1) && s.shows(CardKeys.TRAVEL) }
        val workRide = work?.rides?.firstOrNull { it.leaveBy != null }
        when {
            work == null -> Unit
            work.needsAnswer -> OccasionRow(Icons.Rounded.Work, "A che ora entri al lavoro?", "Rispondi nella scheda Spostamenti: calcolo io quando uscire")
            work.off -> OccasionRow(Icons.Rounded.Work, "Domani non lavori", "Niente sveglia per il lavoro")
            workRide != null -> OccasionRow(
                Icons.Rounded.Work, "Lavoro alle ${work.start!!.format(hm)}: esci entro le ${workRide.leaveBy!!.format(hm)}",
                "${TravelRepo.MODES[workRide.mode] ?: workRide.mode} · ${durationText(workRide.minutes)}" + (workRide.detail?.let { " · $it" } ?: ""),
            )
        }
        val ride = s.tomorrowTravel.firstOrNull { it.leaveBy != null }
        if (ride != null) OccasionRow(
            Icons.Rounded.Commute, "Esci entro le ${ride.leaveBy!!.format(hm)}",
            "${TravelRepo.MODES[ride.mode] ?: ride.mode} · ${durationText(ride.minutes)}" + (ride.detail?.let { " · $it" } ?: ""),
        )
        // Un'ora per prepararsi prima di uscire; senza un tragitto, mezz'ora di margine sull'impegno.
        // La prima uscita della giornata, tra lavoro e primo impegno, decide la sveglia.
        val wake = listOfNotNull(workRide?.leaveBy, ride?.leaveBy ?: first?.start?.minusMinutes(30)).minOrNull()?.minusMinutes(60)
        val alarm = s.nextAlarm?.takeIf { it.toLocalDate() <= s.now.toLocalDate().plusDays(1) }
        when {
            wake != null -> OccasionRow(
                Icons.Rounded.Alarm, "Sveglia consigliata alle ${wake.format(hm)}",
                when {
                    alarm == null -> "Non ne hai una impostata per domani"
                    alarm.isAfter(wake.plusMinutes(10)) -> "La tua suona alle ${alarm.format(hm)}: è più tardi"
                    else -> "La tua suona alle ${alarm.format(hm)}"
                },
            )
            alarm != null -> OccasionRow(Icons.Rounded.Alarm, "Sveglia alle ${alarm.format(hm)}", "Nessun impegno con orario: puoi prendertela comoda")
        }
    }
}

@Composable
fun OccasionsCard(s: BriefState, onGrantContacts: () -> Unit) {
    val o = s.occasions
    val today = LocalDate.now()
    BriefCard(title = "Ricorrenze", icon = Icons.Rounded.Celebration) {
        if (o.saints.isNotEmpty()) OccasionRow(Icons.Rounded.Church, o.saints.first(), if (o.saints.size > 1) "Oggi anche " + o.saints.drop(1).joinToString(", ") else "Santo del giorno")
        o.birthdays.forEach { b ->
            OccasionRow(
                Icons.Rounded.Cake, b.name,
                "Compleanno ${dayLabel(b.date)}" + (b.age?.takeIf { it in 1..120 }?.let { " · $it anni" } ?: ""),
            )
        }
        o.holiday?.let { h ->
            val days = ChronoUnit.DAYS.between(today, h.date)
            OccasionRow(Icons.Rounded.Event, h.name, if (days == 0L) "Oggi è festa" else "${h.date.format(dayMonth)} · tra $days giorni")
        }
        if (o.saints.isEmpty() && o.birthdays.isEmpty() && o.holiday == null) Hint(if (s.loading) "Cerco le ricorrenze…" else "Nessuna ricorrenza da segnalare.")
        if (!o.contactsGranted) {
            Hint("Consenti l'accesso ai contatti per vedere qui i compleanni in arrivo.")
            ActionButton("Consenti", onGrantContacts)
        } else if (o.birthdays.isEmpty()) {
            Text("Nessun compleanno nei prossimi 14 giorni", fontSize = 12.sp, color = LocalPalette.current.sub, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun AlarmTile(alarm: LocalDateTime, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.background(p.text.copy(alpha = 0.06f), RoundedCornerShape(18.dp)).padding(14.dp)) {
        IconBadge(Icons.Rounded.Alarm, size = 34.dp)
        Text(alarm.format(hm), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text("Sveglia ${dayLabel(alarm.toLocalDate())}", fontSize = 12.sp, color = p.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
