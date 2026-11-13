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
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.HudhudSay
import com.innovation313.roshankhata.data.HudhudSpeech
import com.innovation313.roshankhata.data.PaymentNotice
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Hudhud's voice (12 Nov 2026): the phone's own speech (TextToSpeech), as the payment voice uses it — only a voice
 * that is on the phone speaks, never one that needs the network (it would send a customer's name and an amount to
 * the engine's server: [PaymentNotice.offlineVoice]).
 *
 * 13 Nov 2026, the owner: "English aur Urdu ke ilawa doosri zabanon mein bolta hi nahi… poori statement bole… awaaz
 * professional ho… gap kabhi bahut kam, kabhi bahut zyada":
 *  - **Which voice** ([plan]): the app's language when the phone has a voice for it; else the nearest language it has,
 *    in that language's own words — never one language's words read by another's voice. Sindhi falls to Urdu, Urdu
 *    and Roman Urdu are said from the Urdu words (an Urdu voice is made for the Urdu script), Hindi to Urdu, every
 *    other to English; the sheet says once that it speaks another language and where to get the voice.
 *  - **How it sounds**: the owner's style ([HudhudStyle], professional by default: the phone's own pace and pitch)
 *    and the voice he chose among the phone's own ([HudhudPrefs.voiceName]).
 *  - **Even pauses**: the whole answer is one utterance, so the voice keeps its own rhythm between sentences — no
 *    silences of ours between parts, which an engine adds its own pause to, longer or shorter by the punctuation.
 *    His name is said the way he chose for the language ([HudhudSay]), exclamations are said as full stops, and the
 *    shop's rupees in words.
 *
 * An answer the phone took and did not play — an error, or no start at all — is tried once more after a moment, and
 * only then reported ([Listener.onFailed]): he never goes silent without saying so. Played as an assistant's answer
 * (USAGE_ASSISTANT; media before Android 8), so the volume keys set it while the sheet is open. Each answer stops the
 * one before it; the sheet shuts the engine when it closes. Main thread only.
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

    /**
     * Who speaks and in what words: [words] is a context in the language said ([language] its key: "ur", "en"…), with
     * [voice] the phone's voice for it; [fallback] when that is not the app's own language (the phone has none).
     */
    class Plan internal constructor(
        val voice: Voice,
        val words: Context,
        val language: String,
        val fallback: Boolean,
        /** The app's own language, as the sheet names it in its note. */
        val wanted: String
    )

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

    /** The app's own language key: "ur" for Urdu and Roman Urdu alike (said from the Urdu words). */
    private fun appKey(): String {
        val l = AppLanguage.context(app).resources.configuration.locales[0]
        return when (l.language) {
            "id" -> "in"
            else -> l.language
        }
    }

    /** The languages to try, in order, for the app's own: its own first, then the nearest the phone may have. */
    private fun chain(key: String): List<String> = when (key) {
        "ur" -> listOf("ur", "hi", "en")
        "sd" -> listOf("sd", "ur", "hi", "en")
        "hi" -> listOf("hi", "ur", "en")
        "en" -> listOf("en")
        else -> listOf(key, "en")
    }

    /** The voice's locale for a language key, as the engine names its voices. */
    private fun localeOf(key: String): Locale = when (key) {
        "ur" -> Locale("ur", "PK")
        "en" -> Locale.ENGLISH
        else -> Locale(key)
    }

    /** The words of a language key: the app's strings in it. */
    private fun wordsOf(key: String): Context = localized(
        when (key) {
            "en" -> Locale.ENGLISH
            else -> Locale(key)
        }
    )

    /** Which voice speaks and in what words; [then] gets null when the phone has no offline voice for any of them. */
    fun plan(then: (Plan?) -> Unit) {
        withEngine { t ->
            if (t == null) { then(null); return@withEngine }
            val want = appKey()
            for (key in chain(want)) {
                val voices = offlineVoices(t, key)
                if (voices.isEmpty()) continue
                val chosenName = HudhudPrefs.voiceName(app, key)
                val v = voices.firstOrNull { it.name == chosenName } ?: voices.first()
                then(Plan(v, wordsOf(key), key, fallback = key != want, wanted = want))
                return@withEngine
            }
            then(null)
        }
    }

    /** [plan], waited for. */
    suspend fun plan(): Plan? = suspendCancellableCoroutine { wait -> plan { p -> if (wait.isActive) wait.resume(p) } }

    /**
     * Says [text] with [plan]'s voice, made ready for it ([forVoice]); [listener] hears it start, each word, the end or
     * a failure. [style] overrides the owner's choice (the settings' sample).
     */
    fun speak(text: String, plan: Plan, listener: Listener, style: HudhudStyle? = null, nameWay: Int? = null) {
        withEngine { t ->
            if (t == null) { listener.onFailed(); return@withEngine }
            val usable = runCatching { t.setVoice(plan.voice) == TextToSpeech.SUCCESS }.getOrDefault(false)
            stopNow()
            if (!usable) { listener.onFailed(); return@withEngine }
            val said = forVoice(text, plan, nameWay).takeIf { it.isNotBlank() }
            if (said == null) { listener.onDone(); return@withEngine }
            val s = style ?: HudhudPrefs.style(app)
            runCatching {
                t.setSpeechRate(s.rate)
                t.setPitch(s.pitch)
            }
            this.listener = listener
            words = said
            tries = 0
            play(t, said)
        }
    }

    /**
     * Says what [textIn] writes in the voice's words (the settings' samples, the sheet's own sentences); [noVoice]
     * runs instead when the phone has no offline voice at all. [voiceName] overrides the chosen voice.
     */
    fun speak(
        textIn: (Context) -> String,
        listener: Listener,
        noVoice: () -> Unit,
        style: HudhudStyle? = null,
        voiceName: String? = null,
        nameWay: Int? = null
    ) {
        plan { p ->
            if (p == null) { noVoice(); return@plan }
            val chosen = voiceName?.let { name ->
                tts?.let { t -> offlineVoices(t, p.language).firstOrNull { it.name == name } }
            }
            val use = if (chosen != null) Plan(chosen, p.words, p.language, p.fallback, p.wanted) else p
            val text = runCatching { textIn(use.words) }.getOrNull().orEmpty()
            speak(text, use, listener, style, nameWay)
        }
    }

    /** [text] as the voice should get it: the marks for the eye gone, his name his way, calm stops, rupees in words. */
    fun forVoice(text: String, plan: Plan, nameWay: Int? = null): String {
        val rupees: ((String) -> String)? = if (Currency.symbol == Currency.DEFAULT || Currency.symbol == "₹") {
            { digits -> plan.words.getString(R.string.paisa_rupees, digits) }
        } else null
        val clean = HudhudSpeech.clean(text.replace('−', '-').replace(" · ", ", ").replace("·", ","))
            .replace('\n', ' ')
        return HudhudSay.forVoice(clean, plan.language, nameWay ?: HudhudPrefs.nameWay(app, plan.language), rupees)
    }

    /**
     * Plays [text] as one utterance, so the voice keeps its own even pauses between the sentences; only an answer
     * longer than the engine takes at once is cut, at a sentence's end. Watched until it starts.
     */
    private fun play(t: TextToSpeech, text: String) {
        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 100
        val parts = if (text.length <= max) listOf(text) else chunks(text, max)
        val id = "hudhud-" + System.nanoTime()
        current = id
        started = false
        lastPart = "$id#${parts.size - 1}"
        var ok = true
        parts.forEachIndexed { i, part ->
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

    /** [text] in pieces of at most [max] characters, each ending at a sentence's end where one falls. */
    private fun chunks(text: String, max: Int): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (s in HudhudSpeech.parts(text)) {
            if (sb.isNotEmpty() && sb.length + 1 + s.length > max) {
                out += sb.toString()
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(s.take(max))
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out.ifEmpty { listOf(text.take(max)) }
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

    /** The phone's own voices for the language he speaks, best first; empty when it has none (or no engine). */
    fun voices(then: (List<Choice>) -> Unit) {
        plan { p ->
            val t = tts
            then(if (p == null || t == null) emptyList() else offlineVoices(t, p.language).map { Choice(it.name) })
        }
    }

    /** The language key his voices are kept under now ([plan]'s: "ur", "en"…); the app's own before the engine wakes. */
    fun languageKey(then: (String) -> Unit) {
        plan { p -> then(p?.language ?: appKey()) }
    }

    /** The phone's speech engine (its package), to open its own voice downloads; null when there is none. */
    fun engine(then: (String?) -> Unit) {
        withEngine { t -> then(t?.let { runCatching { it.defaultEngine }.getOrNull() }) }
    }

    /** Every voice of [key]'s language on the phone, never one that needs the network, best first. */
    private fun offlineVoices(t: TextToSpeech, key: String): List<Voice> {
        // The engine hands a set: by name first, so voices of one grade keep one order ("Voice 1" stays Voice 1).
        val voices = runCatching { t.voices }.getOrNull()?.sortedBy { it.name } ?: return emptyList()
        val locale = localeOf(key)
        // Indonesian goes by two codes ("in" on older phones, "id"): either is the language.
        val keys = if (key == "in" || key == "id") listOf("in", "id") else listOf(locale.language)
        return runCatching {
            keys.flatMap { k ->
                PaymentNotice.offlineVoices(voices, k, locale.country) { v ->
                    PaymentNotice.VoiceFacts(
                        language = v.locale.language,
                        country = v.locale.country,
                        needsNetwork = v.isNetworkConnectionRequired,
                        notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in v.features.orEmpty(),
                        quality = v.quality
                    )
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun localized(locale: Locale): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(locale)
        return app.createConfigurationContext(config)
    }

    companion object {
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
