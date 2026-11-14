package com.innovation313.roshankhata.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Hudhud's little sounds (14 Nov 2026, the owner: "cartoonish, interacting awaaz"): three bird calls made as numbers —
 * no sound file, so the app does not grow, nothing is downloaded, and the sound is the same on every phone. The phone
 * only plays what this makes ([com.innovation313.roshankhata.ui.HudhudChirpPlayer]).
 *
 *  - [Kind.HOOP]: the hoopoe's own call, three soft low notes — "hoo-po-po" — said before an answer.
 *  - [Kind.READY]: two quick notes going up — "I am listening" — when his microphone opens.
 *  - [Kind.GLAD]: a bright little trill, for good news.
 *
 * Each note slides from one pitch to another with a soft start and end, so it never clicks, and the whole sound is
 * kept quiet (a peak of about half the loudest the phone can play) and short (under half a second): it greets, it
 * does not talk over the answer. Pure arithmetic, tested on the computer ([HudhudChirpTest]).
 */
object HudhudChirp {

    const val SAMPLE_RATE = 22_050

    enum class Kind { HOOP, READY, GLAD }

    /** The loudest sample a chirp reaches, of the 32,767 a 16-bit sound can hold: about half. */
    const val PEAK = 16_000

    private class Note(val from: Double, val to: Double, val millis: Int, val gapMillis: Int, val body: Double)

    private fun notes(kind: Kind): List<Note> = when (kind) {
        // The hoopoe's call sits near 450 Hz: low, round and soft (a strong first harmonic, no brightness).
        Kind.HOOP -> listOf(
            Note(430.0, 470.0, 110, 55, 0.18),
            Note(480.0, 440.0, 90, 50, 0.18),
            Note(480.0, 430.0, 130, 0, 0.18)
        )
        Kind.READY -> listOf(
            Note(1500.0, 2100.0, 70, 30, 0.12),
            Note(2000.0, 2900.0, 90, 0, 0.12)
        )
        Kind.GLAD -> listOf(
            Note(2200.0, 2800.0, 55, 20, 0.12),
            Note(2400.0, 3000.0, 55, 20, 0.12),
            Note(2600.0, 3300.0, 80, 0, 0.12)
        )
    }

    /** How long [kind] lasts, in milliseconds. */
    fun durationMillis(kind: Kind): Int = notes(kind).sumOf { it.millis + it.gapMillis } + TAIL_MILLIS

    /** A little quiet after the last note, so the sound ends before the player lets go of it. */
    private const val TAIL_MILLIS = 12

    /** The soft start and end of every note. */
    private const val EDGE_MILLIS = 12

    /** 16-bit mono PCM of [kind] at [SAMPLE_RATE]; the same every time. */
    fun samples(kind: Kind): ShortArray {
        val list = notes(kind)
        val out = ShortArray(durationMillis(kind) * SAMPLE_RATE / 1000)
        var at = 0
        for (n in list) {
            val length = n.millis * SAMPLE_RATE / 1000
            val edge = min(EDGE_MILLIS * SAMPLE_RATE / 1000, length / 2)
            var phase = 0.0
            for (i in 0 until length) {
                val t = i.toDouble() / length
                val hz = n.from + (n.to - n.from) * t
                phase += 2.0 * PI * hz / SAMPLE_RATE
                // A round first harmonic with a little of its octave: a bird's, not a beep's.
                val wave = sin(phase) + n.body * sin(2.0 * phase)
                val rise = if (i < edge) sin(0.5 * PI * i / edge) else 1.0
                val fall = if (length - 1 - i < edge) sin(0.5 * PI * (length - 1 - i) / edge) else 1.0
                val v = wave / (1.0 + n.body) * rise * fall * PEAK
                if (at + i < out.size) out[at + i] = v.toInt().coerceIn(-PEAK, PEAK).toShort()
            }
            at += length + n.gapMillis * SAMPLE_RATE / 1000
        }
        return out
    }

    /** The loudest sample of [pcm], for a test that holds a chirp quiet. */
    fun peakOf(pcm: ShortArray): Int = pcm.maxOfOrNull { abs(it.toInt()) } ?: 0
}
