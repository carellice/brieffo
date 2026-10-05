package com.brieffo.app.data

import android.Manifest
import android.accounts.Account
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

object CalendarRepo {
    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Eventi di oggi e di domani, in quest'ordine. */
    fun load(ctx: Context): Pair<List<Event>, List<Event>> {
        if (!granted(ctx)) return emptyList<Event>() to emptyList()
        val ids = selected(ctx).map { it.id }
        if (ids.isEmpty()) return emptyList<Event>() to emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toString()).appendPath(to.toString()).build()
        val cols = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val todayEvents = mutableListOf<Event>()
        val tomorrowEvents = mutableListOf<Event>()
        // Solo i calendari scelti, e niente inviti rifiutati.
        val where = "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",")}) AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        ctx.contentResolver.query(uri, cols, where, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(3) == 1
                // Gli eventi "tutto il giorno" sono salvati a mezzanotte UTC, non nel fuso locale.
                val z = if (allDay) ZoneOffset.UTC else zone
                val start = Instant.ofEpochMilli(c.getLong(1)).atZone(z).toLocalDateTime()
                val end = Instant.ofEpochMilli(c.getLong(2)).atZone(z).toLocalDateTime()
                val e = Event(
                    title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(Senza titolo)",
                    start = start, end = end, allDay = allDay,
                    location = c.getString(4) ?: "",
                    color = c.getInt(5),
                )
                val startDay = start.toLocalDate()
                val lastDay = (if (allDay) end.minusSeconds(1) else end).toLocalDate()
                if (!startDay.isAfter(today) && !lastDay.isBefore(today)) todayEvents += e
                else if (startDay == today.plusDays(1)) tomorrowEvents += e
            }
        }
        return todayEvents to tomorrowEvents
    }

    /** Tutti i calendari presenti sul telefono. */
    fun calendars(ctx: Context): List<CalendarInfo> {
        if (!granted(ctx)) return emptyList()
        val out = mutableListOf<CalendarInfo>()
        // Quali calendari hanno almeno un evento salvato sul telefono, a prescindere dall'interruttore di sincronizzazione.
        val withEvents = HashSet<Long>()
        runCatching {
            ctx.contentResolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events.CALENDAR_ID), null, null, null)?.use { c ->
                while (c.moveToNext()) withEvents += c.getLong(0)
            }
        }
        val cols = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val id = c.getLong(0)
                out += CalendarInfo(id, name, c.getString(2) ?: "", c.getString(6) ?: "", c.getInt(3), c.getInt(4) == 1, c.getInt(5) == 1, id in withEvents)
            }
        }
        return out
    }

    fun canWrite(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Accende la sincronizzazione di un calendario e chiede subito ad Android di scaricarne gli eventi.
     * È lo stesso interruttore "Sincronizzazione" di Google Calendar; gli eventi arrivano dopo qualche istante.
     */
    fun enableSync(ctx: Context, c: CalendarInfo): Boolean {
        if (!canWrite(ctx)) return false
        return runCatching {
            val values = ContentValues().apply { put(CalendarContract.Calendars.SYNC_EVENTS, 1) }
            val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, c.id)
            val updated = ctx.contentResolver.update(uri, values, null, null) > 0
            if (c.account.isNotBlank() && c.accountType.isNotBlank()) {
                val extras = Bundle().apply {
                    putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                    putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
                }
                ContentResolver.requestSync(Account(c.account, c.accountType), CalendarContract.AUTHORITY, extras)
            }
            updated
        }.getOrDefault(false)
    }

    /** Senza una scelta esplicita vale quella di Google Calendar: visibile e sincronizzato. */
    fun isSelected(c: CalendarInfo, on: Set<String>, off: Set<String>) = when (c.id.toString()) {
        in on -> true
        in off -> false
        else -> c.visible && c.synced
    }

    fun selected(ctx: Context): List<CalendarInfo> {
        val prefs = Prefs(ctx)
        val on = prefs.calendarsOn
        val off = prefs.calendarsOff
        return calendars(ctx).filter { isSelected(it, on, off) }
    }
}

object HealthRepo {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    suspend fun load(ctx: Context): HealthState {
        if (HealthConnectClient.getSdkStatus(ctx) != HealthConnectClient.SDK_AVAILABLE) return HealthState.Unavailable
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        if (granted.none { it in permissions }) return HealthState.NeedsPermission

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val dayStart = LocalDate.now().atStartOfDay(zone).toInstant()
        val sleepStart = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone).toInstant()

        // Un aggregate per metrica: se manca un solo permesso, le altre continuano a funzionare.
        suspend fun <T : Any> agg(metric: AggregateMetric<T>, from: Instant): T? = runCatching {
            client.aggregate(AggregateRequest(setOf(metric), TimeRangeFilter.between(from, now)))[metric]
        }.getOrNull()

        return HealthState.Data(
            Health(
                steps = agg(StepsRecord.COUNT_TOTAL, dayStart),
                distanceKm = agg(DistanceRecord.DISTANCE_TOTAL, dayStart)?.inKilometers,
                calories = agg(TotalCaloriesBurnedRecord.ENERGY_TOTAL, dayStart)?.inKilocalories,
                sleepMinutes = agg(SleepSessionRecord.SLEEP_DURATION_TOTAL, sleepStart)?.toMinutes(),
                avgHr = agg(HeartRateRecord.BPM_AVG, dayStart),
                minHr = agg(HeartRateRecord.BPM_MIN, dayStart),
            )
        )
    }
}

object UsageRepo {
    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun load(ctx: Context): UsageState {
        if (!granted(ctx)) return UsageState.NeedsPermission
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(e.packageName)?.let { total.merge(e.packageName, e.timeStamp - it, Long::plus) }
            }
        }
        open.forEach { (pkg, since) -> total.merge(pkg, now - since, Long::plus) }

        val pm = ctx.packageManager
        val launcher = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
        val apps = total.filterKeys { it != launcher }
            .map { (pkg, ms) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
                AppUsage(label, ms / 60_000)
            }
            .sortedByDescending { it.minutes }
        return UsageState.Data(Usage(apps.sumOf { it.minutes }, apps.filter { it.minutes > 0 }.take(4)))
    }
}

object DeviceRepo {
    fun battery(ctx: Context): Battery? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return Battery(level * 100 / scale, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    fun moon(at: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)): Moon {
        val synodic = 29.530588853
        val reference = LocalDateTime.of(2000, 1, 6, 18, 14) // luna nuova di riferimento (UTC)
        val days = Duration.between(reference, at).toMinutes() / 1440.0
        val phase = ((days % synodic) + synodic) % synodic / synodic
        val illumination = ((1 - cos(2 * PI * phase)) / 2 * 100).roundToInt()
        val names = listOf(
            "Luna nuova", "Falce crescente", "Primo quarto", "Gibbosa crescente",
            "Luna piena", "Gibbosa calante", "Ultimo quarto", "Falce calante",
        )
        val name = names[(phase * 8 + 0.5).toInt() % 8]
        return Moon(phase, name, illumination)
    }
}
