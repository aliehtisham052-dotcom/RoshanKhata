package com.innovation313.roshankhata.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * How often the app came to the front and how many sums the calculator worked out (13 Nov 2026, the owner: "aaj
 * calculator par kitni martaba calculation hui, app kitni martaba open hui" — part of Hudhud's check and balance).
 *
 * Counted on this phone only: a figure per day for the last [KEEP_DAYS] days in the app's own preferences — never in
 * a book, a backup, a sync or anything that leaves the phone, and nothing about what was opened or worked out. Counting
 * began the day this build first counted ([since]); earlier days are not known, and Hudhud says from when he counts.
 *
 * "Came to the front": the app's screens on screen went from none to one — moving from one screen to the next is not
 * an opening (the next starts before the last stops), and a screen rebuilt by turning the phone is not either.
 */
object UsageLog {

    enum class What(val key: String) { APP_OPENS("open_"), CALCULATIONS("calc_") }

    private const val PREFS = "usage_log"
    private const val KEY_SINCE = "since"
    private const val KEY_SWEPT = "swept"
    private const val KEEP_DAYS = 40

    /** The app's screens started and not stopped. */
    private var started = 0

    /** Screens stopped for a rebuild (a turn of the phone): the start that follows each is not the app coming back. */
    private var rebuilding = 0

    /** One of the app's screens came on (BaseActivity.onStart). */
    @Synchronized
    fun screenStarted(context: Context) {
        if (rebuilding > 0) {
            rebuilding--
            return
        }
        if (started++ == 0) add(context, What.APP_OPENS)
    }

    /** One of the app's screens went off (BaseActivity.onStop); [rebuilt]: it is only being rebuilt. */
    @Synchronized
    fun screenStopped(rebuilt: Boolean) {
        if (rebuilt) {
            rebuilding++
            return
        }
        if (--started < 0) started = 0
    }

    /** The calculator worked a sum out (not a lone figure carried over). */
    fun calculated(context: Context) = add(context, What.CALCULATIONS)

    fun add(context: Context, what: What, now: Long = System.currentTimeMillis()) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val day = dayKey(now)
        val e = p.edit()
        if (!p.contains(KEY_SINCE)) e.putLong(KEY_SINCE, DayMath.startOfDay(now))
        e.putInt(what.key + day, p.getInt(what.key + day, 0) + 1)
        // Days past the kept window go once a day.
        if (p.getString(KEY_SWEPT, null) != day) {
            val oldest = dayKey(now - KEEP_DAYS * DAY_MS)
            p.all.keys.filter { k -> What.values().any { k.startsWith(it.key) } && k.substringAfter('_') < oldest }.forEach { e.remove(it) }
            e.putString(KEY_SWEPT, day)
        }
        e.apply()
    }

    /** How many in the days of [from, to) — whole local days, as the windows Hudhud reads are. */
    fun count(context: Context, what: What, from: Long, to: Long): Int {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var total = 0
        val c = Calendar.getInstance().apply { timeInMillis = DayMath.startOfDay(from) }
        var guard = 0
        while (c.timeInMillis < to && guard++ < 400) {
            total += p.getInt(what.key + dayKey(c.timeInMillis), 0)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return total
    }

    /** The first day counted on this phone, or null when nothing was counted yet. */
    fun since(context: Context): Long? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (p.contains(KEY_SINCE)) p.getLong(KEY_SINCE, 0L) else null
    }

    /** yyyyMMdd in Latin digits: sorts as the days do. */
    private fun dayKey(t: Long): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(java.util.Date(t))

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
