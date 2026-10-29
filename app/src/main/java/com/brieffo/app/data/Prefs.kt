package com.brieffo.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import java.time.LocalDate
import org.json.JSONObject

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("brieffo", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit { putString("name", v.trim()) }

    /** Città impostata a mano; vuota = usa la posizione del telefono. */
    var city: String
        get() = sp.getString("city", "") ?: ""
        set(v) = sp.edit { putString("city", v.trim()) }

    /** Chiave di Google AI Studio: serve solo per la voce di Gemini. */
    var geminiKey: String
        get() = sp.getString("geminiKey", "") ?: ""
        set(v) = sp.edit { putString("geminiKey", v.trim()) }

    /** ID dell'account Cloudflare e token con il permesso Workers AI: insieme fanno scrivere il riepilogo all'IA. */
    var cloudflareAccount: String
        get() = sp.getString("cloudflareAccount", "") ?: ""
        set(v) = sp.edit { putString("cloudflareAccount", v.trim()) }

    var cloudflareToken: String
        get() = sp.getString("cloudflareToken", "") ?: ""
        set(v) = sp.edit { putString("cloudflareToken", v.trim()) }

    /** Vero se account e token di Cloudflare ci sono entrambi. */
    val aiReady: Boolean get() = cloudflareAccount.isNotBlank() && cloudflareToken.isNotBlank()

    var stepGoal: Int
        get() = sp.getInt("stepGoal", 8000)
        set(v) = sp.edit { putInt("stepGoal", v.coerceIn(1000, 50000)) }

    /** Fonti di notizie scelte dall'utente. L'app non ne include nessuna: all'inizio l'elenco è vuoto. */
    var feeds: List<Feed>
        get() = runCatching {
            val a = JSONArray(sp.getString("feeds", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Feed(o.getString("name"), o.getString("url"), o.optBoolean("enabled", true)) } }
        }.getOrDefault(emptyList())
        set(v) = sp.edit {
            putString("feeds", JSONArray(v.map { JSONObject().put("name", it.name).put("url", it.url).put("enabled", it.enabled) }).toString())
        }

    var notifyEnabled: Boolean
        get() = sp.getBoolean("notifyEnabled", true)
        set(v) = sp.edit { putBoolean("notifyEnabled", v) }

    var notifyHour: Int
        get() = sp.getInt("notifyHour", 7)
        set(v) = sp.edit { putInt("notifyHour", v) }

    var notifyMinute: Int
        get() = sp.getInt("notifyMinute", 30)
        set(v) = sp.edit { putInt("notifyMinute", v) }

    /** Falso finché l'utente non ha completato la presentazione iniziale. */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit { putBoolean("onboarded", v) }

    var askedPermissions: Boolean
        get() = sp.getBoolean("askedPermissions", false)
        set(v) = sp.edit { putBoolean("askedPermissions", v) }

    /** Ultima posizione risolta, riusata quando GPS o rete non rispondono. */
    var lastPlace: Place?
        get() {
            val n = sp.getString("placeName", null) ?: return null
            return Place(
                sp.getFloat("placeLat", 0f).toDouble(), sp.getFloat("placeLon", 0f).toDouble(), n,
                sp.getString("placeRegion", "") ?: "", sp.getString("placeCountry", "") ?: "",
            )
        }
        set(v) = sp.edit {
            if (v == null) remove("placeName") else {
                putString("placeName", v.name)
                putFloat("placeLat", v.lat.toFloat())
                putFloat("placeLon", v.lon.toFloat())
                putString("placeRegion", v.region)
                putString("placeCountry", v.country)
            }
        }

    /** Schede nascoste dall'utente (chiavi di CardKeys). */
    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("hidden", v) }

    /** Ordine delle schede scelto dall'utente (chiavi di CardKeys); vuoto = segue il momento della giornata. */
    var cardOrder: List<String>
        get() = (sp.getString("cardOrder", "") ?: "").split(',').filter { it.isNotBlank() }
        set(v) = sp.edit { putString("cardOrder", v.joinToString(",")) }

    /** Calendari accesi o spenti a mano dall'utente (id); gli altri seguono la scelta fatta in Google Calendar. */
    var calendarsOn: Set<String>
        get() = sp.getStringSet("calendarsOn", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("calendarsOn", v) }

    var calendarsOff: Set<String>
        get() = sp.getStringSet("calendarsOff", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("calendarsOff", v) }

    /** "system", "light" oppure "dark". */
    var themeMode: String
        get() = sp.getString("themeMode", "system") ?: "system"
        set(v) = sp.edit { putString("themeMode", v) }

    /** Colore primario scelto dall'utente (ARGB); 0 = automatico, segue il momento della giornata. */
    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) = sp.edit { putInt("accent", v) }

    var team: String
        get() = sp.getString("team", "") ?: ""
        set(v) = sp.edit { putString("team", v.trim()) }

    /** Squadra scelta dall'elenco di ricerca (TeamRef codificato); vuoto = si cerca per nome. */
    var teamRef: String
        get() = sp.getString("teamRef", "") ?: ""
        set(v) = sp.edit { putString("teamRef", v) }

    /** Partita per cui è in attesa un promemoria (chiave di MatchReminder); vuoto = nessuno. */
    var matchReminder: String
        get() = sp.getString("matchReminder", "") ?: ""
        set(v) = sp.edit { putString("matchReminder", v) }

    /** Velocità della lettura ad alta voce, in percentuale: 100 = normale. */
    var speechSpeed: Int
        get() = sp.getInt("speechSpeed", 100)
        set(v) = sp.edit { putInt("speechSpeed", v.coerceIn(60, 160)) }

    /** Vero se il meteo del brief è animato (sole che gira, pioggia e neve che cadono, lampi). */
    var weatherAnimated: Boolean
        get() = sp.getBoolean("weatherAnimated", true)
        set(v) = sp.edit { putBoolean("weatherAnimated", v) }

    /** Vero se l'indice delle schede sta sul bordo destro (comodo per chi usa il telefono con la destra). */
    var railRight: Boolean
        get() = sp.getBoolean("railRight", false)
        set(v) = sp.edit { putBoolean("railRight", v) }

    /** Quanto in fretta deve scrivere l'IA: "fast", "balanced" oppure "careful" (vedi Summary.SPEED_CHOICES). */
    var aiSpeed: String
        get() = sp.getString("aiSpeed", "balanced") ?: "balanced"
        set(v) = sp.edit { putString("aiSpeed", v) }

    /** Quanto lungo deve essere il riepilogo dell'IA: "short", "normal" oppure "long" (vedi Summary.LENGTH_CHOICES). */
    var aiLength: String
        get() = sp.getString("aiLength", "normal") ?: "normal"
        set(v) = sp.edit { putString("aiLength", v) }

    /** Con che tono parla l'IA: "warm", "plain" oppure "cheerful" (vedi Summary.TONE_CHOICES). */
    var aiTone: String
        get() = sp.getString("aiTone", "warm") ?: "warm"
        set(v) = sp.edit { putString("aiTone", v) }

    /** Richieste dell'utente da aggiungere alle istruzioni dell'IA, per esempio "chiamami capo". */
    var aiNotes: String
        get() = sp.getString("aiNotes", "") ?: ""
        set(v) = sp.edit { putString("aiNotes", v.trim().take(Summary.NOTES_MAX)) }

    val aiStyle: Summary.Style get() = Summary.Style(aiLength, aiTone, aiNotes)

    /**
     * Impronta di quello che decide il testo dell'IA: impostazioni del riepilogo e schede accese.
     * Se cambia, il riepilogo salvato non vale più e se ne scrive uno nuovo.
     */
    val aiSummaryKey: String get() = listOf(aiSpeed, aiLength, aiTone, aiNotes, hidden.sorted().joinToString(",")).joinToString("|")

    /** Chi legge il riepilogo: "local" (voce sul telefono, gratuita), "gemini" oppure "phone" (sintesi di Android). */
    var speechEngine: String
        get() = sp.getString("speechEngine", "local") ?: "local"
        set(v) = sp.edit { putString("speechEngine", v) }

    /** Voce locale scelta (una di LocalVoice.VOICES). */
    var localVoice: String
        get() = LocalVoice.voice(sp.getString("localVoice", "") ?: "").id
        set(v) = sp.edit { putString("localVoice", v) }

    /**
     * Ultimo riepilogo scritto dall'IA, quando (millisecondi) e con che impostazioni: si riusa finché è recente
     * e le impostazioni sono le stesse, per non consumare richieste.
     */
    var aiSummary: String
        get() = sp.getString("aiSummary", "") ?: ""
        set(v) = sp.edit { putString("aiSummary", v).putLong("aiSummaryAt", System.currentTimeMillis()).putString("aiSummaryFor", aiSummaryKey) }

    val aiSummaryAt: Long get() = sp.getLong("aiSummaryAt", 0)

    /** Il riepilogo salvato, se è più giovane di [maxAge] millisecondi e scritto con le impostazioni di adesso. */
    fun freshAiSummary(maxAge: Long): String? = aiSummary.takeIf {
        it.isNotBlank() && System.currentTimeMillis() - aiSummaryAt < maxAge && sp.getString("aiSummaryFor", null) == aiSummaryKey
    }

    /** Voce di Gemini scelta per la lettura (una di Summary.VOICES). */
    var speechVoice: String
        get() = sp.getString("speechVoice", "Kore") ?: "Kore"
        set(v) = sp.edit { putString("speechVoice", v) }

    /** Cartella scelta per il backup automatico (indirizzo del sistema dei file); vuoto = spento. */
    var backupFolder: String
        get() = sp.getString("backupFolder", "") ?: ""
        set(v) = sp.edit { putString("backupFolder", v) }

    /** Quando è stato scritto l'ultimo backup automatico e l'impronta del suo contenuto, per non riscriverlo uguale. */
    var backupAt: Long
        get() = sp.getLong("backupAt", 0)
        set(v) = sp.edit { putLong("backupAt", v) }
    var backupHash: Int
        get() = sp.getInt("backupHash", 0)
        set(v) = sp.edit { putInt("backupHash", v) }

    /** Chiave dell'integrazione personale di Notion e pagina della to-do list scelta. */
    var notionToken: String
        get() = sp.getString("notionToken", "") ?: ""
        set(v) = sp.edit { putString("notionToken", v.trim()) }
    var notionPageId: String
        get() = sp.getString("notionPageId", "") ?: ""
        set(v) = sp.edit { putString("notionPageId", v) }
    var notionPageTitle: String
        get() = sp.getString("notionPageTitle", "") ?: ""
        set(v) = sp.edit { putString("notionPageTitle", v) }

    /** Vero se l'app avvisa poco prima dell'ora di uscire per un impegno con un luogo. */
    var leaveAlerts: Boolean
        get() = sp.getBoolean("leaveAlerts", true)
        set(v) = sp.edit { putBoolean("leaveAlerts", v) }

    /** Avviso di partenza in attesa: per quale impegno, quando scatta, che cosa dice, e l'ultimo già mostrato. */
    var leaveKey: String
        get() = sp.getString("leaveKey", "") ?: ""
        set(v) = sp.edit { putString("leaveKey", v) }
    var leaveAt: Long
        get() = sp.getLong("leaveAt", 0)
        set(v) = sp.edit { putLong("leaveAt", v) }
    var leaveText: String
        get() = sp.getString("leaveText", "") ?: ""
        set(v) = sp.edit { putString("leaveText", v) }
    var leaveDone: String
        get() = sp.getString("leaveDone", "") ?: ""
        set(v) = sp.edit { putString("leaveDone", v) }

    /** Vero se durante le partite della squadra l'app segue il punteggio e avvisa a ogni gol. */
    var liveAlerts: Boolean
        get() = sp.getBoolean("liveAlerts", true)
        set(v) = sp.edit { putBoolean("liveAlerts", v) }

    /** Partita seguita in questo momento (casa|ospiti) e ultimo punteggio visto. */
    var liveKey: String
        get() = sp.getString("liveKey", "") ?: ""
        set(v) = sp.edit { putString("liveKey", v) }
    var liveScore: String
        get() = sp.getString("liveScore", "") ?: ""
        set(v) = sp.edit { putString("liveScore", v) }

    /** Quando deve scattare il promemoria della partita (millisecondi) e che cosa deve dire. */
    var matchReminderAt: Long
        get() = sp.getLong("matchReminderAt", 0)
        set(v) = sp.edit { putLong("matchReminderAt", v) }

    var matchReminderTitle: String
        get() = sp.getString("matchReminderTitle", "") ?: ""
        set(v) = sp.edit { putString("matchReminderTitle", v) }

    var matchReminderText: String
        get() = sp.getString("matchReminderText", "") ?: ""
        set(v) = sp.edit { putString("matchReminderText", v) }

    var workAddress: String
        get() = sp.getString("workAddress", "") ?: ""
        set(v) = sp.edit { putString("workAddress", v.trim()) }

    /**
     * Destinazioni per i tempi di spostamento. Chi arriva da una versione con il solo indirizzo del lavoro
     * se lo ritrova qui come prima voce.
     */
    var places: List<SavedPlace>
        get() {
            val saved = sp.getString("places", null)
                ?: return if (workAddress.isBlank()) emptyList() else listOf(SavedPlace("Lavoro", workAddress, workPoint?.first, workPoint?.second))
            return runCatching {
                val a = JSONArray(saved)
                (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                    SavedPlace(
                        o.optString("name"), o.optString("address"), o.optDouble("lat").takeIf { !it.isNaN() }, o.optDouble("lon").takeIf { !it.isNaN() },
                        o.optString("icon"),
                    )
                }.filter { it.name.isNotBlank() }.distinctBy { it.name }
            }.getOrDefault(emptyList())
        }
        set(v) = sp.edit {
            val kept = v.filter { it.address.isNotBlank() }
            putString("places", JSONArray(kept.map { JSONObject().put("name", it.name.trim()).put("address", it.address.trim()).put("lat", it.lat).put("lon", it.lon).put("icon", it.icon) }).toString())
        }

    /** Orari d'ingresso al lavoro detti dall'utente, giorno per giorno: "HH:mm", oppure "off" se quel giorno non lavora. */
    fun workStart(day: LocalDate): String? = runCatching { JSONObject(sp.getString("workStarts", "{}") ?: "{}").optString(day.toString()).takeIf { it.isNotBlank() } }.getOrNull()

    /** Salva (o con null cancella) la risposta per [day]; i giorni passati si buttano. */
    fun setWorkStart(day: LocalDate, value: String?) {
        val o = runCatching { JSONObject(sp.getString("workStarts", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val yesterday = LocalDate.now().minusDays(1)
        o.keys().asSequence().toList().filter { runCatching { LocalDate.parse(it).isBefore(yesterday) }.getOrDefault(true) }.forEach { o.remove(it) }
        if (value == null) o.remove(day.toString()) else o.put(day.toString(), value)
        sp.edit {
            putString("workStarts", o.toString())
            if (value != null && value != "off") putString("lastWorkStart", value)
        }
    }

    /** L'ultimo orario d'ingresso indicato, proposto la volta dopo per rispondere con un tocco. */
    val lastWorkStart: String get() = sp.getString("lastWorkStart", "09:00") ?: "09:00"

    /** Coordinate dell'indirizzo del lavoro, se è stato scelto sulla mappa; altrimenti lo si cerca dal testo. */
    var workPoint: Pair<Double, Double>?
        get() = (sp.getString("workPoint", "") ?: "").split(',').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 2 }?.let { it[0] to it[1] }
        set(v) = sp.edit { putString("workPoint", v?.let { "${it.first},${it.second}" } ?: "") }

    /** Mezzi con cui calcolare i tragitti (chiavi di TravelRepo.MODES), anche più d'uno. */
    var travelModes: List<String>
        get() = (sp.getString("travelModes", null) ?: travelMode).split(',').filter { it in TravelRepo.MODES }.ifEmpty { listOf("car") }
        set(v) = sp.edit { putString("travelModes", v.joinToString(",")) }

    /** Il mezzo unico delle versioni precedenti: resta solo per chi aggiorna. */
    var travelMode: String
        get() = sp.getString("travelMode", "car") ?: "car"
        set(v) = sp.edit { putString("travelMode", v) }

    /** Piccola cache chiave-valore per risultati che cambiano di rado (indirizzi geocodificati, id squadra). */
    fun cached(key: String): String? = sp.getString("cache:$key", null)
    fun cache(key: String, value: String) = sp.edit { putString("cache:$key", value) }

    var lastPlaceCity: String
        get() = sp.getString("placeCity", "") ?: ""
        set(v) = sp.edit { putString("placeCity", v) }

    /**
     * Tutta la configurazione in un testo JSON, da portare su un altro telefono. Restano fuori i dati legati
     * a questo telefono: la scelta dei calendari (i loro id cambiano), l'ultima posizione, la cache e i permessi.
     */
    fun export(): String {
        val values = JSONObject()
        sp.all.forEach { (k, v) ->
            when {
                v == null -> Unit
                k in BACKUP_STRINGS || k in BACKUP_INTS || k in BACKUP_BOOLEANS -> values.put(k, v)
                k in BACKUP_SETS -> values.put(k, JSONArray((v as? Set<*>).orEmpty().toList()))
            }
        }
        return JSONObject().put("app", BACKUP_APP).put("version", 1).put("settings", values).toString(2)
    }

    /** Applica una configurazione creata da [export]; le voci assenti o di tipo sbagliato restano come sono. Falso se il testo non è un backup di Brieffo. */
    fun import(json: String): Boolean {
        val values = runCatching { JSONObject(json).takeIf { it.optString("app") == BACKUP_APP }?.optJSONObject("settings") }.getOrNull() ?: return false
        sp.edit {
            values.keys().forEach { k ->
                val v = values.get(k)
                when {
                    k in BACKUP_STRINGS && v is String -> putString(k, v)
                    k in BACKUP_INTS && v is Int -> putInt(k, v)
                    k in BACKUP_BOOLEANS && v is Boolean -> putBoolean(k, v)
                    k in BACKUP_SETS && v is JSONArray -> putStringSet(k, (0 until v.length()).map { v.optString(it) }.toSet())
                }
            }
        }
        return true
    }

    private companion object {
        const val BACKUP_APP = "brieffo"
        val BACKUP_STRINGS = setOf("name", "city", "geminiKey", "cloudflareAccount", "cloudflareToken", "feeds", "themeMode", "team", "teamRef", "workAddress", "workPoint", "places", "travelModes", "travelMode", "cardOrder", "speechVoice", "speechEngine", "localVoice", "aiSpeed", "aiLength", "aiTone", "aiNotes", "notionToken", "notionPageId", "notionPageTitle")
        val BACKUP_INTS = setOf("stepGoal", "notifyHour", "notifyMinute", "accent", "speechSpeed")
        val BACKUP_BOOLEANS = setOf("notifyEnabled", "railRight", "weatherAnimated", "leaveAlerts", "liveAlerts")
        val BACKUP_SETS = setOf("hidden")
    }
}
