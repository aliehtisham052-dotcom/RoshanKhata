package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudBook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The owner's check of a test build (14 Nov 2026, the owner: "final APK par test karunga… kitne sahi aur kitne ghalat
 * hue, kitne % kamyab"). Off by default. While it is on, every question Hudhud answers is written on this phone — what
 * was asked, what he took it as, the card's heading — and the owner marks it right or wrong under the card. The counts
 * are the build's success rate; Share sends the list where the owner chooses, Clear starts again.
 *
 * The list may hold customers' names and figures: it stays in the app's own storage (not in the book, a backup, a
 * sync or Android's backup — allowBackup is off) until the owner shares or clears it.
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

    /** Writes one answered question; its row number, for [mark]. Off the screen's thread. */
    @Synchronized
    fun record(context: Context, heard: String, takenAs: String, heading: String, language: String): Int {
        val (lines, row) = CheckLog.add(read(context), stamp(), language, heard, takenAs, heading)
        write(context, lines)
        return row
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
    }

    /** The list, through the share sheet; false when there is nothing to send. */
    fun share(activity: Activity): Boolean {
        val lines = synchronized(this) { read(activity) }
        if (CheckLog.counts(lines).asked == 0) return false
        val dir = File(activity.cacheDir, SHARE_DIR).apply { mkdirs() }
        val out = File(dir, "hudhud_check_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".tsv")
        // A spreadsheet opens the tabs as columns; the byte-order mark keeps Urdu letters readable in Excel.
        out.writeText("﻿" + lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/tab-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.hudhud_check_title))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.hudhud_check_share)))
        }.isSuccess
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
        else -> a.javaClass.simpleName
    }
}

/**
 * The check list's own arithmetic, kept apart so a test holds it: a header, then one row per answer —
 * number, time, language, question, taken as, answer, mark ("right", "wrong" or empty). Tabs and line breaks in a
 * field become spaces, so a row is always one line of seven fields.
 */
object CheckLog {

    const val HEADER = "no\ttime\tlanguage\tquestion\ttaken_as\tanswer\tmark"
    const val RIGHT = "right"
    const val WRONG = "wrong"
    /** The oldest rows go first past this many, so the file never grows without end. */
    const val MAX_ROWS = 3000

    data class Counts(val asked: Int, val right: Int, val wrong: Int) {
        val marked: Int get() = right + wrong
        /** The marked answers that were right, 0-100; null until one is marked. */
        val percent: Int? get() = if (marked == 0) null else Math.round(right * 100f / marked)
    }

    fun clean(s: String): String = s.replace(Regex("[\\t\\r\\n]+"), " ").trim()

    private fun rows(lines: List<String>): List<String> = lines.filter { it.isNotBlank() && it != HEADER }

    /** [lines] with one row added, and the new row's number (one past the highest so far). */
    fun add(lines: List<String>, time: String, language: String, heard: String, takenAs: String, answer: String): Pair<List<String>, Int> {
        val old = rows(lines)
        val next = (old.mapNotNull { it.substringBefore('\t').toIntOrNull() }.maxOrNull() ?: 0) + 1
        val row = listOf(next.toString(), clean(time), clean(language), clean(heard), clean(takenAs), clean(answer), "")
            .joinToString("\t")
        val kept = (old + row).takeLast(MAX_ROWS)
        return (listOf(HEADER) + kept) to next
    }

    /** [lines] with row [row]'s mark set; unchanged when there is no such row. */
    fun mark(lines: List<String>, row: Int, right: Boolean): List<String> = lines.map { l ->
        if (l == HEADER || l.substringBefore('\t').toIntOrNull() != row) l
        else {
            val f = l.split('\t').toMutableList()
            while (f.size < 7) f.add("")
            f[6] = if (right) RIGHT else WRONG
            f.take(7).joinToString("\t")
        }
    }

    fun counts(lines: List<String>): Counts {
        val r = rows(lines)
        val marks = r.map { it.split('\t').getOrNull(6).orEmpty() }
        return Counts(asked = r.size, right = marks.count { it == RIGHT }, wrong = marks.count { it == WRONG })
    }
}
