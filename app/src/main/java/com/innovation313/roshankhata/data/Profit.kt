package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.Locale

/**
 * What the shop actually earned on what it sold.
 *
 * Profit on a sale line is quantity × (sale rate − purchase rate). The sale
 * rate is on the line itself. The purchase rate is the part that has to be
 * found, and this object is honest about where it finds it:
 *
 * 1. If the owner picked a BATCH when writing the sale (the line carries a
 *    [billItemId]), that bill line's rate is the cost. No estimate at all.
 * 2. Otherwise the cost is the weighted average of every purchase of that
 *    product dated ON OR BEFORE the sale — the price the shop had actually
 *    paid by the time it sold. A purchase recorded after the sale cannot have
 *    been its cost, so it does not count.
 * 3. A line whose product has no priced purchase by then, whose unit does not
 *    match the purchases (bought in "bag", sold in "kg" — see [Stock]), or
 *    which carries no product or no sale rate at all, has NO cost. It is left
 *    out, counted in [ProfitReport.skippedLines], and the report says what
 *    share of the month's sales it was able to work out. A figure built on a
 *    guessed cost would look exact and be wrong; "worked out from 70% of
 *    sales" is a true sentence.
 *
 * Bonus lines (goods given free, [isBonus]) earn nothing and still cost what
 * they cost, so they lower the profit. Goods the customer brought back
 * (isGiven = 0 with a quantity) reverse both the sale and its cost.
 *
 * Expenses from the Cashbook are reported on their own line, never folded
 * into the per-product figures: rent is not a cost of Urea.
 *
 * Everything is arithmetic over the owner's own ledger, on the phone. Nothing
 * here writes.
 */
object Profit {

    /** One sale (or return) line as the DAO reads it. */
    data class SaleLine(
        val productId: Long?,
        val itemName: String?,
        val quantity: Double?,
        val unit: String?,
        val rate: Double?,
        val isBonus: Boolean,
        val billItemId: Long?,
        val timestamp: Long,
        /** 1 = goods went out (a sale); 0 = goods came back (a return). */
        val isGiven: Boolean
    )

    /** One priced supplier-bill line as the DAO reads it. */
    data class PurchaseLine(
        val id: Long,
        val productId: Long?,
        val quantity: Double,
        val unit: String?,
        val rate: Double,
        val billDate: Long
    )

    /** One product's month, ranked by what it earned. */
    data class ProductProfit(
        val productId: Long,
        val name: String,
        val soldQty: Double,
        val unit: String?,
        val revenue: Double,
        val cost: Double
    ) {
        val profit: Double get() = revenue - cost
    }

    data class ProfitReport(
        /** Revenue minus cost over the lines whose cost is known. */
        val grossProfit: Double,
        /** Sale value of those same lines — what the gross figure covers. */
        val coveredRevenue: Double,
        /** Every sale in the period, lines or no lines (the Insights total). */
        val salesTotal: Double,
        /** Cashbook outgoings in the period. */
        val expenses: Double,
        /** Products with a known cost, highest profit first. */
        val products: List<ProductProfit>,
        /** Sale lines that had to be left out (no product, rate, or cost). */
        val skippedLines: Int,
        /** Sale lines that went in. */
        val countedLines: Int
    ) {
        val netAfterExpenses: Double get() = grossProfit - expenses

        /** Nothing could be worked out at all. */
        val isEmpty: Boolean get() = countedLines == 0

        /**
         * How much of the month's sales the gross figure is built on, 0..100,
         * or null when there were no sales to measure against. Capped at 100:
         * lines can add up to more than the entry they belong to when a
         * discount was given on the total.
         */
        val coveragePercent: Int?
            get() = if (salesTotal <= 0.0) null
            else ((coveredRevenue / salesTotal) * 100).toInt().coerceIn(0, 100)
    }

    /** The [Stock] rule: a blank unit disagrees with nothing; two different units do. */
    private fun unitsAgree(a: String?, b: String?): Boolean {
        val x = a?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val y = b?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        return x == null || y == null || x == y
    }

