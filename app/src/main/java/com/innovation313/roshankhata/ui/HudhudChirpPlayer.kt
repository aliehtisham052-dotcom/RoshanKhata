package com.innovation313.roshankhata.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import com.innovation313.roshankhata.data.HudhudChirp
import java.util.concurrent.Executors

/**
 * Plays Hudhud's little bird calls ([HudhudChirp]: made as numbers, so no sound file and nothing added to the app)
 * when the owner has them on ([HudhudPrefs.chirps]) — 14 Nov 2026, the owner: "cartoonish, interacting awaaz".
 *
 * One short static sound on the phone's own audio track, played as an assistant's sound (USAGE_ASSISTANT; media before
 * Android 8) so the volume keys treat it as they treat his voice. It plays on a thread of its own, one at a time, and
 * the track is let go as soon as it has played. [play] hands back how long the sound lasts so that whatever follows
 * (the microphone, the answer) waits for it instead of talking over it, and 0 when nothing plays. A phone that cannot
 * make the track plays nothing and nothing else changes.
 */
object HudhudChirpPlayer {

    /** A moment past the end of the sound before the track is let go, so its last samples are not cut. */
    private const val HOLD_MARGIN_MS = 80L

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "hudhud-chirp").apply { isDaemon = true } }

    // Each sound is a few thousand numbers: made once.
    private val made = HashMap<HudhudChirp.Kind, ShortArray>()

    @Synchronized
    private fun samplesOf(kind: HudhudChirp.Kind): ShortArray = made.getOrPut(kind) { HudhudChirp.samples(kind) }

    /** Plays [kind] when bird calls are on; returns its length in milliseconds, or 0 when nothing plays. */
    fun play(context: Context, kind: HudhudChirp.Kind): Int {
        if (!HudhudPrefs.chirps(context.applicationContext)) return 0
        return playAlways(kind)
    }

    /** Plays [kind] whatever the switch says: the voice settings' own buttons to hear each call. */
    fun playAlways(kind: HudhudChirp.Kind): Int {
        val pcm = samplesOf(kind)
        val millis = HudhudChirp.durationMillis(kind)
        worker.execute { runCatching { hold(pcm, millis) } }
        return millis
    }

    /** Plays [pcm] once and lets the track go. On the chirp thread. */
    private fun hold(pcm: ShortArray, millis: Int) {
        val attributes = AudioAttributes.Builder()
            .setUsage(if (Build.VERSION.SDK_INT >= 26) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(HudhudChirp.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            if (track.write(pcm, 0, pcm.size) != pcm.size) return
            track.play()
            Thread.sleep(millis + HOLD_MARGIN_MS)
        } finally {
            runCatching { track.release() }
        }
    }
}
