package com.innovation313.roshankhata.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.data.HudhudSpeech
import com.innovation313.roshankhata.data.VoiceLanguage

/**
 * Hudhud's own ears (13 Nov 2026, the owner: "Google ke baghair, direct Hudhud se poochhein, Hudhud khud bataye"):
 * the phone's speech service through [SpeechRecognizer], without the voice-search box, while his sheet shows him
 * listening and the words as they are heard.
 *
 * Where the words are made out: on the phone itself when it has the language (Android 12+,
 * createOnDeviceSpeechRecognizer; Android 13+ asks first which languages it has, and can download one: [downloadPack]
 * — the phone's own pack, nothing added to the app); else through the phone's speech service while there is internet,
 * as the voice-search box does. Without internet and without the language on the phone he says so ([Trouble]) instead
 * of opening a box that fails. Nothing is recorded or kept: the words come back as text.
 *
 * When the question is over is his to judge too (13 Nov 2026, the owner: "recognizer chalta hi rehta hai… response
 * bahut late"): a phone's recogniser may keep listening for seconds after the last word. He watches the words as they
 * come; once they have not changed for [QUIET_MS] (longer, [OPEN_QUIET_MS], when the last word joins to more: "Ali
 * ka…", [HudhudSpeech.endsOpen]) he closes the microphone himself, and if the recogniser does not then hand over its
 * words within [FINAL_WAIT_MS] the last words heard are the question. Nothing heard for [NOTHING_MS] is nothing heard;
 * no question runs past [LONGEST_MS]. A recogniser that ends in an error after words were heard has still been heard.
 *
 * The class needs RECORD_AUDIO (Android's rule for SpeechRecognizer); without it the sheet uses the voice-search box
 * as before. Main thread only; [destroy] when the sheet closes.
 *
 * "Only listen on this phone (never online)" (16 Nov 2026, audit T14): while it is on the online recogniser is never
 * created, whatever the network — the words are made out on the phone or not at all ([ListenRoute]), and [online]
 * answers false so the sheet does not reach for the voice-search box either.
 *
 * Audio goes online only after the owner's yes (25 Nov 2026, D10, [ListenChoice]): where the phone has the language's
 * pack the words are made out there, silently; where it has none and nothing was chosen yet he asks once ([askOnline],
 * [ListenAsk]: Google's online recogniser — "your voice goes to Google" — or type instead) and the phone remembers the
 * answer ([ListenChoiceStore]); the voice settings' switch changes it later. Nothing chosen counts as "only on this
 * phone" everywhere ([phoneOnly], [online]). A "type instead", or a question closed unanswered, never blocks typing: the
 * sheet is told [Trouble.NOT_OFFLINE_HERE], which points at typing.
 */
class HudhudEars(private val context: Context, private val heard: Callbacks) {

    interface Callbacks {
        /** The microphone is open: he can be spoken to. */
        fun onListening()
        /** The words so far, as they are heard. */
        fun onPartial(words: String)
        /** How loud the voice is, 0 to 1. */
        fun onLevel(level: Float)
        /** The recogniser's guesses, best first. */
        fun onHeard(guesses: List<String>)
        fun onTrouble(trouble: Trouble)
    }

    /** Why nothing was heard. */
    enum class Trouble {
        /** Silence, or nothing it could make out. */
        NOTHING_HEARD,
        /** No internet, and this phone cannot hear on its own. */
        OFFLINE,
        /** No internet; the phone can hear this language on its own once its pack is downloaded ([downloadPack]). */
        PACK_MISSING,
        /** No internet; the phone cannot hear this language on its own. */
        NOT_OFFLINE_HERE,
        /** The microphone is in use elsewhere. */
        BUSY,
        /** The microphone could not be used (no permission after all, or an audio fault). */
        MIC,
        /** Anything else: the sheet falls back to the voice-search box. */
        OTHER
    }

    private var recognizer: SpeechRecognizer? = null
    private var downloader: SpeechRecognizer? = null
    private var packTag: String? = null

