package com.brieffo.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Una pagina di Notion condivisa con l'integrazione dell'utente. */
data class NotionPage(val id: String, val title: String, val icon: String = "")

/** Una casella della to-do list di Notion. */
data class Todo(val id: String, val text: String, val checked: Boolean)

class NotionException(message: String) : Exception(message)

/**
 * La to-do list dell'utente su Notion: una pagina con le caselle da spuntare. Si usa la chiave di un'integrazione
 * personale (come per Gemini): Brieffo vede solo le pagine che l'utente ha condiviso con quell'integrazione.
 */
object NotionRepo {
    private const val API = "https://api.notion.com/v1"
    private const val VERSION = "2026-03-11"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    private fun call(token: String, method: String, path: String, body: JSONObject? = null): JSONObject {
        val req = Request.Builder()
            .url(API + path)
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Notion-Version", VERSION)
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
            .build()
        val res = try { client.newCall(req).execute() } catch (e: IOException) { throw NotionException("connessione non riuscita") }
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val code = runCatching { JSONObject(text).optString("code") }.getOrDefault("")
                throw NotionException(
                    when {
                        it.code == 401 -> "la chiave di Notion non è valida"
                        it.code == 404 || code == "object_not_found" -> "la pagina non è condivisa con l'integrazione: in Notion aprila, poi ⋯ > Connessioni e aggiungi l'integrazione"
                        it.code == 403 -> "l'integrazione non ha il permesso di modificare: in Notion attiva «Aggiornare i contenuti» tra le sue funzionalità"
                        it.code == 429 -> "troppe richieste a Notion, riprova tra poco"
                        else -> "errore ${it.code} di Notion"
                    }
                )
            }
            return JSONObject(text)
        }
    }

    private fun plain(rich: org.json.JSONArray?) = (0 until (rich?.length() ?: 0)).joinToString("") { rich!!.getJSONObject(it).optString("plain_text") }.trim()

    /** Le pagine che l'utente ha condiviso con l'integrazione, le più recenti prima. */
    fun pages(token: String): List<NotionPage> {
        val body = JSONObject()
            .put("filter", JSONObject().put("property", "object").put("value", "page"))
            .put("sort", JSONObject().put("direction", "descending").put("timestamp", "last_edited_time"))
            .put("page_size", 50)
        val results = call(token, "POST", "/search", body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            if (o.optBoolean("archived") || o.optBoolean("in_trash")) return@mapNotNull null
            val props = o.optJSONObject("properties") ?: JSONObject()
            val title = props.keys().asSequence().map { props.getJSONObject(it) }.firstOrNull { it.optString("type") == "title" }?.let { plain(it.optJSONArray("title")) }
            val icon = o.optJSONObject("icon")?.takeIf { it.optString("type") == "emoji" }?.optString("emoji").orEmpty()
            NotionPage(o.getString("id"), title?.ifBlank { null } ?: "Senza titolo", icon)
        }
    }

    /** Il titolo di una pagina, per mostrarla dopo averla scelta. */
    fun page(token: String, pageId: String): NotionPage {
        val o = call(token, "GET", "/pages/$pageId")
        val props = o.optJSONObject("properties") ?: JSONObject()
        val title = props.keys().asSequence().map { props.getJSONObject(it) }.firstOrNull { it.optString("type") == "title" }?.let { plain(it.optJSONArray("title")) }
        return NotionPage(pageId, title?.ifBlank { null } ?: "Senza titolo", o.optJSONObject("icon")?.optString("emoji").orEmpty())
    }

    /**
     * Le caselle della pagina, nell'ordine in cui sono scritte. Si guarda anche dentro a colonne, toggle
     * e richiami, perché in Notion una lista può stare annidata lì; le caselle dentro altre caselle no.
     */
    fun todos(token: String, pageId: String): List<Todo> {
        val out = mutableListOf<Todo>()
        fun walk(blockId: String, depth: Int) {
            var cursor: String? = null
            do {
                val r = call(token, "GET", "/blocks/$blockId/children?page_size=100" + (cursor?.let { "&start_cursor=$it" } ?: ""))
                val blocks = r.optJSONArray("results") ?: break
                for (i in 0 until blocks.length()) {
                    val b = blocks.getJSONObject(i)
                    val type = b.optString("type")
                    if (type == "to_do") {
                        val td = b.optJSONObject("to_do") ?: continue
                        val text = plain(td.optJSONArray("rich_text"))
                        if (text.isNotBlank()) out += Todo(b.getString("id"), text, td.optBoolean("checked"))
                    } else if (b.optBoolean("has_children") && depth < 2 && type in setOf("column_list", "column", "toggle", "callout", "quote", "synced_block", "heading_1", "heading_2", "heading_3")) {
                        walk(b.getString("id"), depth + 1)
                    }
                }
                cursor = r.optString("next_cursor").takeIf { r.optBoolean("has_more") && it.isNotBlank() }
            } while (cursor != null && out.size < 500)
        }
        walk(pageId, 0)
        return out
    }

    /** Spunta (o toglie la spunta a) una casella direttamente su Notion. */
    fun setChecked(token: String, blockId: String, checked: Boolean) {
        call(token, "PATCH", "/blocks/$blockId", JSONObject().put("to_do", JSONObject().put("checked", checked)))
    }
}
