package com.brieffo.app.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brieffo.app.MainActivity
import com.brieffo.app.data.CardKeys
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.SportRepo
import com.brieffo.app.data.Summary
import com.brieffo.app.data.wxText
import com.brieffo.app.notify.BriefWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)

/** "Oggi", "Domani" oppure "Ven 9 ott". */
private fun dayLabel(d: LocalDate) = when (ChronoUnit.DAYS.between(LocalDate.now(), d)) {
    0L -> "Oggi"
    1L -> "Domani"
    else -> d.format(dayMonth).replaceFirstChar { it.uppercase() }
}

/** Nei widget non si possono usare le icone disegnate dell'app: il tempo si mostra con un'emoji. */
private fun wxEmoji(code: Int, isDay: Boolean) = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..67, in 80..82 -> "🌧️"
    in 71..77, 85, 86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌤️"
}

object Widgets {
    /** Ridisegna tutti i widget di Brieffo presenti sulla schermata Home con i dati salvati. */
    suspend fun redraw(ctx: Context) {
        runCatching {
            MatchWidget().updateAll(ctx)
            WeatherWidget().updateAll(ctx)
            AgendaWidget().updateAll(ctx)
            SummaryWidget().updateAll(ctx)
        }
    }
}

@Composable
private fun title() = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

@Composable
private fun body(size: Int = 14, bold: Boolean = false) =
    TextStyle(color = GlanceTheme.colors.onSurface, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
private fun faint(size: Int = 12) = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = size.sp)

/** Cornice comune: sfondo arrotondato nei colori del telefono, e un tocco apre Brieffo. */
@Composable
private fun Frame(
    alignment: Alignment.Horizontal = Alignment.Start,
    vertical: Alignment.Vertical = Alignment.CenterVertically,
    padding: Int = 14,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlanceTheme {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
                .padding(padding.dp).clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = alignment, verticalAlignment = vertical, content = content,
        )
    }
}

@Composable
private fun Empty(label: String, text: String) = Frame {
    Text(label.uppercase(), style = title())
    Spacer(GlanceModifier.height(6.dp))
    Text(text, style = faint(13))
}

/** Prossima partita della squadra, oppure quella in corso con il punteggio. */
class MatchWidget : GlanceAppWidget() {
    // Il disegno dipende dalle misure reali: alto una sola riga della Home diventa una striscia.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetStore.load(context)
        val home = runCatching { BitmapFactory.decodeFile(WidgetStore.logo(context, true).path) }.getOrNull()
        val away = runCatching { BitmapFactory.decodeFile(WidgetStore.logo(context, false).path) }.getOrNull()
        provideContent {
            val m = data?.match
            if (m == null) {
                Empty("La tua squadra", if (data?.team.isNullOrBlank()) "Apri Brieffo e scegli la tua squadra nelle impostazioni." else "Nessuna partita in programma per ${data?.team}.")
                return@provideContent
            }
            val played = m.homeScore != null && m.awayScore != null
            if (LocalSize.current.height < 150.dp) {
                // Striscia: stemma e nome ai lati, in mezzo giorno e ora (o il punteggio).
                Frame(Alignment.CenterHorizontally, padding = 8) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (home != null) Image(ImageProvider(home), null, GlanceModifier.size(30.dp))
                        Text(m.home, style = body(13, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight().padding(start = 6.dp))
                        Column(GlanceModifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (m.live) "In corso" else m.date?.let { dayLabel(it.toLocalDate()) } ?: "",
                                style = if (m.live) title() else faint(11), maxLines = 1,
                            )
                            Text(if (played) "${m.homeScore} – ${m.awayScore}" else m.date?.format(hm) ?: "vs", style = body(17, bold = true), maxLines = 1)
                        }
                        Text(
                            m.away, style = body(13, bold = true).copy(textAlign = TextAlign.End), maxLines = 1,
                            modifier = GlanceModifier.defaultWeight().padding(end = 6.dp),
                        )
                        if (away != null) Image(ImageProvider(away), null, GlanceModifier.size(30.dp))
                    }
                }
                return@provideContent
            }
            Frame(Alignment.CenterHorizontally) {
                Text(
                    listOfNotNull(if (m.live) "In corso" else "Prossima partita", m.league.takeIf { it.isNotBlank() }).joinToString(" · ").uppercase(),
                    style = title(), maxLines = 1,
                )
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (home != null) Image(ImageProvider(home), null, GlanceModifier.size(40.dp))
                        Text(m.home, style = body(13, bold = true).copy(textAlign = TextAlign.Center), maxLines = 1)
                    }
                    Column(GlanceModifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!played) m.date?.let { Text(dayLabel(it.toLocalDate()), style = faint()) }
                        Text(if (played) "${m.homeScore} – ${m.awayScore}" else m.date?.format(hm) ?: "vs", style = body(24, bold = true))
                    }
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (away != null) Image(ImageProvider(away), null, GlanceModifier.size(40.dp))
                        Text(m.away, style = body(13, bold = true).copy(textAlign = TextAlign.Center), maxLines = 1)
                    }
                }
                m.venue?.let {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(it, style = faint(11), maxLines = 1)
                }
            }
        }
    }
}

