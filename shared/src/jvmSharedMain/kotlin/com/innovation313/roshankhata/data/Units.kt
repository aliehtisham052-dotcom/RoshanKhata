package com.innovation313.roshankhata.data

/**
 * THE UNITS AND STOCK-VALUATION CONTRACT (24 Nov 2026, plan B11, BC-253). One place says how a quantity is named,
 * converted, compared, rounded and valued; [Units], [Cost], [Stock], [Fefo], [Valuation] and [StockCover] all keep it,
 * and UnitsContract*Test holds them to it together.
 *
 * 1. CANONICAL UNIT. A unit is saved as [UnitWords]' key ("bag", "kg", "piece") or, for the owner's own word, as typed.
 *    Two units are compared through [norm] (that key, lower-cased), so "KG", "kg" and "کلو" are one unit and a
 *    pack's "Carton" meets a line's "carton". Nothing else compares units: not a trim-and-lowercase of its own.
 * 2. PACK CONVERSION. A product has its own unit (what stock is counted in) and at most one pack (carton = 12 bottles,
 *    bori = 50 kg). Only that one stated conversion is ever made ([toBase] to the own unit, [fromBase] back to the
 *    pack). Any other unit is left as written; nobody guesses how many kilos a bag holds.
 * 3. MIXED UNITS. Two units that are both written and differ, with no pack between them, cannot be added or
 *    subtracted: [agree] says so (a missing unit disagrees with nothing). A side that cannot be counted gives NO
 *    figure ([Stock.ProductStock.onHand] null, a lot's movement left out, a cost line left out, a value null) rather
 *    than a wrong one. A figure is never read in a unit it was not counted in.
 * 4. COST ([Cost]). What one of the product's own unit cost: the cost the owner wrote on the product; else the
 *    weighted average of its supplier-bill lines, each turned into the own unit ([Cost.average]); else the newest
 *    rate that can be said per own unit ([Cost.lastInOwnUnit]); else nothing. A rate in another unit is never used
 *    as the own unit's ([Cost.forUnit] says the cost of a line's unit, or null).
 * 5. ROUNDING. A QUANTITY is rounded to [QTY_DECIMALS] (six) places, half up, by [roundQty] — never to the money's
 *    decimals (a bottle is not a paisa: 0.125 kg is 0.125 even in a currency with two decimals). A RATE derived from
 *    a cost keeps [Cost.RATE_DECIMALS] (six). MONEY (a line's total, a stock value) goes through [Money.round] /
 *    [LineMath], which multiply in BigDecimal and round once to the shop's coin: [Cost.value] is the one product of
 *    a quantity and a rate.
 * 6. VALUE. What the shelf is worth is on hand x cost, each product alone ([Valuation.Row.of]): an unknown shelf, an
 *    unknown cost or a shelf counted in a unit the cost is not per is "no value", never a guess; oversold stock is
 *    worth nothing, not less than nothing. A product whose stock sits entirely in batches is worth the sum of its
 *    lots at that cost ([Fefo.lotsOf] caps lots by the shelf, so lots plus the untagged rest are the shelf).
 *
 * A product's two units (schema 36): its own (piece, bottle, kg — what stock is counted in) and, where the owner set
 * one, a pack it is also bought or sold in, with how many of its own unit are in one (carton = 12 bottles, bori = 50 kg).
 *
 * Only that one stated conversion is ever made. Any other unit is left as it was written and, where it disagrees,
 * stock says so instead of guessing (see [Stock]): how many kilos are in a bag is a fact about a product, and only
 * the owner can tell it.
 */
object Units {

    /** Places a quantity keeps (contract 5): one millionth of a unit, far under any real pack or weight. */
    const val QTY_DECIMALS = 6

    /** [u] as it is compared (contract 1): [UnitWords]' key for any language's word for it, else the typed text, lower-cased; null when blank. */
    fun norm(u: String?): String? = UnitWords.canonical(u)?.lowercase()

    /**
     * Whether two units may be counted together (contract 3): either is missing, or they are the same unit. Two
     * written, different units are not — unless a pack stands between them, which [toBase] has already turned into
     * the own unit before this is asked.
     */
    fun agree(a: String?, b: String?): Boolean {
        val x = norm(a)
        val y = norm(b)
        return x == null || y == null || x == y
    }

    /** [qty] to [QTY_DECIMALS] places, half up, by its decimal text (contract 5); not-a-number and infinity pass through. */
    fun roundQty(qty: Double): Double =
        if (!qty.isFinite()) qty
        else java.math.BigDecimal.valueOf(qty).setScale(QTY_DECIMALS, java.math.RoundingMode.HALF_UP).toDouble()

    /** True when [p] has a pack the app may convert: a pack unit, its size, and the product's own unit. */
    fun hasPack(p: Product?): Boolean =
        p != null && norm(p.packUnit) != null && (p.packSize ?: 0.0) > 0.0 && norm(p.defaultUnit) != null &&
            norm(p.packUnit) != norm(p.defaultUnit)

    /** True when [unit] is [p]'s pack. */
    fun isPack(p: Product?, unit: String?): Boolean = hasPack(p) && norm(unit) == norm(p!!.packUnit)

    /**
     * How many of [p]'s own unit one [unit] is: its pack size for its pack,
     * 1 for anything else (its own unit, no unit, or one the app cannot convert).
     */
    fun factor(p: Product?, unit: String?): Double = if (isPack(p, unit)) p!!.packSize!! else 1.0

    /** [qty] [unit] in [p]'s own unit when [unit] is its pack; otherwise as written, with its unit. */
    fun toBase(p: Product?, qty: Double, unit: String?): Pair<Double, String?> =
        if (isPack(p, unit)) roundQty(qty * p!!.packSize!!) to p.defaultUnit else qty to unit

    /**
     * [qtyInOwn] of [p]'s own unit as a count of [unit]: packs for its pack, as written for anything else. The way
     * back of [toBase] — a batch's left turned into the bill's packs, a take at the counter into the line's unit.
     */
    fun fromBase(p: Product?, qtyInOwn: Double, unit: String?): Double = roundQty(qtyInOwn / factor(p, unit))

    /**
     * The rate one [unit] of [p] sells for at the counter: its pack price for
     * the pack where the owner set one, else [unitRate] × the pack size; the
     * unit's own rate otherwise.
     */
    fun rateFor(p: Product?, unit: String?, unitRate: Double): Double {
        if (!isPack(p, unit)) return unitRate
        val pack = p!!.packPrice ?: return LineMath.round(unitRate * p.packSize!!)
        // The pack price is the cash one; a customer on credit pays the same share more as on the unit
        // (8 Nov review L105: cash 100 / credit 110 per unit sold the pack at the cash price on credit).
        val cash = p.salePrice
        return if (cash != null && cash > 0.0 && Math.abs(unitRate - cash) > 0.0001) LineMath.round(pack * unitRate / cash) else pack
    }
}
