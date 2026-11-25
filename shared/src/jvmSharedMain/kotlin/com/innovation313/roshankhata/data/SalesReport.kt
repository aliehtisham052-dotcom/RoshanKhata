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
    val taxIncluded: Boolean? = null,
    /** The ticket's goods before its own discount, and that discount (21 Oct 2026). */
    val saleSubtotal: Double? = null,
    val saleDiscount: Double? = null,
    /**
     * What the line brought in once its ticket's money was shared over ALL its lines to the coin ([SoldLines.spread],
     * BC-252): set, [revenue] is this figure; null (a ticket with no discount, or a line read alone) and [revenue]
     * works the share out as before. Lines of one ticket then add up to the ticket's own total exactly.
     */
    val spread: Double? = null
) {
    /**
     * What the line brought in: its share of the ticket's own discount taken
     * off — a Rs 100 off a Rs 1,000 ticket is Rs 10 off each Rs 100 of goods
     * (21 Oct 2026: the report counted the goods at full price) — then any
     * tax that was inside its price (that money is the government's). The
     * tax comes off after the discount, as it was worked out on the
     * discounted line (SaleMath.lineTaxes; 22 Oct 2026, the review: taken
     * off before, a 1,180 line with 18% inside and 118 off read 916.20, not 900).
     */
    val revenue: Double get() {
        spread?.let { return it }
        val sub = saleSubtotal ?: 0.0
        val off = (saleDiscount ?: 0.0).coerceAtLeast(0.0)
        val share = if (sub > 0.0 && off > 0.0) (1.0 - off / sub).coerceIn(0.0, 1.0) else 1.0
        val inside = if (taxIncluded == true) taxAmount ?: 0.0 else 0.0
        return LineMath.round(SaleMath.lineTotal(quantity, rate, discount) * share - inside)
    }
    val cost: Double? get() = costRate?.let { LineMath.round(it * quantity) }
}

/**
 * The counter's sold lines with each ticket's discount shared over its lines to the coin (BC-252). [KhataSales] does
 * the same for a khata entry's goods as it builds them.
 */
object SoldLines {

    /**
     * [lines] in the same order, each ticket's lines carrying [SoldLine.spread] when the ticket has a discount: the
     * ticket's own goods less its discount, shared over its lines by their worth (largest remainder, [TicketSpread]),
     * then any tax that was inside a line's price taken off it. Lines of a ticket with no discount, or with nothing
     * sound to share, and lines that already carry a spread, are returned as they are.
     */
    fun spread(lines: List<SoldLine>): List<SoldLine> {
        val d = LineMath.scale()
        val byTicket = lines.withIndex().filter { it.value.spread == null }.groupBy { it.value.saleId }
        val out = lines.toMutableList()
        for ((_, ls) in byTicket) {
            val head = ls.first().value
            val sub = head.saleSubtotal ?: continue
            val off = (head.saleDiscount ?: 0.0).coerceAtLeast(0.0)
            if (sub <= 0.0 || off <= 0.0) continue
            val taxable = Money.minor(sub, d) - Money.minor(off.coerceAtMost(sub), d)
            val weights = ls.map { Money.minor(SaleMath.lineTotal(it.value.quantity, it.value.rate, it.value.discount), d) }
            val shares = TicketSpread.shares(taxable, weights) ?: continue
            ls.forEachIndexed { k, iv ->
                val l = iv.value
                val inside = if (l.taxIncluded == true) l.taxAmount ?: 0.0 else 0.0
                out[iv.index] = l.copy(spread = Money.round(TicketSpread.figure(shares[k], d) - inside, d))
            }
        }
        return out
    }
}

/** One khata sale's goods line, or the sale alone when it has none ([KhataDao.khataSaleRowsBetween]). */
data class KhataSaleRow(
    val entryId: Long,
    val timestamp: Long,
    val amount: Double,
    val partyName: String,
    /** Who wrote the entry (the signed-in name, null for the owner) — the report's by-cashier view. */
    val createdBy: String?,
    val itemName: String?,
    val quantity: Double?,
    val unit: String?,
    val rate: Double?,
    val isBonus: Boolean?,
    val productId: Long?,
    val category: String?
)

