package com.brieffo.app.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
            if (t.leaveBy!!.isAfter(s.now)) parts += "Per «${t.label}» parti entro le ${t.leaveBy.format(hm)} (${TravelRepo.MODES[t.mode]?.lowercase() ?: t.mode})."
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
    /** Mette in ordine il testo di Gemini: una riga per argomento, senza trattini o righe vuote. */
    private fun tidy(text: String) = text.lines().map { it.trim().trimStart('-', '•').trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** [onPartial] riceve il testo man mano che Gemini lo scrive, così può comparire subito sullo schermo. */
    fun gemini(apiKey: String, s: BriefState, speed: String = "balanced", onPartial: ((String) -> Unit)? = null): String {
        val health = (s.health as? HealthState.Data)?.health
        val usage = (s.usage as? UsageState.Data)?.usage
        val today = java.time.LocalDate.now()
        fun match(m: Match) = "${m.home} - ${m.away}" +
            (if (m.homeScore != null && m.awayScore != null) " ${m.homeScore}-${m.awayScore}" else "") +
            (m.date?.let { " (${it.toLocalDate()} ore ${it.format(hm)})" } ?: "") + (if (m.league.isNotBlank()) ", ${m.league}" else "")
        // Entra tutto quello che il brief mostra, e solo quello: le schede spente dalle impostazioni restano fuori.
        val data = buildString {
            appendLine("Momento: ${s.daypart.title}, ore ${s.now.format(hm)} di ${s.now.toLocalDate()}")
            if (s.name.isNotBlank()) appendLine("Nome: ${s.name}")
            if (s.shows(CardKeys.WEATHER)) s.weather?.let { w ->
                appendLine("Meteo ora a ${w.place}: ${wxText(w.code)}, ${w.temp.roundToInt()}° (percepiti ${w.feels.roundToInt()}°), vento ${w.wind.roundToInt()} km/h, umidità ${w.humidity}%, UV max ${w.uvMax.roundToInt()}")
                w.today?.let { appendLine("Oggi: ${wxText(it.code)}, min ${it.tMin.roundToInt()}° max ${it.tMax.roundToInt()}°, pioggia ${it.rainProb}%") }
                w.tomorrow?.let { appendLine("Domani: ${wxText(it.code)}, min ${it.tMin.roundToInt()}° max ${it.tMax.roundToInt()}°, pioggia ${it.rainProb}%") }
                w.hourly.firstOrNull { it.rainProb >= 50 }?.let { appendLine("Prima pioggia probabile: ${it.time.format(hm)}") }
                w.aqi?.let { appendLine("Qualità dell'aria: ${aqiText(it)}") }
                appendLine("Alba ${w.sunrise}, tramonto ${w.sunset}")
                if (s.shows(CardKeys.POLLEN)) w.pollen.takeIf { it.isNotEmpty() }?.let { p -> appendLine("Pollini: " + p.joinToString { "${it.name} ${it.level}" }) }
            }
            if (s.shows(CardKeys.ALERTS)) s.alerts.forEach { appendLine("Allerta meteo ${it.color}: ${it.type}") }
            if (s.calendarGranted && s.shows(CardKeys.AGENDA)) {
                fun events(list: List<Event>) = list.joinToString("; ") {
                    (if (it.allDay) "tutto il giorno" else it.start.format(hm)) + " " + it.title + (if (it.location.isNotBlank()) " (${it.location})" else "")
                }.ifEmpty { "nessuno" }
                appendLine("Impegni di oggi: " + events(s.today))
                appendLine("Impegni di domani: " + events(s.tomorrow))
            }
            if (s.shows(CardKeys.TRAVEL)) s.travel.forEach {
                appendLine("Spostamento verso ${it.label} (${TravelRepo.MODES[it.mode]?.lowercase() ?: it.mode}): ${it.minutes} min" + (it.detail?.let { d -> ", $d" } ?: "") + (it.leaveBy?.let { l -> ", partire entro ${l.format(hm)}" } ?: ""))
            }
            if (s.shows(CardKeys.HEALTH)) {
                health?.steps?.let { appendLine("Passi: $it su obiettivo ${s.stepGoal}") }
                health?.distanceKm?.let { appendLine("Distanza a piedi: ${String.format(Locale.ITALY, "%.1f", it)} km") }
                health?.calories?.let { appendLine("Calorie bruciate: ${it.roundToInt()}") }
                health?.sleepMinutes?.takeIf { it > 0 }?.let { appendLine("Sonno stanotte: ${durationText(it)}") }
                health?.avgHr?.let { appendLine("Battito medio: $it bpm") }
            }
            if (s.shows(CardKeys.USAGE)) usage?.let { u ->
                appendLine("Tempo di utilizzo del telefono oggi: ${durationText(u.totalMinutes)}" +
                    u.top.take(3).takeIf { it.isNotEmpty() }?.let { t -> " (più usate: " + t.joinToString { "${it.label} ${durationText(it.minutes)}" } + ")" }.orEmpty())
            }
            if (s.shows(CardKeys.SPORT)) s.sport?.let { sp ->
                appendLine("Squadra del cuore: ${sp.team}" + (sp.standing?.let { ", $it" } ?: ""))
                sp.live?.let { appendLine("Partita in corso adesso: ${match(it)}") }
                sp.last?.let { appendLine("Ultima partita giocata: ${match(it)}") }
                if (sp.form.isNotEmpty()) appendLine("Ultimi risultati (V vinta, N pari, P persa, dalla più vecchia): ${sp.form.joinToString("")}")
                sp.upcoming.take(3).takeIf { it.isNotEmpty() }?.let { appendLine("Prossime partite: " + it.joinToString("; ") { m -> match(m) }) }
            }
            if (s.shows(CardKeys.NEWS) && s.news.isNotEmpty()) appendLine("Titoli del giorno: " + s.news.take(6).joinToString("; ") { "${it.title} (${it.topic})" })
            if (s.shows(CardKeys.MARKETS) && s.markets.isNotEmpty()) appendLine(
                "Mercati: " + s.markets.joinToString("; ") { m -> "${m.label} ${m.value}" + (m.changePct?.let { " (${String.format(Locale.ITALY, "%+.1f", it)}% oggi)" } ?: "") }
            )
            if (s.shows(CardKeys.OCCASIONS)) {
                if (s.occasions.saints.isNotEmpty()) appendLine("Santi del giorno: " + s.occasions.saints.take(3).joinToString())
                s.occasions.birthdays.forEach { b ->
                    appendLine("Compleanno di ${b.name}: " + (if (b.date == today) "oggi" else "il ${b.date}") + (b.age?.let { ", compie $it anni" } ?: ""))
                }
                s.occasions.holiday?.let { appendLine("Prossima festività: ${it.name} il ${it.date}") }
            }
            if (s.shows(CardKeys.EXTRAS)) {
                s.moon?.let { appendLine("Luna: ${it.name}, illuminata al ${it.illumination}%") }
                s.battery?.let { appendLine("Batteria del telefono: ${it.percent}%" + if (it.charging) ", in carica" else "") }
                s.nextAlarm?.let { appendLine("Prossima sveglia: ${it.toLocalDate()} alle ${it.format(hm)}") }
                s.onThisDay?.let { appendLine("Accadde oggi nel ${it.year}: ${it.text}") }
            }
        }
        val prompt = """
            Sei l'assistente di un'app di riepilogo giornaliero. Scrivi in italiano un riepilogo caldo e concreto,
            rivolgendoti all'utente con il tu. Ogni riga è una frase breve su un solo argomento e comincia con una
            emoji adatta all'argomento seguita da uno spazio; vai a capo dopo ogni riga.
            Tocca tutti gli argomenti presenti nei dati (meteo e allerte, agenda, spostamenti, salute, uso del telefono,
            squadra e partite, notizie, mercati, ricorrenze, curiosità): una riga per argomento, al massimo 9 righe,
            unendo quelli vicini se serve. Di ogni argomento scegli il dato più utile adesso, non elencarli tutti.
            Metti per prime le cose che contano in questo momento della giornata e dai un consiglio pratico se serve.
            Usa solo i dati forniti, non inventare nulla. Una sola emoji per riga, all'inizio.
            Niente markdown, niente titoli, niente saluti iniziali.

            DATI:
        """.trimIndent() + "\n" + data

        return tidy(ask(apiKey, prompt, speed, onText = onPartial?.let { show -> { partial -> show(tidy(partial)) } }))
    }

    /** Prova la chiave con una domanda minima: restituisce il nome del modello che ha risposto. */
    fun testKey(apiKey: String, speed: String = "balanced"): String {
        var model = ""
        ask(apiKey, "Rispondi soltanto con la parola: ok", speed) { model = it }
        return model
    }

    /** Voci di Gemini proposte nelle impostazioni, con il carattere che Google attribuisce a ciascuna. */
    val VOICES = listOf(
        "Kore" to "decisa", "Aoede" to "ariosa", "Leda" to "giovane", "Sulafat" to "calda",
        "Puck" to "vivace", "Charon" to "chiara", "Algieba" to "morbida", "Achird" to "amichevole",
    )

    /**
     * Prepara il testo per la lettura ad alta voce: scrive per esteso orari, gradi, percentuali e punteggi,
     * toglie emoji e simboli, che letti diventerebbero rumore, e unisce le righe.
     */
    fun speakable(text: String) = text
        .replace(Regex("(\\d{1,2}):00\\b"), "$1")
        .replace(Regex("(\\d{1,2}):0?(\\d{1,2})\\b"), "$1 e $2")
        .replace(Regex("(\\d)\\s*°"), "$1 gradi")
        .replace(Regex("(\\d)\\s*%"), "$1 per cento")
        .replace(Regex("\\bkm/h\\b"), "chilometri orari")
        .replace(Regex("(\\d)\\s*km\\b"), "$1 chilometri")
        .replace(Regex("(\\d+)\\s*[–-]\\s*(\\d+)"), "$1 a $2")
        .replace(Regex("(\\d)\\s*€"), "$1 euro").replace("€", " euro ")
        .replace("·", ",").replace("«", "").replace("»", "")
        .replace(Regex("[\\p{So}\\p{Sk}\\x{FE0F}\\x{200D}]"), "")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }
        .joinToString(" ") { if (it.last() in ".!?:;,") it else "$it." }

    /**
     * Fa leggere [text] a una voce di Gemini. Restituisce l'audio senza intestazione:
     * PCM a 16 bit, mono, [SPEECH_RATE] campioni al secondo.
     */
    fun speech(apiKey: String, text: String, voice: String): ByteArray = withModels(SPEECH_MODELS) { model ->
        val part = JSONObject().put("type", "text").put("text", text).put(
            "annotations",
            JSONArray().put(JSONObject().put("type", "speech_metadata").put("style", "italiano, tono caldo, naturale e tranquillo, come chi racconta la giornata a un amico")),
        )
        val body = JSONObject()
            .put("model", model)
            .put("input", JSONArray().put(JSONObject().put("type", "user_input").put("content", JSONArray().put(part))))
            .put("response_format", JSONObject().put("type", "audio"))
            .put("generation_config", JSONObject().put("speech_config", JSONArray().put(JSONObject().put("voice", voice))))
        val r = post("https://generativelanguage.googleapis.com/v1beta/interactions", apiKey, body, model)
        val steps = r.optJSONArray("steps")
        var data: String? = null
        for (i in 0 until (steps?.length() ?: 0)) {
            val content = steps!!.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val c = content.optJSONObject(j) ?: continue
                if (c.optString("type") == "audio" && c.optString("data").isNotEmpty()) data = c.optString("data")
            }
        }
        val audio = data?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            ?: throw GeminiException("Gemini ha risposto senza audio", retryOtherModel = true)
        stripWavHeader(audio)
    }

    /** La risposta arriva come file WAV: i campioni iniziano dopo l'etichetta "data" e la sua lunghezza. */
    private fun stripWavHeader(audio: ByteArray): ByteArray {
        if (audio.size < 12 || String(audio, 0, 4, Charsets.ISO_8859_1) != "RIFF") return audio
        var i = 12
        while (i + 8 <= audio.size) {
            val size = (audio[i + 4].toInt() and 0xFF) or ((audio[i + 5].toInt() and 0xFF) shl 8) or
                ((audio[i + 6].toInt() and 0xFF) shl 16) or ((audio[i + 7].toInt() and 0xFF) shl 24)
            if (String(audio, i, 4, Charsets.ISO_8859_1) == "data") return audio.copyOfRange(i + 8, audio.size)
            if (size < 0) break
            i += 8 + size + (size and 1)
        }
        return audio.copyOfRange(minOf(44, audio.size), audio.size)
    }

    /** Manda [prompt] a Gemini e restituisce il testo della risposta. */
    private fun ask(apiKey: String, prompt: String, speed: String, onText: ((String) -> Unit)? = null, onModel: (String) -> Unit = {}): String {
        val plan = SPEEDS[speed] ?: SPEEDS.getValue("balanced")
        return withModels(plan.map { it.first }) { model ->
            val thinking = plan.first { it.first == model }.second
            val text = try {
                askModel(apiKey, prompt, model, thinking, onText)
            } catch (e: GeminiException) {
                // Se il modello di turno non accetta quel livello di ragionamento, si riprova lasciandogli il suo.
                if (thinking == null || !e.badRequest) throw e
                askModel(apiKey, prompt, model, null, onText)
            }
            onModel(model)
            text
        }
    }

    private fun askModel(apiKey: String, prompt: String, model: String, thinking: String?, onText: ((String) -> Unit)? = null): String {
        val config = JSONObject().put("temperature", 0.7).put("maxOutputTokens", 4096)
        if (thinking != null) config.put("thinkingConfig", JSONObject().put("thinkingLevel", thinking))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", config)
        if (onText != null) return stream("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse", apiKey, body, model, onText)
        val r = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", apiKey, body, model)
        val candidate = r.optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = (0 until (parts?.length() ?: 0)).map { parts!!.getJSONObject(it) }.filter { !it.optBoolean("thought") }
            .joinToString("") { it.optString("text") }.replace("*", "").trim()
        if (text.isEmpty()) {
            val reason = candidate?.optString("finishReason")?.takeIf { it.isNotBlank() }
                ?: r.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotBlank() }
            throw GeminiException("Gemini ha risposto senza testo", reason?.let { "Motivo: $it" }, retryOtherModel = true)
        }
        return text
    }

    /** Prova i modelli in ordine: se uno non esiste più, è sovraccarico o ha finito la quota gratuita, si passa al successivo. */
    private fun <T> withModels(models: List<String>, call: (String) -> T): T {
        var last: GeminiException? = null
        for (model in models) {
            try {
                return call(model)
            } catch (e: GeminiException) {
                last = e
                if (!e.retryOtherModel) throw e
            } catch (e: IOException) {
                throw GeminiException("connessione non riuscita", e.message)
            } catch (e: IllegalArgumentException) {
                // Caratteri non ammessi nell'intestazione: la chiave è stata incollata male.
                throw GeminiException("la chiave contiene caratteri non validi: copiala di nuovo", e.message)
            }
        }
        throw last ?: GeminiException("nessun modello disponibile")
    }

    /**
     * Come [post], ma la risposta arriva a pezzi mentre il modello scrive: a ogni pezzo [onText] riceve
     * tutto il testo ricevuto fin lì. Restituisce il testo completo.
     */
    private fun stream(url: String, apiKey: String, body: JSONObject, model: String, onText: (String) -> Unit): String {
        val c = connect(url, apiKey, body, model)
        try {
            val all = StringBuilder()
            c.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val o = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull() ?: continue
                    val parts = o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    val piece = (0 until parts.length()).map { parts.getJSONObject(it) }.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
                    if (piece.isNotEmpty()) {
                        all.append(piece.replace("*", ""))
                        onText(all.toString())
                    }
                }
            }
            return all.toString().trim().ifEmpty { throw GeminiException("Gemini ha risposto senza testo", retryOtherModel = true) }
        } finally {
            c.disconnect()
        }
    }

    private fun post(url: String, apiKey: String, body: JSONObject, model: String): JSONObject {
        val c = connect(url, apiKey, body, model)
        try {
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    /** Apre la richiesta e controlla l'esito: se Google rifiuta, l'errore dice perché. Chi chiama chiude la connessione. */
    private fun connect(url: String, apiKey: String, body: JSONObject, model: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            c.readTimeout = 45_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", apiKey.trim())
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                // Google spiega il motivo nel corpo della risposta: senza leggerlo ogni errore sembrerebbe "chiave sbagliata".
                val err = runCatching {
                    val raw = c.errorStream.bufferedReader().use { it.readText() }.trim()
                    (if (raw.startsWith("[")) JSONArray(raw).getJSONObject(0) else JSONObject(raw)).getJSONObject("error")
                }.getOrNull()
                val detail = err?.optString("message")?.takeIf { it.isNotBlank() }
                val status = err?.optString("status").orEmpty()
                val badKey = detail?.contains("API key", ignoreCase = true) == true
                throw when {
                    code == 400 && badKey -> GeminiException("la chiave API non è valida", detail)
                    code == 400 && status == "FAILED_PRECONDITION" ->
                        GeminiException("il piano gratuito di Gemini non è disponibile per questa chiave: va attivata la fatturazione su Google AI Studio", detail)
                    code == 400 -> GeminiException("richiesta rifiutata da Gemini", detail, badRequest = true)
                    code == 401 || code == 403 -> GeminiException("la chiave non è autorizzata a usare Gemini", detail)
                    code == 404 -> GeminiException("il modello $model non è disponibile", detail, retryOtherModel = true)
                    code == 429 -> GeminiException("limite gratuito raggiunto, riprova tra poco", detail, retryOtherModel = true)
                    code >= 500 -> GeminiException("Gemini è sovraccarico, riprova tra poco", detail, retryOtherModel = true)
                    else -> GeminiException("errore $code del servizio", detail)
                }
            }
            return c
        } catch (e: Throwable) {
            c.disconnect()
            throw e
        }
    }

    const val SPEECH_RATE = 24_000

    /** Dal più capace al più leggero: i nomi "latest" seguono da soli le nuove versioni. */
    private const val FLASH = "gemini-flash-latest"
    private const val LITE = "gemini-flash-lite-latest"

    /**
     * Per ogni velocità, i modelli da provare in ordine e quanto farli ragionare prima di scrivere (null = quanto
     * decide il modello). Il tempo di attesa dipende quasi tutto da questo: il modello leggero che non ragiona
     * risponde in un paio di secondi, quello completo che ragiona può metterci molto di più.
     */
    private val SPEEDS = mapOf(
        "fast" to listOf(LITE to null, FLASH to "low"),
        "balanced" to listOf(FLASH to "low", LITE to null),
        "careful" to listOf(FLASH to null, LITE to null),
    )

    /** Velocità proposte nelle impostazioni: chiave, nome e che cosa comporta. */
    val SPEED_CHOICES = listOf(
        Triple("fast", "Veloce", "Il modello più leggero: risponde in pochi secondi, con un testo un po' più semplice."),
        Triple("balanced", "Bilanciato", "Il modello completo con poco ragionamento: buon testo senza attese lunghe."),
        Triple("careful", "Curato", "Il modello completo che ragiona quanto vuole: il testo migliore, ma può metterci parecchio."),
    )
    private val SPEECH_MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
}

/** Errore di Gemini: [message] è la spiegazione breve in italiano, [detail] quello che ha risposto Google. */
class GeminiException(message: String, val detail: String? = null, val retryOtherModel: Boolean = false, val badRequest: Boolean = false) : Exception(message)
