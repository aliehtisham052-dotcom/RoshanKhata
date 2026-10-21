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
    fun build(words: Words, shop: String, s: SaleWithLines, balanceAfter: Double?): String {
        val sale = s.sale
        val b = StringBuilder()
        b.append(shop).append('\n')
        b.append(Format.dateTime(sale.timestamp)).append("  ").append(sale.saleNumber).append('\n')
        b.append(sale.customerName?.let { Format.name(it) } ?: words.text(W.pos_walk_in)).append("\n\n")
        for (i in s.items) {
            b.append(i.itemName).append('\n')
            b.append("  ").append(Calc.trim(i.quantity)).append(i.unit?.let { " $it" } ?: "").append(" × ").append(Format.money(i.rate))
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
            val m = words.text(
                when (p.method) {
                    PaymentMethod.CASH -> W.payment_method_cash
                    PaymentMethod.BANK -> W.payment_method_bank
                    PaymentMethod.CHEQUE -> W.payment_method_cheque
                    else -> W.payment_method_online
                }
            )
            b.append(m).append(": ").append(Format.money(p.amount)).append(p.reference?.let { "  $it" } ?: "").append('\n')
        }
        if (sale.creditAmount > 0.005) b.append(words.text(W.pos_to_account)).append(": ").append(Format.money(sale.creditAmount)).append('\n')
        balanceAfter?.let { b.append(words.text(W.net_balance)).append(": ").append(Format.money(it)).append('\n') }
        b.append('\n').append(words.text(W.app_name))
        return b.toString()
    }
}