/**
 * The khata's sales as the report's lines (Marhala 3, 25 Oct 2026, "kya bika" by one rule): each sale's
 * amount — what Sale Insights counts — spread over its goods. Priced goods take their share of it (an
 * amount under the goods' value is a discount spread over them; one over it leaves the rest as a line of
 * no item); a lone goods line with no rate takes the whole amount; free goods and goods with no rate count
 * their quantity at nothing (several unpriced lines cannot be told apart, so their money is a line of no
 * item); a sale with no goods is one line of no item ("" — the views word it, srep_no_goods). A line's cost
 * is its product's ([Cost.perUnit]) in the product's own unit, else unknown.
 * Each sale is its own ticket in the counts (a negative id, never a counter ticket's).
 */
object KhataSales {

    /** A fortnight: a wholesaler's khata sales of 14 days fit one of Android's 2 MB answers. */
    private const val SLICE_MS = 14L * 24 * 60 * 60 * 1000
    /** Past this many fortnights (some fifteen years, or a window left open) the rows are read in one go, as before. */
    private const val MAX_SLICES = 400

    /**
     * [KhataDao.khataSaleRowsBetween] a fortnight at a time (3 Nov 2026, the speed work): a year of a wholesaler's
     * khata sales is some 130,000 rows, and Android hands an answer over 2 MB at a time, running the query again
     * from its first row for each — the year's sales report took 8.8 s on CI's emulator. The rows and their order
     * are the single query's: each window is [a, b), the next starts where it ended, and a sale's goods share its
     * moment, so they are never split.
     */
    suspend fun rowsBetween(dao: KhataDao, from: Long, to: Long): List<KhataSaleRow> {
        if (to <= from) return emptyList()
        if ((to - from) / SLICE_MS > MAX_SLICES) return dao.khataSaleRowsBetween(from, to)
        val out = ArrayList<KhataSaleRow>()
        var a = from
        while (a < to) {
            val b = if (to - a > SLICE_MS) a + SLICE_MS else to
            out += dao.khataSaleRowsBetween(a, b)
            a = b
        }
        return out
    }

    /**
     * What a sold line's cost is read from: the product and its supplier-bill lines with their days (newest first), or
     * nothing for a line with no product. Each product once ([lines] keeps what it was given).
     */
    class Shelf(val product: Product?, val purchases: List<Cost.Dated>)

    suspend fun lines(dao: KhataDao, rows: List<KhataSaleRow>): List<SoldLine> = lines(rows) { id ->
        val p = dao.productById(id)
        // The owner's own cost price is the first rule: no purchases are read for it.
        Shelf(p, if (p == null || p.costPrice != null) emptyList() else dao.datedPurchasesOfProduct(id))
    }

