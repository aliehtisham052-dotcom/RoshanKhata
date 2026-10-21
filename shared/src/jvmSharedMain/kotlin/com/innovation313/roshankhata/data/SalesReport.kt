package com.innovation313.roshankhata.data

import java.util.Calendar

/**
 * One sold line with its ticket's facts beside it (POS Phase 7): what the
 * sales report is summed from. Posted tickets only.
 */
data class SoldLine(
    val saleId: Long,
    val timestamp: Long,
    val customerName: String?,
    val createdBy: String?,
    val taxPercent: Double?,
    val productId: Long?,
    val itemName: String,
    val category: String?,
    val quantity: Double,
    val unit: String?,
    val rate: Double,
    val discount: Double,
    val costRate: Double?,
    /** The line's own tax (schema 36): rate, money, and whether the price held it. Null on older lines. */
    val taxRate: Double? = null,
    val taxAmount: Double? = null,
    val taxIncluded: Boolean? = null
) {
    /** What the line brought in, less any tax that was inside its price (that money is the government's). */
    val revenue: Double get() = LineMath.round(SaleMath.lineTotal(quantity, rate, discount) - (if (taxIncluded == true) taxAmount ?: 0.0 else 0.0))
    val cost: Double? get() = costRate?.let { LineMath.round(it * quantity) }
}

/**
 * The sales report for a window (POS Phase 7): by day, by item, by
 * category, by customer, by cashier, by hour, the tax given, profit and
 * loss, and the products that have not sold. Every figure summed from the
 * ticket lines ([SoldLine]) and the cashbook rows — never from a stored
 * total. Profit is counted only where every line's cost is known; the
 * report says how much of the revenue that covers.
 */
class SalesReport(val from: Long, val to: Long, val lines: List<SoldLine>, val expenses: Double, val returnsTotal: Double, val deadStock: List<String>) {

    data class Row(val key: String, val count: Int, val quantity: Double, val revenue: Double, val cost: Double?) {
        val profit: Double? get() = cost?.let { LineMath.round(revenue - it) }
    }

    private fun group(key: (SoldLine) -> String): List<Row> =
        lines.groupBy(key).map { (k, ls) ->
            val costKnown = ls.all { it.cost != null }
            Row(k, ls.map { it.saleId }.distinct().size, ls.sumOf { it.quantity }, LineMath.round(ls.sumOf { it.revenue }),
                if (costKnown) LineMath.round(ls.sumOf { it.cost!! }) else null)
        }.sortedByDescending { it.revenue }

