package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppScope
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The owner's check of a test build (14 Nov 2026, the owner: "final APK par test karunga… kitne sahi aur kitne ghalat
 * hue, kitne % kamyab"). Off by default. While it is on, every question Hudhud answers is written on this phone — what
 * was asked, what he took it as, the card's heading — and the owner marks it right or wrong under the card. The counts
 * are the build's success rate; Share sends the list where the owner chooses, Clear starts again.
 *
 * The list may hold customers' names and figures: it stays in the app's own storage (not in the book, a backup, a
 * sync or Android's backup — allowBackup is off) until the owner shares or clears it.
 *
 * A shared copy (16 Nov 2026, audit T16) is written to cache/hudhud/ for the share sheet and deleted again: ten minutes
 * after the share is handed over (the app that was picked reads it first — Backup.sweepCache's and the Waris card's
 * reason), and, for one a closed app left behind, the next time the list is shared or its settings open. Clear takes
 * every copy with the list.
 *
 * Each row says which business it was asked in (16 Nov 2026, audit S-06): the list is one file for every shop on the phone,
 * so a shop deleted takes its own rows with it ([forgetBusiness], from Businesses.delete), and a shared list says whose book
 * each answer read. Erase all takes the whole file, the copies and the switch (EraseAll.wipe empties filesDir, cacheDir and
 * every preferences file). Rows written before have no business and stay until the list is cleared.
 */
object HudhudCheck {

    private const val PREFS = "hudhud_check"
    private const val KEY_ON = "on"
    private const val FILE = "hudhud_check.tsv"
    private const val SHARE_DIR = "hudhud"

    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun read(context: Context): List<String> = file(context).takeIf { it.exists() }?.readLines(Charsets.UTF_8).orEmpty()

    private fun write(context: Context, lines: List<String>) {
        val f = file(context)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    /**
     * Writes one answered question; its row number, for [mark]. Off the screen's thread. [input]: how the question came
     * in — spoken, typed or a chip (25 Nov 2026), so the recogniser's mistakes are counted apart from Hudhud's own.
     */
    @Synchronized
    fun record(context: Context, heard: String, takenAs: String, heading: String, language: String, input: CheckLog.Input?): Int {
        val business = runCatching { Businesses.active(context).id }.getOrNull()
        val (lines, row) = CheckLog.add(read(context), stamp(), language, heard, takenAs, heading, business, input)
        write(context, lines)
        return row
    }

    /** Deletes the rows asked in business [id], and every shared copy (they hold the whole list). Off the main thread. */
    @Synchronized
    fun forgetBusiness(context: Context, id: Long) {
        val lines = read(context)
        val kept = CheckLog.withoutBusiness(lines, id)
        if (kept != lines) {
            if (CheckLog.counts(kept).asked == 0) file(context).delete() else write(context, kept)
        }
        sweepCopies(context, keepMs = 0L)
    }

    /** The owner's mark on row [row]: right or wrong (a second tap changes it). */
    @Synchronized
    fun mark(context: Context, row: Int, right: Boolean) {
        val lines = read(context)
        val marked = CheckLog.mark(lines, row, right)
        if (marked != lines) write(context, marked)
    }

    @Synchronized
    fun counts(context: Context): CheckLog.Counts = CheckLog.counts(read(context))

    @Synchronized
    fun clear(context: Context) {
        file(context).delete()
        // A write cut off by a killed process leaves its whole text here.
        File(context.applicationContext.filesDir, "$FILE.tmp").delete()
        sweepCopies(context, keepMs = 0L)
    }

    /**
     * Deletes the shared copies in cache/hudhud/ older than [keepMs] ([CheckLog.staleCopies]); nothing else in that folder
     * is touched. Never throws: a copy that cannot be deleted now is tried again next time.
     */
    fun sweepCopies(context: Context, keepMs: Long = CheckLog.SHARE_KEEP_MS) {
        runCatching {
            val dir = File(context.applicationContext.cacheDir, SHARE_DIR)
            val files = dir.listFiles { f -> f.isFile }?.toList().orEmpty()
            val stale = CheckLog.staleCopies(files.map { it.name to it.lastModified() }, System.currentTimeMillis(), keepMs).toSet()
            files.filter { it.name in stale }.forEach { runCatching { it.delete() } }
        }
    }

    /** The list, through the share sheet; false when there is nothing to send. */
    fun share(activity: Activity): Boolean {
        val lines = synchronized(this) { read(activity) }
        if (CheckLog.counts(lines).asked == 0) return false
        sweepCopies(activity)
        val dir = File(activity.cacheDir, SHARE_DIR).apply { mkdirs() }
        val out = File(dir, CheckLog.COPY_PREFIX + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + CheckLog.COPY_SUFFIX)
        // A spreadsheet opens the tabs as columns; the byte-order mark keeps Urdu letters readable in Excel.
        out.writeText("﻿" + lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/tab-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.hudhud_check_title))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val sent = runCatching {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.hudhud_check_share)))
        }.isSuccess
        if (!sent) runCatching { out.delete() }
        // Not at once: the chooser hands the file over before the chosen app has read it. By its path, so a screen closed
        // meanwhile still has it deleted; a process killed before then leaves it to the next sweep.
        else AppScope.launch { delay(CheckLog.SHARE_KEEP_MS); runCatching { out.delete() } }
        return sent
    }

    private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    /** What a card said at its top: its heading and figure, then its first line. */
    fun heading(a: HudhudBook.Answer): String =
        (listOfNotNull(a.label, a.figure) + a.lines.take(1)).joinToString(" · ")

    /** What Hudhud took the question as, in the log's plain words. */
    fun describe(a: Hudhud.Ask?): String = when (a) {
        null -> "error"
        is Hudhud.Ask.Guessed -> "guessed > " + describe(a.ask)
        is Hudhud.Ask.DidYouMean -> "choices: " + a.options.joinToString(" | ") { describe(it) }
        is Hudhud.Ask.Help -> "help: " + a.key
        is Hudhud.Ask.Figure -> "figure: " + a.kind
        is Hudhud.Ask.Count -> "count: " + a.thing + (a.window?.let { " / $it" } ?: "")
        is Hudhud.Ask.Status -> "status: " + a.of
        is Hudhud.Ask.Person -> "person: " + a.facet + " " + a.ids
        is Hudhud.Ask.Goods -> "goods: " + a.facet + " " + a.ids
        is Hudhud.Ask.Shop -> "shop: " + a.facet
        is Hudhud.Ask.About -> "about: " + a.topic
        is Hudhud.Ask.Chat -> "talk: " + a.talk
        is Hudhud.Ask.NeedName -> "whose: " + (a.facet ?: "balance")
        is Hudhud.Ask.Period -> "period: " + a.measure + " / " + a.window
        is Hudhud.Ask.Parties -> "balance: " + a.ids
        is Hudhud.Ask.CallMe -> "call me"
        is Hudhud.Ask.Now -> "now > " + describe(a.ask)
        is Hudhud.Ask.SpanLimit -> "span limit: " + a.options.joinToString(" | ") { describe(it) }
        else -> a.javaClass.simpleName
    }
}

