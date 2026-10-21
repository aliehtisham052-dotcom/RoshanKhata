package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * A counter ticket as an A4 invoice (21 Oct 2026, the critical review): the
 * customer who needs a proper bill for their own books gets one from the
 * sale, in the shop's invoice design, without the lines typed twice.
 *
 * The invoice adds up to exactly what the ticket did: its lines at their
 * rates; every discount (the lines' and the bill's) as one discount; the
 * ticket's tax % where it had one, or the lines' own tax and any round-off
 * as one extra line; what was paid as received. Tax that was inside the
 * prices is said in the note. One invoice per sale: asking again opens the
 * same one (the sale remembers it).
 */
object SaleInvoice {

    /** The sale's invoice id: the one it already has, or a new one made from it. Null for a sale that is not posted. */
    suspend fun of(dao: KhataDao, saleId: Long, words: Words): Long? {
        val full = dao.saleWithLines(saleId) ?: return null
        val s = full.sale
        if (s.status != SaleStatus.DONE || s.isDeleted) return null
        s.invoiceId?.let { id -> dao.getInvoice(id)?.takeIf { !it.isDeleted }?.let { return it.id } }
        val ownTax = full.items.any { (it.taxRate ?: 0.0) > 0.0 }
        val discount = LineMath.round(full.items.sumOf { it.discount } + s.discountAmount)
        val extra = LineMath.round((if (ownTax) s.taxAmount else 0.0) + s.roundOff)
        val label = when {
            ownTax && s.taxAmount > 0.0 && s.roundOff != 0.0 -> words.text(W.pos_tax) + " + " + words.text(W.pos_round_off)
            ownTax && s.taxAmount > 0.0 -> words.text(W.pos_tax)
            else -> words.text(W.pos_round_off)
        }
        val inside = full.items.filter { it.taxIncluded == true }.sumOf { it.taxAmount ?: 0.0 }
        val invoice = Invoice(
            customerName = s.customerName ?: words.text(W.pos_walk_in),
            customerPhone = s.partyId?.let { dao.getParty(it)?.phone },
            invoiceDate = s.timestamp,
            taxPercent = if (ownTax) null else s.taxPercent,
            discountAmount = discount.takeIf { it > 0.0 },
            additionalChargeLabel = label.takeIf { extra != 0.0 },
            additionalChargeAmount = extra.takeIf { it != 0.0 },
            receivedAmount = s.paidTotal,
            note = listOfNotNull(
                s.saleNumber, s.note,
                inside.takeIf { it > 0.004 }?.let { words.text(W.pos_tax_included) + ": " + Format.money(it) }
            ).joinToString(" · ")
        )
        val items = full.items.map { InvoiceItem(invoiceId = 0, itemName = it.itemName, quantity = it.quantity, unit = it.unit, rate = it.rate) }
        return dao.linkSaleInvoice(saleId, invoice, items)
    }
}
