package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.innovation313.roshankhata.data.HudhudSpeech
import com.innovation313.roshankhata.data.PaymentNotice
import java.util.Locale

/**
 * Hudhud's voice (12 Nov 2026): the phone's own speech (TextToSpeech), as the payment voice uses it — only a voice
 * that is on the phone speaks, never one that needs the network (it would send a customer's name and an amount to
 * the engine's server: [PaymentNotice.offlineVoice]). He speaks the app's language; Urdu and Roman Urdu are said in
 * an Urdu voice from the Urdu words (an Urdu voice is made for the Urdu script). A phone without such a voice says
 * nothing ([speak]'s noVoice), and the sheet says why once.
 *
 * 13 Nov 2026 (the owner: better voices, "achhe tareeqe se samjha sake"): the voice the owner chose among the phone's
 * own ([HudhudPrefs.voiceName], else the best there is), at the pace and pitch of his style ([HudhudStyle]); an
 * answer is said a sentence at a time with a short pause between ([HudhudSpeech.parts]). An answer the phone took
 * and did not play — an error, or no start at all — is tried once more after a moment, and only then reported
 * ([Listener.onFailed]): he never goes silent without saying so.
 *
 * Played as an assistant's answer (USAGE_ASSISTANT; media before Android 8), so the volume keys set it while the
 * sheet is open. Each answer stops the one before it; the sheet shuts the engine when it closes. Main thread only.
 */
class HudhudVoice(context: Context) {

    /** What the sheet hears of the speech, on the main thread. */
    interface Listener {
        fun onStart() {}
        /** A word begins (Android 8+): Hudhud bobs with it. */
        fun onWord() {}
        fun onDone() {}
        /** The phone took the words and did not play them, twice over. */
        fun onFailed() {}
    }