    // The app's language of the listening in hand, for the question about going online (it asks, then starts again).
    private var appTagNow: String = ""

    // The question about going online, while it is on the screen; [askId] tells a stale answer from the present one.
    private var asking: Dialog? = null
    private var askId = 0

    // His own end of speech: what was heard last, when it last changed, and whether he has closed the microphone.
    private val main = Handler(Looper.getMainLooper())
    private var heardSoFar: String? = null
    private var changedAt = 0L
    private var readyAt = 0L
    private var closingAt = 0L
    private var watch: Runnable? = null

    /** Whether he is listening, or getting ready to (a second tap stops it). */
    var listening = false
        private set

    /** Whether the microphone is open and taking the voice: a stop then makes out what was said; before, nothing was. */
    var open = false
        private set

    // The wait before the microphone opens when a little call plays first (see [start]).
    private var lead: Runnable? = null

    /**
     * Starts listening in the app's language ([appTag]): on the phone when it has it, else online, else says why.
     * [leadMillis]: a little call ([HudhudChirp.Kind.READY]) is playing, so the microphone opens when it has ended and
     * does not hear it. During that wait he counts as getting ready to listen ([listening], not [open]): a stop in it
     * cancels, as a stop before the microphone opens always did.
     */
    fun start(appTag: String, leadMillis: Long = 0L) {
        cancel()
        if (leadMillis <= 0L) {
            begin(appTag)
            return
        }
        listening = true
        val go = Runnable {
            lead = null
            listening = false
            begin(appTag)
        }
        lead = go
        main.postDelayed(go, leadMillis)
    }

    private fun begin(appTag: String) {
        appTagNow = appTag
        // [online] already answers false until the owner said yes to the online recogniser (and while the switch keeps
        // listening on this phone): the online recogniser is never a way here then ([ListenRoute], [ListenChoice]).
        val keepOnPhone = phoneOnly(context)
        val online = online(context)
        val tag = VoiceLanguage.preferred(appTag).firstOrNull()
        when (ListenRoute.first(Build.VERSION.SDK_INT, offlineCapable(context), online, keepOnPhone)) {
            ListenRoute.Way.ASK_PHONE -> if (Build.VERSION.SDK_INT >= 33) startChecked(appTag, tag, online)
            ListenRoute.Way.ON_PHONE -> {
                val r = if (Build.VERSION.SDK_INT >= 31) runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull() else null
                if (r != null) {
                    recognizer = r
                    listen(r, intent(tag), onDevice = true)
                } else startOnline(tag, online)
            }
            ListenRoute.Way.ONLINE, ListenRoute.Way.NOT_HERE -> startOnline(tag, online)
        }
    }

    /** Android 13+: the phone says which languages it has on itself; one it has is heard there, privately. */
    @RequiresApi(33)
    private fun startChecked(appTag: String, tag: String?, online: Boolean) {
        val r = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            startOnline(tag, online)
            return
        }
        recognizer = r
        listening = true
        r.checkRecognitionSupport(intent(tag), ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                if (recognizer !== r) return
                val installed = exact(appTag, support.installedOnDeviceLanguages)
                val downloadable = exact(appTag, support.supportedOnDeviceLanguages + support.pendingOnDeviceLanguages)
                packTag = downloadable ?: installed
                when (ListenRoute.afterSupport(installed != null, downloadable != null, online)) {
                    ListenRoute.Next.LISTEN_HERE -> listen(r, intent(installed ?: tag), onDevice = true)
                    ListenRoute.Next.GO_ONLINE -> { drop(r); startOnline(tag, true) }
                    // No pack on the phone and no yes to the online recogniser: asked once when there is internet to use it
                    // (D10), else said as before.
                    ListenRoute.Next.PACK_MISSING -> { drop(r); if (!askedInstead()) heard.onTrouble(Trouble.PACK_MISSING) }
                    ListenRoute.Next.NOT_HERE -> { drop(r); if (!askedInstead()) heard.onTrouble(Trouble.NOT_OFFLINE_HERE) }
                }
            }

