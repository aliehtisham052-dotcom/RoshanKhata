package com.innovation313.roshankhata.data

/**
 * Goods from one of the owner's shops to another (21 Oct 2026, the critical
 * review): the shops are separate books, so the transfer is written twice —
 * into the receiving book first (its products found by barcode or name, or
 * added), then out of this one — each side one transaction, both with the
 * same TR- number. Two files cannot share one transaction: should the second
 * half fail, the first is reversed by a row of its own (nothing erased) and
 * the failure is reported.
 */
object StockTransfer {

    data class Line(val product: Product, val quantity: Double)

    /** The number both books keep: TR- and the moment, to the second. */
    fun number(now: Long): String = "TR-" + DateWords.format("yyMMdd-HHmmss", now, java.util.Locale.US)

    suspend fun send(from: KhataDao, to: KhataDao, lines: List<Line>, fromShop: String, toShop: String,
                     user: String? = Access.userName, now: Long = System.currentTimeMillis()): String {
        val live = lines.filter { it.quantity > 0.0 }
        require(live.isNotEmpty()) { "nothing to transfer" }
        val number = number(now)
        val there = to.productsForTransfer(live.map { it.product })
        // In the receiving product's own unit (22 Oct 2026, the review: 2 cartons sent to a shop that counts
        // bottles arrived as 2 bottles). Units that do not convert stop the transfer before anything is written.
        val inbound = there.zip(live).map { (id, line) ->
            val here = to.productById(id) ?: throw IllegalArgumentException("no such product")
            id to (inUnitOf(line.product, line.quantity, here) ?: throw IllegalArgumentException("units differ: " + line.product.name))
        }
        to.transferStock(number, inbound, fromShop, out = false, user = user, now = now)
        try {
            from.transferStock(number, live.map { it.product.id to it.quantity }, toShop, out = true, user = user, now = now)
        } catch (e: Exception) {
            // The other book took the goods in; this one could not send them: take them back out there.
            runCatching { to.transferStock("$number ✕", inbound, fromShop, out = true, user = user) }
            throw e
        }
        return number
    }

    /**
     * [qty] of [from]'s own unit in [to]'s own unit: the same unit (or one
     * not set), or across one of the two products' packs; null when it
     * cannot be said.
     */
    fun inUnitOf(from: Product, qty: Double, to: Product): Double? {
        val a = Units.norm(from.defaultUnit)
        val b = Units.norm(to.defaultUnit)
        if (a == null || b == null || a == b) return qty
        if (Units.hasPack(to) && Units.norm(to.packUnit) == a) return qty * to.packSize!!
        if (Units.hasPack(from) && Units.norm(from.packUnit) == b) return qty / from.packSize!!
        return null
    }
}
