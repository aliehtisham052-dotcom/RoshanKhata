package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * "Where is it cheaper?" (6 Oct 2026): what each supplier charged, product by
 * product, from the rates already written on supplier-bill lines.
 *
 * For each product (in one unit — a bag and a kilo are never compared):
 *  · the LAST rate paid, who charged it and when;
 *  · the change against the bill BEFORE it (any supplier), so a quiet rise
 *    shows up the day it lands;
 *  · the cheapest OTHER supplier's own last rate, if it was lower and is
 *    recent (within [FRESH_DAYS]) — an old price from last year is not an offer.
 *
 * Read-only, plain Kotlin, unit-tested (SupplierRatesTest).
 */
object SupplierRates {

    const val FRESH_DAYS = 180
    private const val DAY = 24L * 60 * 60 * 1000

    data class Line(
        val productId: Long?, val productName: String, val unit: String?, val rate: Double,
        val billDate: Long, val billId: Long, val supplierId: Long, val supplierName: String
    )

    data class Quote(val supplierId: Long, val supplierName: String, val rate: Double, val date: Long)

    data class Row(val name: String, val unit: String?, val last: Quote, val previous: Quote?, val cheaperElsewhere: Quote?) {
        val change: Double get() = previous?.let { last.rate - it.rate } ?: 0.0
        val percent: Double get() = previous?.takeIf { it.rate > 0 }?.let { change / it.rate * 100 } ?: 0.0
        /** Got dearer than the bill before, or another supplier was cheaper lately. */
        val needsLook: Boolean get() = change > 0.004 || cheaperElsewhere != null
    }

    private fun key(l: Line): String =
        (l.productId?.toString() ?: "n:" + l.productName.trim().lowercase(Locale.ROOT)) + "|" +
            (l.unit?.trim()?.lowercase(Locale.ROOT).orEmpty())

    fun rows(lines: List<Line>, now: Long): List<Row> =
        lines.filter { it.rate > 0.0 }.groupBy(::key).values.map { group ->
            val ordered = group.sortedWith(compareByDescending<Line> { it.billDate }.thenByDescending { it.billId })
            val l = ordered.first()
            val last = Quote(l.supplierId, l.supplierName, l.rate, l.billDate)
            val prev = ordered.firstOrNull { it.billId != l.billId }?.let { Quote(it.supplierId, it.supplierName, it.rate, it.billDate) }
            val bySupplier = ordered.distinctBy { it.supplierId }
            val cheaper = bySupplier
                .filter { it.supplierId != l.supplierId && it.billDate >= now - FRESH_DAYS * DAY && it.rate < l.rate - 0.004 }
                .minByOrNull { it.rate }
                ?.let { Quote(it.supplierId, it.supplierName, it.rate, it.billDate) }
            Row(l.productName.trim(), l.unit?.trim()?.takeIf { it.isNotEmpty() }, last, prev, cheaper)
        }.sortedWith(compareByDescending<Row> { it.needsLook }.thenBy { it.name.lowercase(Locale.ROOT) })
}
