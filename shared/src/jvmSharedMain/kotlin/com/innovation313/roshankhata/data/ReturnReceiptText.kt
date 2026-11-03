package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * A return's slip for the receipt printer (3 Nov 2026, the competitor study: a return had only its A4 credit
 * note, and the customer handed his goods back with no paper for them). The lines are worked out once, here, so
 * the phone's text receipt and the computer's thermal lines say the same: the shop, "Goods back" with the
 * return's number and the ticket it came from, each line as the sale's receipt writes it, what came back, where
 * the money went (back in a way of paying, or kept on the customer's account) and, for a customer, the balance
 * the return left in his own words ([ReceiptBalance]).
 */
object ReturnReceiptText {

    /** How a line is printed: the shop's name, a heading, a small fact, the customer, an item and its sum, the total. */
    enum class Kind { SHOP, HEADING, META, NAME, ITEM, DETAIL, TOTAL, PLAIN, BLANK, FOOT }

    class Line(val kind: Kind, val text: String)

    fun lines(
        words: Words,
        shop: String,
        ret: SaleReturn,
        items: List<SaleReturnItem>,
        /** The ticket the goods came back from, when the return knows it. */
        saleNumber: String?,
        /** The customer's balance once the return was written; null for a walk-in. */
        balanceAfter: Double?
    ): List<Line> {
        val out = ArrayList<Line>()
        out += Line(Kind.SHOP, shop)
        out += Line(Kind.HEADING, words.text(W.pos_return_title))
        out += Line(Kind.META, Format.dateTime(ret.timestamp) + "  " + ret.returnNumber)
        saleNumber?.let { out += Line(Kind.META, words.text(W.pos_return_of, it)) }
        out += Line(Kind.BLANK, "")
        out += Line(Kind.NAME, ret.customerName?.let { Format.name(it) } ?: words.text(W.pos_walk_in))
        for (i in items.sortedBy { it.lineNo }) {
            out += Line(Kind.ITEM, i.itemName)
            out += Line(
                Kind.DETAIL,
                "  " + Calc.trim(i.quantity) + UnitWords.label(i.unit).let { u -> if (u.isEmpty()) "" else " $u" } +
                    " × " + Format.money(i.rate) + "  =  " + Format.money(i.lineTotal)
            )
        }
        out += Line(Kind.BLANK, "")
        out += Line(Kind.TOTAL, words.text(W.pos_return_title) + ": " + Format.money(ret.total))
        out += Line(
            Kind.PLAIN,
            if (ret.refundMethod == SaleReturn.ACCOUNT) words.text(W.pos_refund_account) + ": " + Format.money(ret.total)
            else words.text(W.pos_return_refund) + " · " + PaymentMethod.label(words, ret.refundMethod) + ": " + Format.money(ret.refundAmount)
        )
        if (ret.reason.isNotBlank()) out += Line(Kind.PLAIN, words.text(W.pos_return_reason) + ": " + ret.reason)
        balanceAfter?.let { out += Line(Kind.PLAIN, words.text(W.net_balance) + ": " + ReceiptBalance.text(words, it)) }
        out += Line(Kind.BLANK, "")
        out += Line(Kind.FOOT, words.text(W.app_name))
        return out
    }

    /** The slip as plain text: what the phone's receipt printer draws, and what it shares. */
    fun text(lines: List<Line>): String = lines.joinToString("\n") { it.text }

    /** Money went back out of the drawer: a cash refund, so the drawer may open as the slip prints. */
    fun cashOut(ret: SaleReturn): Boolean =
        ret.refundMethod == PaymentMethod.CASH && ret.refundAmount > 0.0
}
