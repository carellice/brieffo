package com.brieffo.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.resume

fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Brieffo/1.0 (Android; personal app)")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} per $url")
        return c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

object WeatherRepo {
    private val pollenNames = linkedMapOf(
        "grass_pollen" to "Graminacee", "birch_pollen" to "Betulla", "alder_pollen" to "Ontano",
        "olive_pollen" to "Olivo", "mugwort_pollen" to "Artemisia", "ragweed_pollen" to "Ambrosia",
    )

    suspend fun resolvePlace(ctx: Context, prefs: Prefs): Place? {
        val city = prefs.city
        if (city.isNotBlank()) {
            prefs.lastPlace?.takeIf { prefs.lastPlaceCity.equals(city, true) && it.country.isNotEmpty() }?.let { return it }
            val found = runCatching { geocode(city) }.getOrNull()
            if (found != null) {
                prefs.lastPlace = found
                prefs.lastPlaceCity = city
            }
            return found
        }
        val loc = deviceLocation(ctx) ?: return prefs.lastPlace.takeIf { prefs.lastPlaceCity.isEmpty() }
        // Due decimali (~1 km) bastano per il meteo e non espongono l'indirizzo preciso.
        val lat = Math.round(loc.latitude * 100) / 100.0
        val lon = Math.round(loc.longitude * 100) / 100.0
        val address = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.ITALY).getFromLocation(lat, lon, 1)?.firstOrNull()
        }.getOrNull()
        val name = address?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: "Posizione attuale"
        return Place(lat, lon, name, address?.adminArea ?: "", address?.countryCode ?: "").also {
            prefs.lastPlace = it
            prefs.lastPlaceCity = ""
        }
    }

    private fun geocode(city: String): Place? {
        val q = URLEncoder.encode(city, "UTF-8")
        val r = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=it"))
        val o = r.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Place(o.getDouble("latitude"), o.getDouble("longitude"), o.getString("name"), o.optString("admin1"), o.optString("country_code"))
    }

    @SuppressLint("MissingPermission")
    private suspend fun deviceLocation(ctx: Context): Location? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 6 * 3600_000L) return last
        if (Build.VERSION.SDK_INT >= 30) {
            // Si provano tutti i provider attivi: su alcuni telefoni quello di rete risponde subito con null.
            for (provider in listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { it in providers }) {
                val fresh = withTimeoutOrNull(6000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        lm.getCurrentLocation(provider, null, ctx.mainExecutor) { if (cont.isActive) cont.resume(it) }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return last
    }

    fun fetch(place: Place): Weather {
        val base = "latitude=${place.lat}&longitude=${place.lon}&timezone=auto"
        val r = JSONObject(
            httpGet(
                "https://api.open-meteo.com/v1/forecast?$base&forecast_days=7" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,relative_humidity_2m" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,sunrise,sunset"
            )
        )
        val cur = r.getJSONObject("current")
        val now = LocalDateTime.parse(cur.getString("time")).withMinute(0)

        val h = r.getJSONObject("hourly")
        val hTimes = h.getJSONArray("time")
        val hourly = (0 until hTimes.length()).map { i ->
            HourWx(
                LocalDateTime.parse(hTimes.getString(i)),
                h.getJSONArray("temperature_2m").optDouble(i, 0.0),
                h.getJSONArray("weather_code").optInt(i),
                h.getJSONArray("precipitation_probability").optInt(i, 0),
                h.getJSONArray("is_day").optInt(i, 1) == 1,
            )
        }.filter { !it.time.isBefore(now) }.take(24)

        val d = r.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val daily = (0 until dTimes.length()).map { i ->
            DayWx(
                LocalDate.parse(dTimes.getString(i)),
                d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i, 0.0),
                d.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                d.getJSONArray("precipitation_probability_max").optInt(i, 0),
            )
        }

        val air = runCatching {
            JSONObject(httpGet("https://air-quality-api.open-meteo.com/v1/air-quality?$base&current=european_aqi,pm2_5,${pollenNames.keys.joinToString(",")}"))
                .getJSONObject("current")
        }.getOrNull()

        return Weather(
            place = place.name,
            temp = cur.getDouble("temperature_2m"),
            feels = cur.getDouble("apparent_temperature"),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            wind = cur.getDouble("wind_speed_10m"),
            humidity = cur.getInt("relative_humidity_2m"),
            uvMax = d.getJSONArray("uv_index_max").optDouble(0, 0.0),
            sunrise = d.getJSONArray("sunrise").optString(0).substringAfter('T'),
            sunset = d.getJSONArray("sunset").optString(0).substringAfter('T'),
            hourly = hourly,
            daily = daily,
            aqi = air?.takeIf { !it.isNull("european_aqi") }?.optInt("european_aqi"),
            pm25 = air?.takeIf { !it.isNull("pm2_5") }?.optDouble("pm2_5"),
            // Sotto 1 granulo/m³ il polline è di fatto assente: non lo si elenca.
            pollen = pollenNames.mapNotNull { (key, label) ->
                air?.takeIf { !it.isNull(key) }?.optDouble(key)?.takeIf { it >= 1 }?.let { Pollen(label, it) }
            }.sortedByDescending { it.grains },
        )
    }
}
