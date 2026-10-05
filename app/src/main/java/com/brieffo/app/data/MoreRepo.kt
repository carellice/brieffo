package com.brieffo.app.data

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

object AlertsRepo {
    private val types = mapOf(
        1 to "Vento", 2 to "Neve e ghiaccio", 3 to "Temporali", 4 to "Nebbia", 5 to "Caldo estremo", 6 to "Freddo estremo",
        7 to "Mareggiate", 8 to "Incendi", 9 to "Valanghe", 10 to "Pioggia", 12 to "Alluvioni", 13 to "Pioggia e alluvioni",
    )

    // "Emilia-Romagna" e "Emilia e Romagna" devono coincidere: si confrontano le prime lettere senza simboli.
    private fun norm(s: String) = s.lowercase().filter { it.isLetter() }.take(6)

    /** Allerte MeteoAlarm (Aeronautica Militare) dalla gialla in su per la regione indicata. Solo Italia. */
    fun load(place: Place): List<WxAlert> {
        if (!place.country.equals("IT", true) || place.region.isBlank()) return emptyList()
        val warnings = JSONObject(httpGet("https://feeds.meteoalarm.org/api/v1/warnings/feeds-italy")).optJSONArray("warnings") ?: return emptyList()
        val now = OffsetDateTime.now()
        val horizon = now.plusHours(36)
        val out = LinkedHashMap<String, WxAlert>()
        for (i in 0 until warnings.length()) {
            val info = warnings.getJSONObject(i).optJSONObject("alert")?.optJSONArray("info")?.optJSONObject(0) ?: continue
            val areas = info.optJSONArray("area") ?: continue
            if ((0 until areas.length()).none { norm(areas.getJSONObject(it).optString("areaDesc")) == norm(place.region) }) continue
            val onset = runCatching { OffsetDateTime.parse(info.optString("onset")) }.getOrNull()
            val expires = runCatching { OffsetDateTime.parse(info.optString("expires")) }.getOrNull()
            if (expires != null && expires.isBefore(now)) continue
            if (onset != null && onset.isAfter(horizon)) continue
            var level = 0
            var type = 0
            val params = info.optJSONArray("parameter") ?: JSONArray()
            for (j in 0 until params.length()) {
                val p = params.getJSONObject(j)
                val n = p.optString("value").substringBefore(';').trim().toIntOrNull() ?: continue
                when (p.optString("valueName")) {
                    "awareness_level" -> level = n
                    "awareness_type" -> type = n
                }
            }
            if (level < 2) continue
            val name = types[type] ?: "Maltempo"
            fun local(t: OffsetDateTime?) = t?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
            val alert = WxAlert(name, level, local(expires), local(onset?.takeIf { it.isAfter(now) }))
            if ((out[name]?.level ?: 0) < level) out[name] = alert
        }
        return out.values.sortedByDescending { it.level }
    }
}

object TravelRepo {
    /**
     * Con [near] la ricerca è limitata a circa 60 km dalla posizione: i luoghi degli impegni sono spesso
     * vaghi ("Ufficio", "Bar Mario") e senza limite verrebbero trovati dall'altra parte d'Italia.
     */
    private fun geocode(prefs: Prefs, address: String, near: Place? = null): Pair<Double, Double>? {
        val key = "geo:" + address.lowercase() + (near?.let { "@${it.name}" } ?: "")
        prefs.cached(key)?.split(',')?.let { return it[0].toDouble() to it[1].toDouble() }
        val box = near?.let { "&bounded=1&viewbox=${it.lon - 0.75},${it.lat + 0.55},${it.lon + 0.75},${it.lat - 0.55}" } ?: ""
        val r = JSONArray(httpGet("https://nominatim.openstreetmap.org/search?q=${enc(address)}&format=json&limit=1&accept-language=it$box"))
        val o = r.optJSONObject(0) ?: return null
        val lat = o.getString("lat").toDouble()
        val lon = o.getString("lon").toDouble()
        prefs.cache(key, "$lat,$lon")
        return lat to lon
    }

    private fun route(mode: String, from: Place, to: Pair<Double, Double>): Pair<Long, Double>? {
        val profile = when (mode) { "bike" -> "routed-bike"; "foot" -> "routed-foot"; else -> "routed-car" }
        val r = JSONObject(httpGet("https://routing.openstreetmap.de/$profile/route/v1/driving/${from.lon},${from.lat};${to.second},${to.first}?overview=false"))
        val route = r.optJSONArray("routes")?.optJSONObject(0) ?: return null
        return (route.getDouble("duration") / 60).roundToLong() to route.getDouble("distance") / 1000
    }

