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

    var workAddress: String
        get() = sp.getString("workAddress", "") ?: ""
        set(v) = sp.edit { putString("workAddress", v.trim()) }

    /** "car", "bike" oppure "foot". */
    var travelMode: String
        get() = sp.getString("travelMode", "car") ?: "car"
        set(v) = sp.edit { putString("travelMode", v) }

    /** Piccola cache chiave-valore per risultati che cambiano di rado (indirizzi geocodificati, id squadra). */
    fun cached(key: String): String? = sp.getString("cache:$key", null)
    fun cache(key: String, value: String) = sp.edit { putString("cache:$key", value) }

    var lastPlaceCity: String
        get() = sp.getString("placeCity", "") ?: ""
        set(v) = sp.edit { putString("placeCity", v) }
}
