package com.brieffo.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Sveglie con cui Brieffo si fa richiamare all'ora giusta. A differenza dei lavori in background, che Android
 * rimanda anche di ore quando il telefono è a riposo, queste scattano puntuali.
 */
object Alarms {
    const val BRIEF = "com.brieffo.app.action.BRIEF"
    const val MATCH = "com.brieffo.app.action.MATCH"
    const val LEAVE = "com.brieffo.app.action.LEAVE"
    const val LIVE = "com.brieffo.app.action.LIVE"

    private fun intent(ctx: Context, action: String) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Vero se Android permette a Brieffo sveglie al minuto; altrimenti possono tardare un po'. */
    fun exact(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Programma (o sposta) la sveglia di [action] al momento [at], in millisecondi. */
    fun set(ctx: Context, action: String, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pending = intent(ctx, action)
        runCatching {
            if (exact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }.onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending) }
    }

    fun cancel(ctx: Context, action: String) = ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx, action))
}

/** Riceve le sveglie di [Alarms] e le rimette in piedi dopo un riavvio o un aggiornamento dell'app, che le cancellano. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Alarms.BRIEF -> {
                BriefWorker.runNow(ctx)
                BriefWorker.schedule(ctx)
            }
            Alarms.MATCH -> MatchReminder.show(ctx)
            Alarms.LEAVE -> LeaveReminder.show(ctx)
            Alarms.LIVE -> LiveMatch.poll(ctx)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                BriefWorker.schedule(ctx)
                MatchReminder.restore(ctx)
                LeaveReminder.restore(ctx)
                // Il prossimo controllo della partita si riprogramma rileggendo i dati.
                LiveMatch.poll(ctx)
            }
        }
    }
}
