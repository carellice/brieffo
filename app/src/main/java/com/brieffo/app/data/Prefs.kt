package com.brieffo.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
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

    var geminiKey: String
        get() = sp.getString("geminiKey", "") ?: ""
        set(v) = sp.edit { putString("geminiKey", v.trim()) }

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

    /** Quanto in fretta deve scrivere Gemini: "fast", "balanced" oppure "careful" (vedi Summary.SPEED_CHOICES). */
    var aiSpeed: String
        get() = sp.getString("aiSpeed", "balanced") ?: "balanced"
        set(v) = sp.edit { putString("aiSpeed", v) }

    /** Vero se il riepilogo di Gemini parte chiuso, con solo le prime righe in vista. */
    var summaryCollapsed: Boolean
        get() = sp.getBoolean("summaryCollapsed", false)
        set(v) = sp.edit { putBoolean("summaryCollapsed", v) }

    /** Chi legge il riepilogo: "local" (voce sul telefono, gratuita), "gemini" oppure "phone" (sintesi di Android). */
    var speechEngine: String
        get() = sp.getString("speechEngine", "local") ?: "local"
        set(v) = sp.edit { putString("speechEngine", v) }

    /** Voce locale scelta (una di LocalVoice.VOICES). */
    var localVoice: String
        get() = LocalVoice.voice(sp.getString("localVoice", "") ?: "").id
        set(v) = sp.edit { putString("localVoice", v) }

    /** Ultimo riepilogo scritto da Gemini e quando (millisecondi): si riusa finché è recente, per non consumare richieste. */
    var aiSummary: String
        get() = sp.getString("aiSummary", "") ?: ""
        set(v) = sp.edit { putString("aiSummary", v).putLong("aiSummaryAt", System.currentTimeMillis()) }

    val aiSummaryAt: Long get() = sp.getLong("aiSummaryAt", 0)

    /** Voce di Gemini scelta per la lettura (una di Summary.VOICES). */
    var speechVoice: String
        get() = sp.getString("speechVoice", "Kore") ?: "Kore"
        set(v) = sp.edit { putString("speechVoice", v) }

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
        val BACKUP_STRINGS = setOf("name", "city", "geminiKey", "feeds", "themeMode", "team", "teamRef", "workAddress", "workPoint", "travelModes", "travelMode", "cardOrder", "speechVoice", "speechEngine", "localVoice", "aiSpeed")
        val BACKUP_INTS = setOf("stepGoal", "notifyHour", "notifyMinute", "accent", "speechSpeed")
        val BACKUP_BOOLEANS = setOf("notifyEnabled", "summaryCollapsed", "railRight", "weatherAnimated")
        val BACKUP_SETS = setOf("hidden")
    }
}
