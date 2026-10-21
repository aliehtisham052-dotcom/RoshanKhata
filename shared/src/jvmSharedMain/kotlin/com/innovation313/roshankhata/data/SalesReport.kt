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
    /** Revenue less returns less the cost of what was sold (where known). */
    val grossProfit: Double get() = LineMath.round(revenue - returnsTotal - cost)
    /** Gross profit less the cashbook's expenses in the window (the walk-ins' refunds are not expenses — they are the returns). */
    val netProfit: Double get() = LineMath.round(grossProfit - expenses)

    companion object {
        const val DEAD_DAYS = 90

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