            override fun onError(error: Int) {
                if (recognizer !== r) return
                // The service cannot say what it has: online it is asked as before; offline (or on this phone only) it
                // is tried on the phone anyway.
                if (ListenRoute.supportUnknown(online) == ListenRoute.Next.GO_ONLINE) { drop(r); startOnline(tag, true) }
                else listen(r, intent(tag), onDevice = true)
            }
        })
    }

    /** The online recogniser; [online] is false while the owner keeps listening on this phone, so it is never made then. */
    private fun startOnline(tag: String?, online: Boolean) {
        if (!online) {
            listening = false
            if (askedInstead()) return
            // On this phone only, with internet or without: "this phone can't hear this language" is the truth, not "no
            // internet".
            val keepOnPhone = phoneOnly(context)
            heard.onTrouble(if (offlineCapable(context) || keepOnPhone) Trouble.NOT_OFFLINE_HERE else Trouble.OFFLINE)
            return
        }
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            listening = false
            heard.onTrouble(Trouble.OTHER)
            return
        }
        recognizer = r
        listen(r, intent(tag), onDevice = false)
    }

    private fun listen(r: SpeechRecognizer, intent: Intent, onDevice: Boolean) {
        listening = true
        heardSoFar = null
        closingAt = 0L
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (recognizer !== r) return
                open = true
                readyAt = SystemClock.uptimeMillis()
                changedAt = readyAt
                heard.onListening()
                startWatch(r)
            }

            override fun onBeginningOfSpeech() {
                if (recognizer === r) changedAt = SystemClock.uptimeMillis()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2 dB (quiet) to 10 dB (a voice close by), made 0 to 1.
                if (recognizer === r) heard.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (recognizer !== r) return
                val so = heardSoFar
                drop(r)
                // Words were heard and then it ended in silence or a fault of its own: those words are the question.
                if (so != null && error in HEARD_THEN_ENDED) heard.onHeard(listOf(so))
                // The phone's own recogniser lacks the language (12, 13), no yes to the online one yet: asked once (D10).
                else if (onDevice && (error == 12 || error == 13) && askedInstead()) Unit
                else heard.onTrouble(troubleOf(error, onDevice))
            }

            override fun onResults(results: Bundle?) {
                if (recognizer !== r) return
                val guesses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    .map { it.trim() }.filter { it.isNotEmpty() }
                val so = heardSoFar
                drop(r)
                when {
                    guesses.isNotEmpty() -> heard.onHeard(guesses)
                    so != null -> heard.onHeard(listOf(so))
                    else -> heard.onTrouble(Trouble.NOTHING_HEARD)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== r) return
                val words = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return
                if (words != heardSoFar) {
                    heardSoFar = words
                    changedAt = SystemClock.uptimeMillis()
                }
                heard.onPartial(words)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            drop(r)
            heard.onTrouble(Trouble.OTHER)
        }
    }

    /** Every tenth of a second or so while he listens: has the question ended, though the recogniser listens on? */
    private fun startWatch(r: SpeechRecognizer) {
        stopWatch()
        val tick = object : Runnable {
            override fun run() {
                if (recognizer !== r) return
                val now = SystemClock.uptimeMillis()
                val so = heardSoFar
                if (closingAt > 0L) {
                    // Closed by him (or a stop): the recogniser's words did not come in time — his own are the question.
                    if (now - closingAt >= FINAL_WAIT_MS) {
                        drop(r)
                        if (so != null) heard.onHeard(listOf(so)) else heard.onTrouble(Trouble.NOTHING_HEARD)
                        return
                    }
                } else if (so == null) {
                    if (now - readyAt >= NOTHING_MS) {
                        drop(r)
                        heard.onTrouble(Trouble.NOTHING_HEARD)
                        return
                    }
                } else {
                    val quiet = if (HudhudSpeech.endsOpen(so)) OPEN_QUIET_MS else QUIET_MS
                    if (now - changedAt >= quiet || now - readyAt >= LONGEST_MS) close(r)
                }
                main.postDelayed(this, WATCH_MS)
            }
        }
        watch = tick
        main.postDelayed(tick, WATCH_MS)
    }

    private fun stopWatch() {
        watch?.let { main.removeCallbacks(it) }
        watch = null
    }

    /** He closes the microphone: the recogniser makes out what it has. */
    private fun close(r: SpeechRecognizer) {
        if (closingAt > 0L) return
        closingAt = SystemClock.uptimeMillis()
        runCatching { r.stopListening() }
    }

    private fun troubleOf(error: Int, onDevice: Boolean): Trouble = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Trouble.NOTHING_HEARD
        // ERROR_SERVER_DISCONNECTED is 11 (Android 12).
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, 11 ->
            if (online(context)) Trouble.OTHER
            else if (offlineCapable(context)) Trouble.NOT_OFFLINE_HERE else Trouble.OFFLINE
        // ERROR_LANGUAGE_NOT_SUPPORTED (12) and ERROR_LANGUAGE_UNAVAILABLE (13), Android 12.
        12 -> if (onDevice) Trouble.NOT_OFFLINE_HERE else Trouble.OTHER
        13 -> if (onDevice) Trouble.PACK_MISSING else Trouble.OTHER
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Trouble.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS, SpeechRecognizer.ERROR_AUDIO -> Trouble.MIC
        else -> Trouble.OTHER
    }

    /** The microphone stops and what was said is made out (a second tap on the microphone). */
    fun stop() {
        val r = recognizer ?: return
        close(r)
    }

    /** Stops listening and forgets it. */
    fun cancel() {
        lead?.let { main.removeCallbacks(it) }
        lead = null
        // A question about going online is withdrawn with the listening: its answer, if it came, is stale ([askId]).
        askId++
        asking?.let { d -> asking = null; runCatching { d.dismiss() } }
        recognizer?.let { drop(it) }
        listening = false
        open = false
    }

    private fun drop(r: SpeechRecognizer) {
        if (recognizer === r) recognizer = null
        stopWatch()
        closingAt = 0L
        listening = false
        open = false
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    /**
     * The phone cannot hear the language itself: when nothing was chosen yet and there is internet, asks once whether
     * to use Google's online recogniser (D10) and answers true — the listening waits for the answer; false means the
     * caller says what it said before ([ListenChoice.lacking]). A yes is kept and he starts again, now online; "type
     * instead" is kept as "only on this phone" and says [Trouble.NOT_OFFLINE_HERE]; a question closed unanswered keeps
     * nothing and says the same. Never a way to hear online without that yes.
     */
    private fun askedInstead(): Boolean {
        val activity = context as? Activity ?: return false
        if (ListenChoice.lacking(ListenChoiceStore.mode(context), networkUp(context), canHear(context)) != ListenChoice.Lacking.ASK) return false
        val mine = ++askId
        val appTag = appTagNow
        val dialog = ListenAsk.show(
            activity,
            onYes = {
                if (mine == askId) {
                    asking = null
                    ListenChoiceStore.set(context, ListenChoice.answered(yesOnline = true))
                    begin(appTag)
                }
            },
            onNo = {
                if (mine == askId) {
                    asking = null
                    ListenChoiceStore.set(context, ListenChoice.answered(yesOnline = false))
                    listening = false
                    heard.onTrouble(Trouble.NOT_OFFLINE_HERE)
                }
            },
            onCancel = {
                if (mine == askId) {
                    asking = null
                    listening = false
                    heard.onTrouble(Trouble.NOT_OFFLINE_HERE)
                }
            }
        ) ?: return false
        asking = dialog
        // Getting ready to listen: a stop (cancel) in the meantime withdraws the question.
        listening = true
        return true
    }

    /**
     * Android 13+: asks the phone to download its own pack for the app's language, so he can listen without internet.
     * The phone does the download (its own notice); nothing is added to the app. False when the phone cannot.
     */
    fun downloadPack(appTag: String): Boolean {
        if (Build.VERSION.SDK_INT < 33 || !offlineCapable(context)) return false
        val tag = packTag ?: VoiceLanguage.preferred(appTag).firstOrNull() ?: return false
        return runCatching {
            downloader?.let { runCatching { it.destroy() } }
            val d = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            downloader = d
            d.triggerModelDownload(intent(tag))
            true
        }.getOrDefault(false)
    }

    /** Lets everything go: the sheet closed. */
    fun destroy() {
        cancel()
        downloader?.let { runCatching { it.destroy() } }
        downloader = null
    }

    private fun intent(tag: String?) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        tag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Asked of the recogniser too (many keep their own longer pause): end a second after the last word.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, QUIET_MS)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, QUIET_MS)
    }

    /** What the phone can do for the settings (Android 13+ checks; older phones are taken at their word). */
    enum class Status { NO_MIC, READY_OFFLINE, NEEDS_PACK, ONLINE_ONLY }

    companion object {
        /** The words have not changed this long: the question is over. */
        const val QUIET_MS = 1200L

        /** The same after a word that joins to more ("Ali ka…"): someone thinking of the rest. */
        const val OPEN_QUIET_MS = 1900L

        /** After he closes the microphone, how long the recogniser has to hand over its words. */
        const val FINAL_WAIT_MS = 1200L

        /** Nothing heard this long after the microphone opened: nothing was said. */
        const val NOTHING_MS = 7000L

        /** The longest question. */
        const val LONGEST_MS = 15_000L

        private const val WATCH_MS = 120L

        /** Errors that end a recognition which had already heard words: no match, a timeout, the client's own end. */
        private val HEARD_THEN_ENDED = setOf(
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_CLIENT
        )

        /** The app holds the microphone permission. */
        fun micGranted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** The phone has a speech service at all. */
        fun canHear(context: Context): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

        /** The phone has a speech service that works on the phone itself (Android 12+). */
        fun offlineCapable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= 31 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

        /**
         * Whether the online recogniser may be used: a network that reaches the internet ([networkUp]) and the owner has
         * not kept listening on this phone only ([ListenChoice.mayGoOnline], D10: nothing online without the owner's yes). The sheet asks this
         * before it opens the voice-search box, so the switch keeps that box closed too.
         */
        fun online(context: Context): Boolean = ListenChoice.mayGoOnline(ListenChoiceStore.mode(context), networkUp(context))

        /**
         * Whether listening is kept on this phone: everything but the owner's yes ([ListenChoice.phoneOnly]) — a phone
         * where nothing was chosen yet is "only on this phone" until it is asked. What the voice settings' switch shows.
         */
        fun phoneOnly(context: Context): Boolean = ListenChoice.phoneOnly(ListenChoiceStore.mode(context))

        /**
         * Whether the phone itself has the language's pack, for the one-shot users of the voice-search box (Khata's voice
         * entry). Android 13+ only, and no microphone permission is asked for it; anything that cannot say — an older
         * phone, no on-device recogniser, an error — answers false, which is the side that asks or types, never the one
         * that goes online. [then] runs on the main thread.
         */
        fun hasPack(context: Context, appTag: String, then: (Boolean) -> Unit) {
            if (Build.VERSION.SDK_INT < 33 || !offlineCapable(context)) { then(false); return }
            // A phone that never answers is a phone without the pack: the answer is taken once, at most 2 s from now.
            var told = false
            val once = { have: Boolean -> if (!told) { told = true; then(have) } }
            Handler(Looper.getMainLooper()).postDelayed({ once(false) }, HAS_PACK_WAIT_MS)
            checkStatus(context, appTag) { status -> once(status == Status.READY_OFFLINE) }
        }

        private const val HAS_PACK_WAIT_MS = 2000L

        /** A network that reaches the internet. */
        fun networkUp(context: Context): Boolean = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)

        /** The phone's tag for the app's language among [have], by VoiceLanguage's rule, only when it is that language. */
        internal fun exact(appTag: String, have: List<String>): String? {
            if (have.isEmpty()) return null
            val choice = VoiceLanguage.choose(appTag, have)
            return if (choice.exact) choice.tag else null
        }

        /**
         * How he listens on this phone, for his voice settings: [then] runs on the main thread. Android 13+ asks the
         * phone (with the microphone allowed); older phones listen online and, without internet, only if the phone
         * happens to have the language.
         */
        fun status(context: Context, appTag: String, then: (Status) -> Unit) {
            if (!micGranted(context)) { then(Status.NO_MIC); return }
            if (Build.VERSION.SDK_INT < 33 || !offlineCapable(context)) { then(Status.ONLINE_ONLY); return }
            checkStatus(context, appTag, then)
        }

        @RequiresApi(33)
        private fun checkStatus(context: Context, appTag: String, then: (Status) -> Unit) {
            val r = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            if (r == null) { then(Status.ONLINE_ONLY); return }
            val tag = VoiceLanguage.preferred(appTag).firstOrNull()
            val probe = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                tag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
            }
            val done = { s: Status ->
                runCatching { r.destroy() }
                then(s)
            }
            try {
                r.checkRecognitionSupport(probe, ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        done(
                            when {
                                exact(appTag, support.installedOnDeviceLanguages) != null -> Status.READY_OFFLINE
                                exact(appTag, support.supportedOnDeviceLanguages + support.pendingOnDeviceLanguages) != null ->
                                    Status.NEEDS_PACK
                                else -> Status.ONLINE_ONLY
                            }
                        )
                    }

                    override fun onError(error: Int) = done(Status.ONLINE_ONLY)
                })
            } catch (e: Exception) {
                done(Status.ONLINE_ONLY)
            }
        }
    }
}

