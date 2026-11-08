package com.innovation313.roshankhata.data

/**
 * What a log on this device keeps of an error (3 Nov 2026 audit, L25).
 *
 * The phone's crash records and the computer's errors.log wrote each exception's message whole, and Report a
 * Problem sends the newest of them by email: a message can carry what was typed or read ("For input string:
 * \"0300 1234567\"", a customer's name in a file path, an amount). The sync notes already cleaned theirs
 * ([clean], from the phone's SyncLog); every log now does the same, on both platforms.
 */
object LogText {

    const val MESSAGE_CHARS = 160

    /**
     * A message with anything that could be the book's taken out: quoted text, runs of six or more digits (phone
     * numbers, amounts, sequence ids), long codes mixing letters and digits (keys, uids, tokens); on one line, at
     * most [max] characters. Kinds of failure and short figures (an HTTP status, a port) stay, as they are what
     * makes the note useful.
     */
    fun clean(message: String?, max: Int = MESSAGE_CHARS): String? {
        if (message.isNullOrBlank()) return null
        return message
            .replace(Regex("[\\r\\n\\t]+"), " ")
            // A file path or name ("…/Statement_Ali Traders.pdf") can carry a customer's name (8 Nov review m28).
            .replace(Regex("[A-Za-z]:\\\\(?:[^\\\\/:*?\"<>|]+\\\\)*[^\\\\/:*?\"<>|]*?\\.[A-Za-z0-9]{2,5}\\b"), "<file>")
            .replace(Regex("(?<![\\w.])/(?:[^/:*?\"<>|]+/)+[^/:*?\"<>|]*?\\.[A-Za-z0-9]{2,5}\\b"), "<file>")
            .replace(Regex("\"[^\"]*\""), "\"…\"")
            .replace(Regex("'[^']*'"), "'…'")
            .replace(Regex("(?=[A-Za-z0-9+/=_-]*\\d)(?=[A-Za-z0-9+/=_-]*[A-Za-z])[A-Za-z0-9+/=_-]{16,}"), "…")
            .replace(Regex("\\d{6,}"), "#")
            .trim()
            .let { if (it.length <= max) it else it.take(max) + "…" }
    }

    /**
     * An exception as a stack trace, each message in the chain [clean]ed: the kind and where it happened, which is
     * what a report needs, without what the owner had typed. At most [frames] lines of the first exception and a
     * few of each cause.
     */
    fun trace(e: Throwable, frames: Int = 40): String = buildString {
        var t: Throwable? = e
        var depth = 0
        val seen = HashSet<Throwable>()
        while (t != null && depth < 8 && seen.add(t)) {
            if (depth > 0) append("Caused by: ")
            append(t.javaClass.name)
            clean(t.message, 400)?.let { append(": ").append(it) }
            append('\n')
            val shown = if (depth == 0) frames else 12
            t.stackTrace.take(shown).forEach { append("\tat ").append(it).append('\n') }
            if (t.stackTrace.size > shown) append("\t… ").append(t.stackTrace.size - shown).append(" more\n")
            t = t.cause
            depth++
        }
    }
}
