package com.brieffo.app.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object NewsRepo {
    private class Parsed(val title: String, val items: List<NewsItem>)

    suspend fun load(feeds: List<Feed>, limit: Int = 45): List<NewsItem> = coroutineScope {
        val lists = feeds.filter { it.enabled }.map { f ->
            async(Dispatchers.IO) { runCatching { parse(httpGet(f.url), f.name, f.url).items }.getOrDefault(emptyList()) }
        }.awaitAll()
        // Un titolo a testa per fonte, a giro, così nessun feed monopolizza la lista.
        val out = LinkedHashMap<String, NewsItem>()
        var i = 0
        while (out.size < limit && lists.any { i < it.size }) {
            lists.forEach { f -> f.getOrNull(i)?.let { if (out.size < limit) out.putIfAbsent(it.title, it) } }
            i++
        }
        // Se il feed non porta la foto, l'anteprima si legge dai meta tag della pagina dell'articolo.
        // Subito solo per le prime notizie; per le altre la scheda la chiede quando la riga compare.
        out.values.mapIndexed { n, item ->
            async(Dispatchers.IO) { if (n < 8 && item.image == null) item.copy(image = preview(item.link)) else item }
        }.awaitAll()
    }

    /**
     * Trasforma ciò che l'utente ha scritto in una fonte utilizzabile. Accetta l'indirizzo di un feed RSS/Atom
     * oppure quello di un sito: in quel caso cerca il feed dichiarato nella pagina. Null se non trova un feed; il secondo valore dice se il feed ha notizie degli ultimi giorni.
     */
    fun resolve(input: String): Pair<Feed, Boolean>? {
        val url = input.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val body = runCatching { httpGet(url) }.getOrNull() ?: return null
        // Un feed fermo da tempo resta valido: lo si accetta e si avvisa che non ha notizie recenti.
        fun accept(parsed: Parsed, at: String): Pair<Feed, Boolean> {
            val cutoff = LocalDateTime.now().minusDays(3)
            return Feed(nameFor(parsed.title, at), at) to parsed.items.any { it.published == null || it.published.isAfter(cutoff) }
        }
        runCatching { parse(body, "", url, recentOnly = false) }.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { return accept(it, url) }
        // Non è un feed: è una pagina web. I siti dichiarano i propri feed con <link rel="alternate">.
        val candidates = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(body)
            .map { it.value }
            .filter { Regex("type=[\"'](application/(rss|atom)\\+xml)[\"']", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .mapNotNull { Regex("href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            .map { runCatching { URL(URL(url), it.replace("&amp;", "&")).toString() }.getOrNull() }
            .filterNotNull().distinct().take(4).toList()
            // Molti siti non dichiarano il feed nella pagina ma lo pubblicano a uno degli indirizzi più comuni.
            .plus(listOf("/feed", "/feed/", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml", "/feed/rss", "/rss/homepage.xml")
                .mapNotNull { path -> runCatching { URL(URL(url), path).toString() }.getOrNull() })
        for (candidate in candidates) {
            val parsed = runCatching { parse(httpGet(candidate), "", candidate, recentOnly = false) }.getOrNull() ?: continue
            if (parsed.items.isNotEmpty()) return accept(parsed, candidate)
        }
        return null
    }

    private fun nameFor(title: String, url: String) =
        title.substringBefore(" - ").substringBefore(" | ").trim().take(28).ifBlank { runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("Notizie") }

    private fun parseDate(raw: String): LocalDateTime? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val zoned = runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(t, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }.getOrNull()
        return zoned?.withZoneSameInstant(ZoneId.systemDefault())?.toLocalDateTime()
    }

    /** Legge sia RSS (item, pubDate) sia Atom (entry, published/updated). */
    private fun parse(xml: String, topic: String, base: String, recentOnly: Boolean = true): Parsed {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val items = mutableListOf<NewsItem>()
        var channel = ""
        var title = ""
        var link = ""
        var date = ""
        var image: String? = null
        var inItem = false
        // nextText fallisce se il tag contiene altri tag (titoli Atom in XHTML): in quel caso il campo resta vuoto.
        fun text() = runCatching { p.nextText() }.getOrDefault("")
        fun imageAttr() = p.getAttributeValue(null, "url")?.takeIf { it.startsWith("http") }
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item", "entry" -> { inItem = true; title = ""; link = ""; date = ""; image = null }
                    "title" -> if (inItem) title = text() else if (channel.isEmpty()) channel = text()
                    "link" -> if (inItem) {
                        val href = p.getAttributeValue(null, "href")
                        val rel = p.getAttributeValue(null, "rel")
                        if (href != null) { if (rel == null || rel == "alternate") link = href } else link = text()
                    }
                    "pubDate", "published", "dc:date" -> if (inItem) date = text()
                    "updated" -> if (inItem && date.isEmpty()) date = text()
                    "media:thumbnail" -> if (inItem && image == null) image = imageAttr()
                    "media:content", "enclosure" -> if (inItem && image == null) {
                        val type = p.getAttributeValue(null, "type") ?: p.getAttributeValue(null, "medium") ?: ""
                        if (type.startsWith("image")) image = imageAttr()
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) {
                inItem = false
                val clean = title.replace(Regex("<[^>]+>"), "").trim('+', ' ').replace(Regex("\\s+"), " ").trim()
                val url = runCatching { URL(URL(base), link.trim()).toString() }.getOrDefault("")
                if (clean.isNotEmpty() && url.startsWith("http")) {
                    val published = parseDate(date)
                    // Alcuni feed restano online ma non vengono più aggiornati: le notizie vecchie si scartano.
                    if (!recentOnly || published == null || published.isAfter(LocalDateTime.now().minusDays(3))) {
                        items += NewsItem(clean, topic, url, published, image)
                    }
                }
            }
            ev = runCatching { p.next() }.getOrDefault(XmlPullParser.END_DOCUMENT)
        }
        return Parsed(channel.trim(), items)
    }

    private val previews = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Immagine di anteprima di un articolo, con memoria: ogni pagina si scarica una volta sola. Stringa vuota = nessuna. */
    fun preview(link: String): String? =
        previews.getOrPut(link) { runCatching { previewImage(link) }.getOrNull() ?: "" }.ifEmpty { null }

    private val ogImage = listOf(
        Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']"""),
        Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']"""),
    )

    private fun previewImage(link: String): String? {
        val c = URL(link).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6000
            c.readTimeout = 6000
            c.setRequestProperty("User-Agent", "Brieffo/1.0 (Android; personal app)")
            // Il tag sta nell'<head>: bastano i primi KB, senza scaricare tutta la pagina.
            val head = CharArray(48_000)
            val read = c.inputStream.bufferedReader().use { r ->
                var n = 0
                while (n < head.size) { val k = r.read(head, n, head.size - n); if (k < 0) break; n += k }
                n
            }
            val html = String(head, 0, read)
            return ogImage.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }
                ?.replace("&amp;", "&")
                // Alcune pagine indicano come "immagine" la home del sito: serve un indirizzo con un vero percorso.
                ?.takeIf { it.startsWith("https://") && (runCatching { URL(it).path.length > 1 }.getOrDefault(false)) }
        } finally {
            c.disconnect()
        }
    }
}

object ExtrasRepo {
    fun markets(): List<Market> {
        val out = mutableListOf<Market>()
        runCatching {
            val r = JSONObject(httpGet("https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=eur&include_24hr_change=true"))
            listOf("bitcoin" to "Bitcoin", "ethereum" to "Ethereum").forEach { (id, label) ->
                val o = r.getJSONObject(id)
                out += Market(label, String.format(Locale.ITALY, "%,.0f €", o.getDouble("eur")), o.optDouble("eur_24h_change").takeIf { !it.isNaN() })
            }
        }
        runCatching {
            val rates = JSONObject(httpGet("https://api.frankfurter.dev/v1/latest?base=EUR&symbols=USD,GBP,CHF")).getJSONObject("rates")
            listOf("USD", "GBP", "CHF").forEach {
                out += Market("EUR/$it", String.format(Locale.ITALY, "%.4f", rates.getDouble(it)), null)
            }
        }
        return out
    }

    fun onThisDay(date: LocalDate = LocalDate.now()): OnThisDay? {
        val r = JSONObject(httpGet("https://it.wikipedia.org/api/rest_v1/feed/onthisday/selected/%02d/%02d".format(date.monthValue, date.dayOfMonth)))
        val list = r.optJSONArray("selected") ?: return null
        if (list.length() == 0) return null
        // Scelta stabile per l'intera giornata, diversa di anno in anno.
        val o = list.getJSONObject(date.year % list.length())
        return OnThisDay(o.optInt("year"), o.optString("text"))
    }
}
