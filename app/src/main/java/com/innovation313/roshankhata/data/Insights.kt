package com.innovation313.roshankhata.data

import java.util.Calendar

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
    val todayCount: Int = 0
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
            todayCount = dao.entryCountBetween(todayStart(), now)
        )
    }
}
