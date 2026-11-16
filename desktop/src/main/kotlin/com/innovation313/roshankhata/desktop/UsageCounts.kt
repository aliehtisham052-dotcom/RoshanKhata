package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.DayMath
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Properties

/**
 * How often Roshan Khata came to the front on this computer and how many sums its calculator worked out (17 Nov 2026):
 * the phone's UsageLog (13 Nov 2026, the owner: "aaj calculator par kitni martaba calculation hui, app kitni martaba open
 * hui"), so Hudhud's "app opens" and "calculator" answers say a figure on the computer too instead of "none yet".
 *
 * The same rule as the phone's: a figure per day for the last [KEEP_DAYS] days, nothing about what was opened or worked
 * out, counted from the day this build first counted ([since]; earlier days are not known and Hudhud says from when). Kept
 * in its own file beside app.properties (usage.properties) — per computer, as the phone's are per phone, because an
 * opening is the app's and not a shop's — never in a book, a backup, a daily copy, sync or anything that leaves the
 * computer. Deleting the file forgets them.
 *
 * "Came to the front" on the computer: the app's window opened (the app started), or it was brought back from the taskbar
 * after being minimised — the phone's "the app's screens went from none to one". Moving between the app's pages, a dialog,
 * or another window taking the focus is not an opening.
 */
class UsageCounts(private val file: File) {

    enum class What(val key: String) { APP_OPENS("open_"), CALCULATIONS("calc_") }

    private val props = Properties().apply {
        if (file.isFile) runCatching { file.reader(Charsets.UTF_8).use { load(it) } }
    }

    @Synchronized
    fun add(what: What, now: Long = System.currentTimeMillis()) {
        val day = dayKey(now)
        if (props.getProperty(KEY_SINCE) == null) props.setProperty(KEY_SINCE, DayMath.startOfDay(now).toString())
        val key = what.key + day
        props.setProperty(key, ((props.getProperty(key)?.toIntOrNull() ?: 0) + 1).toString())
        // The whole count since [since] too: the days are kept only [KEEP_DAYS], the "since counting" figure for good.
        val all = TOTAL + what.key
        props.setProperty(all, ((props.getProperty(all)?.toLongOrNull() ?: 0L) + 1).toString())
        // Days past the kept window go once a day (yyyyMMdd sorts as the days do).
        if (props.getProperty(KEY_SWEPT) != day) {
            val oldest = dayKey(now - KEEP_DAYS * DAY_MS)
            props.stringPropertyNames()
                .filter { k -> What.values().any { k.startsWith(it.key) } && k.substringAfter('_') < oldest }
                .forEach { props.remove(it) }
            props.setProperty(KEY_SWEPT, day)
        }
        // A count that cannot be written is lost, never a failure of the app.
        runCatching { save() }
    }

    /**
     * How many in the days of [from, to) — whole local days, as the windows Hudhud reads are (the phone's rule). A window
     * from the first day counted (or before it) to now (or after) is the whole count, kept apart from the days, so it is
     * right after the days older than [KEEP_DAYS] went; any other window is read from its days, starting no earlier than
     * the first day counted (a window from 1970 is not walked day by day).
     */
    @Synchronized
    fun count(what: What, from: Long, to: Long, now: Long = System.currentTimeMillis()): Int {
        val first = since() ?: return 0
        if (from <= first && to > now) {
            return (props.getProperty(TOTAL + what.key)?.toLongOrNull() ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        var total = 0
        val c = Calendar.getInstance().apply { timeInMillis = DayMath.startOfDay(maxOf(from, first)) }
        var guard = 0
        while (c.timeInMillis < to && guard++ < 400) {
            total += props.getProperty(what.key + dayKey(c.timeInMillis))?.toIntOrNull() ?: 0
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return total
    }

    /** The first day counted on this computer, or null when nothing was counted yet. */
    @Synchronized
    fun since(): Long? = props.getProperty(KEY_SINCE)?.toLongOrNull()

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata usage counts") }
        replaceAtomically(tmp, file)
    }

    companion object {
        private const val KEY_SINCE = "since"
        private const val KEY_SWEPT = "swept"
        /** The whole count of a kind: "all_" + its day prefix. */
        private const val TOTAL = "all_"
        const val KEEP_DAYS = 40
        private const val DAY_MS = 24L * 60 * 60 * 1000

        /** yyyyMMdd in Latin digits: sorts as the days do. */
        private fun dayKey(t: Long): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(java.util.Date(t))
    }
}

/**
 * This computer's [UsageCounts] once the app's folder is known (Main's app()): the window counts its openings and the
 * calculator its sums through here; nothing is counted before it is attached (a test, a proof picture).
 */
object DeskUsage {
    @Volatile
    var counts: UsageCounts? = null
        private set

    /** The file is written off the window's thread, one count after another (a daemon: nothing waits for it on closing). */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "rk-usage").apply { isDaemon = true } }

    fun attach(counts: UsageCounts) { this.counts = counts }

    fun opened() = count(UsageCounts.What.APP_OPENS)

    fun calculated() = count(UsageCounts.What.CALCULATIONS)

    private fun count(what: UsageCounts.What) {
        val c = counts ?: return
        val at = System.currentTimeMillis()
        runCatching { writer.execute { c.add(what, at) } }
    }
}