/** Meteo di adesso con minima, massima e pioggia della giornata. */
class WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = WidgetStore.load(context)?.weather
        provideContent {
            if (w == null) {
                Empty("Meteo", "Apri Brieffo per caricare il meteo.")
                return@provideContent
            }
            Frame {
                Text(w.place.uppercase(), style = title(), maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(wxEmoji(w.code, w.isDay), style = TextStyle(fontSize = 30.sp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("${w.temp}°", style = body(34, bold = true))
                }
                Text(wxText(w.code).replaceFirstChar { it.uppercase() }, style = body(13), maxLines = 1)
                // Su due righe: nel formato quadrato una sola verrebbe tagliata.
                Text(listOfNotNull(w.max?.let { "Max $it°" }, w.min?.let { "Min $it°" }).joinToString(" · "), style = faint(), maxLines = 1)
                w.rain?.takeIf { it > 0 }?.let { Text("Pioggia $it%", style = faint(), maxLines = 1) }
            }
        }
    }
}

/** I prossimi impegni di oggi e di domani. */
class AgendaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetStore.load(context)
        provideContent {
            if (data == null || !data.agendaGranted) {
                Empty("Agenda", "Apri Brieffo e consenti l'accesso al calendario.")
                return@provideContent
            }
            // Gli impegni già cominciati da un pezzo spariscono anche se il widget non è stato ancora aggiornato.
            val now = LocalDateTime.now()
            val events = data.events.filter { it.allDay || it.start.isAfter(now.minusHours(1)) }.take(4)
            Frame(vertical = Alignment.Top) {
                Text("AGENDA", style = title())
                Spacer(GlanceModifier.height(6.dp))
                if (events.isEmpty()) Text("Nessun impegno tra oggi e domani.", style = faint(13))
                events.forEach { e ->
                    val day = if (e.start.toLocalDate() == now.toLocalDate()) "" else "Dom. "
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(day + if (e.allDay) "Giorno" else e.start.format(hm), style = faint(12), modifier = GlanceModifier.width(if (day.isEmpty()) 48.dp else 78.dp))
                        Text(e.title, style = body(14), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Stima se [lines], scritte a [font] punti, stanno in un riquadro largo [width] e alto [height] (in dp):
 * nei widget non si può misurare il testo, quindi si conta quante righe occuperà ogni capoverso.
 */
private fun fits(lines: List<String>, width: Float, height: Float, font: Int): Boolean {
    val perRow = (width / (font * 0.5f)).toInt().coerceAtLeast(1)
    val rows = lines.sumOf { (it.length + perRow - 1) / perRow }
    return rows * font * 1.32f + (lines.size - 1) * 4f <= height
}

/** Il riepilogo della giornata, lo stesso che apre il brief: il testo si ingrandisce o si stringe con il widget. */
class SummaryWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetStore.load(context)
        provideContent {
            if (data == null || data.summary.isBlank()) {
                Empty("Riepilogo", "Apri Brieffo per preparare il riepilogo.")
                return@provideContent
            }
            val size = LocalSize.current
            val lines = data.summary.lines().map { it.trim() }.filter { it.isNotEmpty() }
            // Nei formati bassi il titolo lascia il posto al testo e i margini si stringono.
            val small = size.height < 150.dp
            val padding = if (small) 10 else 14
            val width = size.width.value - padding * 2
            val height = size.height.value - padding * 2 - if (small) 0 else 24
            // Il carattere più grande con cui il testo entra tutto; se non entra nemmeno al minimo, si scorre.
            val font = (20 downTo 11).firstOrNull { fits(lines, width, height, it) } ?: 11
            Frame(vertical = Alignment.Top, padding = padding) {
                if (!small) {
                    Text("RIEPILOGO", style = title())
                    Spacer(GlanceModifier.height(6.dp))
                }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(lines) { line ->
                        Text(
                            line, style = body(font),
                            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp).clickable(actionStartActivity<MainActivity>()),
                        )
                    }
                }
            }
        }
    }
}

/** I widget si tengono aggiornati da soli: finché ce n'è almeno uno sulla Home, un lavoro periodico rilegge i dati. */
abstract class BriefWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetWorker.schedule(context)
    }
}

class MatchWidgetReceiver : BriefWidgetReceiver() { override val glanceAppWidget = MatchWidget() }
class WeatherWidgetReceiver : BriefWidgetReceiver() { override val glanceAppWidget = WeatherWidget() }
class AgendaWidgetReceiver : BriefWidgetReceiver() { override val glanceAppWidget = AgendaWidget() }
class SummaryWidgetReceiver : BriefWidgetReceiver() { override val glanceAppWidget = SummaryWidget() }

/** Rilegge meteo, agenda e partite in background e ridisegna i widget, senza che serva aprire l'app. */
class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val base = BriefWorker.buildState(ctx)
        val sport = if (base.shows(CardKeys.SPORT)) runCatching { SportRepo.load(prefs) }.getOrNull() else null
        val state = base.copy(sport = sport)
        // Il riepilogo di Gemini resta quello dell'ultima apertura finché è recente: qui non si chiama l'IA.
        val ai = prefs.aiSummary.takeIf { prefs.geminiKey.isNotBlank() && it.isNotBlank() && System.currentTimeMillis() - prefs.aiSummaryAt < 4 * 60 * 60 * 1000L }
        WidgetStore.update(ctx, state.copy(summary = ai ?: Summary.rules(state)))
        Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("widgets", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
