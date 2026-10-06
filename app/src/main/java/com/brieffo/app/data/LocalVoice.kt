package com.brieffo.app.data

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Voce che gira tutta sul telefono: il modello Supertonic 3 eseguito da sherpa-onnx. Si scarica una volta sola
 * (un unico modello per tutte le voci), poi la lettura funziona senza rete, senza chiavi e senza limiti.
 */
object LocalVoice {
    data class Voice(val id: String, val label: String)

    /** Le dieci voci del modello, nell'ordine in cui lui le numera: prima le femminili, poi le maschili. */
    val VOICES = (1..5).map { Voice("${it - 1}", "Donna $it") } + (1..5).map { Voice("${it + 4}", "Uomo $it") }

    /** Dimensione del download, da mostrare prima di avviarlo. */
    const val MEGABYTES = 123

    fun voice(id: String) = VOICES.firstOrNull { it.id == id } ?: VOICES.first()

    private fun root(ctx: Context) = File(ctx.filesDir, "voices")
    private fun dir(ctx: Context) = File(root(ctx), MODEL)

    fun installed(ctx: Context) = File(dir(ctx), READY).exists()

    /**
     * Scarica e scompatta il modello. [onProgress] riceve la percentuale scaricata;
     * [active] viene interrogata di continuo e, se diventa falsa, il download si interrompe.
     */
    fun download(ctx: Context, active: () -> Boolean, onProgress: (Int) -> Unit) {
        val target = dir(ctx)
        val tmp = File(root(ctx), "$MODEL.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val c = URL("https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL.tar.bz2").openConnection() as HttpURLConnection
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
                val base = tmp.canonicalPath + File.separator
                while (true) {
                    val entry = tar.nextEntry ?: break
                    // Nell'archivio tutto sta dentro una cartella con il nome del modello: qui si toglie.
                    val name = entry.name.substringAfter('/', "")
                    if (name.isEmpty()) continue
                    val out = File(tmp, name)
                    if (!out.canonicalPath.startsWith(base)) continue
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { (tar as InputStream).copyTo(it, 1 shl 16) }
                    }
                }
            }
            if (FILES.any { !File(tmp, it).exists() }) error("archivio della voce incompleto")
            File(tmp, READY).writeText("ok")
            release()
            // Via anche i modelli delle versioni precedenti dell'app, che non servono più.
            root(ctx).listFiles().orEmpty().filter { it != tmp }.forEach { it.deleteRecursively() }
            if (!tmp.renameTo(target)) error("non riesco a salvare la voce")
        } finally {
            c.disconnect()
            tmp.deleteRecursively()
        }
    }

    /** Cancella il modello scaricato e libera la memoria. */
    fun deleteAll(ctx: Context) {
        release()
        root(ctx).deleteRecursively()
    }

    private var engine: OfflineTts? = null

    /** Legge [text] con la voce [voiceId]: restituisce l'audio (PCM a 16 bit, mono) e i campioni al secondo. */
    @Synchronized
    fun synthesize(ctx: Context, voiceId: String, text: String, speed: Float): Pair<ByteArray, Int> {
        // Caricare il modello richiede qualche secondo: poi resta in memoria.
        val tts = engine ?: run {
            val d = dir(ctx)
            if (!installed(ctx)) error("voce non scaricata")
            fun path(name: String) = File(d, name).absolutePath
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = OfflineTtsSupertonicModelConfig(
                        durationPredictor = path("duration_predictor.int8.onnx"),
                        textEncoder = path("text_encoder.int8.onnx"),
                        vectorEstimator = path("vector_estimator.int8.onnx"),
                        vocoder = path("vocoder.int8.onnx"),
                        ttsJson = path("tts.json"),
                        unicodeIndexer = path("unicode_indexer.bin"),
                        voiceStyle = path("voice.bin"),
                    ),
                    numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                ),
            )
            OfflineTts(config = config).also { engine = it }
        }
        val audio = tts.generateWithConfig(
            text,
            GenerationConfig(speed = speed, sid = voice(voiceId).id.toInt(), numSteps = 6, extra = mapOf("lang" to "it")),
        )
        if (audio.samples.isEmpty()) error("il modello non ha prodotto audio")
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
    }

    private const val MODEL = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
    private const val READY = ".ready"
    private val FILES = listOf(
        "duration_predictor.int8.onnx", "text_encoder.int8.onnx", "vector_estimator.int8.onnx", "vocoder.int8.onnx",
        "tts.json", "unicode_indexer.bin", "voice.bin",
    )
}
