package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File

/**
 * Live sync's notes on the phone (23 Oct 2026, the review): the failures a round passed over and tried
 * again, written down so a relay or a book that failed every round for a week leaves something to read.
 * The computer writes the same notes to its errors.log; the phone's [CrashLog] is for crashes only, so
 * these have a small file of their own and are never shown as a crash.
 *
 * Fed by [LiveTrouble] (one line of each kind every ten minutes, twenty an hour), and kept small here
 * too: past [MAX_BYTES] only the newest half stays. Nothing is uploaded: Report a Problem puts the
 * newest lines into the email the owner reads before sending, labelled as sync notes.
 *
 * What a line holds: when, where in sync, and what was thrown — its kind, its message cleaned
 * ([clean]) and the app's own code line it came from. Never a name, an amount, a phone number or a
 * key: quoted text, long numbers and long codes are taken out of a message, and a JSON parser's
 * message (which quotes the text it read) is left out whole.
 */
object SyncLog {

    private const val DIR = "synclog"
    private const val FILE = "sync-notes.txt"
    const val MAX_BYTES = 32 * 1024
    private const val MESSAGE_CHARS = 160

    /** From the Application: the shared engine's notes come here. */
    fun install(context: Context) {
        val app = context.applicationContext
        LiveTrouble.sink = { where, e -> write(app, where, e) }
    }

    private fun file(context: Context) = File(File(context.filesDir, DIR).apply { mkdirs() }, FILE)

    @Synchronized
    private fun write(context: Context, where: String, e: Throwable) {
        val f = file(context)
        val stamp = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", java.util.Date()).toString()
        f.appendText(line(stamp, where, e) + "\n", Charsets.UTF_8)
        if (f.length() > MAX_BYTES) {
            val kept = trimmed(f.readLines(Charsets.UTF_8), MAX_BYTES / 2)
            f.writeText(kept.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        }
    }

    /** The newest [lines] notes from the last week, or null when there are none. */
    fun latestText(context: Context, lines: Int = 15): String? {
        val f = File(File(context.filesDir, DIR), FILE)
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > 7L * 24 * 60 * 60 * 1000) return null
        val all = runCatching { f.readLines(Charsets.UTF_8) }.getOrNull() ?: return null
        return all.filter { it.isNotBlank() }.takeLast(lines).joinToString("\n").ifBlank { null }
    }

    /** One note: "[stamp]  [where]: Kind: message | cause: Kind: message | at Class.method(File:line)". */
    fun line(stamp: String, where: String, e: Throwable): String = buildString {
        append(stamp).append("  ").append(where.replace('\n', ' ').take(80)).append(": ")
        append(describe(e))
        var cause = e.cause
        var depth = 0
        while (cause != null && cause !== e && depth < 3) {
            append(" | cause: ").append(describe(cause))
            cause = cause.cause
            depth++
        }
        // Where in the app's own code it came from: a code line, nothing of the book.
        e.stackTrace.firstOrNull { it.className.startsWith("com.innovation313.") }?.let {
            append(" | at ").append(it.className.substringAfterLast('.')).append('.').append(it.methodName)
            if (it.fileName != null) append('(').append(it.fileName).append(':').append(it.lineNumber).append(')')
        }
    }

    private fun describe(e: Throwable): String {
        val kind = e.javaClass.name
        val message = if (kind.contains("json", ignoreCase = true)) null else clean(e.message)
        return if (message.isNullOrEmpty()) kind else "$kind: $message"
    }

    /**
     * A message with anything that could be the book's taken out: quoted text, runs of six or more
     * digits (phone numbers, amounts, sequence ids), long codes mixing letters and digits (keys, uids,
     * tokens); on one line, at most [MESSAGE_CHARS] characters. Kinds of failure and short figures
     * (an HTTP status, a port) stay, as they are what makes the note useful.
     */
    fun clean(message: String?): String? {
        if (message.isNullOrBlank()) return null
        return message
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\"[^\"]*\""), "\"…\"")
            .replace(Regex("'[^']*'"), "'…'")
            .replace(Regex("(?=[A-Za-z0-9+/=_-]*\\d)(?=[A-Za-z0-9+/=_-]*[A-Za-z])[A-Za-z0-9+/=_-]{16,}"), "…")
            .replace(Regex("\\d{6,}"), "#")
            .trim()
            .let { if (it.length <= MESSAGE_CHARS) it else it.take(MESSAGE_CHARS) + "…" }
    }

    /** The newest lines that fit in [bytes] (a long file cut down to its latest half). */
    fun trimmed(lines: List<String>, bytes: Int): List<String> {
        val out = ArrayDeque<String>()
        var size = 0
        for (l in lines.asReversed()) {
            if (l.isBlank()) continue
            val n = l.toByteArray(Charsets.UTF_8).size + 1
            if (size + n > bytes) break
            out.addFirst(l)
            size += n
        }
        return out.toList()
    }
}
