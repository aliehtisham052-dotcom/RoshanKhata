package com.innovation313.roshankhata.data

import java.util.Calendar

/**
 * When to look, for "who should be told this is back in".
 *
 * There is no season table in this app and there should not be one. A dealer
 * would have to say which months are Rabi and which are Kharif, per crop, per
 * district, and keep it true as sowing shifts with the weather — a form to fill
 * in, get wrong, and forget to update. The book already knows: whoever bought
 * urea last October is exactly who to tell that the urea is in.
 *
 * So a season is a window of time, worked out from today.
 */
object SeasonWindow {

    /** How far either side of the anniversary still counts as the same season. */
    const val SPREAD_DAYS = 45

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A span of time, half-open: [from, to). */
    data class Window(val from: Long, val to: Long)

    /**
     * The same stretch of the year, one year ago.
     *
     * Not a single date. Sowing does not happen on an anniversary — it moves
     * with the rain, the previous harvest, and when the money came in. Six
     * weeks either side catches the same season; a narrower window would miss
     * the customer who came late last year and is exactly the one worth a
     * reminder this year.
     */
    fun sameSeasonLastYear(now: Long): Window {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -1)
        }
        val anniversary = cal.timeInMillis
        return Window(
            from = anniversary - SPREAD_DAYS * DAY_MS,
            to = anniversary + SPREAD_DAYS * DAY_MS
        )
    }

    /** Everyone who has bought it in the past year, season or not. */
    fun lastTwelveMonths(now: Long): Window {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -1)
        }
        return Window(from = cal.timeInMillis, to = now)
    }

    /**
     * Everyone, ever.
     *
     * `to` is deliberately now and not Long.MAX_VALUE: an entry dated into the
     * future is a typo, and a promotion list is not the place to surface one.
     */
    fun everything(now: Long): Window = Window(from = 0L, to = now)
}
