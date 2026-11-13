package com.innovation313.roshankhata.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
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
 * The class needs RECORD_AUDIO (Android's rule for SpeechRecognizer); without it the sheet uses the voice-search box
 * as before. Main thread only; [destroy] when the sheet closes.
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

    /** Whether he is listening, or getting ready to (a second tap stops it). */
    var listening = false
        private set

    /** Whether the microphone is open and taking the voice: a stop then makes out what was said; before, nothing was. */
    var open = false
        private set

    /** Starts listening in the app's language ([appTag]): on the phone when it has it, else online, else says why. */
    fun start(appTag: String) {
        cancel()
        val online = online(context)
        val preferred = VoiceLanguage.preferred(appTag)
        if (Build.VERSION.SDK_INT >= 33 && offlineCapable(context)) {
            startChecked(appTag, preferred.firstOrNull(), online)
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && !online && offlineCapable(context)) {
            val r = runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            if (r != null) {
                recognizer = r
                listen(r, intent(preferred.firstOrNull()), onDevice = true)
                return
            }
        }
        startOnline(preferred.firstOrNull(), online)
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
                when {
                    installed != null -> listen(r, intent(installed), onDevice = true)
                    online -> { drop(r); startOnline(tag, true) }
                    downloadable != null -> { drop(r); heard.onTrouble(Trouble.PACK_MISSING) }
                    else -> { drop(r); heard.onTrouble(Trouble.NOT_OFFLINE_HERE) }
                }
            }

            override fun onError(error: Int) {
                if (recognizer !== r) return
                // The service cannot say what it has: online it is asked as before; offline it is tried anyway.
                if (online) { drop(r); startOnline(tag, true) } else listen(r, intent(tag), onDevice = true)
            }
        })
    }

    private fun startOnline(tag: String?, online: Boolean) {
        if (!online) {
            listening = false
            heard.onTrouble(if (offlineCapable(context)) Trouble.NOT_OFFLINE_HERE else Trouble.OFFLINE)
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
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (recognizer !== r) return
                open = true
                heard.onListening()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2 dB (quiet) to 10 dB (a voice close by), made 0 to 1.
                if (recognizer === r) heard.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (recognizer !== r) return
                drop(r)
                heard.onTrouble(troubleOf(error, onDevice))
            }

            override fun onResults(results: Bundle?) {
                if (recognizer !== r) return
                val guesses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    .map { it.trim() }.filter { it.isNotEmpty() }
                drop(r)
                if (guesses.isEmpty()) heard.onTrouble(Trouble.NOTHING_HEARD) else heard.onHeard(guesses)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== r) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { heard.onPartial(it) }
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
        runCatching { recognizer?.stopListening() }
    }

    /** Stops listening and forgets it. */
    fun cancel() {
        recognizer?.let { drop(it) }
        listening = false
        open = false
    }

    private fun drop(r: SpeechRecognizer) {
        if (recognizer === r) recognizer = null
        listening = false
        open = false
        runCatching { r.cancel() }
        runCatching { r.destroy() }
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
    }

    /** What the phone can do for the settings (Android 13+ checks; older phones are taken at their word). */
    enum class Status { NO_MIC, READY_OFFLINE, NEEDS_PACK, ONLINE_ONLY }

    companion object {
        /** The app holds the microphone permission. */
        fun micGranted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** The phone has a speech service at all. */
        fun canHear(context: Context): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

        /** The phone has a speech service that works on the phone itself (Android 12+). */
        fun offlineCapable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= 31 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

        /** A network that reaches the internet. */
        fun online(context: Context): Boolean = runCatching {
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