    val byItem: List<Row> get() = group { it.itemName }
    val byCategory: List<Row> get() = group { it.category ?: "" }
    val byCustomer: List<Row> get() = group { it.customerName ?: "" }
    val byCashier: List<Row> get() = group { it.createdBy ?: "" }
    val byDay: List<Row> get() = group { DateWords.format("yyyy-MM-dd", it.timestamp) }.sortedBy { it.key }
    val byHour: List<Row> get() = group { "%02d".format(Digits.FIGURES, Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)) }.sortedBy { it.key }

    /** The tax given, rate by rate: the taxable revenue and the tax on it. */
    val tax: List<Triple<Double, Double, Double>>
        get() = lines.mapNotNull { l ->
            // A line's own rate first (schema 36), else its ticket's tax %; its own figure where it kept one.
            val rate = l.taxRate?.takeIf { it > 0.0 } ?: l.taxPercent?.takeIf { it > 0.0 } ?: return@mapNotNull null
            Triple(rate, l.revenue, l.taxAmount ?: (l.revenue * rate / 100.0))
        }.groupBy { it.first }.map { (rate, ls) ->
            Triple(rate, LineMath.round(ls.sumOf { it.second }), LineMath.round(ls.sumOf { it.third }))
        }.sortedBy { it.first }

    val revenue: Double get() = LineMath.round(lines.sumOf { it.revenue })
    val saleCount: Int get() = lines.map { it.saleId }.distinct().size
    val costKnownRevenue: Double get() = LineMath.round(lines.filter { it.cost != null }.sumOf { it.revenue })
    val cost: Double get() = LineMath.round(lines.sumOf { it.cost ?: 0.0 })
    /**
     * The revenue whose cost is known, less that cost, less returns (21 Oct 2026:
     * a line with no cost counted as costing nothing, so its whole price read as
     * profit). The report says how much of the revenue this covers
     * ([costKnownRevenue]); nothing is guessed for the rest.
     */
    val grossProfit: Double get() = LineMath.round(costKnownRevenue - returnsTotal - cost)
    /** Gross profit less the cashbook's expenses in the window (the walk-ins' refunds are not expenses — they are the returns). */
    val netProfit: Double get() = LineMath.round(grossProfit - expenses)

    /** One bar of the report's chart: its label, where its slice of time starts, and what sold in it. */
    data class Bar(val label: String, val from: Long, val revenue: Double)

    /**
     * The report's chart (21 Oct 2026): by hour for a window of one day, by
     * day up to [DAILY_BARS] days, by month beyond — every slice of the
     * window present and an empty one as zero, so a gap in the chart is a
     * day nothing sold, never a missing day. Summed from the same lines.
     */
    fun bars(tz: java.util.TimeZone = java.util.TimeZone.getDefault()): List<Bar> {
        val firstDay = DayCloseMath.dayOf(from, tz)
        val lastDay = DayCloseMath.dayOf(maxOf(from, to - 1), tz)
        val days = (lastDay - firstDay + 1).toInt()
        fun cal(ms: Long) = Calendar.getInstance(tz).apply { timeInMillis = ms }
        return when {
            days <= 1 -> {
                val sums = DoubleArray(24)
                lines.forEach { sums[cal(it.timestamp).get(Calendar.HOUR_OF_DAY)] += it.revenue }
                val dayStart = DayCloseMath.startOf(firstDay, tz)
                (0 until 24).map { h -> Bar("%02d".format(Digits.FIGURES, h), dayStart + h * 3_600_000L, LineMath.round(sums[h])) }
            }
            days <= DAILY_BARS -> {
                val sums = DoubleArray(days)
                lines.forEach { l -> val i = (DayCloseMath.dayOf(l.timestamp, tz) - firstDay).toInt(); if (i in 0 until days) sums[i] += l.revenue }
                (0 until days).map { i -> val start = DayCloseMath.startOf(firstDay + i, tz); Bar(DateWords.format("d MMM", start), start, LineMath.round(sums[i])) }
            }
            else -> {
                fun monthOf(ms: Long) = cal(ms).let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
                val first = monthOf(from)
                val sums = DoubleArray(monthOf(maxOf(from, to - 1)) - first + 1)
                lines.forEach { l -> val i = monthOf(l.timestamp) - first; if (i in sums.indices) sums[i] += l.revenue }
                sums.indices.map { i ->
                    val m = first + i
                    val start = Calendar.getInstance(tz).apply { clear(); set(m / 12, m % 12, 1) }.timeInMillis
                    Bar(DateWords.format("MMM yyyy", start), start, LineMath.round(sums[i]))
                }
            }
        }
    }

    companion object {
        const val DEAD_DAYS = 90
        /** Up to this many days the chart has a bar a day; past it, a bar a month. */
        const val DAILY_BARS = 62

        suspend fun compute(dao: KhataDao, from: Long, to: Long): SalesReport {
            val lines = dao.soldLinesBetween(from, to)
            val expenses = dao.cashbookExpensesBetween(from - 1, to - 1)
            val returns = dao.returnsBetween(from, to).filter { it.status == SaleStatus.DONE }.sumOf { it.total }
            // Dead stock: on the shelf, and not sold over the counter or the khata in DEAD_DAYS.
            val since = to - DEAD_DAYS * 24L * 60 * 60 * 1000
            val recent = dao.productsSoldSince(since).toSet()
            val dead = Stock.of(dao).filter { (it.onHand ?: 0.0) > 0.0 && it.productId !in recent }.map { it.name }.sorted()
            return SalesReport(from, to, lines, LineMath.round(expenses), LineMath.round(returns), dead)
        }
    }
}
