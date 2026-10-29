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

    /** Un tragitto per ogni mezzo scelto, in una frase: "in auto 18 min (esci entro le 08:37), con i mezzi pubblici 32 min (…)". */
    fun byMode(rides: List<Travel>) = rides.joinToString(", ") { r ->
        val how = when (r.mode) { "car" -> "in auto"; "transit" -> "con i mezzi pubblici"; "bike" -> "in bici"; "foot" -> "a piedi"; else -> r.mode }
        "$how ${durationText(r.minutes)}" + (r.leaveBy?.let { " (esci entro le ${it.format(hm)})" } ?: "")
    }

    /** Riepilogo composto in locale, senza rete: è sempre disponibile e fa da base a quello dell'IA. */
    fun rules(s: BriefState): String {
        val parts = mutableListOf<String>()
        val evening = s.daypart == Daypart.EVENING || s.daypart == Daypart.NIGHT
        val w = s.weather
        val health = (s.health as? HealthState.Data)?.health
        val usage = (s.usage as? UsageState.Data)?.usage

        if (w != null && s.shows(CardKeys.WEATHER)) {
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

        s.alerts.firstOrNull()?.takeIf { s.shows(CardKeys.ALERTS) }?.let { parts += "Attenzione: allerta ${it.color} per ${it.type.lowercase()} nella tua zona" + (it.from?.let { f -> if (f.toLocalDate() == java.time.LocalDate.now()) " dalle ${f.format(hm)}" else " da domani" } ?: "") + "." }

        // Un'agenda vuota non è un dato da raccontare: se non ci sono impegni, il riepilogo non ne parla.
        if (s.calendarGranted && s.shows(CardKeys.AGENDA)) {
            if (evening) {
                if (s.tomorrow.isNotEmpty()) {
                    val n = s.tomorrow.size
                    val first = s.tomorrow.firstOrNull { !it.allDay } ?: s.tomorrow.first()
                    val at = if (first.allDay) "" else " alle ${first.start.format(hm)}"
                    parts += if (n == 1) "Domani hai un impegno: «${first.title}»$at." else "Domani hai $n impegni, si parte con «${first.title}»$at."
                }
            } else if (s.today.isNotEmpty()) {
                val upcoming = s.today.filter { it.allDay || it.end.isAfter(s.now) }
                parts += when {
                    upcoming.isEmpty() -> "Hai già chiuso tutti gli impegni di oggi."
                    else -> {
                        val next = upcoming.firstOrNull { !it.allDay } ?: upcoming.first()
                        val at = if (next.allDay) "" else " alle ${next.start.format(hm)}"
                        if (upcoming.size == 1) "Ti resta un impegno: «${next.title}»$at." else "Ti restano ${upcoming.size} impegni, il prossimo è «${next.title}»$at."
                    }
                }
            }
        }

        // To-do list di Notion: quante cose restano e la prima.
        s.todos.filter { !it.checked }.takeIf { s.shows(CardKeys.TODO) && it.isNotEmpty() }?.let { open ->
            parts += if (open.size == 1) "Ti resta una cosa da fare: «${open[0].text}»." else "Hai ${open.size} cose da fare, a partire da «${open[0].text}»."
        }
        s.commute?.takeIf { s.shows(CardKeys.TRAVEL) }?.let { c ->
            val day = if (c.day == s.now.toLocalDate()) "Oggi" else "Domani"
            when {
                c.off -> Unit
                c.needsAnswer -> parts += "Dimmi a che ora entri al lavoro ${day.lowercase()} per sapere quando uscire."
                c.rides.isNotEmpty() -> parts += "$day per il lavoro: ${byMode(c.rides)}."
            }
        }
        // Per il prossimo impegno con un luogo, di nuovo tutti i mezzi scelti.
        s.travel.filter { it.leaveBy != null && it.leaveBy.isAfter(s.now) }.groupBy { it.label }.entries.firstOrNull()?.let { (label, rides) ->
            parts += "Per «$label»: ${byMode(rides)}."
        }

        if (health != null && s.shows(CardKeys.HEALTH)) {
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

        if (usage != null && s.shows(CardKeys.USAGE) && evening && usage.totalMinutes > 0) {
            parts += "Oggi hai usato il telefono per ${durationText(usage.totalMinutes)}."
        }

        return parts.joinToString(" ").ifEmpty { "Concedi i permessi o imposta una città per ricevere il tuo riepilogo." }
    }

    /**
     * Mette in ordine il testo dell'IA: paragrafi senza trattini, asterischi o righe vuote.
     * Toglie anche il ragionamento che alcuni modelli scrivono tra <think> e </think>, anche se non è ancora chiuso.
     */
    private fun tidy(text: String) = text.replace(Regex("(?s)<think>.*?(</think>|$)"), "").replace("*", "").replace("#", "")
        .lines().map { it.trim().trimStart('-', '•').trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /**
     * I dati del brief da raccontare: entrano solo le schede accese in "Schede da mostrare" che hanno
     * davvero qualcosa da dire. Una scheda accesa ma vuota resta fuori, così l'IA non la nomina.
     */
    private fun aiData(s: BriefState): List<String> = buildList {
        val health = (s.health as? HealthState.Data)?.health
        val usage = (s.usage as? UsageState.Data)?.usage
        val today = java.time.LocalDate.now()
        val evening = s.daypart == Daypart.EVENING || s.daypart == Daypart.NIGHT
        fun match(m: Match) = "${m.home} - ${m.away}" +
            (if (m.homeScore != null && m.awayScore != null) " ${m.homeScore}-${m.awayScore}" else "") +
            (m.date?.let { " (${it.toLocalDate()} ore ${it.format(hm)})" } ?: "") + (if (m.league.isNotBlank()) ", ${m.league}" else "")
        if (s.shows(CardKeys.WEATHER)) s.weather?.let { w ->
            add("Meteo ora a ${w.place}: ${wxText(w.code)}, ${w.temp.roundToInt()}° (percepiti ${w.feels.roundToInt()}°), vento ${w.wind.roundToInt()} km/h, umidità ${w.humidity}%, UV max ${w.uvMax.roundToInt()}")
            w.today?.let { add("Oggi: ${wxText(it.code)}, min ${it.tMin.roundToInt()}° max ${it.tMax.roundToInt()}°, pioggia ${it.rainProb}%") }
            w.tomorrow?.let { add("Domani: ${wxText(it.code)}, min ${it.tMin.roundToInt()}° max ${it.tMax.roundToInt()}°, pioggia ${it.rainProb}%") }
            w.hourly.firstOrNull { it.rainProb >= 50 }?.let { add("Prima pioggia probabile: ${it.time.format(hm)}") }
            w.aqi?.let { add("Qualità dell'aria: ${aqiText(it)}") }
            add("Alba ${w.sunrise}, tramonto ${w.sunset}")
            if (s.shows(CardKeys.POLLEN)) w.pollen.takeIf { it.isNotEmpty() }?.let { p -> add("Pollini: " + p.joinToString { "${it.name} ${it.level}" }) }
        }
        if (s.shows(CardKeys.ALERTS)) s.alerts.forEach { add("Allerta meteo ${it.color}: ${it.type}") }
        if (s.calendarGranted) {
            fun events(list: List<Event>) = list.joinToString("; ") {
                (if (it.allDay) "tutto il giorno" else it.start.format(hm)) + " " + it.title + (if (it.location.isNotBlank()) " (${it.location})" else "")
            }
            if (s.shows(CardKeys.AGENDA) && s.today.isNotEmpty()) add("Impegni di oggi: " + events(s.today))
            // Quelli di domani sono anche nella scheda "Domani", che c'è solo la sera.
            if ((s.shows(CardKeys.AGENDA) || (evening && s.shows(CardKeys.TOMORROW))) && s.tomorrow.isNotEmpty()) add("Impegni di domani: " + events(s.tomorrow))
        }
        if (evening && s.shows(CardKeys.TOMORROW)) s.tomorrowTravel.forEach {
            add("Per il primo impegno di domani (${it.label}, ${TravelRepo.MODES[it.mode]?.lowercase() ?: it.mode}): ${it.minutes} min" + (it.leaveBy?.let { l -> ", partire entro le ${l.format(hm)}" } ?: ""))
        }
        if (s.shows(CardKeys.TODO)) s.todos.filter { !it.checked }.takeIf { it.isNotEmpty() }?.let { open ->
            add("Cose da fare (to-do list dell'utente, ${open.size} aperte): " + open.take(10).joinToString("; ") { it.text })
        }
        if (s.shows(CardKeys.TRAVEL)) s.commute?.let { c ->
            val day = if (c.day == s.now.toLocalDate()) "oggi" else "domani"
            when {
                c.off -> add("Lavoro: $day non lavora")
                c.start != null -> add("Lavoro $day: entra alle ${c.start}" + c.rides.joinToString("") { r ->
                    "; ${TravelRepo.MODES[r.mode]?.lowercase() ?: r.mode} ${r.minutes} min, uscire entro ${r.leaveBy?.format(hm)}" + (r.detail?.let { d -> " ($d)" } ?: "")
                })
            }
        }
        if (s.shows(CardKeys.TRAVEL)) s.travel.forEach {
            add("Spostamento verso ${it.label} (${TravelRepo.MODES[it.mode]?.lowercase() ?: it.mode}): ${it.minutes} min" + (it.detail?.let { d -> ", $d" } ?: "") + (it.leaveBy?.let { l -> ", partire entro ${l.format(hm)}" } ?: ""))
        }
        if (s.shows(CardKeys.HEALTH)) {
            health?.steps?.let { add("Passi: $it su obiettivo ${s.stepGoal}") }
            health?.distanceKm?.let { add("Distanza a piedi: ${String.format(Locale.ITALY, "%.1f", it)} km") }
            health?.calories?.let { add("Calorie bruciate: ${it.roundToInt()}") }
            health?.sleepMinutes?.takeIf { it > 0 }?.let { add("Sonno stanotte: ${durationText(it)}") }
            health?.avgHr?.let { add("Battito medio: $it bpm") }
        }
        if (s.shows(CardKeys.USAGE)) usage?.let { u ->
            add("Tempo di utilizzo del telefono oggi: ${durationText(u.totalMinutes)}" +
                u.top.take(3).takeIf { it.isNotEmpty() }?.let { t -> " (più usate: " + t.joinToString { "${it.label} ${durationText(it.minutes)}" } + ")" }.orEmpty())
        }
        if (s.shows(CardKeys.SPORT)) s.sport?.let { sp ->
            add("Squadra del cuore: ${sp.team}" + (sp.standing?.let { ", $it" } ?: ""))
            sp.live?.let { add("Partita in corso adesso: ${match(it)}") }
            sp.last?.let { add("Ultima partita giocata: ${match(it)}") }
            if (sp.form.isNotEmpty()) add("Ultimi risultati (V vinta, N pari, P persa, dalla più vecchia): ${sp.form.joinToString("")}")
            sp.upcoming.take(3).takeIf { it.isNotEmpty() }?.let { add("Prossime partite: " + it.joinToString("; ") { m -> match(m) }) }
        }
        if (s.shows(CardKeys.NEWS) && s.news.isNotEmpty()) add("Titoli del giorno: " + s.news.take(6).joinToString("; ") { "${it.title} (${it.topic})" })
        if (s.shows(CardKeys.MARKETS) && s.markets.isNotEmpty()) add(
            "Mercati: " + s.markets.joinToString("; ") { m -> "${m.label} ${m.value}" + (m.changePct?.let { " (${String.format(Locale.ITALY, "%+.1f", it)}% oggi)" } ?: "") }
        )
        if (s.shows(CardKeys.OCCASIONS)) {
            if (s.occasions.saints.isNotEmpty()) add("Santi del giorno: " + s.occasions.saints.take(3).joinToString())
            s.occasions.birthdays.forEach { b ->
                add("Compleanno di ${b.name}: " + (if (b.date == today) "oggi" else "il ${b.date}") + (b.age?.let { ", compie $it anni" } ?: ""))
            }
            s.occasions.holiday?.let { add("Prossima festività: ${it.name} il ${it.date}") }
        }
        if (s.shows(CardKeys.EXTRAS)) {
            s.moon?.let { add("Luna: ${it.name}, illuminata al ${it.illumination}%") }
            s.battery?.let { add("Batteria del telefono: ${it.percent}%" + if (it.charging) ", in carica" else "") }
            s.nextAlarm?.let { add("Prossima sveglia: ${it.toLocalDate()} alle ${it.format(hm)}") }
            s.onThisDay?.let { add("Accadde oggi nel ${it.year}: ${it.text}") }
        }
    }

    /** Come vuole il riepilogo l'utente: quanto lungo, con che tono e con quali richieste sue. */
    data class Style(val length: String = "normal", val tone: String = "warm", val notes: String = "")

    /**
     * Scrive il riepilogo con Cloudflare Workers AI (piano gratuito, account e token dell'utente).
     * Il testo non compare nel brief: è fatto per essere ascoltato, quindi niente emoji né elenchi.
     */
    fun ai(account: String, token: String, s: BriefState, speed: String = "balanced", style: Style = Style()): String {
        val topics = aiData(s)
        if (topics.isEmpty()) throw AiException("le schede accese non hanno ancora dati da raccontare")
        val data = (listOf("Momento: ${s.daypart.title}, ore ${s.now.format(hm)} di ${s.now.toLocalDate()}") +
            listOfNotNull(s.name.takeIf { it.isNotBlank() }?.let { "Nome: $it" }) + topics).joinToString("\n")
        val length = LENGTH_PROMPTS[style.length] ?: LENGTH_PROMPTS.getValue("normal")
        val tone = TONE_PROMPTS[style.tone] ?: TONE_PROMPTS.getValue("warm")
        val instructions = """
            Sei la voce di Brieffo, un'app di riepilogo giornaliero. Scrivi in italiano il testo che una sintesi vocale
            leggerà all'utente, rivolgendoti a lui con il tu.
            $length
            $tone
            Parla solo di quello che compare nei DATI: sono le schede che l'utente ha scelto di vedere. Se un argomento
            non c'è, non nominarlo e non dire che mancano informazioni. Usa solo i dati forniti, non inventare nulla.
            Metti per prime le cose che contano in questo momento della giornata e dai un consiglio pratico quando serve.
            Per gli spostamenti indica il tempo e l'ora di uscita per ciascun mezzo presente nei dati.
            Il testo va ascoltato, non letto: frasi complete e scorrevoli, niente elenchi, emoji, simboli, titoli,
            markdown o abbreviazioni. Scrivi gli orari come si dicono a voce, per esempio "alle otto e mezza".
        """.trimIndent() + style.notes.trim().take(NOTES_MAX).takeIf { it.isNotEmpty() }?.let {
            "\nRichieste dell'utente, da seguire se non contraddicono le regole qui sopra: $it"
        }.orEmpty()

        return tidy(ask(account, token, instructions, "DATI:\n$data", speed)).ifEmpty { throw AiException("l'IA ha risposto senza testo") }
    }

    /** Prova account e token con una domanda minima: restituisce il nome del modello che ha risposto. */
    fun testKey(account: String, token: String, speed: String = "balanced"): String {
        var model = ""
        ask(account, token, "Rispondi in italiano, in una parola.", "Rispondi soltanto con la parola: ok", speed) { model = it }
        return model.substringAfterLast('/')
    }

    /** Quanto possono essere lunghe le richieste personali dell'utente. */
    const val NOTES_MAX = 300

    /** Lunghezze proposte nelle impostazioni: chiave, nome e che cosa comporta. */
    val LENGTH_CHOICES = listOf(
        Triple("short", "Breve", "Due o tre frasi con le cose più importanti adesso: circa 20 secondi."),
        Triple("normal", "Normale", "Il dato più utile di ogni scheda: circa un minuto."),
        Triple("long", "Approfondito", "Ogni scheda con i suoi dettagli, e i collegamenti tra l'una e l'altra: un paio di minuti."),
    )

    private val LENGTH_PROMPTS = mapOf(
        "short" to "Sii brevissimo: due o tre frasi, al massimo 60 parole, solo le due o tre cose più importanti in questo momento.",
        "normal" to "Scrivi da cinque a otto frasi, circa 130 parole: per ogni argomento il dato più utile adesso, non elencarli tutti.",
        "long" to "Scrivi un riepilogo approfondito di circa 280 parole, in tre o quattro paragrafi: per ogni argomento i dettagli che contano, " +
            "collegando quelli vicini (per esempio il meteo con gli spostamenti, gli impegni con le cose da fare).",
    )

    /** Toni proposti nelle impostazioni: chiave, nome e che cosa comporta. */
    val TONE_CHOICES = listOf(
        Triple("warm", "Amichevole", "Caldo e informale, come un amico che ti racconta la giornata."),
        Triple("plain", "Essenziale", "Sobrio e diretto come un notiziario: solo informazioni, niente commenti."),
        Triple("cheerful", "Allegro", "Vivace e positivo, con un pizzico di ironia leggera."),
    )

    private val TONE_PROMPTS = mapOf(
        "warm" to "Usa un tono caldo e informale, come un amico che racconta la giornata. Puoi aprire con un saluto brevissimo, con il nome se c'è.",
        "plain" to "Usa un tono sobrio e diretto, come un notiziario: niente saluti, niente commenti, solo informazioni utili.",
        "cheerful" to "Usa un tono vivace e positivo, con un pizzico di ironia leggera quando ci sta, mai sulle notizie serie o sulle allerte. Puoi aprire con un saluto brevissimo, con il nome se c'è.",
    )

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
        val r = post(connect("https://generativelanguage.googleapis.com/v1beta/interactions", "x-goog-api-key", apiKey.trim(), body) { code, raw -> geminiError(code, raw, model) })
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
            ?: throw AiException("Gemini ha risposto senza audio", retryOtherModel = true)
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

    /**
     * Manda a Workers AI le [instructions] e il [prompt] e restituisce il testo della risposta.
     * [onModel] riceve il modello che ha risposto davvero, dopo eventuali tentativi con altri.
     */
    private fun ask(account: String, token: String, instructions: String, prompt: String, speed: String, onModel: (String) -> Unit = {}): String {
        val id = account.trim()
        // L'ID dell'account è fatto di 32 cifre esadecimali: se non lo è, inutile chiamare Cloudflare.
        if (!id.matches(Regex("[0-9a-fA-F]{32}"))) throw AiException("l'ID account non è valido: sono 32 caratteri tra cifre e lettere da a a f")
        val url = "https://api.cloudflare.com/client/v4/accounts/$id/ai/v1/chat/completions"
        return withModels(SPEEDS[speed] ?: SPEEDS.getValue("balanced")) { model ->
            val body = JSONObject()
                .put("model", model)
                .put(
                    "messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", instructions))
                        .put(JSONObject().put("role", "user").put("content", prompt)),
                )
                .put("temperature", 0.7)
                .put("max_tokens", 2048)
            val r = post(connect(url, "Authorization", "Bearer ${token.trim()}", body) { code, raw -> cloudflareError(code, raw, model) })
            // Risposta nel formato di OpenAI; quella nativa di Workers AI mette il testo in result.response.
            // Il ragionamento dei modelli che pensano arriva a parte (reasoning_content) e resta fuori.
            val message = r.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            val text = if (message != null) message.optString("content").takeIf { !message.isNull("content") }.orEmpty()
            else r.optJSONObject("result")?.optString("response").orEmpty()
            if (tidy(text).isEmpty()) throw AiException("l'IA ha risposto senza testo", retryOtherModel = true)
            onModel(model)
            text
        }
    }

    /** Prova i modelli in ordine: se uno non esiste più, è sovraccarico o rifiuta la richiesta, si passa al successivo. */
    private fun <T> withModels(models: List<String>, call: (String) -> T): T {
        var last: AiException? = null
        for (model in models) {
            try {
                return call(model)
            } catch (e: AiException) {
                last = e
                if (!e.retryOtherModel) throw e
            } catch (e: IOException) {
                throw AiException("connessione non riuscita", e.message)
            } catch (e: IllegalArgumentException) {
                // Caratteri non ammessi nell'intestazione: la chiave è stata incollata male.
                throw AiException("la chiave contiene caratteri non validi: copiala di nuovo", e.message)
            }
        }
        throw last ?: AiException("nessun modello disponibile")
    }

    private fun post(c: HttpURLConnection): JSONObject {
        try {
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    /**
     * Apre la richiesta e controlla l'esito: se il servizio rifiuta, [fail] trasforma il codice e il corpo
     * della risposta in un errore che dice perché. Chi chiama chiude la connessione.
     */
    private fun connect(url: String, header: String, value: String, body: JSONObject, fail: (Int, String) -> AiException): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10_000
            // Il riepilogo arriva tutto insieme e un modello che ragiona può metterci un po'.
            c.readTimeout = 90_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty(header, value)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            // Il motivo è nel corpo della risposta: senza leggerlo ogni errore sembrerebbe "chiave sbagliata".
            if (code !in 200..299) throw fail(code, runCatching { c.errorStream.bufferedReader().use { it.readText() }.trim() }.getOrDefault(""))
            return c
        } catch (e: Throwable) {
            c.disconnect()
            throw e
        }
    }

    /** Spiega in italiano un rifiuto di Workers AI. Cloudflare risponde con "errors" o, nel formato di OpenAI, con "error". */
    private fun cloudflareError(code: Int, raw: String, model: String): AiException {
        val o = runCatching { JSONObject(raw) }.getOrNull()
        val detail = (
            o?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                ?: o?.optJSONObject("error")?.optString("message")
                ?: o?.optString("error")
            )?.takeIf { it.isNotBlank() } ?: raw.take(300).takeIf { it.isNotBlank() }
        val quota = listOf("neuron", "allocation").any { detail?.contains(it, ignoreCase = true) == true }
        val name = model.substringAfterLast('/')
        return when {
            code == 401 || code == 403 -> AiException("il token non è valido o non ha il permesso Workers AI, oppure l'ID account non corrisponde", detail)
            code == 429 && quota -> AiException("hai finito i neuroni gratuiti di oggi: si ricaricano all'una di notte (alle due con l'ora legale)", detail)
            code == 429 -> AiException("troppe richieste, riprova tra poco", detail, retryOtherModel = true)
            code == 404 -> AiException("il modello $name non è disponibile, oppure l'ID account è sbagliato", detail, retryOtherModel = true)
            code == 400 -> AiException("richiesta rifiutata da Workers AI", detail, retryOtherModel = true)
            code >= 500 -> AiException("Workers AI è sovraccarico, riprova tra poco", detail, retryOtherModel = true)
            else -> AiException("errore $code del servizio", detail)
        }
    }

    /** Spiega in italiano un rifiuto di Gemini, che resta solo per la voce. */
    private fun geminiError(code: Int, raw: String, model: String): AiException {
        val err = runCatching { (if (raw.startsWith("[")) JSONArray(raw).getJSONObject(0) else JSONObject(raw)).getJSONObject("error") }.getOrNull()
        val detail = err?.optString("message")?.takeIf { it.isNotBlank() }
        val status = err?.optString("status").orEmpty()
        val badKey = detail?.contains("API key", ignoreCase = true) == true
        return when {
            code == 400 && badKey -> AiException("la chiave API non è valida", detail)
            code == 400 && status == "FAILED_PRECONDITION" ->
                AiException("il piano gratuito di Gemini non è disponibile per questa chiave: va attivata la fatturazione su Google AI Studio", detail)
            code == 400 -> AiException("richiesta rifiutata da Gemini", detail)
            code == 401 || code == 403 -> AiException("la chiave non è autorizzata a usare Gemini", detail)
            code == 404 -> AiException("il modello $model non è disponibile", detail, retryOtherModel = true)
            code == 429 -> AiException("limite gratuito raggiunto, riprova tra poco", detail, retryOtherModel = true)
            code >= 500 -> AiException("Gemini è sovraccarico, riprova tra poco", detail, retryOtherModel = true)
            else -> AiException("errore $code del servizio", detail)
        }
    }

    const val SPEECH_RATE = 24_000

    /**
     * Modelli di Workers AI che scrivono bene in italiano. Il piano gratuito dà 10.000 neuroni al giorno:
     * un riepilogo ne consuma all'incirca da 25 a 130, a seconda del modello.
     */
    private const val MISTRAL = "@cf/mistralai/mistral-small-3.1-24b-instruct"
    private const val LLAMA = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
    private const val GEMMA = "@cf/google/gemma-4-26b-a4b-it"

    /**
     * Per ogni velocità, i modelli da provare in ordine. Il tempo di attesa dipende quasi tutto da questo:
     * quelli che scrivono subito rispondono in pochi secondi, quello che ragiona prima ci mette di più.
     */
    private val SPEEDS = mapOf(
        "fast" to listOf(MISTRAL, LLAMA),
        "balanced" to listOf(LLAMA, MISTRAL),
        "careful" to listOf(GEMMA, LLAMA, MISTRAL),
    )

    /** Velocità proposte nelle impostazioni: chiave, nome e che cosa comporta. */
    val SPEED_CHOICES = listOf(
        Triple("fast", "Veloce", "Mistral Small: risponde in pochi secondi, con un testo un po' più semplice."),
        Triple("balanced", "Bilanciato", "Llama 3.3 70B: il modello più grande, buon testo senza attese lunghe."),
        Triple("careful", "Curato", "Gemma 4, che ragiona prima di scrivere: il testo più curato, ma può metterci di più."),
    )
    private val SPEECH_MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
}

/** Errore dell'IA: [message] è la spiegazione breve in italiano, [detail] quello che ha risposto il servizio. */
class AiException(message: String, val detail: String? = null, val retryOtherModel: Boolean = false) : Exception(message)
