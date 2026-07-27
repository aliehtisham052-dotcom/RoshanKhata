package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File

/**
 * A temporary record of what the microphone actually did.
 *
 * Voice entry lands right roughly a third of the time, and nobody knows why,
 * because the one thing that would say why has never been written down. The
 * failure could sit in any of three places and the fix for each is different:
 *
 *  - the recogniser mishears, and no code in this app can rescue that
 *  - the recogniser hears correctly but its best guess is not its first, and
 *    this app was throwing the rest away unread
 *  - the sentence arrives intact and this app picks the wrong customer out of
 *    eleven hundred
 *
 * So every attempt is written down whole: every candidate the recogniser
 * offered with its confidence, what was parsed out of the one used, how the
 * closest names scored, what was decided, and what the owner did about it.
 * Twenty real entries turn an argument into a count.
 *
 * THIS IS DIAGNOSTIC SCAFFOLDING AND COMES OUT AGAIN. It is here to answer one
 * question — keep voice entry, cut it down, or drop it — and when that is
 * answered this file and its calls go, whichever way the answer falls.
 *
 * Where it lives: the app's own private files, like [CrashLog]. Nothing is
 * uploaded, ever. Customer names appear in it because the whole question is
 * whether the right customer was picked, so the owner shares it deliberately
 * or not at all — [export] hands them a file and the share sheet, and that is
 * the only way a single line of it leaves the phone.
 */
object VoiceLog {

    /** The one line to flip when this scaffolding is no longer wanted. */
    const val ENABLED = true

    private const val DIR = "voicelog"
    private const val FILE = "voice-log.txt"
    private const val SHARE_DIR = "voicelogs"

    /** Past this the file is started again — a runaway log is not a diagnosis. */
    private const val MAX_BYTES = 256 * 1024

    private fun file(context: Context): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, FILE)

    private fun stamp(): String = android.text.format.DateFormat
        .format("dd MMM yyyy, HH:mm:ss", java.util.Date()).toString()

    /** How many attempts are on record, counted by their headers. */
    fun count(context: Context): Int {
        val f = file(context)
        if (!f.exists()) return 0
        return runCatching { f.readLines().count { it.startsWith("=== #") } }.getOrDefault(0)
    }

    private fun append(context: Context, text: String) {
        if (!ENABLED) return
        runCatching {
            val f = file(context)
            if (f.exists() && f.length() > MAX_BYTES) f.delete()
            f.appendText(text)
        }
        // A recorder that can throw would break the very feature it is
        // measuring, and a lost line is cheaper than a lost entry.
    }

    /** Nothing came back from the recogniser at all. That is a result too. */
    fun nothingHeard(context: Context, languageTag: String?) {
        append(context, buildString {
            appendLine()
            appendLine("=== #${count(context) + 1}  ${stamp()} ===")
            appendLine("Asked in: ${languageTag ?: "phone's own setting"}")
            appendLine("Heard: (nothing returned)")
            appendLine("Decision: —")
        })
    }

    /**
     * One attempt, written the moment the app has decided but before the owner
     * has answered. [outcome] adds their answer to the same block.
     *
     * @param candidates every string the recogniser offered, best first
     * @param confidences its own scores for those, when the phone supplies them
     * @param topNames the closest names in the book: name, score, strong hits
     */
    fun spoken(
        context: Context,
        languageTag: String?,
        candidates: List<String>,
        confidences: List<Float>?,
        amount: Double?,
        isGiven: Boolean?,
        nameWords: List<String>,
        topNames: List<Triple<String, Double, Int>>,
        decision: String
    ) {
        append(context, buildString {
            appendLine()
            appendLine("=== #${count(context) + 1}  ${stamp()} ===")
            appendLine("Asked in: ${languageTag ?: "phone's own setting"}")
            appendLine("Candidates (${candidates.size}):")
            candidates.forEachIndexed { i, c ->
                val score = confidences?.getOrNull(i)
                    ?.let { " [%.2f]".format(it) }
                    ?: ""
                appendLine("  ${if (i == 0) "USED >" else "      "} ${i + 1}. \"$c\"$score")
            }
            appendLine("Parsed: amount=${amount ?: "—"}, direction=" + when (isGiven) {
                true -> "I GAVE"
                false -> "I GOT"
                null -> "— (not said)"
            })
            appendLine("Name words taken from the sentence: $nameWords")
            if (topNames.isEmpty()) {
                appendLine("Closest names: (none scored)")
            } else {
                appendLine("Closest names in the book:")
                topNames.forEachIndexed { i, (name, score, strong) ->
                    appendLine("  ${i + 1}. %-28s %.1f  (strong hits: %d)".format(name, score, strong))
                }
            }
            appendLine("Decision: $decision")
        })
    }

    /** What the owner did with what they were shown. */
    fun outcome(context: Context, outcome: String) {
        append(context, "Outcome: $outcome\n")
    }

    /**
     * A copy in the cache, where the share sheet can reach it. The record
     * itself stays in private storage; this is a deliberate handover, made
     * only when the owner long-presses the microphone and picks somewhere to
     * send it.
     */
    fun export(context: Context): File? {
        val source = file(context)
        if (!source.exists() || source.length() == 0L) return null
        return runCatching {
            val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "roshan-khata-voice-log.txt")
            source.copyTo(out, overwrite = true)
            out
        }.getOrNull()
    }

    /** Start again — used after a batch has been sent and studied. */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