    /** Tempi stimati (senza traffico) verso il lavoro e verso il prossimo impegno di oggi che ha un luogo. */
    fun load(prefs: Prefs, from: Place, today: List<Event>): List<Travel> {
        val out = mutableListOf<Travel>()
        val mode = prefs.travelMode
        val work = prefs.workAddress
        if (work.isNotBlank()) runCatching {
            geocode(prefs, work)?.let { route(mode, from, it) }?.let { (min, km) -> out += Travel("Lavoro", min, km) }
        }
        val now = LocalDateTime.now()
        val next = today.firstOrNull { !it.allDay && it.location.isNotBlank() && it.start.isAfter(now) }
        if (next != null) runCatching {
            geocode(prefs, next.location, near = from)?.let { route(mode, from, it) }?.let { (min, km) ->
                // Cinque minuti di margine per parcheggio e imprevisti.
                out += Travel(next.title, min, km, next.start.minusMinutes(min + 5))
            }
        }
        return out
    }
}

object SportRepo {
    private const val ESPN = "https://site.api.espn.com/apis/site/v2/sports"
    private const val SPORTSDB = "https://www.thesportsdb.com/api/v1/json/3"

    /** Cerca una squadra nel catalogo ESPN; le squadre dei campionati italiani vengono per prime. */
    fun search(query: String): List<TeamRef> {
        val r = JSONObject(httpGet("https://site.web.api.espn.com/apis/search/v2?limit=8&type=team&query=${enc(query.trim())}"))
        val out = mutableListOf<TeamRef>()
        val groups = r.optJSONArray("results") ?: return out
        for (i in 0 until groups.length()) {
            val items = groups.getJSONObject(i).optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val o = items.getJSONObject(j)
                val id = o.optString("uid").substringAfterLast("t:", "")
                val league = o.optString("defaultLeagueSlug")
                if (id.isEmpty() || league.isEmpty()) continue
                out += TeamRef(o.optString("sport"), league, id, o.optString("displayName"), o.optString("subtitle"))
            }
        }
        return out.sortedByDescending { it.league.startsWith("ita.") }
    }

    private val logoCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String?>>()

    /** Stemma della squadra e logo della lega (versione per fondo scuro se [dark]); null dove ESPN non li ha. */
    fun logos(ref: TeamRef, dark: Boolean): Pair<String?, String?> = logoCache.getOrPut(ref.encode() + dark) {
        fun pick(list: JSONArray?): String? {
            list ?: return null
            val urls = (0 until list.length()).mapNotNull { list.getJSONObject(it).optString("href").takeIf { u -> u.startsWith("https") } }
            return (if (dark) urls.getOrNull(1) else null) ?: urls.firstOrNull()
        }
        val team = runCatching {
            pick(JSONObject(httpGet("$ESPN/${ref.sport}/${ref.league}/teams/${ref.id}")).getJSONObject("team").optJSONArray("logos")?.let { JSONArray().put(it.optJSONObject(0)) })
        }.getOrNull()
        val league = runCatching {
            pick(JSONObject(httpGet("$ESPN/${ref.sport}/${ref.league}/scoreboard?limit=1")).optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("logos"))
        }.getOrNull()
        team to league
    }

    fun load(prefs: Prefs): Sport? {
        val name = prefs.team
        val chosen = TeamRef.decode(prefs.teamRef)
        if (chosen == null && name.isBlank()) return null
        val key = "espn:" + name.lowercase()
        val ref = chosen
            ?: prefs.cached(key)?.let(TeamRef::decode)
            ?: runCatching { search(name).firstOrNull() }.getOrNull()?.also { prefs.cache(key, it.encode()) }
        val fromEspn = ref?.let { runCatching { espn(it) }.getOrNull() }?.takeIf { it.last != null || it.upcoming.isNotEmpty() || it.live != null }
        // Le squadre che ESPN non copre (per esempio il basket italiano) si cercano su TheSportsDB.
        return fromEspn ?: runCatching { sportsDb(prefs, name.ifBlank { chosen?.name ?: "" }) }.getOrNull()
    }

    private fun espn(ref: TeamRef): Sport {
        // Per il calcio "all" riunisce campionato, coppe e amichevoli; negli altri sport c'è un solo torneo.
        val scope = if (ref.sport == "soccer") "all" else ref.league
        val base = "$ESPN/${ref.sport}/$scope/teams/${ref.id}/schedule"
        val events = LinkedHashMap<String, JSONObject>()
        for (url in listOf(base, "$base?fixture=true")) runCatching {
            val list = JSONObject(httpGet(url)).optJSONArray("events") ?: return@runCatching
            for (i in 0 until list.length()) list.getJSONObject(i).let { events[it.optString("id")] = it }
        }

        class Parsed(val match: Match, val state: String, val friendly: Boolean, val outcome: Char?)

        val parsed = events.values.mapNotNull { e ->
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
            val sides = comp.optJSONArray("competitors") ?: return@mapNotNull null
            val list = (0 until sides.length()).map { sides.getJSONObject(it) }
            val home = list.firstOrNull { it.optString("homeAway") == "home" } ?: return@mapNotNull null
            val away = list.firstOrNull { it.optString("homeAway") == "away" } ?: return@mapNotNull null
            fun JSONObject.teamName() = optJSONObject("team")?.optString("displayName") ?: ""
            fun JSONObject.logo() = optJSONObject("team")?.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.startsWith("https") }
            // Il punteggio arriva come oggetto nel calcio e come testo in altri sport.
            fun JSONObject.points() = (optJSONObject("score")?.optString("displayValue") ?: optString("score")).toIntOrNull()
            val state = comp.optJSONObject("status")?.optJSONObject("type")?.optString("state") ?: ""
            val date = runCatching { OffsetDateTime.parse(e.optString("date")).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
            val mine = list.firstOrNull { it.optJSONObject("team")?.optString("id") == ref.id }
            val other = list.firstOrNull { it !== mine }
            val outcome = when {
                state != "post" || mine == null -> null
                mine.optBoolean("winner") -> 'V'
                other?.optBoolean("winner") == true -> 'P'
                else -> 'N'
            }
            val league = e.optJSONObject("league")
            Parsed(
                Match(
                    home.teamName(), away.teamName(),
                    home.points().takeIf { state != "pre" }, away.points().takeIf { state != "pre" },
                    date, league?.optString("abbreviation") ?: ref.subtitle,
                    home.logo(), away.logo(), comp.optJSONObject("venue")?.optString("fullName")?.takeIf { it.isNotBlank() },
                    live = state == "in",
                ),
                state, league?.optString("slug") == "club.friendly", outcome,
            )
        }.filter { it.match.date != null }.sortedBy { it.match.date }

        val played = parsed.filter { it.state == "post" }
        val official = played.filter { !it.friendly }
        val now = LocalDateTime.now()

        var logo: String? = null
        var standing: String? = null
        runCatching {
            val t = JSONObject(httpGet("$ESPN/${ref.sport}/${ref.league}/teams/${ref.id}")).getJSONObject("team")
            logo = t.optJSONArray("logos")?.optJSONObject(0)?.optString("href")?.takeIf { it.startsWith("https") }
            val record = t.optJSONObject("record")?.optJSONArray("items")?.optJSONObject(0)
            val stats = record?.optJSONArray("stats")
            val values = HashMap<String, Int>()
            if (stats != null) for (i in 0 until stats.length()) stats.getJSONObject(i).let { values[it.optString("name")] = it.optDouble("value", 0.0).toInt() }
            val rank = values["rank"]?.takeIf { it > 0 }
            standing = when {
                ref.sport == "soccer" && rank != null ->
                    "${rank}º in ${ref.subtitle} · ${values["points"] ?: 0} punti · ${values["wins"] ?: 0}V ${values["ties"] ?: 0}N ${values["losses"] ?: 0}P"
                else -> record?.optString("summary")?.takeIf { it.isNotBlank() }?.let { "${ref.subtitle} · bilancio $it" }
            }
        }

        val recent = official.filter { it.outcome != null }.takeLast(5)
        return Sport(
            team = ref.name,
            // L'ultimo risultato è quello ufficiale più recente; le amichevoli valgono solo se non c'è altro.
            last = (official.lastOrNull() ?: played.lastOrNull())?.match,
            upcoming = parsed.filter { it.state == "pre" && it.match.date!!.isAfter(now.minusHours(3)) }.take(3).map { it.match },
            live = parsed.firstOrNull { it.state == "in" }?.match,
            logo = logo,
            standing = standing,
            form = recent.map { it.outcome!! },
            formMatches = recent.map { it.match },
            source = "ESPN",
        )
    }

    private fun sportsDbMatch(o: JSONObject?): Match? {
        o ?: return null
        val date = runCatching {
            LocalDateTime.parse(o.getString("strTimestamp").take(19)).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }.getOrNull()
        return Match(
            o.optString("strHomeTeam"), o.optString("strAwayTeam"),
            o.optString("intHomeScore").toIntOrNull(), o.optString("intAwayScore").toIntOrNull(),
            date, o.optString("strLeague"),
        )
    }

    private fun sportsDb(prefs: Prefs, query: String): Sport? {
        if (query.isBlank()) return null
        val key = "team:" + query.lowercase()
        val cached = prefs.cached(key) ?: run {
            val t = JSONObject(httpGet("$SPORTSDB/searchteams.php?t=${enc(query)}")).optJSONArray("teams")?.optJSONObject(0) ?: return null
            (t.getString("idTeam") + "|" + t.getString("strTeam")).also { prefs.cache(key, it) }
        }
        val (id, name) = cached.split('|', limit = 2)
        val last = runCatching { sportsDbMatch(JSONObject(httpGet("$SPORTSDB/eventslast.php?id=$id")).optJSONArray("results")?.optJSONObject(0)) }.getOrNull()
        val next = runCatching { sportsDbMatch(JSONObject(httpGet("$SPORTSDB/eventsnext.php?id=$id")).optJSONArray("events")?.optJSONObject(0)) }.getOrNull()
        return Sport(name, last, listOfNotNull(next), source = "TheSportsDB")
    }
}