    /**
     * The sale's cost is [Cost.forSale]: the one rule Insights' profit calls too (BC-261) — purchases dated up to the sale
     * only, and the line's unit by [Cost.forUnit] / [Units] (BC-271), never a rate in another unit.
     */
    suspend fun lines(rows: List<KhataSaleRow>, shelfOf: suspend (Long) -> Shelf): List<SoldLine> {
        val shelves = HashMap<Long, Shelf>()
        suspend fun costRate(r: KhataSaleRow, qty: Double): Double? {
            val id = r.productId ?: return null
            if (qty <= 0.0) return null
            val shelf = shelves.getOrPut(id) { shelfOf(id) }
            val product = shelf.product ?: return null
            return Cost.forSale(product, r.timestamp, r.unit, shelf.purchases)
        }
        val out = ArrayList<SoldLine>()
        for ((entryId, rs) in rows.groupBy { it.entryId }) {
            val head = rs.first()
            val amount = head.amount
            fun line(r: KhataSaleRow?, qty: Double, rate: Double, cost: Double?, sub: Double? = null, off: Double? = null, spread: Double? = null) = SoldLine(
                saleId = -entryId, timestamp = head.timestamp, customerName = head.partyName, createdBy = head.createdBy, taxPercent = null,
                productId = r?.productId, itemName = r?.itemName?.trim().orEmpty(), category = r?.category,
                quantity = qty, unit = r?.unit, rate = rate, discount = 0.0, costRate = cost,
                saleSubtotal = sub, saleDiscount = off, spread = spread
            )
            // A sale with no goods comes back from the LEFT JOIN as one row with no line at all.
            val goods = rs.filter { it.itemName != null || it.quantity != null || it.rate != null }
            val sold = goods.filter { it.isBonus != true }
            val priced = sold.filter { (it.quantity ?: 0.0) > 0.0 && (it.rate ?: 0.0) > 0.0 }
            val value = priced.sumOf { it.quantity!! * it.rate!! }
            when {
                goods.isEmpty() -> out += line(null, 1.0, amount, null)
                priced.isEmpty() && sold.size == 1 && (sold[0].quantity ?: 0.0) > 0.0 -> {
                    val r = sold[0]
                    val q = r.quantity!!
                    out += line(r, q, amount / q, costRate(r, q), spread = Money.round(amount))
                    goods.filter { it.isBonus == true }.forEach { b -> out += line(b, b.quantity ?: 0.0, 0.0, costRate(b, b.quantity ?: 0.0)) }
                }
                else -> {
                    // The goods' own value first; an amount under it is a discount spread over the priced lines. The
                    // lines of one entry add up to its amount to the coin (BC-252): the discount is cut by the
                    // largest-remainder rule from the entry's own amount, and what an amount holds above its goods
                    // is the coins left over, exactly, as a line of no item.
                    val d = LineMath.scale()
                    val weights = priced.map { Money.minor(LineMath.lineTotal(it.quantity, it.rate)!!, d) }
                    val cut = TicketSpread.cut(Money.minor(amount, d), weights)
                    val off = if (cut.discounted) value - amount else 0.0
                    var k = 0
                    goods.forEach { r ->
                        val q = r.quantity ?: 0.0
                        if ((r.quantity ?: 0.0) > 0.0 && (r.rate ?: 0.0) > 0.0 && r.isBonus != true) {
                            out += line(r, q, r.rate!!, costRate(r, q), sub = value, off = off, spread = TicketSpread.figure(cut.shares[k++], d))
                        } else out += line(r, q, 0.0, costRate(r, q))
                    }
                    if (cut.rest > 0L) out += line(null, 1.0, TicketSpread.figure(cut.rest, d), null, spread = TicketSpread.figure(cut.rest, d))
                }
            }
        }
        return out
    }
}

/**
 * A line that came back, with the sold line it came back from (22 Oct 2026,
 * the review): the share of what that line brought in, and what it had cost
 * — so profit takes off a return exactly as the sale was counted (its tax and
 * the ticket's discount out), and only where its cost is known.
 */
data class ReturnedLine(
    val quantity: Double,
    val soldQuantity: Double,
    val rate: Double,
    val discount: Double,
    val costRate: Double?,
    val taxAmount: Double?,
    val taxIncluded: Boolean?,
    val saleSubtotal: Double?,
    val saleDiscount: Double?,
    /** The sold line this came back from and its ticket: [ReturnedLines.spread] finds the line's share of the ticket's discount by them. */
    val saleItemId: Long = 0,
    val saleId: Long = 0,
    /**
     * What the sold line was COUNTED at once its ticket's discount was shared over all its lines to the coin
     * ([SoldLines.spread], BC-262): set, a return gives back exactly that (its share for the part returned); null (a line
     * read alone, a ticket with no discount) and [revenue] works the share out as before.
     */
    val spread: Double? = null,
    /**
     * How much of the same sold line came back in returns written before this one (D3, BC-293). A line returned in parts
     * gives back its cumulative share each time, rounded once: (before + this) of the line less (before) of it. Three
     * returns of one bag each from a line of 3 counted at 966.67 give back 322.22, 322.23 and 322.22 — the 966.67 the
     * sale brought in, not 966.66. 0 (a line returned whole, a test's line) is the single share as before.
     */
    val backBefore: Double = 0.0
) {
    val revenue: Double get() {
        if (soldQuantity <= 0.0) return 0.0
        val line = spread ?: soldLine().revenue
        val before = (backBefore / soldQuantity).coerceIn(0.0, 1.0)
        val through = ((backBefore + quantity) / soldQuantity).coerceIn(0.0, 1.0)
        return LineMath.round(LineMath.round(line * through) - LineMath.round(line * before))
    }
    val cost: Double? get() = costRate?.let { LineMath.round(it * quantity) }

    /** The sold line, whole, as the sales report reads it (no spread yet). */
    internal fun soldLine(): SoldLine = SoldLine(
        saleId = saleId, timestamp = 0, customerName = null, createdBy = null, taxPercent = null, productId = null, itemName = "",
        category = null, quantity = soldQuantity, unit = null, rate = rate, discount = discount, costRate = costRate,
        taxAmount = taxAmount, taxIncluded = taxIncluded, saleSubtotal = saleSubtotal, saleDiscount = saleDiscount
    )
}