    /**
     * The purchase rate for one sale line, or null when there is none to be
     * had honestly. Exposed for the tests; [compute] is the caller.
     */
    internal fun costFor(
        line: SaleLine,
        purchasesById: Map<Long, PurchaseLine>,
        purchasesByProduct: Map<Long, List<PurchaseLine>>
    ): Double? {
        // 1. The batch the owner chose, when its unit does not contradict the line.
        line.billItemId?.let { purchasesById[it] }?.let { batch ->
            if (unitsAgree(batch.unit, line.unit)) return batch.rate
        }
        // 2. Weighted average of what was bought by the day of the sale.
        val productId = line.productId ?: return null
        val before = purchasesByProduct[productId].orEmpty()
            .filter { it.billDate <= line.timestamp && it.quantity > 0.0 && unitsAgree(it.unit, line.unit) }
        if (before.isEmpty()) return null
        val qty = before.sumOf { it.quantity }
        if (qty <= 0.0) return null
        return before.sumOf { it.quantity * it.rate } / qty
    }

    /**
     * Marry the two halves the DAO reads separately — the same reason as
     * [Stock.combine]: a product on three bills and four sales must not be
     * counted twelve times by one joined query.
     */
    fun compute(
        sales: List<SaleLine>,
        purchases: List<PurchaseLine>,
        productNames: Map<Long, String>,
        salesTotal: Double,
        expenses: Double
    ): ProfitReport {
        val byId = purchases.associateBy { it.id }
        val byProduct = purchases.filter { it.productId != null }.groupBy { it.productId!! }

        var gross = 0.0
        var covered = 0.0
        var counted = 0
        var skipped = 0
        val perProduct = LinkedHashMap<Long, ProductProfit>()

        for (line in sales) {
            val qty = line.quantity
            if (qty == null || qty <= 0.0) { skipped++; continue }
            // A bonus line has no price by definition; any other line needs one.
            val rate = if (line.isBonus) 0.0 else line.rate
            if (rate == null) { skipped++; continue }
            val cost = costFor(line, byId, byProduct)
            if (cost == null) { skipped++; continue }

            val sign = if (line.isGiven) 1.0 else -1.0
            val revenue = sign * qty * rate
            val lineCost = sign * qty * cost
            gross += revenue - lineCost
            covered += revenue
            counted++

            val pid = line.productId ?: continue
            val prev = perProduct[pid]
            val name = productNames[pid] ?: line.itemName ?: prev?.name ?: ""
            perProduct[pid] = ProductProfit(
                productId = pid,
                name = name,
                soldQty = (prev?.soldQty ?: 0.0) + sign * qty,
                unit = prev?.unit ?: line.unit,
                revenue = (prev?.revenue ?: 0.0) + revenue,
                cost = (prev?.cost ?: 0.0) + lineCost
            )
        }

        return ProfitReport(
            grossProfit = gross,
            coveredRevenue = covered,
            salesTotal = salesTotal,
            expenses = expenses,
            products = perProduct.values.sortedByDescending { it.profit },
            skippedLines = skipped,
            countedLines = counted
        )
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

    /** The report for [from, to). Four small reads, all indexed on time. */
    suspend fun between(dao: KhataDao, from: Long, to: Long): ProfitReport {
        val sales = dao.profitSaleLinesBetween(from, to)
        val purchases = dao.profitPurchaseLinesBefore(to)
        val names = dao.productsOnce().associate { it.id to it.name }
        return compute(
            sales = sales,
            purchases = purchases,
            productNames = names,
            salesTotal = dao.salesTotalBetween(from, to),
            // cashbookTotalBetween is (from, to]; shift by one to read [from, to).
            expenses = dao.cashbookTotalBetween(false, from - 1, to - 1)
        )
    }

    /** The month in progress. */
    suspend fun thisMonth(dao: KhataDao): ProfitReport =
        between(dao, monthStart(0), monthStart(-1))

    /** The month just gone. */
    suspend fun lastMonth(dao: KhataDao): ProfitReport =
        between(dao, monthStart(1), monthStart(0))
}
