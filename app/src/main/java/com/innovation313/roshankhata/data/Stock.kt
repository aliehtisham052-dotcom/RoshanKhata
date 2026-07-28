package com.innovation313.roshankhata.data

/**
 * How much of a product is actually on the shelf.
 *
 * The arithmetic is trivial and the honesty is not, which is why this lives in
 * Kotlin where it can be tested rather than inside a SUM in SQL.
 *
 * WHAT COUNTS AS WHAT. A supplier bill line is stock arriving. A ledger entry
 * where goods went OUT to the customer (isGiven = 1) is stock leaving. An entry
 * where goods came back IN (isGiven = 0) carrying a quantity is a return, and
 * it puts the goods back on the shelf — a shopkeeper who takes back two
 * unopened bottles has two more bottles, and a stock figure that ignored that
 * would drift further from the truth every season.
 *
 * THE UNIT PROBLEM, AND WHY THIS REFUSES TO GUESS. The bill says the supplier's
 * unit and the counter says the shopkeeper's, and nothing has ever forced them
 * to agree: stock can arrive in "bag" and leave in "kg", or arrive in "carton"
 * and leave in "bottle". Subtracting those numbers produces a figure that looks
 * exact and means nothing. There is no table of conversions here and there
 * should not be one — how many kilos are in a bag is a fact about the product,
 * which nobody has told this app, and inventing 50 would be inventing stock.
 *
 * So when the units do not agree, [ProductStock.onHand] is null and the two
 * sides are reported separately. A screen showing "in: 40 bag / out: 300 kg"
 * tells the owner something true. A screen showing "-260" tells them something
 * false, and they would find out which by trusting it.
 */
object Stock {

    /**
     * Everything known about one product's movement.
     *
     * [boughtQty] and [soldQty] are always meaningful on their own.
     * [onHand] is only offered when it can be trusted.
     */
    data class ProductStock(
        val productId: Long,
        val name: String,
        val boughtQty: Double,
        val soldQty: Double,
        val returnedQty: Double,
        /** The unit stock arrived in, where the bills agree on one. */
        val boughtUnit: String?,
        /** The unit stock left in, where the entries agree on one. */
        val soldUnit: String?
    ) {

        /**
         * Bought, plus what came back, minus what went out — or null when the
         * two sides are not counting the same thing.
         *
         * Null is a real answer and the screen must show it as one. It is not
         * "zero" and it is not "unknown because the app is missing something";
         * it is "these numbers cannot be subtracted from each other".
         */
        val onHand: Double?
            get() = if (unitsAgree) boughtQty + returnedQty - soldQty else null

        /**
         * Whether the two sides are counting in the same unit.
         *
         * A side that has no unit recorded at all does not disagree with
         * anything — plenty of entries are written without one, and treating a
         * blank as a conflict would hide a figure the owner could have had.
         * Only two units that are both present and different are a conflict.
         */
        val unitsAgree: Boolean
            get() {
                val a = boughtUnit?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                val b = soldUnit?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                return a == null || b == null || a == b
            }

        /** What to label the figure with, when there is a figure. */
        val unit: String? get() = boughtUnit ?: soldUnit

        /** Nothing has moved either way — a product created but never traded. */
        val isUntouched: Boolean
            get() = boughtQty == 0.0 && soldQty == 0.0 && returnedQty == 0.0
    }

    /**
     * Join the two halves the database counts separately.
     *
     * The DAO asks SQLite for purchases per product and sales per product in
     * two queries rather than one, because a single query with two joins would
     * multiply the rows — a product on three bills and four sales would return
     * twelve rows and count everything three or four times over. That bug is
     * silent and produces a plausible-looking number, so the two sides are
     * summed apart and married here, where it can be tested.
     */
    fun combine(
        products: List<Product>,
        bought: List<ProductQty>,
        sold: List<ProductQty>,
        returned: List<ProductQty>
    ): List<ProductStock> {
        val boughtBy = bought.associateBy { it.productId }
        val soldBy = sold.associateBy { it.productId }
        val returnedBy = returned.associateBy { it.productId }

        return products.map { p ->
            val b = boughtBy[p.id]
            val s = soldBy[p.id]
            val r = returnedBy[p.id]
            ProductStock(
                productId = p.id,
                name = p.name,
                boughtQty = b?.qty ?: 0.0,
                soldQty = s?.qty ?: 0.0,
                returnedQty = r?.qty ?: 0.0,
                boughtUnit = b?.unit ?: p.defaultUnit,
                soldUnit = s?.unit
            )
        }
    }

    /**
     * A quantity summed for one product, with the unit it was counted in.
     *
     * [unit] is whatever unit the rows carried; where the rows disagree among
     * themselves the query returns one of them, and the disagreement surfaces
     * as a unit mismatch above rather than being silently averaged away.
     */
    data class ProductQty(
        val productId: Long,
        val qty: Double,
        val unit: String?
    )
}
