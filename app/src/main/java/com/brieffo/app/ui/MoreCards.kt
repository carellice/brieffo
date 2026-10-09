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

@Composable
fun TravelCard(s: BriefState, onSetup: () -> Unit) {
    val p = LocalPalette.current
    BriefCard(title = "Spostamenti", icon = Icons.Rounded.Commute) {
        if (s.travel.isEmpty()) {
            if (s.travelConfigured) Hint("Non riesco a calcolare il percorso: controlla l'indirizzo nelle impostazioni e che la posizione sia attiva.")
            else {
                Hint("Aggiungi l'indirizzo del lavoro nelle impostazioni per vedere quanto ci metti. Per gli impegni in agenda con un luogo il tempo si calcola da solo.")
                ActionButton("Imposta", onSetup)
            }
            return@BriefCard
        }
        // Una destinazione alla volta, con sotto una riga per ogni mezzo scelto.
        s.travel.groupBy { it.label }.entries.forEachIndexed { i, (label, rides) ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = p.text.copy(alpha = 0.08f))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            rides.forEach { t ->
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
                        Text(TravelRepo.MODES[t.mode] ?: t.mode, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                t.detail ?: String.format(Locale.ITALY, "%.1f km", t.km),
                                t.leaveBy?.let { "parti entro le ${it.format(hm)}" },
                            ).joinToString(" · "),
                            fontSize = 12.sp, lineHeight = 16.sp, color = if (t.leaveBy != null) p.accent else p.sub,
                        )
                    }
                    Text(durationText(t.minutes), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("Auto, bici e piedi: stima senza traffico. Mezzi pubblici: orari ufficiali, con l'attesa alla fermata.", fontSize = 11.sp, color = p.sub.copy(alpha = 0.8f), modifier = Modifier.padding(top = 12.dp))
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
