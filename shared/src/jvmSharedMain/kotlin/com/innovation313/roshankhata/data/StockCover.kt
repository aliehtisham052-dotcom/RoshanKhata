package com.innovation313.roshankhata.data

/**
 * (Units and valuation contract, written on [Units]: sold quantities are turned into the shelf's unit by the same
 * [Units.toBase] and agreement as the shelf itself, and kept to [Units.QTY_DECIMALS].)
 *
 * How many days a product's shelf lasts at the pace it has been selling (15 Nov 2026, "Urea kitne din chalegi?").
 *
 * One rule for Hudhud's answer and for "Order the low stock" ([Automations.reorder] reads the same sold figure through
 * [soldBase]): the last [DAYS] days' sales of the product, counted in the product's own unit, divide the shelf. Nothing
 * here guesses: a shelf the book cannot state, no sale in the window, or sales in a unit that cannot be turned into the
 * shelf's, each say so ([Cover.Unknown], [Cover.NoSales]) instead of a figure.
 */
object StockCover {
    /** The sales window the pace is taken from. */
    const val DAYS = 30
    const val WINDOW_MS = DAYS * 24L * 60 * 60 * 1000

    /** Most days ever said; a shelf that outlasts it is told as this many (a pace of a few grains a month). */
    const val MAX_DAYS = 99_999

    sealed class Cover {
        /** The shelf lasts [days] whole days at [perDay] (a unit a day); never rounded up, so it never promises more than is there. */
        data class Days(val days: Int, val perDay: Double) : Cover()

        /** Nothing on the shelf. */
        object NoStock : Cover()

        /** The shelf is known, but nothing of it was sold in the window: no pace, so no figure. */
        object NoSales : Cover()

        /** The shelf, or what was sold in its unit, cannot be worked out. */
        object Unknown : Cover()
    }

    /**
     * What was sold of one product in the window, in the product's own unit ([unit], else its default): each row's quantity
     * is turned to the product's base unit by its packs ([Units.toBase]) and a row in a unit that is not the shelf's is
     * left out, as [Automations.reorder] always did. Null when there are rows but none could be counted in the shelf's
     * unit (sales in kilos, a shelf in bags): that is not "no sales".
     */
    fun soldBase(rows: List<ProductStat>, product: Product?, unit: String?): Double? {
        if (rows.isEmpty()) return 0.0
        val counted = rows.map { s -> Units.toBase(product, s.qty, s.unit) }
            .filter { (_, u) -> Units.agree(u, unit) } // contract 3: the same agreement the shelf itself uses
        return if (counted.isEmpty()) null else Units.roundQty(counted.sumOf { it.first })
    }

    /** The cover of [onHand] when [sold] left the shelf in the last [windowDays] days. */
    fun of(onHand: Double?, sold: Double?, windowDays: Int = DAYS): Cover {
        if (onHand == null || sold == null || !onHand.isFinite() || !sold.isFinite() || windowDays <= 0) return Cover.Unknown
        if (onHand <= 0.0) return Cover.NoStock
        if (sold <= 0.0) return Cover.NoSales
        val perDay = sold / windowDays
        val days = kotlin.math.floor(onHand / perDay)
        return Cover.Days(if (days >= MAX_DAYS) MAX_DAYS else days.toInt(), perDay)
    }
}
