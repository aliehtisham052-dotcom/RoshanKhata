package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words

/** A day's khata entry with its party and how the money moved (the day book's khata rows). */
data class DayBookEntry(
    val id: Long,
    val partyName: String,
    val isCustomer: Boolean,
    val amount: Double,
    val isGiven: Boolean,
    val note: String?,
    val entryNumber: String,
    val timestamp: Long,
    val paymentMethod: String?,
    val isQarzeHasna: Boolean
)

/** A supplier bill paid in cash on delivery, with no khata entry of its own: money out of the drawer that day. */
data class DayBookBill(val id: Long, val billNumber: String?, val totalAmount: Double, val createdAt: Long, val partyName: String)

/** A staff payment (an advance or a salary) with the person's name. */
data class DayBookStaffPay(val id: Long, val staffName: String, val amount: Double, val kind: Int, val timestamp: Long, val note: String?)

/**
 * The day book (3 Nov 2026, the competitor study: the day report had the day's totals and no entries): every
 * money movement of one day in the order it happened — the khata's entries, the cashbook's rows (a walk-in's
 * ticket is one: its payments are cashbook income), the bills paid in cash on delivery and the staff paid — read
 * from the same rows as the day report (ZReport) and Close the day, so a line here and a figure there never
 * disagree. Nothing is summed here that the day report does not already say.
 */
object DayBook {

    enum class Kind { GAVE, GOT, CASH_IN, CASH_OUT, BILL_PAID, STAFF_PAID }

    data class Row(
        val at: Long,
        val kind: Kind,
        /** Who: the party, the cashbook's category, the supplier, the staff member. */
        val who: String,
        /** The way of paying, when the row says one (a key of [PaymentMethod]). */
        val method: String?,
        val amount: Double,
        /** Its number or note, when it has one. */
        val detail: String?,
        /** A khata entry's Qarz-e-Hasna mark. */
        val loan: Boolean = false,
        /** A staff payment's kind ([Payroll.ADVANCE] / [Payroll.SALARY]). */
        val staffKind: Int = 0,
        /** The row it was read from (an entry's, a cashbook row's, a bill's or a payment's id): with [kind], its key. */
        val ref: Long = 0
    ) {
        /** Money that came to the shop: a khata "I got" or cashbook income. */
        val isIn: Boolean get() = kind == Kind.GOT || kind == Kind.CASH_IN
    }

    suspend fun rows(dao: KhataDao, from: Long, to: Long): List<Row> {
        val out = ArrayList<Row>()
        dao.dayBookEntries(from, to).forEach { e ->
            out += Row(e.timestamp, if (e.isGiven) Kind.GAVE else Kind.GOT, e.partyName, e.paymentMethod, e.amount,
                listOfNotNull(e.entryNumber.takeIf { it.isNotBlank() }, e.note?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null },
                loan = e.isQarzeHasna, ref = e.id)
        }
        dao.cashbookRowsBetween(from, to).forEach { c ->
            out += Row(c.timestamp, if (c.isIncome) Kind.CASH_IN else Kind.CASH_OUT, c.category, c.paymentMethod, c.amount,
                listOfNotNull(c.paymentRef?.takeIf { it.isNotBlank() }, c.note?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null }, ref = c.id)
        }
        dao.cashBillsBetween(from, to).forEach { b ->
            out += Row(b.createdAt, Kind.BILL_PAID, b.partyName, PaymentMethod.CASH, b.totalAmount, b.billNumber?.takeIf { it.isNotBlank() }, ref = b.id)
        }
        dao.staffPaymentsBetween(from, to).forEach { s ->
            out += Row(s.timestamp, Kind.STAFF_PAID, s.staffName, PaymentMethod.CASH, s.amount, s.note?.takeIf { it.isNotBlank() }, staffKind = s.kind, ref = s.id)
        }
        return out.sortedBy { it.at }
    }

    /** The row's kind in the owner's words, its way of paying beside it: "I Got · Cash". */
    fun kindText(words: Words, r: Row): String {
        val kind = when (r.kind) {
            Kind.GAVE -> words.text(W.i_gave)
            Kind.GOT -> words.text(W.i_got)
            Kind.CASH_IN -> words.text(W.cash_in)
            Kind.CASH_OUT -> words.text(W.cash_out)
            Kind.BILL_PAID -> words.text(W.bill_paid_cash)
            Kind.STAFF_PAID -> words.text(if (r.staffKind == Payroll.ADVANCE) W.staff_advance else W.staff_salary)
        }
        return listOfNotNull(
            kind,
            if (r.loan) words.text(W.pdf_stmt_qarze_hasna) else null,
            // A bill or a staff payment is cash by its own rule: the way is not said twice.
            r.method?.takeIf { r.kind != Kind.BILL_PAID && r.kind != Kind.STAFF_PAID }?.let { PaymentMethod.label(words, it) }
        ).joinToString(" · ")
    }

    /** Who, in the owner's words: the counter's own cashbook categories in his language, the rest as written. */
    fun whoText(words: Words, r: Row): String = when {
        r.kind == Kind.CASH_IN || r.kind == Kind.CASH_OUT -> when (r.who) {
            CashCategory.SALE -> words.text(W.cash_category_sale)
            CashCategory.RETURN -> words.text(W.cash_category_return)
            else -> r.who
        }
        else -> r.who
    }
}
