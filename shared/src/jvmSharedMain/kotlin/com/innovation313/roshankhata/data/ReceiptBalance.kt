package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * The balance line of an entry's receipt, the one the customer is handed or
 * sent (25 Oct 2026, the audit). The figure is the balance right after that
 * entry ([KhataDao.balanceAfterEntry]) — the phone printed the entry's own
 * amount under "Balance after", the computer today's balance. The words are
 * the statement's, his side of it ("You will pay"), so a customer never reads
 * the shop's "You will get" as money coming to him. One rule for both
 * platforms.
 */
object ReceiptBalance {

    /** The word's name for [balance]: he will pay, he will receive, or settled. */
    fun sideWord(balance: Double): String = when {
        Money.isPositive(balance) -> W.pdf_stmt_you_will_pay
        Money.isNegative(balance) -> W.pdf_stmt_you_will_receive
        else -> W.pdf_stmt_settled
    }

    /** "Rs 45,000 · You will pay"; the settled words alone at zero. */
    fun text(words: Words, balance: Double, sign: String = Currency.symbol): String =
        if (Money.isZero(balance)) words.text(W.pdf_stmt_settled)
        else Format.ltr(Format.money(balance, sign)) + " · " + words.text(sideWord(balance))

    /*
     * Names, not "you" (4 Nov 2026, the owner's phone): the entry's receipt said "Payment Receipt", "You gave Rs 800"
     * (the shop speaking) and "Rs 2,800 · You will pay" (the customer spoken to). Handed to the customer, nobody could
     * tell who "you" was, and nothing was paid. The entry's paper now says what it is and who did what, by name, and
     * the balance says who owes whom. [shop] is the shop's name (the app's when none is set), [party] the customer's
     * or supplier's; both are isolated so a Latin name keeps its place in an Urdu line. The sign rule is [sideWord]'s.
     */

    /** The paper's own name: a credit slip when the shop gave, a payment receipt when it got. */
    fun title(words: Words, isGiven: Boolean): String = words.text(if (isGiven) W.receipt_credit_slip else W.payment_receipt)

    /** "Bhatti Traders gave Aslam" / "Received from Aslam" — over the amount. */
    fun way(words: Words, isGiven: Boolean, shop: String, party: String): String =
        if (isGiven) words.text(W.receipt_line_gave, Format.isolate(shop), Format.isolate(party))
        else words.text(W.receipt_line_got, Format.isolate(party))

    /** "Rs 2,800 · Aslam owes Bhatti Traders", "Rs 300 · Bhatti Traders owes Aslam", or the settled words alone. */
    fun named(words: Words, balance: Double, shop: String, party: String, sign: String = Currency.symbol): String {
        if (Money.isZero(balance)) return words.text(W.receipt_settled)
        val (who, whom) = if (Money.isPositive(balance)) party to shop else shop to party
        return Format.ltr(Format.money(balance, sign)) + " · " + words.text(W.receipt_owes, Format.isolate(who), Format.isolate(whom))
    }
}