object OccasionsRepo {
    fun contactsGranted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Santi del giorno dalla pagina di Wikipedia dedicata alla data (sezione "Religiose"). */
    fun saints(date: LocalDate = LocalDate.now()): List<String> {
        val day = if (date.dayOfMonth == 1) "1º" else date.dayOfMonth.toString()
        val page = day + "_" + date.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
        val text = JSONObject(httpGet("https://it.wikipedia.org/w/api.php?action=parse&page=${enc(page)}&prop=wikitext&format=json"))
            .getJSONObject("parse").getJSONObject("wikitext").getString("*")
        val section = text.substringAfter("=== Religiose ===", "").substringBefore("\n==")
        return section.lineSequence()
            .filter { it.startsWith("* San") || it.startsWith("* Sant") }
            .map { line ->
                line.removePrefix("* ")
                    .replace(Regex("<ref.*?(</ref>|/>)"), "")
                    .replace(Regex("\\[\\[(?:[^\\]|]*\\|)?([^\\]]*)]]"), "$1")
                    .replace("''", "")
                    .substringBefore(',').substringBefore(" (").trim()
            }
            .filter { it.isNotEmpty() }
            .take(3).toList()
    }

    fun holiday(today: LocalDate = LocalDate.now()): Holiday? {
        val list = JSONArray(httpGet("https://date.nager.at/api/v3/NextPublicHolidays/IT"))
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val d = LocalDate.parse(o.getString("date"))
            if (!d.isBefore(today)) return Holiday(o.getString("localName"), d)
        }
        return null
    }

    /** Compleanni dei contatti nei prossimi 14 giorni, oggi compreso. */
    fun birthdays(ctx: Context, today: LocalDate = LocalDate.now()): List<Birthday> {
        if (!contactsGranted(ctx)) return emptyList()
        val out = mutableListOf<Birthday>()
        val cols = arrayOf(ContactsContract.Data.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE)
        val where = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ${ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY}"
        ctx.contentResolver.query(ContactsContract.Data.CONTENT_URI, cols, where, arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE), null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                // Formati possibili: "1990-05-12" oppure "--05-12" quando l'anno non è noto.
                val raw = c.getString(1)?.take(10) ?: continue
                val monthDay = raw.takeLast(5).split('-').mapNotNull { it.toIntOrNull() }
                if (monthDay.size != 2) continue
                val year = raw.take(4).toIntOrNull()
                val next = runCatching {
                    LocalDate.of(today.year, monthDay[0], monthDay[1]).let { if (it.isBefore(today)) it.plusYears(1) else it }
                }.getOrNull() ?: continue
                if (next.isBefore(today.plusDays(15))) out += Birthday(name, next, year?.let { next.year - it })
            }
        }
        return out.distinctBy { it.name to it.date }.sortedBy { it.date }
    }

    fun nextAlarm(ctx: Context): LocalDateTime? =
        ctx.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
}