/**
 * The check list's own arithmetic, kept apart so a test holds it: a header, then one row per answer —
 * number, time, language, question, taken as, answer, mark ("right", "wrong" or empty), business (the id of the shop it
 * was asked in; empty in rows written before 16 Nov 2026), input ("voice", "typed" or "chip" — [Input]; empty in rows
 * written before 25 Nov 2026). Tabs and line breaks in a field become spaces, so a row is always one line of nine
 * fields; an older row of seven is read as one with no business, and one of seven or eight as one with no input.
 */
object CheckLog {

    const val HEADER = "no\ttime\tlanguage\tquestion\ttaken_as\tanswer\tmark\tbusiness\tinput"
    /** Fields in a row: [HEADER]'s. */
    const val FIELDS = 9

    /**
     * How a question came in (25 Nov 2026, Marhala 2 of the master plan: a recogniser's mistake is not an understanding
     * mistake, so a log that cannot tell them apart cannot measure either). [word] is what the row says;
     * scripts/hudhud_checklog.py reads the same three words.
     */
    enum class Input(val word: String) {
        /** Spoken: the question is what the recogniser heard. */
        VOICE("voice"),
        /** Typed in the box (or handed to the sheet as words). */
        TYPED("typed"),
        /** A chip or a row tapped: its question was known already. */
        CHIP("chip");

