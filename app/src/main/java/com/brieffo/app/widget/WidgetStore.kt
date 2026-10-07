package com.brieffo.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import com.brieffo.app.data.BriefState
import com.brieffo.app.data.CardKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Ciò che i widget mostrano: una fotografia dell'ultimo brief, salvata sul telefono per disegnarli senza rete. */
data class WidgetData(
    val updatedAt: Long,
    val summary: String,
    val weather: WeatherData?,
    val match: MatchData?,
    val team: String,
    val agendaGranted: Boolean,
    val events: List<EventData>,
)

data class WeatherData(val place: String, val temp: Int, val code: Int, val isDay: Boolean, val min: Int?, val max: Int?, val rain: Int?)

data class MatchData(
    val home: String, val away: String, val date: LocalDateTime?, val league: String,
    val live: Boolean, val homeScore: Int?, val awayScore: Int?, val venue: String?,
)

data class EventData(val title: String, val start: LocalDateTime, val allDay: Boolean)

object WidgetStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /** Stemma salvato della squadra di casa ([home] vero) o di quella ospite. */
    fun logo(ctx: Context, home: Boolean) = File(ctx.filesDir, if (home) "widget_home.png" else "widget_away.png")

    /** Salva i dati del brief per i widget, scarica gli stemmi se sono cambiati e ridisegna tutto. */
    suspend fun update(ctx: Context, s: BriefState) {
        val sp = prefs(ctx)
        val old = runCatching { JSONObject(sp.getString("data", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val o = JSONObject().put("updatedAt", System.currentTimeMillis()).put("summary", s.summary)
        if (s.shows(CardKeys.WEATHER)) s.weather?.let { w ->
            o.put("weather", JSONObject().put("place", w.place).put("temp", Math.round(w.temp)).put("code", w.code).put("isDay", w.isDay)
                .put("min", w.today?.tMin?.let { Math.round(it) }).put("max", w.today?.tMax?.let { Math.round(it) }).put("rain", w.today?.rainProb))
        }
        val sport = s.sport.takeIf { s.shows(CardKeys.SPORT) }
        o.put("team", sport?.team ?: "")
        val m = sport?.live ?: sport?.next
        var homeLogo = ""
        var awayLogo = ""
        if (m != null) {
            homeLogo = m.homeLogo ?: ""
            awayLogo = m.awayLogo ?: ""
            o.put("match", JSONObject().put("home", m.home).put("away", m.away).put("date", m.date?.let(::millis)).put("league", m.league)
                .put("live", m.live).put("homeScore", m.homeScore).put("awayScore", m.awayScore).put("venue", m.venue))
        }
        o.put("homeLogo", homeLogo).put("awayLogo", awayLogo)
        o.put("agendaGranted", s.calendarGranted && s.shows(CardKeys.AGENDA))
        val now = LocalDateTime.now()
        o.put("events", JSONArray((s.today.filter { it.allDay || it.end.isAfter(now) } + s.tomorrow).take(8).map {
            JSONObject().put("title", it.title).put("start", millis(it.start)).put("allDay", it.allDay)
        }))
        sp.edit { putString("data", o.toString()) }

        if (homeLogo != old.optString("homeLogo") || !logo(ctx, true).exists()) saveLogo(ctx, homeLogo, logo(ctx, true))
        if (awayLogo != old.optString("awayLogo") || !logo(ctx, false).exists()) saveLogo(ctx, awayLogo, logo(ctx, false))
        Widgets.redraw(ctx)
    }

    private suspend fun saveLogo(ctx: Context, url: String, file: File) {
        file.delete()
        if (url.isBlank()) return
        runCatching {
            val result = ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(url).size(192).allowHardware(false).build())
            val bitmap = result.drawable?.toBitmap() ?: return
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    fun load(ctx: Context): WidgetData? {
        val o = runCatching { JSONObject(prefs(ctx).getString("data", null) ?: return null) }.getOrNull() ?: return null
        fun JSONObject.intOrNull(k: String) = if (isNull(k) || !has(k)) null else optInt(k)
        val events = o.optJSONArray("events") ?: JSONArray()
        return WidgetData(
            updatedAt = o.optLong("updatedAt"),
            summary = o.optString("summary"),
            weather = o.optJSONObject("weather")?.let {
                WeatherData(it.optString("place"), it.optInt("temp"), it.optInt("code"), it.optBoolean("isDay", true), it.intOrNull("min"), it.intOrNull("max"), it.intOrNull("rain"))
            },
            match = o.optJSONObject("match")?.let {
                MatchData(
                    it.optString("home"), it.optString("away"), if (it.isNull("date") || !it.has("date")) null else time(it.optLong("date")),
                    it.optString("league"), it.optBoolean("live"), it.intOrNull("homeScore"), it.intOrNull("awayScore"),
                    it.optString("venue").takeIf { v -> v.isNotBlank() && !it.isNull("venue") },
                )
            },
            team = o.optString("team"),
            agendaGranted = o.optBoolean("agendaGranted"),
            events = (0 until events.length()).map { i ->
                events.getJSONObject(i).let { EventData(it.optString("title"), time(it.optLong("start")), it.optBoolean("allDay")) }
            },
        )
    }
}
