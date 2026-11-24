package com.innovation313.roshankhata.data

/**
 * What one of a product's own unit cost the shop (21 Oct 2026, the critical
 * review: the last purchase rate alone swung every profit figure with the
 * newest bill). Part of the units and valuation contract written on [Units]
 * (item 4: the cost rule; item 5: rates keep [RATE_DECIMALS], money goes
 * through [value]).
 *
 * In order: the cost the owner wrote on the product; else the weighted
 * average of every purchase on a supplier bill — Σ(quantity × rate) over Σ
 * quantity, each line turned into the product's own unit through its pack
 * ([Units]); else the last purchase rate as it was written. Lines in a unit
 * the app cannot convert are left out of the average, not guessed — and a
 * product with no own unit set takes its cost only from lines that all agree
 * on one unit (24 Nov 2026, B11: bag lines and kilo lines were averaged
 * together). Nothing here is stored: it is worked out when a sale is rung up
 * (and kept on the sale line, so a ticket's profit never moves afterwards) or
 * when the shelf is valued.
 */
object Cost {

    /** Places a cost per unit keeps (a third of 100 is 33.333333, not 33.33): the sale line's own rate, never rounded to the coin. */
    const val RATE_DECIMALS = 6

    data class Purchase(val qty: Double, val unit: String?, val rate: Double)

    /** A [Purchase] with the day it was bought (the supplier bill's date): what a sale's cost is read from ([forSale]). */
    data class Dated(val qty: Double, val unit: String?, val rate: Double, val boughtAt: Long) {
        fun asPurchase(): Purchase = Purchase(qty, unit, rate)
    }

    /** [rate] to [RATE_DECIMALS] places, half up; not-a-number and infinity pass through. */
    fun roundRate(rate: Double): Double =
        if (!rate.isFinite()) rate
        else java.math.BigDecimal.valueOf(rate).setScale(RATE_DECIMALS, java.math.RoundingMode.HALF_UP).toDouble()

    /**
     * What [qty] of [unit]'s own count at [perUnit] comes to, in the shop's coin: the one product of a quantity and a
     * rate (BigDecimal, rounded once, as every line total is), not a Double times a Double — 0.1 kg at 0.35 is 0.04, not
     * 0.03. A shelf below nothing is worth nothing (oversold stock is not a negative value, 8 Nov review L73).
     */
    fun value(qty: Double, perUnit: Double): Double = LineMath.lineTotal(maxOf(qty, 0.0), perUnit) ?: 0.0

    /**
     * The one distinct unit the lines are written in (after packs), when the product names none of its own: null for
     * "no unit written anywhere" is fine (the lines then agree with anything), and [Conflict] when two differ.
     */
    private object Conflict

    private fun commonUnit(product: Product, units: List<String?>): Any? {
        val own = Units.norm(product.defaultUnit)
        if (own != null) return own
        val written = units.mapNotNull { Units.norm(it) }.distinct()
        return when (written.size) { 0 -> null; 1 -> written[0]; else -> Conflict }
    }

    /** The weighted average purchase cost per one of [product]'s own unit; null when no line can be counted. */
    fun average(product: Product, purchases: List<Purchase>): Double? {
        var money = 0.0
        var qty = 0.0
        val counted = purchases.filter { it.qty > 0.0 && it.rate >= 0.0 }.map { p -> p to Units.toBase(product, p.qty, p.unit) }
        val common = commonUnit(product, counted.map { it.second.second })
        if (common === Conflict) return null
        for ((p, converted) in counted) {
            val (q, u) = converted
            // A line in a unit that is neither the product's own nor its pack cannot be averaged in.
            if (!Units.agree(u, common as String?)) continue
            money += p.qty * p.rate
            qty += q
        }
        return if (qty > 0.0) money / qty else null
    }

