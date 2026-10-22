package com.innovation313.roshankhata.data

/**
 * What one of a product's own unit cost the shop (21 Oct 2026, the critical
 * review: the last purchase rate alone swung every profit figure with the
 * newest bill).
 *
 * In order: the cost the owner wrote on the product; else the weighted
 * average of every purchase on a supplier bill — Σ(quantity × rate) over Σ
 * quantity, each line turned into the product's own unit through its pack
 * ([Units]); else the last purchase rate as it was written. Lines in a unit
 * the app cannot convert are left out of the average, not guessed. Nothing
 * here is stored: it is worked out when a sale is rung up (and kept on the
 * sale line, so a ticket's profit never moves afterwards) or when the shelf
 * is valued.
 */
object Cost {

    data class Purchase(val qty: Double, val unit: String?, val rate: Double)

    /** The weighted average purchase cost per one of [product]'s own unit; null when no line can be counted. */
    fun average(product: Product, purchases: List<Purchase>): Double? {
        var money = 0.0
        var qty = 0.0
        val own = Units.norm(product.defaultUnit)
        for (p in purchases) {
            if (p.qty <= 0.0 || p.rate < 0.0) continue
            val (q, u) = Units.toBase(product, p.qty, p.unit)
            // A line in a unit that is neither the product's own nor its pack cannot be averaged in.
            if (own != null && Units.norm(u) != null && Units.norm(u) != own) continue
            money += p.qty * p.rate
            qty += q
        }
        return if (qty > 0.0) money / qty else null
    }

    /** The cost of one of [product]'s own unit, by the rule above; null when nobody can know it. */
    suspend fun perUnit(dao: KhataDao, product: Product?): Double? {
        if (product == null) return null
        product.costPrice?.let { return it }
        average(product, dao.purchasesOfProduct(product.id))?.let { return it }
        return lastInOwnUnit(product, dao.purchaseRatesNewestFirst(product.id))
    }

    /**
     * The newest purchase rate that can be said per one of [product]'s own
     * unit: as written in that unit, or a pack's rate over its size (22 Oct
     * 2026, the review: the rate of a 50 kg bag was taken as the rate of a
     * kilo, and a 50 kg sale showed a loss of 220,250 on its line for good).
     * Null when every rate is in a unit the app cannot convert.
     */
    fun lastInOwnUnit(product: Product, newestFirst: List<Purchase>): Double? {
        val own = Units.norm(product.defaultUnit)
        for (p in newestFirst) {
            if (p.rate < 0.0) continue
            if (Units.isPack(product, p.unit)) return p.rate / product.packSize!!
            if (own == null || Units.norm(p.unit) == null || Units.norm(p.unit) == own) return p.rate
        }
        return null
    }
}
