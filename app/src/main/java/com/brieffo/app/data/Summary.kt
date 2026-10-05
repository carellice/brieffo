package com.brieffo.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Summary {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    private fun Long.grouped() = String.format(Locale.ITALY, "%,d", this)

    /** Riepilogo composto in locale, senza rete: è sempre disponibile e fa da base a quello dell'IA. */
    fun rules(s: BriefState): String {
        val parts = mutableListOf<String>()
        val evening = s.daypart == Daypart.EVENING || s.daypart == Daypart.NIGHT
        val w = s.weather
        val health = (s.health as? HealthState.Data)?.health
        val usage = (s.usage as? UsageState.Data)?.usage

        if (w != null) {
            val day = if (evening) w.tomorrow else w.today
            if (day != null) {
                val whenText = if (evening) "Domani" else "Oggi"
                parts += "$whenText a ${w.place} ${wxText(day.code)}, tra ${day.tMin.roundToInt()}° e ${day.tMax.roundToInt()}°."
                if (!evening) {
                    val rain = w.hourly.firstOrNull { it.time.toLocalDate() == day.date && it.rainProb >= 50 }
                    if (rain != null) parts += "Porta l'ombrello: pioggia probabile dalle ${rain.time.format(hm)}."
                    else if (w.uvMax >= 6 && s.daypart == Daypart.MORNING) parts += "Indice UV alto (${w.uvMax.roundToInt()}): meglio la crema solare."
                } else if (day.rainProb >= 50) {
                    parts += "Probabile pioggia (${day.rainProb}%)."
                }
            }
        }

        s.alerts.firstOrNull()?.let { parts += "Attenzione: allerta ${it.color} per ${it.type.lowercase()} nella tua zona" + (it.from?.let { f -> if (f.toLocalDate() == java.time.LocalDate.now()) " dalle ${f.format(hm)}" else " da domani" } ?: "") + "." }

        if (s.calendarGranted && s.shows(CardKeys.AGENDA)) {
            if (evening) {
                parts += when (val n = s.tomorrow.size) {
                    0 -> "Domani l'agenda è libera."
                    else -> {
                        val first = s.tomorrow.firstOrNull { !it.allDay } ?: s.tomorrow.first()
                        val at = if (first.allDay) "" else " alle ${first.start.format(hm)}"
                        if (n == 1) "Domani hai un impegno: «${first.title}»$at." else "Domani hai $n impegni, si parte con «${first.title}»$at."
                    }
                }
            } else {
                val upcoming = s.today.filter { it.allDay || it.end.isAfter(s.now) }
                parts += when {
                    s.today.isEmpty() -> "Agenda libera per oggi."
                    upcoming.isEmpty() -> "Hai già chiuso tutti gli impegni di oggi."
                    else -> {
                        val next = upcoming.firstOrNull { !it.allDay } ?: upcoming.first()
                        val at = if (next.allDay) "" else " alle ${next.start.format(hm)}"
                        if (upcoming.size == 1) "Ti resta un impegno: «${next.title}»$at." else "Ti restano ${upcoming.size} impegni, il prossimo è «${next.title}»$at."
                    }
                }
            }
        }

        s.travel.firstOrNull { it.leaveBy != null }?.let { t ->
            if (t.leaveBy!!.isAfter(s.now)) parts += "Per «${t.label}» parti entro le ${t.leaveBy.format(hm)}."
        }

        if (health != null) {
            val sleep = health.sleepMinutes
            if (sleep != null && sleep > 0 && s.daypart == Daypart.MORNING) {
                parts += "Hai dormito ${durationText(sleep)}" + when {
                    sleep < 360 -> ", un po' poco: prenditela con calma."
                    sleep >= 450 -> ", ottimo riposo."
                    else -> "."
                }
            }
            val steps = health.steps
            if (steps != null && s.daypart != Daypart.MORNING) {
                parts += if (steps >= s.stepGoal) "Obiettivo passi raggiunto: ${steps.grouped()}."
                else "Sei a ${steps.grouped()} passi su ${s.stepGoal.toLong().grouped()}."
            }
        }

        if (usage != null && evening && usage.totalMinutes > 0) {
            parts += "Oggi hai usato il telefono per ${durationText(usage.totalMinutes)}."
        }

        return parts.joinToString(" ").ifEmpty { "Concedi i permessi o imposta una città per ricevere il tuo riepilogo." }
    }

    /** Riscrive il riepilogo con Gemini (piano gratuito di Google AI Studio, chiave dell'utente). */
    fun gemini(apiKey: String, s: BriefState): String {
        val health = (s.health as? HealthState.Data)?.health
        val usage = (s.usage as? UsageState.Data)?.usage
        val data = buildString {
            appendLine("Momento: ${s.daypart.title}, ore ${s.now.format(hm)}")
            if (s.name.isNotBlank()) appendLine("Nome: ${s.name}")
            s.weather?.let { w ->
                appendLine("Meteo ora a ${w.place}: ${wxText(w.code)}, ${w.temp.roundToInt()}° (percepiti ${w.feels.roundToInt()}°), vento ${w.wind.roundToInt()} km/h, UV max ${w.uvMax.roundToInt()}")
                w.today?.let { appendLine("Oggi: ${wxText(it.code)}, min ${it.tMin.roundToInt()}° max ${it.tMax.roundToInt()}°, pioggia ${it.rainProb}%") }
                w.tomorrow?.let { appendLine("Domani: ${wxText(it.code)}, min ${it.tMin.roundToInt()}° max ${it.tMax.roundToInt()}°, pioggia ${it.rainProb}%") }
                w.hourly.firstOrNull { it.rainProb >= 50 }?.let { appendLine("Prima pioggia probabile: ${it.time.format(hm)}") }
                w.aqi?.let { appendLine("Qualità dell'aria: ${aqiText(it)}") }
            }
            if (s.calendarGranted) {
                appendLine("Impegni di oggi: " + s.today.joinToString("; ") { (if (it.allDay) "tutto il giorno" else it.start.format(hm)) + " " + it.title }.ifEmpty { "nessuno" })
                appendLine("Impegni di domani: " + s.tomorrow.joinToString("; ") { (if (it.allDay) "tutto il giorno" else it.start.format(hm)) + " " + it.title }.ifEmpty { "nessuno" })
            }
            s.alerts.forEach { appendLine("Allerta meteo ${it.color}: ${it.type}") }
            s.weather?.pollen?.takeIf { it.isNotEmpty() }?.let { p -> appendLine("Pollini: " + p.joinToString { "${it.name} ${it.level}" }) }
            s.travel.forEach { appendLine("Spostamento verso ${it.label}: ${it.minutes} min" + (it.leaveBy?.let { l -> ", partire entro ${l.format(hm)}" } ?: "")) }
            s.sport?.next?.let { m -> appendLine("Prossima partita: ${m.home} - ${m.away}" + (m.date?.let { " il ${it.toLocalDate()} alle ${it.format(hm)}" } ?: "")) }
            s.occasions.birthdays.filter { it.date == java.time.LocalDate.now() }.forEach { appendLine("Oggi è il compleanno di ${it.name}") }
            health?.steps?.let { appendLine("Passi: $it su obiettivo ${s.stepGoal}") }
            health?.sleepMinutes?.takeIf { it > 0 }?.let { appendLine("Sonno stanotte: ${durationText(it)}") }
            health?.avgHr?.let { appendLine("Battito medio: $it bpm") }
            usage?.let { appendLine("Tempo di utilizzo del telefono oggi: ${durationText(it.totalMinutes)}") }
            if (s.news.isNotEmpty()) appendLine("Titoli del giorno: " + s.news.take(3).joinToString("; ") { it.title })
        }
        val prompt = """
            Sei l'assistente di un'app di riepilogo giornaliero. Scrivi in italiano un riepilogo di 3-4 frasi,
            caldo e concreto, rivolgendoti all'utente con il tu. Metti in evidenza ciò che conta in questo momento
            della giornata e dai un consiglio pratico se serve. Usa solo i dati forniti, non inventare nulla.
            Niente elenchi, niente markdown, niente emoji, niente saluti iniziali.

            DATI:
        """.trimIndent() + "\n" + data

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 2048))

        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 25_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", apiKey)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            when (val code = c.responseCode) {
                in 200..299 -> Unit
                400, 401, 403 -> error("la chiave API non è valida")
                429 -> error("limite gratuito raggiunto, riprova tra poco")
                else -> error("errore $code del servizio")
            }
            val r = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val parts = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            val text = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
            return text.replace("*", "").trim().ifEmpty { error("Risposta vuota") }
        } finally {
            c.disconnect()
        }
    }
}
