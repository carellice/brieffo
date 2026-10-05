package com.brieffo.app.data

import java.time.LocalDate
import java.time.LocalDateTime

enum class Daypart(val title: String, val greeting: String) {
    MORNING("Brief del mattino", "Buongiorno"),
    MIDDAY("Brief di metà giornata", "Buon pomeriggio"),
    EVENING("Brief della sera", "Buonasera"),
    NIGHT("Brief della notte", "Buonanotte");

    companion object {
        fun of(hour: Int) = when (hour) {
            in 5..11 -> MORNING
            in 12..17 -> MIDDAY
            in 18..22 -> EVENING
            else -> NIGHT
        }
    }
}

data class Place(val lat: Double, val lon: Double, val name: String, val region: String = "", val country: String = "")

data class HourWx(val time: LocalDateTime, val temp: Double, val code: Int, val rainProb: Int, val isDay: Boolean)

data class DayWx(val date: LocalDate, val code: Int, val tMax: Double, val tMin: Double, val rainProb: Int)

data class Weather(
    val place: String,
    val temp: Double,
    val feels: Double,
    val code: Int,
    val isDay: Boolean,
    val wind: Double,
    val humidity: Int,
    val uvMax: Double,
    val sunrise: String,
    val sunset: String,
    val hourly: List<HourWx>,
    val daily: List<DayWx>,
    val aqi: Int?,
    val pm25: Double?,
    val pollen: List<Pollen> = emptyList(),
) {
    val today get() = daily.firstOrNull()
    val tomorrow get() = daily.getOrNull(1)
}

data class Event(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String,
    val color: Int,
)

/**
 * Un calendario presente sul telefono. [synced] è l'interruttore di sincronizzazione di Android,
 * [hasEvents] dice se sul dispositivo ci sono davvero suoi eventi.
 */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
    val synced: Boolean,
    val hasEvents: Boolean,
) {
    /** Vero quando non c'è nulla da leggere: sincronizzazione spenta e nessun evento salvato. */
    val empty get() = !synced && !hasEvents
}

data class Health(
    val steps: Long?,
    val distanceKm: Double?,
    val calories: Double?,
    val sleepMinutes: Long?,
    val avgHr: Long?,
    val minHr: Long?,
)

sealed interface HealthState {
    data object Loading : HealthState
    data object Unavailable : HealthState
    data object NeedsPermission : HealthState
    data class Data(val health: Health) : HealthState
}

data class AppUsage(val label: String, val minutes: Long)

data class Usage(val totalMinutes: Long, val top: List<AppUsage>)

sealed interface UsageState {
    data object Loading : UsageState
    data object NeedsPermission : UsageState
    data class Data(val usage: Usage) : UsageState
}

/** Fonte di notizie aggiunta dall'utente: un feed RSS o Atom. */
data class Feed(val name: String, val url: String, val enabled: Boolean = true)

data class NewsItem(val title: String, val topic: String, val link: String, val published: LocalDateTime?, val image: String? = null)

data class Market(val label: String, val value: String, val changePct: Double?)

data class OnThisDay(val year: Int, val text: String)

/** [phase] va da 0 (luna nuova) a 0,5 (piena) a 1. */
data class Moon(val phase: Double, val name: String, val illumination: Int)

data class Battery(val percent: Int, val charging: Boolean)

data class Pollen(val name: String, val grains: Double) {
    val level get() = when {
        grains < 10 -> "basso"
        grains < 50 -> "medio"
        grains < 100 -> "alto"
        else -> "molto alto"
    }
}

/** Allerta meteo regionale; level 2 = gialla, 3 = arancione, 4 = rossa. */
data class WxAlert(val type: String, val level: Int, val until: LocalDateTime?, val from: LocalDateTime? = null) {
    val color get() = when (level) { 2 -> "gialla"; 3 -> "arancione"; else -> "rossa" }
}

data class Travel(val label: String, val minutes: Long, val km: Double, val leaveBy: LocalDateTime? = null)

data class Match(
    val home: String,
    val away: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val date: LocalDateTime?,
    val league: String,
    val homeLogo: String? = null,
    val awayLogo: String? = null,
    val venue: String? = null,
    val live: Boolean = false,
)

/** Squadra scelta dall'utente nel catalogo ESPN. */
data class TeamRef(val sport: String, val league: String, val id: String, val name: String, val subtitle: String) {
    fun encode() = listOf(sport, league, id, name, subtitle).joinToString("|")

    companion object {
        fun decode(s: String): TeamRef? = s.split('|').takeIf { it.size == 5 }?.let { TeamRef(it[0], it[1], it[2], it[3], it[4]) }
    }
}

