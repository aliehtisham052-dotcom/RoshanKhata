package com.innovation313.roshankhata.data

/**
 * A product's two units (schema 36): its own (piece, bottle, kg — what stock
 * is counted in) and, where the owner set one, a pack it is also bought or
 * sold in, with how many of its own unit are in one (carton = 12 bottles,
 * bori = 50 kg).
 *
 * Only that one stated conversion is ever made. Any other unit is left as it
 * was written and, where it disagrees, stock says so instead of guessing (see
 * [Stock]): how many kilos are in a bag is a fact about a product, and only
 * the owner can tell it.
 */
object Units {

    fun norm(u: String?): String? = u?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

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
        if (isPack(p, unit)) qty * p!!.packSize!! to p.defaultUnit else qty to unit

    /**
     * The rate one [unit] of [p] sells for at the counter: its pack price for
     * the pack where the owner set one, else [unitRate] × the pack size; the
     * unit's own rate otherwise.
     */
    fun rateFor(p: Product?, unit: String?, unitRate: Double): Double =
        if (isPack(p, unit)) p!!.packPrice ?: LineMath.round(unitRate * p.packSize!!) else unitRate
}