    /**
     * The cost of one of [product]'s own unit, by the rule above; null when nobody can know it. The two lists are read
     * only as far as the rule goes (the owner's cost needs neither, the average needs the first).
     */
    suspend fun perUnit(dao: KhataDao, product: Product?): Double? {
        if (product == null) return null
        return rule(product, { dao.purchasesOfProduct(product.id) }, { dao.purchaseRatesNewestFirst(product.id) })
    }

    /**
     * The rule itself, with nothing read: [purchases] (every priced bill line) and [newestFirst] (the same, newest first) are
     * asked for only when the step before gives nothing. [perUnit] is this with the database behind the two lists, so a
     * test of this is a test of the cost every screen shows.
     */
    inline fun rule(product: Product, purchases: () -> List<Purchase>, newestFirst: () -> List<Purchase>): Double? {
        product.costPrice?.let { return it }
        average(product, purchases())?.let { return it }
        return lastInOwnUnit(product, newestFirst())
    }

    /**
     * The newest purchase rate that can be said per one of [product]'s own
     * unit: as written in that unit, or a pack's rate over its size (22 Oct
     * 2026, the review: the rate of a 50 kg bag was taken as the rate of a
     * kilo, and a 50 kg sale showed a loss of 220,250 on its line for good).
     * Null when every rate is in a unit the app cannot convert.
     */
    fun lastInOwnUnit(product: Product, newestFirst: List<Purchase>): Double? {
        val priced = newestFirst.filter { it.rate >= 0.0 }
        val common = commonUnit(product, priced.map { Units.toBase(product, 1.0, it.unit).second })
        if (common === Conflict) return null
        for (p in priced) {
            if (Units.isPack(product, p.unit)) return p.rate / product.packSize!!
            if (Units.agree(p.unit, common as String?)) return p.rate
        }
        return null
    }

    /**
     * THE ONE COST OF A SOLD LINE (BC-261, 24 Nov 2026): what one [unit] of [product] cost the shop when it was sold at
     * [saleAt]. A purchase made after the sale cannot have been its cost, so only the supplier-bill lines dated on or
     * before [saleAt] are read ([purchases], any order; the newest of equal days first when they are given so). Then the
     * rule above ([rule]: the owner's cost price, else the weighted average, else the newest rate that can be said) and
     * [forUnit] for the line's unit. A product with no unit of its own can only say its cost per the unit its purchases
     * were written in, so only the purchases that agree with the line's [unit] count ("bought in bags, sold in kilos" has
     * no cost, not a bag's cost read as a kilo's). Null when nobody can know it: the callers leave the line out or say so.
     * Insights' profit ([Profit.costFor]) and the sales report's khata lines ([KhataSales.lines]) both call this, so the
     * two screens cost one sale at one figure.
     */
    fun forSale(product: Product, saleAt: Long, unit: String?, purchases: List<Dated>): Double? {
        val upTo = purchases.filter { it.boughtAt <= saleAt }.sortedByDescending { it.boughtAt }
        val usable = if (Units.norm(product.defaultUnit) == null) upTo.filter { Units.agree(it.unit, unit) } else upTo
        val lines = usable.map { it.asPurchase() }
        val perUnit = rule(product, { lines }, { lines }) ?: return null
        return forUnit(product, unit, perUnit)
    }

    /**
     * The cost of one [unit] of [product] given the cost of one of its own unit ([perUnit], from [rule]): a pack costs
     * its size times the own unit's, a line in the own unit (or in none) costs [perUnit], and a line in any other unit
     * costs nothing the app can say — null, never the own unit's cost read as that unit's (a kilo is not a bag). Kept to
     * [RATE_DECIMALS]: the sale line keeps it for good, so a ticket's profit never moves.
     */
    fun forUnit(product: Product?, unit: String?, perUnit: Double?): Double? {
        if (perUnit == null) return null
        if (Units.isPack(product, unit)) return roundRate(perUnit * product!!.packSize!!)
        if (product != null && !Units.agree(product.defaultUnit, unit)) return null
        return roundRate(perUnit)
    }
}
