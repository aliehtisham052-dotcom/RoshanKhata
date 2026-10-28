package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/**
 * A field of a row that two devices changed at once, said in the owner's words (Marhala 4, part 10 — the audit:
 * the phone and the computer both showed the book's own column names and stored values, "timestamp
 * 1761212345678", "isGiven 1"). Fields the owner knows by a name get it; a moment reads as a date and time,
 * money as money (a minus kept), which way an entry ran as I gave / I got; anything else stays as it was saved.
 */
object ConflictWords {
    private val TIMES = setOf(
        "timestamp", "billDate", "invoiceDate", "dueDate", "expiryDate", "createdAt", "updatedAt", "nextDueDate", "harvestPromise", "date"
    )
    private val MONEY = setOf(
        "amount", "totalAmount", "grandTotal", "subtotal", "paidTotal", "receivedAmount", "discountAmount", "rate", "salePrice",
        "creditPrice", "costPrice", "monthlySalary", "creditLimit", "installmentAmount", "paidSoFar", "openingBalance"
    )

    /** The field's name as the owner reads it. */
    fun field(words: Words, field: String): String = when (field) {
        "amount", "totalAmount", "grandTotal" -> words.text(W.amount)
        "note" -> words.text(W.note_label)
        "timestamp", "billDate", "invoiceDate", "date" -> words.text(W.date_label)
        "name", "itemName", "customerName", "partyName" -> words.text(W.name)
        "phone" -> words.text(W.bp_phone)
        "quantity" -> words.text(W.qty_hint)
        "rate" -> words.text(W.rate_hint)
        "isGiven" -> words.text(W.i_gave) + " / " + words.text(W.i_got)
        else -> field
    }

    /** The value as the owner reads it; null stays null (the screen shows a dash). */
    fun value(words: Words, field: String, raw: String?): String? {
        if (raw == null) return null
        return when (field) {
            in TIMES -> raw.toLongOrNull()?.takeIf { it > 0 }?.let { Format.dateTime(it) } ?: raw
            in MONEY -> raw.toDoubleOrNull()?.let { Format.signedIfNegative(it) } ?: raw
            "isGiven" -> when (raw) {
                "1", "true" -> words.text(W.i_gave)
                "0", "false" -> words.text(W.i_got)
                else -> raw
            }
            else -> raw
        }
    }
}