        companion object {
            fun of(word: String?): Input? = values().firstOrNull { it.word == word?.trim() }
        }
    }
    const val RIGHT = "right"
    const val WRONG = "wrong"
    /** The oldest rows go first past this many, so the file never grows without end. */
    const val MAX_ROWS = 3000

    /** A shared copy's name: [COPY_PREFIX] + a time + [COPY_SUFFIX] (HudhudCheck.share). */
    const val COPY_PREFIX = "hudhud_check_"
    const val COPY_SUFFIX = ".tsv"
    /** How long a shared copy may stay for the app it was handed to (as the Waris card's picture). */
    const val SHARE_KEEP_MS = 10 * 60_000L

    /**
     * Of [files] (name to last-modified time) in cache/hudhud/, the shared copies of the list older than [keepMs] at [now]
     * (16 Nov 2026, audit T16): only names this list writes, so another file in that folder is never touched. A copy with a
     * time in the future (a clock set back) is old too — it can never become old by waiting.
     */
    fun staleCopies(files: List<Pair<String, Long>>, now: Long, keepMs: Long): List<String> =
        files.filter { (name, at) ->
            name.startsWith(COPY_PREFIX) && name.endsWith(COPY_SUFFIX) && (at > now || now - at >= keepMs)
        }.map { it.first }

    data class Counts(val asked: Int, val right: Int, val wrong: Int) {
        val marked: Int get() = right + wrong
        /** The marked answers that were right, 0-100; null until one is marked. */
        val percent: Int? get() = if (marked == 0) null else Math.round(right * 100f / marked)
    }

    fun clean(s: String): String = s.replace(Regex("[\\t\\r\\n]+"), " ").trim()

    /** A header line, this one's or an older list's (seven or eight fields): never counted as a row. */
    private fun isHeader(l: String): Boolean = l.startsWith("no\t")

    private fun rows(lines: List<String>): List<String> = lines.filter { it.isNotBlank() && !isHeader(it) }

    /**
     * [lines] with one row added, and the new row's number (one past the highest so far). [business]: the shop it was asked
     * in; [input]: how the question came in (null writes an empty field, as an older row reads). An older list's header
     * is written again as this one's; its rows stay as they were (shorter, read as having no business or input).
     */
    fun add(
        lines: List<String>, time: String, language: String, heard: String, takenAs: String, answer: String,
        business: Long? = null, input: Input? = null
    ): Pair<List<String>, Int> {
        val old = rows(lines)
        val next = (old.mapNotNull { it.substringBefore('\t').toIntOrNull() }.maxOrNull() ?: 0) + 1
        val row = listOf(next.toString(), clean(time), clean(language), clean(heard), clean(takenAs), clean(answer), "",
            business?.toString().orEmpty(), input?.word.orEmpty())
            .joinToString("\t")
        val kept = (old + row).takeLast(MAX_ROWS)
        return (listOf(HEADER) + kept) to next
    }

    /** [lines] with row [row]'s mark set; unchanged when there is no such row. */
    fun mark(lines: List<String>, row: Int, right: Boolean): List<String> = lines.map { l ->
        if (isHeader(l) || l.substringBefore('\t').toIntOrNull() != row) l
        else {
            val f = l.split('\t').toMutableList()
            while (f.size < FIELDS) f.add("")
            f[6] = if (right) RIGHT else WRONG
            f.take(FIELDS).joinToString("\t")
        }
    }

    /** The shop a row was asked in; null for a row written before rows said it. */
    fun businessOf(row: String): Long? = row.split('\t').getOrNull(7)?.toLongOrNull()

    /** How a row's question came in; null for a row written before rows said it (seven or eight fields). */
    fun inputOf(row: String): Input? = Input.of(row.split('\t').getOrNull(8))

    /** [lines] without the rows asked in business [id] (rows that name no business stay). */
    fun withoutBusiness(lines: List<String>, id: Long): List<String> {
        val kept = rows(lines).filter { businessOf(it) != id }
        return if (kept.size == rows(lines).size) lines else listOf(HEADER) + kept
    }

    fun counts(lines: List<String>): Counts {
        val r = rows(lines)
        val marks = r.map { it.split('\t').getOrNull(6).orEmpty() }
        return Counts(asked = r.size, right = marks.count { it == RIGHT }, wrong = marks.count { it == WRONG })
    }
}
