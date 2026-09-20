package com.innovation313.roshankhata.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One month's sales, ready to draw as a bar.
 *
 * [shortLabel] is what fits under a bar on a phone ("Oct"); [fullLabel] is what
 * the reader needs when they tap it and two Octobers could be meant ("Oct 2025").
 * Month names stay in English like every other date in this app, so a date read
 * on the ledger and a date read on the chart are the same word.
 */
data class MonthSale(
    val shortLabel: String,
    val fullLabel: String,
    val total: Double
)

/**
 * Everything the Sale Insights screen shows, gathered in one place.
 *
 * All of it is computed on the device from the ledger the owner already has —
 * no server, no internet, no cost. It is arithmetic over their own entries, the
 * same sums a shopkeeper would do by hand at month's end, done instantly.
 */
data class SaleInsights(
    val thisMonthTotal: Double,
    val lastMonthTotal: Double,
    val salesCount: Int,
    val topProducts: List<ProductStat>,
    val topCustomers: List<CustomerStat>,
    val todayGiven: Double = 0.0,
    val todayReceived: Double = 0.0,
    val todayCount: Int = 0,
    /** The last 12 months, oldest first — the month just gone at the end. */
    val monthlySales: List<MonthSale> = emptyList()
) {
    /**
     * The month-on-month change as a percentage, or null when there is nothing
     * to compare against (no sales last month). Showing "+∞%" against a zero
     * base would be noise, not insight.
     */
    val changePercent: Int?
        get() = if (lastMonthTotal <= 0.0) null
        else (((thisMonthTotal - lastMonthTotal) / lastMonthTotal) * 100).toInt()
}

object Insights {

    /**
     * Start-of-month timestamp for [monthsAgo] months back from now. monthsAgo=0
     * is the first instant of the current month, 1 is the start of last month.
     */
    /** Start-of-today timestamp (local midnight). */
    private fun todayStart(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun monthStart(monthsAgo: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, -monthsAgo)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** First instant of next month — the exclusive upper bound for "this month". */
    private fun nextMonthStart(): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, 1)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /**
     * The last 12 months of sales, oldest first, ending with the month in
     * progress.
     *
     * Twelve small indexed sums, not one big scan: the same query the month
     * total already uses, asked twelve times. A year is the shortest window in
     * which a seasonal trade can see its own season, which is the whole point
     * of drawing it.
     */
    private suspend fun lastTwelveMonths(dao: KhataDao): List<MonthSale> {
        val shortFmt = SimpleDateFormat("MMM", Locale.ENGLISH)
        val fullFmt = SimpleDateFormat("MMM yyyy", Locale.ENGLISH)

        return (11 downTo 0).map { monthsAgo ->
            val from = monthStart(monthsAgo)
            val to = if (monthsAgo == 0) nextMonthStart() else monthStart(monthsAgo - 1)
            MonthSale(
                shortLabel = shortFmt.format(Date(from)),
                fullLabel = fullFmt.format(Date(from)),
                total = dao.salesTotalBetween(from, to)
            )
        }
    }

    /**
     * Gather this month's insights. A single suspend call the UI can await; each
     * query is small and indexed on timestamp.
     */
    suspend fun thisMonth(dao: KhataDao): SaleInsights {
        val thisStart = monthStart(0)
        val thisEnd = nextMonthStart()
        val lastStart = monthStart(1)
        val now = System.currentTimeMillis()

        return SaleInsights(
            thisMonthTotal = dao.salesTotalBetween(thisStart, thisEnd),
            lastMonthTotal = dao.salesTotalBetween(lastStart, thisStart),
            salesCount = dao.salesCountBetween(thisStart, thisEnd),
            topProducts = dao.topProductsBetween(thisStart, thisEnd, 5),
            topCustomers = dao.topCustomersBetween(thisStart, thisEnd, 3),
            todayGiven = dao.givenBetween(todayStart(), now),
            todayReceived = dao.receivedBetween(todayStart(), now),
            todayCount = dao.entryCountBetween(todayStart(), now),
            monthlySales = lastTwelveMonths(dao)
        )
    }
}
