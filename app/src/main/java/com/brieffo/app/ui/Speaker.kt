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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
                var audio = cached.takeIf { cachedFor == cacheKey }
                if (audio == null) {
                    audio = try {
                        when (engine) {
                            "local" -> local(voice, clean, speed)
                            "gemini" -> if (key.isBlank()) error("manca la chiave API") else withContext(Dispatchers.IO) { Summary.speech(key, clean, voice) to Summary.SPEECH_RATE }
                            else -> null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val who = if (engine == "local") "Voce locale" else "Voce di Gemini"
                        note = "$who non disponibile (${e.message ?: "errore"}): uso quella del telefono."
                        null
                    }
                    if (audio != null) {
                        cachedFor = cacheKey
                        cached = audio
                    }
                }
                status = Status.PLAYING
                if (audio != null && audio.first.size >= 2) playPcm(audio.first, audio.second, if (engine == "local") 1f else speed)
                else if (!speakWithPhone(clean, speed)) note = "Sul telefono non c'è una voce italiana: installala dalle impostazioni di sintesi vocale di Android."
            } finally {
                status = Status.IDLE
            }
        }
    }

    /** Legge con la voce locale, scaricandola prima se è la prima volta. */
    private suspend fun local(voice: String, text: String, speed: Float): Pair<ByteArray, Int> = withContext(Dispatchers.IO) {
        if (!LocalVoice.installed(ctx, voice)) {
            val label = LocalVoice.voice(voice).label
            note = "Scarico la voce $label, solo questa volta…"
            try {
                LocalVoice.download(ctx, voice, active = { isActive }) { pct -> note = "Scarico la voce $label, solo questa volta: $pct%" }
            } catch (e: InterruptedException) {
                throw CancellationException("download interrotto")
            }
            note = null
        }
        LocalVoice.synthesize(ctx, voice, text, speed)
    }

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
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes)
            .build()
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

    /** Falso se sul telefono non c'è una voce italiana utilizzabile. */
    private suspend fun speakWithPhone(text: String, speed: Float): Boolean {
        val engine = tts ?: suspendCancellableCoroutine { cont ->
            var created: TextToSpeech? = null
            created = TextToSpeech(ctx) { result -> if (cont.isActive) cont.resume(created.takeIf { result == TextToSpeech.SUCCESS }) }
        }?.also { tts = it } ?: return false
        if (engine.setLanguage(Locale.ITALIAN) < TextToSpeech.LANG_AVAILABLE) return false
        // Tra le voci italiane si sceglie la migliore che funziona anche senza rete.
        runCatching {
            engine.voices?.filter { it.locale.language == "it" && !it.isNetworkConnectionRequired }?.maxByOrNull { it.quality }?.let { engine.voice = it }
        }
        engine.setSpeechRate(speed)
        try {
            suspendCancellableCoroutine { cont ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { if (cont.isActive) cont.resume(Unit) }
                })
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "brief") != TextToSpeech.SUCCESS && cont.isActive) cont.resume(Unit)
            }
        } finally {
            engine.stop()
        }
        return true
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