/**
 * Goods back from a ticket that carried a discount (BC-262, 24 Nov 2026): the sales report counted each of the ticket's
 * lines at its share cut to the coin over ALL the ticket's lines ([SoldLines.spread]: Rs 100 off 3 x 1,000 is 966.67,
 * 966.67 and 966.66), but a returned line was rebuilt alone and took the plain share, 966.67, so returning the third line
 * reversed a cent more than it had brought in. [spread] gives each returned line what its sold line was counted at.
 */
object ReturnedLines {

    /** One line of a ticket as the spread needs it ([KhataDao.ticketLinesOf]): its worth and the tax inside its price. */
    data class TicketLine(
        val saleItemId: Long,
        val quantity: Double,
        val rate: Double,
        val discount: Double,
        val taxAmount: Double?,
        val taxIncluded: Boolean?
    )

    /**
     * [line] with the spread of its own sold line, found by cutting its ticket's discount over [ticket] (every line of the
     * ticket, [SoldLines.spread]'s way). Unchanged when the ticket had no discount, the sold line is not among [ticket],
     * or there is nothing sound to share.
     */
    fun spreadOf(line: ReturnedLine, ticket: List<TicketLine>): ReturnedLine {
        val off = line.saleDiscount ?: 0.0
        if (line.soldQuantity <= 0.0 || off <= 0.0 || (line.saleSubtotal ?: 0.0) <= 0.0) return line
        val whole = ticket.map { t ->
            SoldLine(
                saleId = line.saleId, timestamp = 0, customerName = null, createdBy = null, taxPercent = null, productId = null,
                itemName = "", category = null, quantity = t.quantity, unit = null, rate = t.rate, discount = t.discount, costRate = null,
                taxAmount = t.taxAmount, taxIncluded = t.taxIncluded, saleSubtotal = line.saleSubtotal, saleDiscount = line.saleDiscount
            )
        }
        val at = ticket.indexOfFirst { it.saleItemId == line.saleItemId }
        if (at < 0) return line
        return line.copy(spread = SoldLines.spread(whole)[at].revenue)
    }

    /**
     * What is left of a sold line to come back: [sold] less [back], a quantity kept to six places by the units contract
     * ([Units.roundQty], BC-271) — 0.3 less 0.1 and 0.2 is nothing left, not a dust of 5e-17 beside the form's own check.
     */
    fun soldLeft(sold: Double, back: Double): Double = Units.roundQty(sold - back)

    /** [returned] each with its sold line's spread; one read of a ticket's lines per ticket ([ticketLines]). */
    suspend fun spread(returned: List<ReturnedLine>, ticketLines: suspend (Long) -> List<TicketLine>): List<ReturnedLine> {
        val tickets = HashMap<Long, List<TicketLine>>()
        return returned.map { r ->
            if ((r.saleDiscount ?: 0.0) <= 0.0) r
            else spreadOf(r, tickets.getOrPut(r.saleId) { ticketLines(r.saleId) })
        }
    }

    suspend fun spread(dao: KhataDao, returned: List<ReturnedLine>): List<ReturnedLine> = spread(returned) { dao.ticketLinesOf(it) }
}

/**
 * The sales report for a window (POS Phase 7): by day, by item, by
 * category, by customer, by cashier, by hour, the tax given, profit and
 * loss, and the products that have not sold. Every figure summed from the
 * ticket lines ([SoldLine]) and the cashbook rows — never from a stored
 * total. Profit is counted only where every line's cost is known; the
 * report says how much of the revenue that covers.
 */
