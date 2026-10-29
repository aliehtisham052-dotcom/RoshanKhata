package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * A ticket as plain text — what the phone shares on WhatsApp after a sale,
 * and what any screen can show as the receipt without a printer.
 */
object SaleReceiptText {
    fun build(words: Words, shop: String, s: SaleWithLines, balanceAfter: Double?, points: Loyalty.OnReceipt? = null): String {
        val sale = s.sale
        val b = StringBuilder()
        b.append(shop).append('\n')
        b.append(Format.dateTime(sale.timestamp)).append("  ").append(sale.saleNumber).append('\n')
        b.append(sale.customerName?.let { Format.name(it) } ?: words.text(W.pos_walk_in)).append("\n\n")
        for (i in s.items) {
            b.append(i.itemName).append('\n')
            b.append("  ").append(Calc.trim(i.quantity)).append(com.innovation313.roshankhata.data.UnitWords.label(i.unit).let { u -> if (u.isEmpty()) "" else " $u" }).append(" × ").append(Format.money(i.rate))
            if (i.discount > 0.0) b.append("  −").append(Format.money(i.discount))
            b.append("  =  ").append(Format.money(i.lineTotal)).append('\n')
        }
        b.append('\n')
        if (sale.discountAmount > 0.0 || sale.taxAmount > 0.0 || sale.roundOff != 0.0) {
            b.append(words.text(W.pos_subtotal)).append(": ").append(Format.money(sale.subtotal)).append('\n')
            if (sale.discountAmount > 0.0) b.append(words.text(W.pos_discount)).append(": −").append(Format.money(sale.discountAmount)).append('\n')
            if (sale.taxAmount > 0.0) b.append(words.text(W.pos_tax)).append(": ").append(Format.money(sale.taxAmount)).append('\n')
            if (sale.roundOff != 0.0) b.append(words.text(W.pos_round_off)).append(": ").append(Format.money(sale.roundOff)).append('\n')
        }
        b.append(words.text(W.pos_grand_total)).append(": ").append(Format.money(sale.grandTotal)).append('\n')
        // Tax that was inside the prices (schema 36): said, not added.
        val inside = s.items.filter { it.taxIncluded == true }.sumOf { it.taxAmount ?: 0.0 }
        if (inside > 0.004) b.append(words.text(W.pos_tax_included)).append(": ").append(Format.money(inside)).append('\n')
        for (p in s.payments) {
            val m = PaymentMethod.label(words, p.method)
            b.append(m).append(": ").append(Format.money(p.amount)).append(p.reference?.let { "  $it" } ?: "").append('\n')
        }
        if (sale.creditAmount > 0.005) b.append(words.text(W.pos_to_account)).append(": ").append(Format.money(sale.creditAmount)).append('\n')
        balanceAfter?.let { b.append(words.text(W.net_balance)).append(": ").append(Format.money(it)).append('\n') }
        // Loyalty (21 Oct 2026): what was spent, what was earned, what the customer holds now.
        points?.let { pt ->
            if (pt.used > 0.0) b.append(words.text(W.loyalty_used, Calc.trim(pt.used), Format.money(pt.usedValue))).append('\n')
            b.append(words.text(W.loyalty_earned, Calc.trim(pt.earned), Calc.trim(pt.balance))).append('\n')
        }
        b.append('\n').append(words.text(W.app_name))
        return b.toString()
    }
}
