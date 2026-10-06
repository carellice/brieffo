package com.brieffo.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.brieffo.app.data.LocalVoice
import com.brieffo.app.data.Prefs
import com.brieffo.app.data.Summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Legge un testo ad alta voce con il motore scelto nelle impostazioni: voce locale (scaricata una volta,
 * poi senza rete), voce di Gemini oppure sintesi di Android, che fa anche da riserva se le altre falliscono.
 */
class Speaker(private val ctx: Context, private val scope: CoroutineScope) {
    enum class Status { IDLE, LOADING, PLAYING }

    var status by mutableStateOf(Status.IDLE)
        private set

    /** Avviso da mostrare sotto il pulsante, per esempio quando si ripiega sulla voce del telefono. */
    var note by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null
    private var tts: TextToSpeech? = null

    // L'ultimo audio generato si tiene da parte: riascoltare lo stesso testo è immediato e non consuma quota.
    private var cachedFor: String? = null
    private var cached: Pair<ByteArray, Int>? = null

    /** Avvia la lettura, oppure la ferma se è già in corso. */
    fun toggle(text: String) {
        if (status != Status.IDLE) return stop()
        val prefs = Prefs(ctx)
        val clean = Summary.speakable(text)
        if (clean.isBlank()) return
        val speed = prefs.speechSpeed / 100f
        val engine = prefs.speechEngine
        val key = prefs.geminiKey
        val voice = if (engine == "local") prefs.localVoice else prefs.speechVoice
        // Nella voce locale la velocità fa parte dell'audio generato, in quella di Gemini si applica riproducendo.
        val cacheKey = "$engine|$voice|${if (engine == "local") speed else 1f}|$clean"
        note = null
        job = scope.launch {
            try {
                status = Status.LOADING
                val ready = cached.takeIf { cachedFor == cacheKey }
                val played = try {
                    when {
                        ready != null -> {
                            status = Status.PLAYING
                            playPcm(ready.first, ready.second, if (engine == "local") 1f else speed)
                            true
                        }
                        engine == "local" -> {
                            remember(cacheKey, playLocal(voice, clean, speed))
                            true
                        }
                        engine == "gemini" -> {
                            if (key.isBlank()) error("manca la chiave API")
                            val audio = withContext(Dispatchers.IO) { Summary.speech(key, clean, voice) } to Summary.SPEECH_RATE
                            remember(cacheKey, audio)
                            status = Status.PLAYING
                            playPcm(audio.first, audio.second, speed)
                            true
                        }
                        else -> false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val who = if (engine == "local") "Voce locale" else "Voce di Gemini"
                    note = "$who non disponibile (${e.message ?: "errore"}): uso quella del telefono."
                    false
                }
                if (!played) {
                    status = Status.PLAYING
                    speakWithPhone(clean, speed)?.let { problem -> note = listOfNotNull(note, problem).joinToString(" ") }
                }
            } finally {
                status = Status.IDLE
            }
        }
    }

    private fun remember(key: String, audio: Pair<ByteArray, Int>) {
        cachedFor = key
        cached = audio
    }

    /**
     * Legge con la voce locale, scaricando prima il modello se è la prima volta. Il testo si genera una frase
     * alla volta e si comincia a sentire appena è pronta la prima, senza aspettare tutto il resto.
     * Restituisce l'audio completo, da tenere per un eventuale riascolto.
     */
    private suspend fun playLocal(voice: String, text: String, speed: Float): Pair<ByteArray, Int> = coroutineScope {
        if (!LocalVoice.installed(ctx)) withContext(Dispatchers.IO) {
            note = "Scarico la voce locale (${LocalVoice.MEGABYTES} MB), solo questa volta…"
            try {
                LocalVoice.download(ctx, active = { isActive }) { pct -> note = "Scarico la voce locale (${LocalVoice.MEGABYTES} MB), solo questa volta: $pct%" }
            } catch (e: InterruptedException) {
                throw CancellationException("download interrotto")
            }
            note = null
        }
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val chunks = Channel<Pair<ByteArray, Int>>(Channel.UNLIMITED)
        val producer = launch(Dispatchers.IO) {
            try {
                for (sentence in sentences) {
                    ensureActive()
                    chunks.send(LocalVoice.synthesize(ctx, voice, sentence, speed))
                }
                chunks.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                chunks.close(e)
            }
        }
        val all = ByteArrayOutputStream()
        var track: AudioTrack? = null
        var rate = 0
        try {
            for ((pcm, sampleRate) in chunks) {
                val t = track ?: newTrack(sampleRate, AudioTrack.MODE_STREAM, AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4).also {
                    track = it
                    rate = sampleRate
                    it.play()
                    status = Status.PLAYING
                }
                all.write(pcm)
                // A piccoli pezzi, così fermando la lettura non si resta bloccati nella scrittura.
                withContext(Dispatchers.IO) {
                    var offset = 0
                    while (offset < pcm.size && isActive) {
                        val n = t.write(pcm, offset, minOf(8192, pcm.size - offset))
                        if (n <= 0) break
                        offset += n
                    }
                }
            }
            // Finito di scrivere, si aspetta che l'altoparlante arrivi in fondo.
            track?.let { t ->
                val frames = all.size() / 2
                var waited = 0
                while (t.playbackHeadPosition < frames && waited < 5000) { delay(50); waited += 50 }
            }
            all.toByteArray() to rate
        } finally {
            producer.cancel()
            track?.let { t ->
                runCatching { t.pause(); t.flush() }
                t.release()
            }
        }
    }

    private fun newTrack(sampleRate: Int, mode: Int, bufferBytes: Int) = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        .setAudioFormat(
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        )
        .setTransferMode(mode)
        .setBufferSizeInBytes(bufferBytes)
        .build()

    fun stop() {
        job?.cancel()
        job = null
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private suspend fun playPcm(pcm: ByteArray, sampleRate: Int, speed: Float) {
        val bytes = pcm.size - pcm.size % 2
        val track = newTrack(sampleRate, AudioTrack.MODE_STATIC, bytes)
        try {
            track.write(pcm, 0, bytes)
            // Cambia solo la velocità: il tono della voce resta quello originale.
            runCatching { track.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f) }
            track.play()
            val millis = bytes / 2 * 1000L / sampleRate
            delay((millis / speed).toLong() + 250)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Legge con la sintesi vocale di Android. Restituisce null se è andata bene, altrimenti il motivo da mostrare. */
    private suspend fun speakWithPhone(text: String, speed: Float): String? {
        val engine = tts ?: suspendCancellableCoroutine { cont ->
            var created: TextToSpeech? = null
            created = TextToSpeech(ctx) { result -> if (cont.isActive) cont.resume(created.takeIf { result == TextToSpeech.SUCCESS }) }
        }?.also { tts = it } ?: return "Sul telefono non trovo un motore di sintesi vocale: installa \"Sintesi vocale Google\" dal Play Store."
        val language = engine.setLanguage(Locale.ITALY)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            return "Sul telefono manca la voce italiana: scaricala da Impostazioni di Android > Sintesi vocale."
        }
        // Tra le voci italiane già scaricate si sceglie la migliore che funziona senza rete. Quelle solo elencate
        // ma non installate vanno scartate: sceglierne una farebbe restare la lettura in silenzio.
        runCatching {
            engine.voices
                ?.filter { v ->
                    v.locale.language == "it" && !v.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
                }
                ?.maxByOrNull { it.quality }
                ?.let { engine.voice = it }
        }
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        engine.setSpeechRate(speed)
        try {
            return suspendCancellableCoroutine { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(null) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume("La voce del telefono non è riuscita a leggere il testo.") }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (cont.isActive) cont.resume("La voce del telefono non è riuscita a leggere il testo (errore $errorCode).")
                    }
                })
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "brief") != TextToSpeech.SUCCESS && cont.isActive) {
                    cont.resume("La voce del telefono ha rifiutato il testo.")
                }
            }
        } finally {
            engine.stop()
        }
    }
}

/** Un lettore legato alla schermata: uscendo la lettura si ferma. */
@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val speaker = remember { Speaker(context, scope) }
    DisposableEffect(Unit) { onDispose { speaker.release() } }
    return speaker
}
