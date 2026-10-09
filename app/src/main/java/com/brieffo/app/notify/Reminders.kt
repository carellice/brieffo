package com.brieffo.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.brieffo.app.MainActivity
import com.brieffo.app.R
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.Sport
import com.brieffo.app.data.SportRepo
import com.brieffo.app.data.Travel
import com.brieffo.app.data.TravelRepo
import com.brieffo.app.data.durationText
import com.brieffo.app.widget.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** Mostra una notifica di Brieffo che, toccata, apre il brief. Senza il permesso non fa nulla. */
internal fun notify(ctx: Context, channel: String, channelName: String, id: Int, title: String, text: String, high: Boolean = true) {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(channel, channelName, if (high) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_brief)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    @Suppress("MissingPermission")
    NotificationManagerCompat.from(ctx).notify(id, n)
}

/**
 * "È ora di partire": poco prima dell'ora entro cui uscire per il prossimo impegno che ha un luogo,
 * calcolata con il primo dei mezzi scelti. Per ogni impegno avvisa una volta sola.
 */
object LeaveReminder {
    /** Quanti minuti prima dell'ora di partenza arriva l'avviso. */
    const val LEAD_MINUTES = 10L

    /** Da chiamare ogni volta che i tragitti vengono ricalcolati. */
    fun plan(ctx: Context, travel: List<Travel>) {
        val prefs = Prefs(ctx)
        val now = LocalDateTime.now()
        val next = travel.firstOrNull { it.leaveBy != null && it.leaveBy.isAfter(now) }.takeIf { prefs.leaveAlerts }
        if (next == null) {
            Alarms.cancel(ctx, Alarms.LEAVE)
            prefs.leaveAt = 0
            return
        }
        val leaveBy = next.leaveBy!!
        val key = "${next.label}|$leaveBy"
        if (prefs.leaveDone == key) return
        prefs.leaveKey = key
        prefs.leaveText = "Per «${next.label}» esci entro le ${leaveBy.format(hm)}: ${TravelRepo.MODES[next.mode]?.lowercase() ?: next.mode} ci metti ${durationText(next.minutes)}" +
            (next.detail?.let { " ($it)" } ?: "") + "."
        prefs.leaveAt = leaveBy.minusMinutes(LEAD_MINUTES).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (prefs.leaveAt <= System.currentTimeMillis()) show(ctx) else Alarms.set(ctx, Alarms.LEAVE, prefs.leaveAt)
    }

    fun show(ctx: Context) {
        val prefs = Prefs(ctx)
        if (prefs.leaveKey.isBlank() || prefs.leaveDone == prefs.leaveKey) return
        prefs.leaveDone = prefs.leaveKey
        prefs.leaveAt = 0
        if (prefs.leaveAlerts) notify(ctx, "leave", "Ora di partire", 4, "È quasi ora di partire", prefs.leaveText)
    }

    /** Dopo un riavvio o un aggiornamento la sveglia va rimessa. */
    fun restore(ctx: Context) {
        val prefs = Prefs(ctx)
        if (prefs.leaveAt <= 0 || !prefs.leaveAlerts) return
        val now = System.currentTimeMillis()
        when {
            prefs.leaveAt > now -> Alarms.set(ctx, Alarms.LEAVE, prefs.leaveAt)
            now < prefs.leaveAt + LEAD_MINUTES * 60_000 -> show(ctx)
            else -> prefs.leaveAt = 0
        }
    }
}

/**
 * Partita in diretta: dal fischio d'inizio l'app ricontrolla il punteggio ogni paio di minuti, aggiorna il widget
 * e avvisa a ogni gol e a fine partita. Tra una partita e l'altra non fa nulla, se non svegliarsi all'ora d'inizio.
 */
object LiveMatch {
    private const val EVERY = 2 * 60_000L

    /** Da chiamare con i dati sportivi appena letti: aggiorna lo stato e programma il prossimo controllo. */
    suspend fun update(ctx: Context, sport: Sport?) {
        val prefs = Prefs(ctx)
        if (!prefs.liveAlerts) {
            Alarms.cancel(ctx, Alarms.LIVE)
            prefs.liveKey = ""
            return
        }
        val now = System.currentTimeMillis()
        val m = sport?.live
        if (m != null) {
            val key = "${m.home}|${m.away}"
            val score = "${m.homeScore ?: 0}-${m.awayScore ?: 0}"
            val line = "${m.home} ${m.homeScore ?: 0} – ${m.awayScore ?: 0} ${m.away}"
            if (prefs.liveKey != key) {
                notify(ctx, "live", "Partita in diretta", 5, "È cominciata ${m.home} – ${m.away}", listOfNotNull(m.league.takeIf { it.isNotBlank() }, m.venue).joinToString(" · ").ifBlank { line })
            } else if (prefs.liveScore != score) {
                val old = prefs.liveScore.split('-').mapNotNull { it.toIntOrNull() }
                val title = when {
                    old.size == 2 && (m.homeScore ?: 0) > old[0] -> "Gol ${m.home}!"
                    old.size == 2 && (m.awayScore ?: 0) > old[1] -> "Gol ${m.away}!"
                    else -> "Punteggio aggiornato"
                }
                notify(ctx, "live", "Partita in diretta", 5, title, line)
            }
            prefs.liveKey = key
            prefs.liveScore = score
            Alarms.set(ctx, Alarms.LIVE, now + EVERY)
        } else {
            if (prefs.liveKey.isNotBlank()) {
                // Era in corso e non lo è più: è finita.
                sport?.last?.takeIf { "${it.home}|${it.away}" == prefs.liveKey && it.homeScore != null }?.let {
                    notify(ctx, "live", "Partita in diretta", 5, "Finita", "${it.home} ${it.homeScore} – ${it.awayScore} ${it.away}")
                }
                prefs.liveKey = ""
                prefs.liveScore = ""
            }
            val kickoff = sport?.next?.date?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            when {
                // Subito dopo l'orario d'inizio i dati possono tardare qualche minuto a segnarla "in corso": si insiste per un po'.
                kickoff != null && kickoff <= now && now < kickoff + 20 * 60_000 -> Alarms.set(ctx, Alarms.LIVE, now + EVERY)
                kickoff != null && kickoff > now -> Alarms.set(ctx, Alarms.LIVE, kickoff + 60_000)
                else -> Alarms.cancel(ctx, Alarms.LIVE)
            }
        }
        if (sport != null) runCatching { WidgetStore.updateSport(ctx, sport) }
    }

    /** La sveglia è scattata: si rileggono i dati in background. */
    fun poll(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<LiveWorker>().setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("live-match", ExistingWorkPolicy.REPLACE, request)
    }
}

class LiveWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val sport = runCatching { SportRepo.load(Prefs(ctx)) }.getOrNull()
        // Senza dati (rete assente) si riprova al prossimo giro invece di credere che la partita sia finita.
        if (sport == null) Alarms.set(ctx, Alarms.LIVE, System.currentTimeMillis() + 2 * 60_000L) else LiveMatch.update(ctx, sport)
        Result.success()
    }
}
