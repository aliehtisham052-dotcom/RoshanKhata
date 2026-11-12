package com.innovation313.roshankhata.ui

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.innovation313.roshankhata.data.PaymentNotice
import java.util.Locale

/**
 * Hudhud's voice (12 Nov 2026): the phone's own speech (TextToSpeech), as the payment voice uses it — only a voice
 * that is on the phone speaks, never one that needs the network (it would send a customer's name and an amount to
 * the engine's server: [PaymentNotice.offlineVoice]). He speaks the app's language; Urdu and Roman Urdu are said in
 * an Urdu voice from the Urdu words (an Urdu voice cannot read Latin letters). A phone without such a voice says
 * nothing ([speak]'s noVoice), and the sheet says why once.
 *
 * Played as an assistant's answer (USAGE_ASSISTANT; media before Android 8), so the volume keys set it while the
 * sheet is open. Each answer stops the one before it; the sheet shuts the engine when it closes.
 */
class HudhudVoice(context: Context) {

    /** What the sheet hears of the speech, on the main thread. */
    interface Listener {
        fun onStart() {}
        /** A word begins (Android 8+): Hudhud bobs with it. */
        fun onWord() {}
        fun onDone() {}
    }

    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val waiting = ArrayList<(TextToSpeech?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var current: String? = null
    @Volatile private var listener: Listener? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (Build.VERSION.SDK_INT >= 26) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val progress = object : UtteranceProgressListener() {
        private fun ours(id: String?) = id != null && id == current

        override fun onStart(utteranceId: String?) {
            if (ours(utteranceId)) main.post { listener?.onStart() }
        }

        override fun onDone(utteranceId: String?) {
            if (ours(utteranceId)) main.post { finish(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (ours(utteranceId)) main.post { finish(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (ours(utteranceId)) main.post { finish(utteranceId) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            if (ours(utteranceId)) main.post { finish(utteranceId) }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            if (ours(utteranceId)) main.post { listener?.onWord() }
        }
    }

    /** The utterance [id] is over: its listener hears so once. */
    private fun finish(id: String?) {
        if (id == null || id != current) return
        current = null
        val l = listener
        listener = null
        l?.onDone()
    }

    /** Runs [then] with the engine once it is up, or with null when the phone has none. Main thread. */
    private fun withEngine(then: (TextToSpeech?) -> Unit) {
        if (failed) { then(null); return }
        if (ready) { then(tts); return }
        waiting += then
        if (tts != null) return
        tts = runCatching {
            TextToSpeech(app) { status ->
                main.post {
                    ready = status == TextToSpeech.SUCCESS
                    failed = !ready
                    if (ready) runCatching {
                        tts?.setAudioAttributes(attributes)
                        tts?.setOnUtteranceProgressListener(progress)
                    }
                    val all = ArrayList(waiting); waiting.clear()
                    all.forEach { it(if (ready) tts else null) }
                }
            }
        }.getOrNull()
        if (tts == null) {
            failed = true
            val all = ArrayList(waiting); waiting.clear()
            all.forEach { it(null) }
        }
    }

    /** Wakes the engine before the first answer, so that answer is not kept waiting for it. */
    fun warmUp() = withEngine { }

    /**
     * Says what [textIn] writes, with a context in the voice's language; [listener] hears it start, each word and
     * the end. [noVoice] runs instead when this phone has no voice for the language on it.
     */
    fun speak(textIn: (Context) -> String, listener: Listener, noVoice: () -> Unit) {
        withEngine { t ->
            if (t == null) { noVoice(); return@withEngine }
            val appLocale = AppLanguage.context(app).resources.configuration.locales[0]
            val urdu = appLocale.language == "ur"
            val speakIn = if (urdu) Locale("ur", "PK") else appLocale
            val chosen = offlineVoice(t, speakIn)
            val usable = chosen != null && runCatching { t.setVoice(chosen) == TextToSpeech.SUCCESS }.getOrDefault(false)
            if (!usable) { noVoice(); return@withEngine }
            val words = if (urdu) localized(Locale("ur")) else AppLanguage.context(app)
            val text = runCatching { textIn(words) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@withEngine
            stopNow()
            val id = "hudhud-" + System.nanoTime()
            this.listener = listener
            current = id
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) finish(id)
        }
    }

    /** Stops what is being said; its listener hears the end. */
    fun stop() {
        stopNow()
    }

    private fun stopNow() {
        val id = current
        runCatching { tts?.stop() }
        if (id != null) finish(id)
    }

    /** Lets the engine go. */
    fun shutdown() {
        current = null
        listener = null
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ready = false; failed = false
        waiting.clear()
    }

    /** The voice of [locale] this engine has on the phone, never one needing the network. */
    private fun offlineVoice(t: TextToSpeech, locale: Locale): android.speech.tts.Voice? {
        val voices = runCatching { t.voices }.getOrNull() ?: return null
        return runCatching {
            PaymentNotice.offlineVoice(voices, locale.language, locale.country) { v ->
                PaymentNotice.VoiceFacts(
                    language = v.locale.language,
                    country = v.locale.country,
                    needsNetwork = v.isNetworkConnectionRequired,
                    notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in v.features.orEmpty(),
                    quality = v.quality
                )
            }
        }.getOrNull()
    }

    private fun localized(locale: Locale): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(locale)
        return app.createConfigurationContext(config)
    }
}
