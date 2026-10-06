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
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.brieffo.app.MainActivity
import com.brieffo.app.R
import com.brieffo.app.data.BriefState
import com.brieffo.app.data.CalendarRepo
import com.brieffo.app.data.Daypart
import com.brieffo.app.data.HealthRepo
import com.brieffo.app.data.HealthState
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.Summary
import com.brieffo.app.data.WeatherRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/** Prepara il brief in background e lo mostra come notifica all'ora scelta. */
class BriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.notifyEnabled && !inputData.getBoolean("test", false)) return@withContext Result.success()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.success()
        }
        val state = buildState(ctx)
        val text = state.summary

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Brief giornaliero", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = state.daypart.greeting + (if (prefs.name.isNotBlank()) ", ${prefs.name}" else "") + " · " + state.daypart.title
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brief)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(1, n)
        Result.success()
    }

    /** Sulle versioni di Android precedenti alla 12 un lavoro urgente deve mostrare una notifica mentre gira. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Brief giornaliero", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_brief).setContentTitle("Preparo il brief…").build()
        return ForegroundInfo(3, n)
    }

    companion object {
        /** Versione leggera del brief (meteo, agenda, salute) per la notifica, senza interfaccia. */
        suspend fun buildState(ctx: Context): BriefState {
            val prefs = Prefs(ctx)
            val now = LocalDateTime.now()
            val (today, tomorrow) = runCatching { CalendarRepo.load(ctx) }.getOrDefault(emptyList<com.brieffo.app.data.Event>() to emptyList())
            val state = BriefState(
                loading = false,
                daypart = Daypart.of(now.hour),
                now = now,
                name = prefs.name,
                stepGoal = prefs.stepGoal,
                hidden = prefs.hidden,
                weather = runCatching { WeatherRepo.resolvePlace(ctx, prefs)?.let(WeatherRepo::fetch) }.getOrNull(),
                calendarGranted = CalendarRepo.granted(ctx),
                today = today,
                tomorrow = tomorrow,
                health = runCatching { HealthRepo.load(ctx) }.getOrDefault(HealthState.Unavailable),
            )
            return state.copy(summary = Summary.rules(state))
        }

        private const val CHANNEL = "brief"

        /** Prossimo momento in cui arriverà il brief giornaliero. */
        fun nextTime(prefs: Prefs): LocalDateTime {
            val now = LocalDateTime.now()
            val next = now.withHour(prefs.notifyHour).withMinute(prefs.notifyMinute).withSecond(0).withNano(0)
            // Un minuto di margine: la sveglia scatta proprio a quell'ora e il prossimo giro è quello di domani.
            return if (next.isAfter(now.plusMinutes(1))) next else next.plusDays(1)
        }

        /**
         * Programma il brief giornaliero all'ora scelta. Si usa una sveglia e non un lavoro periodico:
         * quello partiva a orari sbagliati (anche di notte) e a telefono fermo poteva tardare di ore.
         */
        fun schedule(ctx: Context) {
            val prefs = Prefs(ctx)
            val wm = WorkManager.getInstance(ctx)
            // Residui delle versioni precedenti: l'aggiornamento orario del widget e il vecchio lavoro periodico.
            wm.cancelUniqueWork("widget-refresh")
            wm.cancelUniqueWork("daily-brief")
            if (!prefs.notifyEnabled) {
                Alarms.cancel(ctx, Alarms.BRIEF)
                return
            }
            Alarms.set(ctx, Alarms.BRIEF, nextTime(prefs).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }

        /** Prepara e mostra subito la notifica del brief; con [test] lo fa anche se quella giornaliera è spenta. */
        fun runNow(ctx: Context, test: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<BriefWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf("test" to test))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("brief-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
