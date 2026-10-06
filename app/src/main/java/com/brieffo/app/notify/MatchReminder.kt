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
import androidx.work.WorkManager
import com.brieffo.app.MainActivity
import com.brieffo.app.R
import com.brieffo.app.data.Match
import com.brieffo.app.data.Prefs
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Notifica che ricorda la prossima partita poco prima del fischio d'inizio. Ce n'è al massimo una in attesa. */
object MatchReminder {
    /** Quanto prima dell'inizio arriva il promemoria. */
    const val MINUTES_BEFORE = 30L

    private const val CHANNEL = "match"
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /** Identifica la partita a cui è legato il promemoria: se cambia data o orario, cambia anche la chiave. */
    fun key(m: Match) = "${pair(m)}${m.date}"

    /** Vero se [key] è il promemoria di questa stessa partita, ma con una data diversa da quella attuale. */
    fun moved(key: String, m: Match) = key.startsWith(pair(m)) && key != key(m)

    private fun pair(m: Match) = "${m.home}|${m.away}|"

    fun schedule(ctx: Context, m: Match) {
        val date = m.date ?: return
        val prefs = Prefs(ctx)
        prefs.matchReminder = key(m)
        prefs.matchReminderTitle = "${m.home} – ${m.away}"
        prefs.matchReminderText = listOfNotNull("Inizia alle ${date.format(hm)}", m.league.takeIf { it.isNotBlank() }, m.venue).joinToString(" · ")
        prefs.matchReminderAt = date.minusMinutes(MINUTES_BEFORE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Se manca meno del preavviso, la notifica parte subito.
        if (prefs.matchReminderAt <= System.currentTimeMillis()) show(ctx) else Alarms.set(ctx, Alarms.MATCH, prefs.matchReminderAt)
    }

    fun cancel(ctx: Context) {
        Alarms.cancel(ctx, Alarms.MATCH)
        clear(ctx)
    }

    /** Dopo un riavvio o un aggiornamento la sveglia va rimessa; se nel frattempo è passata l'ora, il promemoria parte adesso. */
    fun restore(ctx: Context) {
        // Le versioni precedenti usavano un lavoro in background: non serve più.
        WorkManager.getInstance(ctx).cancelUniqueWork("match-reminder")
        val prefs = Prefs(ctx)
        if (prefs.matchReminder.isBlank()) return
        val now = System.currentTimeMillis()
        when {
            prefs.matchReminderAt > now -> Alarms.set(ctx, Alarms.MATCH, prefs.matchReminderAt)
            now < prefs.matchReminderAt + MINUTES_BEFORE * 60_000 -> show(ctx)
            else -> clear(ctx)
        }
    }

    private fun clear(ctx: Context) {
        val prefs = Prefs(ctx)
        prefs.matchReminder = ""
        prefs.matchReminderAt = 0
    }

    fun show(ctx: Context) {
        val prefs = Prefs(ctx)
        val title = prefs.matchReminderTitle.ifBlank { "Partita in arrivo" }
        val text = prefs.matchReminderText
        clear(ctx)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria partite", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brief)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(2, n)
    }
}