data class Sport(
    val team: String,
    val last: Match?,
    val upcoming: List<Match> = emptyList(),
    val live: Match? = null,
    val logo: String? = null,
    val standing: String? = null,
    /** Esiti delle ultime partite ufficiali, dalla più vecchia alla più recente: 'V', 'N' o 'P'. */
    val form: List<Char> = emptyList(),
    /** Le partite a cui si riferisce [form], nello stesso ordine. */
    val formMatches: List<Match> = emptyList(),
    val source: String = "",
) {
    val next get() = upcoming.firstOrNull()
}

data class Birthday(val name: String, val date: LocalDate, val age: Int?)

data class Holiday(val name: String, val date: LocalDate)

data class Occasions(val saints: List<String> = emptyList(), val birthdays: List<Birthday> = emptyList(), val holiday: Holiday? = null, val contactsGranted: Boolean = false)

/** Schede che si possono nascondere dalle impostazioni. */
object CardKeys {
    const val WEATHER = "weather"; const val ALERTS = "alerts"; const val POLLEN = "pollen"
    const val AGENDA = "agenda"
    const val HEALTH = "health"; const val USAGE = "usage"; const val NEWS = "news"
    const val MARKETS = "markets"; const val TRAVEL = "travel"; const val SPORT = "sport"
    const val OCCASIONS = "occasions"; const val EXTRAS = "extras"

    /** Schede che si possono riordinare, nell'ordine proposto a chi inizia a sistemarle a mano. */
    val sortable = listOf(WEATHER, AGENDA, TRAVEL, HEALTH, USAGE, NEWS, SPORT, MARKETS, OCCASIONS, EXTRAS)

    /** Ordine completo a partire da quello salvato: via le chiavi sconosciute, in coda le schede che mancano. */
    fun ordered(saved: List<String>) = saved.filter { it in sortable }.distinct().let { it + (sortable - it.toSet()) }

    val labels = linkedMapOf(
        WEATHER to "Meteo", ALERTS to "Allerte meteo", POLLEN to "Pollini",
        AGENDA to "Agenda",
        TRAVEL to "Tempi di spostamento", HEALTH to "Salute e attività", USAGE to "Tempo di utilizzo",
        NEWS to "Notizie", SPORT to "La tua squadra", MARKETS to "Mercati",
        OCCASIONS to "Ricorrenze (santi, compleanni, festività)", EXTRAS to "In breve (luna, batteria, sveglia)",
    )
}

data class BriefState(
    val loading: Boolean = true,
    val daypart: Daypart,
    val now: LocalDateTime,
    val name: String = "",
    val stepGoal: Int = 8000,
    val summary: String = "",
    val summaryByAi: Boolean = false,
    /** Vero mentre Gemini sta scrivendo il riepilogo. */
    val summaryLoading: Boolean = false,
    /** Vero se il riepilogo di Gemini va mostrato chiuso finché l'utente non lo apre. */
    val summaryCollapsed: Boolean = false,
    val aiError: String? = null,
    val weather: Weather? = null,
    val weatherLoaded: Boolean = false,
    val calendarGranted: Boolean = false,
    val calendars: List<String> = emptyList(),
    val calendarsOff: List<String> = emptyList(),
    val today: List<Event> = emptyList(),
    val tomorrow: List<Event> = emptyList(),
    val health: HealthState = HealthState.Loading,
    val usage: UsageState = UsageState.Loading,
    val news: List<NewsItem> = emptyList(),
    val newsConfigured: Boolean = false,
    val markets: List<Market> = emptyList(),
    val onThisDay: OnThisDay? = null,
    val moon: Moon? = null,
    val battery: Battery? = null,
    val hidden: Set<String> = emptySet(),
    /** Ordine delle schede scelto dall'utente; vuoto = segue il momento della giornata. */
    val cardOrder: List<String> = emptyList(),
    val alerts: List<WxAlert> = emptyList(),
    val travel: List<Travel> = emptyList(),
    val travelConfigured: Boolean = false,
    val sport: Sport? = null,
    val sportConfigured: Boolean = false,
    val occasions: Occasions = Occasions(),
    val nextAlarm: LocalDateTime? = null,
) {
    fun shows(key: String) = key !in hidden
}

fun wxText(code: Int) = when (code) {
    0 -> "sereno"
    1 -> "poco nuvoloso"
    2 -> "parzialmente nuvoloso"
    3 -> "coperto"
    45, 48 -> "nebbia"
    in 51..57 -> "pioviggine"
    in 61..67 -> "pioggia"
    in 71..77 -> "neve"
    in 80..82 -> "rovesci"
    85, 86 -> "rovesci di neve"
    in 95..99 -> "temporali"
    else -> "variabile"
}

fun aqiText(aqi: Int) = when {
    aqi <= 20 -> "ottima"
    aqi <= 40 -> "buona"
    aqi <= 60 -> "discreta"
    aqi <= 80 -> "scarsa"
    else -> "pessima"
}

fun durationText(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}
