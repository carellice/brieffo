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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.brieffo.app.MainActivity
import com.brieffo.app.R
import com.brieffo.app.data.Match
import com.brieffo.app.data.Prefs
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Notifica che ricorda la prossima partita poco prima del fischio d'inizio. Ce n'è al massimo una in attesa. */
class MatchReminder(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Prefs(ctx).matchReminder = ""
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Promemoria partite", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = inputData.getString("text") ?: ""
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brief)
            .setContentTitle(inputData.getString("title") ?: "Partita in arrivo")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(2, n)
        return Result.success()
    }

    companion object {
        /** Quanto prima dell'inizio arriva il promemoria. */
        const val MINUTES_BEFORE = 30L

        private const val CHANNEL = "match"
        private const val WORK = "match-reminder"
        private val hm = DateTimeFormatter.ofPattern("HH:mm")

        /** Identifica la partita a cui è legato il promemoria: se cambia data o orario, cambia anche la chiave. */
        fun key(m: Match) = "${pair(m)}${m.date}"

        /** Vero se [key] è il promemoria di questa stessa partita, ma con una data diversa da quella attuale. */
        fun moved(key: String, m: Match) = key.startsWith(pair(m)) && key != key(m)

        private fun pair(m: Match) = "${m.home}|${m.away}|"

        fun schedule(ctx: Context, m: Match) {
            val date = m.date ?: return
            // Se manca meno del preavviso, la notifica parte subito.
            val delay = Duration.between(LocalDateTime.now(), date.minusMinutes(MINUTES_BEFORE)).toMillis().coerceAtLeast(0)
            val text = listOfNotNull("Inizia alle ${date.format(hm)}", m.league.takeIf { it.isNotBlank() }, m.venue).joinToString(" · ")
            val request = OneTimeWorkRequestBuilder<MatchReminder>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to "${m.home} – ${m.away}", "text" to text))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
            Prefs(ctx).matchReminder = key(m)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
            Prefs(ctx).matchReminder = ""
        }
    }
}