class SalesReport(
    val from: Long, val to: Long, val lines: List<SoldLine>, val expenses: Double, val returnsTotal: Double, val deadStock: List<String>,
    /** What the returned goods had cost (where known): they are back on the shelf. */
    val returnedCost: Double = 0.0,
    /** What those same goods (the ones whose cost is known) had brought in when sold ([ReturnedLine.revenue]). */
    val returnedRevenue: Double = 0.0
) {

    data class Row(val key: String, val count: Int, val quantity: Double, val revenue: Double, val cost: Double?) {
        val profit: Double? get() = cost?.let { LineMath.round(revenue - it) }
    }

    private fun group(key: (SoldLine) -> String): List<Row> =
        lines.groupBy(key).map { (k, ls) ->
            val costKnown = ls.all { it.cost != null }
            Row(k, ls.map { it.saleId }.distinct().size, ls.sumOf { it.quantity }, LineMath.round(ls.sumOf { it.revenue }),
                if (costKnown) LineMath.round(ls.sumOf { it.cost!! }) else null)
        }.sortedByDescending { it.revenue }

    /**
     * Per item AND unit (8 Nov review M54: 2 cartons + 10 bottles of Cola read "Qty 12"). An item sold in one unit
     * keeps its plain name; one sold in several is listed once per unit, the unit after the name.
     */
    val byItem: List<Row>
        get() {
            val unitsOf = lines.groupBy { it.itemName }.mapValues { (_, ls) -> ls.map { it.unit.orEmpty() }.distinct().size }
            return group { l -> if ((unitsOf[l.itemName] ?: 1) > 1 && !l.unit.isNullOrBlank()) l.itemName + " · " + UnitWords.label(l.unit) else l.itemName }
        }
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
     * The revenue whose cost is known, less that cost, less the margin of the
     * goods that came back (21 Oct 2026: a line with no cost counted as costing
     * nothing, so its whole price read as profit). A return is taken off as
     * its sale was counted, and only where its cost is known (22 Oct 2026, the
     * review: the whole refund, tax and unknown-cost goods included, came off
     * a revenue that held none of them). The report says how much of the
     * revenue this covers ([costKnownRevenue]); nothing is guessed for the rest.
     */
    val grossProfit: Double get() = LineMath.round(costKnownRevenue - cost - (returnedRevenue - returnedCost))
    /** Gross profit less the expenses in the window: the cashbook's and the staff's pay (the walk-ins' refunds are not expenses — they are the returns). */
    val netProfit: Double get() = LineMath.round(grossProfit - expenses)

    /**
     * Every line sold has a known cost (25 Oct 2026, the audit). Only then is
     * a net figure the shop's own: the expenses are the whole shop's, while
     * the gross covers only the goods whose cost is known — with no cost
     * known at all, "Net profit" was the expenses alone, as a loss.
     */
    val costComplete: Boolean get() = lines.all { it.cost != null }

    /** The gross, or null when goods sold and none of them has a known cost (a 0 there reads as "no profit"). */
    val grossOrNull: Double? get() = if (lines.isNotEmpty() && lines.none { it.cost != null }) null else grossProfit

    /** The net, or null until every line sold has its cost ([costComplete]). */
    val netOrNull: Double? get() = if (costComplete) netProfit else null

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
            // The counter's tickets and the khata's own sales (Marhala 3): what Sale Insights counts, line by line.
            val lines = (SoldLines.spread(dao.soldLinesBetween(from, to)) + KhataSales.lines(dao, KhataSales.rowsBetween(dao, from, to)))
                .sortedBy { it.timestamp }
            // The same expenses Insights' profit takes (Profit.between): the cashbook's, and the staff's pay, which lives in
            // its own table (reports-agree, 24 Nov 2026: the net here ignored it and read higher than Insights' by that pay).
            val expenses = dao.cashbookExpensesBetween(from - 1, to - 1) + dao.staffPaidBetween(from - 1, to - 1)
            val returns = dao.returnsBetween(from, to).filter { it.status == SaleStatus.DONE }.sumOf { it.total }
            // Dead stock: on the shelf, and not sold over the counter or the khata in DEAD_DAYS.
            val since = to - DEAD_DAYS * 24L * 60 * 60 * 1000
            val recent = dao.productsSoldSince(since).toSet()
            val dead = Stock.of(dao).filter { (it.onHand ?: 0.0) > 0.0 && it.productId !in recent }.map { it.name }.sortedWith(NameOrder.byName)
            val returned = ReturnedLines.spread(dao, dao.returnedLinesBetween(from, to)).filter { it.cost != null }
            return SalesReport(
                from, to, lines, LineMath.round(expenses), LineMath.round(returns), dead,
                returnedCost = LineMath.round(returned.sumOf { it.cost!! }),
                returnedRevenue = LineMath.round(returned.sumOf { it.revenue })
            )
        }
    }
}