    /** One of the phone's own voices for Hudhud's language, by the engine's name for it. */
    data class Choice(val name: String)

    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val waiting = ArrayList<(TextToSpeech?) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    // What is being said. [current] is this try's id; each of its parts is "$current#n".
    private var current: String? = null
    private var lastPart: String? = null
    private var listener: Listener? = null
    private var started = false
    private var words: String? = null
    private var tries = 0
    private var pending: Runnable? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (Build.VERSION.SDK_INT >= 26) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // The engine calls these on a thread of its own: each is handed to the main thread, where the state lives.
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post { started(utteranceId) }
        }

        override fun onDone(utteranceId: String?) {
            main.post { if (utteranceId != null && utteranceId == lastPart && ours(utteranceId)) finish() }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { if (ours(utteranceId)) failedTry() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { if (ours(utteranceId)) failedTry() }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            main.post { if (ours(utteranceId)) finish() }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            main.post { if (ours(utteranceId)) listener?.onWord() }
        }
    }

    private fun ours(id: String?): Boolean {
        val c = current ?: return false
        return id != null && id.startsWith("$c#")
    }

    private fun started(id: String?) {
        if (!ours(id) || started) return
        started = true
        cancelPending()
        listener?.onStart()
    }

    /** What was being said is over: its listener hears so once. */
    private fun finish() {
        cancelPending()
        current = null
        lastPart = null
        words = null
        val l = listener
        listener = null
        l?.onDone()
    }

    /**
     * The phone did not play this try: once more after a moment (the engine may have been busy with another app's
     * sound, or still loading the voice), then the listener hears that it failed.
     */
    private fun failedTry() {
        val text = words
        cancelPending()
        current = null
        lastPart = null
        runCatching { tts?.stop() }
        if (tries < RETRIES && text != null && listener != null) {
            tries++
            val again = Runnable {
                pending = null
                val t = tts
                if (t != null && listener != null) play(t, text) else finish()
            }
            pending = again
            main.postDelayed(again, RETRY_MS)
        } else {
            val l = listener
            listener = null
            words = null
            l?.onFailed()
        }
    }

    private fun cancelPending() {
        pending?.let { main.removeCallbacks(it) }
        pending = null
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

    /** The language he speaks and the words to say it in: Urdu (and Roman Urdu) in an Urdu voice from Urdu words. */
    private class Speaking(val locale: Locale, val words: Context)

    private fun speaking(): Speaking {
        val appLocale = AppLanguage.context(app).resources.configuration.locales[0]
        return if (appLocale.language == "ur") Speaking(Locale("ur", "PK"), localized(Locale("ur")))
        else Speaking(appLocale, AppLanguage.context(app))
    }

    /** The language key his voice choice is kept under ("ur", "ar"…). */
    fun languageKey(): String = speaking().locale.language

    /**
     * Says what [textIn] writes, with a context in the voice's language; [listener] hears it start, each word, the end
     * or a failure. [noVoice] runs instead when this phone has no voice for the language on it. [style] and
     * [voiceName] override the owner's choice (the settings' sample); null keeps it.
     */
    fun speak(
        textIn: (Context) -> String,
        listener: Listener,
        noVoice: () -> Unit,
        style: HudhudStyle? = null,
        voiceName: String? = null
    ) {
        withEngine { t ->
            if (t == null) { noVoice(); return@withEngine }
            val how = speaking()
            val voices = offlineVoices(t, how.locale)
            val wanted = voiceName ?: HudhudPrefs.voiceName(app, how.locale.language)
            val chosen = voices.firstOrNull { it.name == wanted } ?: voices.firstOrNull()
            val usable = chosen != null && runCatching { t.setVoice(chosen) == TextToSpeech.SUCCESS }.getOrDefault(false)
            if (!usable) { noVoice(); return@withEngine }
            val text = runCatching { textIn(how.words) }.getOrNull()?.takeIf { it.isNotBlank() }
            stopNow()
            if (text == null) { listener.onDone(); return@withEngine }
            val s = style ?: HudhudPrefs.style(app)
            runCatching {
                t.setSpeechRate(s.rate)
                t.setPitch(s.pitch)
            }
            this.listener = listener
            words = text
            tries = 0
            play(t, text)
        }
    }

    /** Plays [text] a sentence at a time, a short silence between; watched until it starts. */
    private fun play(t: TextToSpeech, text: String) {
        val parts = HudhudSpeech.parts(text).ifEmpty { listOf(text) }
        val id = "hudhud-" + System.nanoTime()
        current = id
        started = false
        lastPart = "$id#${parts.size - 1}"
        var ok = true
        parts.forEachIndexed { i, part ->
            if (i > 0 && ok) {
                ok = t.playSilentUtterance(GAP_MS, TextToSpeech.QUEUE_ADD, "$id#g$i") == TextToSpeech.SUCCESS
            }
            if (ok) {
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                ok = t.speak(part, mode, null, "$id#$i") == TextToSpeech.SUCCESS
            }
        }
        if (!ok) { failedTry(); return }
        // A voice that takes the words and never starts them is given up on after a while, as a failed try.
        val watch = Runnable {
            pending = null
            if (current == id && !started) failedTry()
        }
        pending = watch
        main.postDelayed(watch, START_WAIT_MS)
    }

    /** Stops what is being said; its listener hears the end. */
    fun stop() {
        stopNow()
    }

    private fun stopNow() {
        cancelPending()
        val had = current != null || listener != null
        current = null
        lastPart = null
        words = null
        runCatching { tts?.stop() }
        if (had) {
            val l = listener
            listener = null
            l?.onDone()
        }
    }

    /** Lets the engine go. */
    fun shutdown() {
        cancelPending()
        current = null
        lastPart = null
        listener = null
        words = null
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ready = false; failed = false
        waiting.clear()
    }

    /** The phone's own voices for his language, best first; empty when it has none (or no engine). */
    fun voices(then: (List<Choice>) -> Unit) {
        withEngine { t ->
            then(if (t == null) emptyList() else offlineVoices(t, speaking().locale).map { Choice(it.name) })
        }
    }

    /** The phone's speech engine (its package), to open its own voice downloads; null when there is none. */
    fun engine(then: (String?) -> Unit) {
        withEngine { t -> then(t?.let { runCatching { it.defaultEngine }.getOrNull() }) }
    }

    /** Every voice of [locale]'s language on the phone, never one that needs the network, best first. */
    private fun offlineVoices(t: TextToSpeech, locale: Locale): List<Voice> {
        // The engine hands a set: by name first, so voices of one grade keep one order ("Voice 1" stays Voice 1).
        val voices = runCatching { t.voices }.getOrNull()?.sortedBy { it.name } ?: return emptyList()
        return runCatching {
            PaymentNotice.offlineVoices(voices, locale.language, locale.country) { v ->
                PaymentNotice.VoiceFacts(
                    language = v.locale.language,
                    country = v.locale.country,
                    needsNetwork = v.isNetworkConnectionRequired,
                    notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in v.features.orEmpty(),
                    quality = v.quality
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun localized(locale: Locale): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(locale)
        return app.createConfigurationContext(config)
    }

    companion object {
        /** Silence between two sentences: a breath, as a person reads aloud. */
        private const val GAP_MS = 280L

        /** A failed try is tried this many times more. */
        private const val RETRIES = 1

        /** The wait before the one more try. */
        private const val RETRY_MS = 700L

        /** How long a voice may take to start (a voice loading for the first time takes a second or two). */
        private const val START_WAIT_MS = 8000L

        /**
         * Opens the phone's own place to download voices ([engine]'s, else the phone's speech settings): a voice for
         * a language the phone lacks, or more voices for it. Nothing is added to the app. False when the phone has
         * neither screen.
         */
        fun openVoiceDownloads(activity: Activity, engine: String?): Boolean {
            val ways = listOf(
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { engine?.let { setPackage(it) } },
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                Intent("com.android.settings.TTS_SETTINGS")
            )
            for (way in ways) {
                try {
                    activity.startActivity(way.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (e: ActivityNotFoundException) {
                    // the next way
                } catch (e: SecurityException) {
                    // the next way
                }
            }
            return false
        }
    }
}
