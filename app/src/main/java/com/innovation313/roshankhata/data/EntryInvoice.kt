package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A khata sale ("I gave") turned into the rows of a new invoice.
 *
 * Only a starting point: every row lands in the invoice editor, where the
 * owner reads and edits it before anything is saved. The rules keep the
 * rows honest rather than clever:
 *
 *  - A line with a recorded rate keeps that rate. Never recalculated.
 *  - A bonus (free) line keeps its name at rate 0, so the invoice still
 *    shows that it was handed over without charging for it.
 *  - Exactly ONE line without a rate takes whatever is left of the entry's
 *    amount after the priced lines — the only case where the rate is not a
 *    guess. Two or more unpriced lines get rate 0 and the owner fills them.
 *  - An entry with no goods lines (an older one, or a plain amount) becomes
 *    one row: its goods text or note, quantity as recorded or 1, at the rate
 *    that makes the row equal the entry's amount.
 *
 * The invoice id is 0 here; the DAO sets it on save.
 */
object EntryInvoice {

    fun rows(entry: LedgerEntry, items: List<EntryItem>): List<InvoiceItem> {
        val lines = items.sortedWith(compareBy({ it.lineNo }, { it.id }))
            .filter { !it.itemName.isNullOrBlank() || it.rate != null }
        if (lines.isEmpty()) return listOf(single(entry))

        val unpriced = lines.filter { !it.isBonus && it.rate == null }
        val pricedTotal = lines.filter { !it.isBonus && it.rate != null }
            .fold(BigDecimal.ZERO) { sum, l ->
                sum.add(BigDecimal.valueOf(LineMath.lineTotal(qty(l.quantity), l.rate) ?: 0.0))
            }
        val leftover = BigDecimal.valueOf(entry.amount).subtract(pricedTotal)

        return lines.map { l ->
            val q = qty(l.quantity)
            val rate = when {
                l.isBonus -> 0.0
                l.rate != null -> l.rate
                unpriced.size == 1 && leftover.signum() > 0 -> divide(leftover, q)
                else -> 0.0
            }
            InvoiceItem(invoiceId = 0, itemName = l.itemName.orEmpty().trim(), quantity = q, unit = l.unit, rate = rate)
        }
    }

    private fun single(entry: LedgerEntry): InvoiceItem {
        val q = qty(entry.quantity)
        val name = entry.itemName?.takeIf { it.isNotBlank() } ?: entry.note.orEmpty()
        return InvoiceItem(
            invoiceId = 0,
            itemName = name.trim(),
            quantity = q,
            unit = entry.unit,
            rate = divide(BigDecimal.valueOf(entry.amount), q)
        )
    }

    /** A missing or non-positive quantity counts as one. */
    private fun qty(q: Double?): Double = q?.takeIf { it > 0 } ?: 1.0

    private fun divide(total: BigDecimal, q: Double): Double =
        total.divide(BigDecimal.valueOf(q), 2, RoundingMode.HALF_UP).toDouble()
}
