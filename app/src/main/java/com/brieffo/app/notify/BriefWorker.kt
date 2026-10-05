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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Prepara il brief in background e lo mostra come notifica all'ora scelta. */
class BriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.notifyEnabled) return@withContext Result.success()
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
        private const val WORK = "daily-brief"

        fun schedule(ctx: Context) {
            val prefs = Prefs(ctx)
            val wm = WorkManager.getInstance(ctx)
            // Il widget non esiste più: si ferma l'aggiornamento orario lasciato dalle versioni precedenti.
            wm.cancelUniqueWork("widget-refresh")
            if (!prefs.notifyEnabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val now = LocalDateTime.now()
            var next = now.withHour(prefs.notifyHour).withMinute(prefs.notifyMinute).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<BriefWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