/**
 * Where his ears make out the words (16 Nov 2026, audit T14): pure, so a test holds it. [online] here is
 * [HudhudEars.online] — already false until the owner said yes to the online recogniser, and while the owner keeps
 * listening on this phone ([ListenChoice], D10) — so no branch can reach the online recogniser then; [phoneOnly] also keeps an Android 12 phone with internet on its own recogniser.
 */
object ListenRoute {

    /**
     * The 16 Nov rule: the online recogniser (or the voice-search box) only with internet and [phoneOnly] off. Since D10
     * [HudhudEars.online] reads [ListenChoice.mayGoOnline] (a yes is needed); this stays as the second guard of [first].
     */
    fun mayGoOnline(networkUp: Boolean, phoneOnly: Boolean): Boolean = networkUp && !phoneOnly

    /** The first step. */
    enum class Way {
        /** Android 13+ with an on-device recogniser: ask it which languages it has ([afterSupport]). */
        ASK_PHONE,
        /** Android 12: listen on the phone (it cannot say beforehand). */
        ON_PHONE,
        /** The online recogniser. */
        ONLINE,
        /** Neither: the online step, which says why ([HudhudEars.Trouble]) without opening anything. */
        NOT_HERE
    }

    fun first(sdk: Int, onDeviceAvailable: Boolean, online: Boolean, phoneOnly: Boolean): Way = when {
        sdk >= 33 && onDeviceAvailable -> Way.ASK_PHONE
        sdk >= 31 && onDeviceAvailable && (!online || phoneOnly) -> Way.ON_PHONE
        // [phoneOnly] here too, whatever [online] a caller passes: this rule alone never says ONLINE on this phone only.
        online && !phoneOnly -> Way.ONLINE
        else -> Way.NOT_HERE
    }

    /** After an Android 13+ phone said which languages it has. */
    enum class Next { LISTEN_HERE, GO_ONLINE, PACK_MISSING, NOT_HERE }

    fun afterSupport(installed: Boolean, downloadable: Boolean, online: Boolean): Next = when {
        installed -> Next.LISTEN_HERE
        online -> Next.GO_ONLINE
        downloadable -> Next.PACK_MISSING
        else -> Next.NOT_HERE
    }

    /** The phone could not say what it has: online it is asked as before, else tried on the phone anyway. */
    fun supportUnknown(online: Boolean): Next = if (online) Next.GO_ONLINE else Next.LISTEN_HERE
}
