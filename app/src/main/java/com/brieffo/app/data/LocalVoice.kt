package com.brieffo.app.data

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Voce che gira tutta sul telefono: modelli Piper eseguiti da sherpa-onnx. Il modello si scarica una volta sola,
 * poi la lettura funziona senza rete, senza chiavi e senza limiti.
 */
object LocalVoice {
    data class Voice(val id: String, val label: String, val megabytes: Int)

    val VOICES = listOf(
        Voice("it_IT-paola-medium", "Paola", 64),
        Voice("it_IT-miro-high", "Miro", 64),
        Voice("it_IT-dii-high", "Dii", 64),
    )

    fun voice(id: String) = VOICES.firstOrNull { it.id == id } ?: VOICES.first()

    private fun dir(ctx: Context, id: String) = File(ctx.filesDir, "voices/$id")

    fun installed(ctx: Context, id: String) = File(dir(ctx, id), READY).exists()

    /**
     * Scarica e scompatta il modello della voce. [onProgress] riceve la percentuale scaricata;
     * [active] viene interrogata di continuo e, se diventa falsa, il download si interrompe.
     */
    fun download(ctx: Context, id: String, active: () -> Boolean, onProgress: (Int) -> Unit) {
        val target = dir(ctx, id)
        val tmp = File(ctx.filesDir, "voices/$id.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val c = URL("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$id.tar.bz2").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (c.responseCode !in 200..299) error("download non riuscito (errore ${c.responseCode})")
            val total = c.contentLengthLong
            var read = 0L
            var shown = -1
            val counted = object : FilterInputStream(c.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (!active()) throw InterruptedException()
                    return super.read(b, off, len).also { n ->
                        if (n > 0 && total > 0) {
                            read += n
                            val pct = (read * 100 / total).toInt()
                            if (pct != shown) { shown = pct; onProgress(pct) }
                        }
                    }
                }
                override fun read(): Int = super.read().also { if (it >= 0) read++ }
            }
            TarArchiveInputStream(BZip2CompressorInputStream(counted.buffered(1 shl 16))).use { tar ->
                val root = tmp.canonicalPath + File.separator
                while (true) {
                    val entry = tar.nextEntry ?: break
                    // Nell'archivio tutto sta dentro una cartella con il nome del modello: qui si toglie.
                    val name = entry.name.substringAfter('/', "")
                    if (name.isEmpty()) continue
                    val out = File(tmp, name)
                    if (!out.canonicalPath.startsWith(root)) continue
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { (tar as InputStream).copyTo(it, 1 shl 16) }
                    }
                }
            }
            if (tmp.listFiles().orEmpty().none { it.name.endsWith(".onnx") }) error("archivio della voce incompleto")
            File(tmp, READY).writeText("ok")
            release()
            target.deleteRecursively()
            if (!tmp.renameTo(target)) error("non riesco a salvare la voce")
        } finally {
            c.disconnect()
            tmp.deleteRecursively()
        }
    }

    /** Cancella tutte le voci scaricate e libera la memoria. */
    fun deleteAll(ctx: Context) {
        release()
        File(ctx.filesDir, "voices").deleteRecursively()
    }

    private var loadedId: String? = null
    private var engine: OfflineTts? = null

    /** Legge [text] con la voce [id] già scaricata: restituisce l'audio (PCM a 16 bit, mono) e i campioni al secondo. */
    @Synchronized
    fun synthesize(ctx: Context, id: String, text: String, speed: Float): Pair<ByteArray, Int> {
        // Caricare il modello richiede qualche secondo: resta in memoria finché non si cambia voce.
        val tts = engine?.takeIf { loadedId == id } ?: run {
            release()
            val d = dir(ctx, id)
            val model = d.listFiles().orEmpty().firstOrNull { it.name.endsWith(".onnx") } ?: error("voce non scaricata")
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = model.absolutePath,
                        tokens = File(d, "tokens.txt").absolutePath,
                        dataDir = File(d, "espeak-ng-data").absolutePath,
                    ),
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                ),
            )
            OfflineTts(config = config).also { engine = it; loadedId = id }
        }
        val audio = tts.generate(text = text, sid = 0, speed = speed)
        val pcm = ByteArray(audio.samples.size * 2)
        audio.samples.forEachIndexed { i, f ->
            val v = (f.coerceIn(-1f, 1f) * 32767).toInt()
            pcm[i * 2] = v.toByte()
            pcm[i * 2 + 1] = (v shr 8).toByte()
        }
        return pcm to audio.sampleRate
    }

    @Synchronized
    private fun release() {
        runCatching { engine?.release() }
        engine = null
        loadedId = null
    }

    private const val READY = ".ready"
}
