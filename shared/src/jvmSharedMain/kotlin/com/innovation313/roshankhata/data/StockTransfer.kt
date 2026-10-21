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
        val inbound = there.zip(live.map { it.quantity })
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
}
