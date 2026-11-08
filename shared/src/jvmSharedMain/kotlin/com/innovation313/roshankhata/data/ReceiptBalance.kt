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
}
