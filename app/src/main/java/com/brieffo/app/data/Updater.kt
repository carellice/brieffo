package com.brieffo.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Aggiornamenti dell'app: Brieffo non è sul Play Store, le nuove versioni escono come release su GitHub. */
object Updater {
    private const val REPO = "carellice/brieffo"

    data class Release(val version: String, val apkUrl: String, val bytes: Long)

    /** Versione installata, per esempio "1.10". */
    fun installed(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    /** L'ultima release pubblicata, oppure null se non ha un APK allegato. */
    fun latest(): Release? {
        val r = JSONObject(httpGet("https://api.github.com/repos/$REPO/releases/latest", mapOf("Accept" to "application/vnd.github+json")))
        val assets = r.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return Release(r.optString("tag_name").removePrefix("v"), a.getString("browser_download_url"), a.optLong("size"))
            }
        }
        return null
    }

    /** Vero se [candidate] è più recente di [current]: si confrontano i numeri uno a uno, così 1.10 viene dopo 1.9. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Scarica l'APK della release; [onProgress] riceve la percentuale e [active], se diventa falsa, interrompe. */
    fun download(ctx: Context, release: Release, active: () -> Boolean, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "Brieffo-${release.version}.apk")
        val c = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (c.responseCode !in 200..299) error("download non riuscito (errore ${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.bytes
            var read = 0L
            var shown = -1
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        if (!active()) throw InterruptedException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt().coerceAtMost(100)
                            if (pct != shown) { shown = pct; onProgress(pct) }
                        }
                    }
                }
            }
            if (release.bytes > 0 && file.length() != release.bytes) error("file scaricato incompleto")
            return file
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    /** Passa l'APK all'installatore di Android, che chiede conferma e sostituisce l'app tenendo i dati. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
