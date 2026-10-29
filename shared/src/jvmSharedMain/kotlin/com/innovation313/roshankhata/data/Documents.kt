package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W

/**
 * Which document an [Invoice] row is (schema 40, 29 Oct 2026).
 *
 * A quotation, a sales order, a delivery challan and an invoice are the same
 * document at four steps: the shopkeeper quotes a price, the customer agrees,
 * the goods go out, the bill is raised. The same customer, the same lines,
 * the same ten designs. So they are one table ([Invoice]), one editor, one
 * sync and one backup, told apart by [Invoice.kind]; a step forward is
 * [KhataDao.convertDocument], which copies the row into the next kind and
 * marks the old one [DocStatus.CONVERTED]. A proforma invoice stands beside
 * the chain (a formal quotation) and converts straight to the invoice.
 *
 * Two kinds are PRINT-ONLY and never saved: a credit note is a customer's
 * return ([SaleReturn]) and a debit note is a return to a supplier (a khata
 * "I gave" with goods lines), each drawn as a document by [NoteDocument]
 * from the row that already holds the money. Saving either would write the
 * same return twice. [saved] lists the kinds a row may carry; the DAO refuses
 * the other two.
 */
object DocKind {
    const val QUOTATION = "QUOTATION"
    const val PROFORMA = "PROFORMA"
    const val SALES_ORDER = "SALES_ORDER"
    const val CHALLAN = "CHALLAN"
    const val INVOICE = "INVOICE"

    /** Print-only (see the class doc): a customer's return drawn as a document. */
    const val CREDIT_NOTE = "CREDIT_NOTE"

    /** Print-only (see the class doc): a return to a supplier drawn as a document. */
    const val DEBIT_NOTE = "DEBIT_NOTE"

    /** The kinds a saved row may carry, in the chain's order. */
    val saved: List<String> = listOf(QUOTATION, PROFORMA, SALES_ORDER, CHALLAN, INVOICE)

    /** Every kind, saved and print-only, in the chain's order. */
    val ALL: List<String> = saved + listOf(CREDIT_NOTE, DEBIT_NOTE)

    /** The series each kind is numbered in: Q-000001, PI-, SO-, DC-, INV-; the notes print their return's own number. */
    fun prefix(kind: String): String = when (kind) {
        QUOTATION -> "Q-"
        PROFORMA -> "PI-"
        SALES_ORDER -> "SO-"
        CHALLAN -> "DC-"
        INVOICE -> "INV-"
        CREDIT_NOTE, DEBIT_NOTE -> ReturnNumber.PREFIX
        else -> throw IllegalArgumentException("not a document kind: $kind")
    }

    /** The word for the kind — a heading on paper, a label on a list — as a string name for [W]. */
    fun wordKey(kind: String): String = when (kind) {
        QUOTATION -> W.doc_kind_quotation
        PROFORMA -> W.doc_kind_proforma
        SALES_ORDER -> W.doc_kind_sales_order
        CHALLAN -> W.doc_kind_challan
        INVOICE -> W.pdf_label_invoice
        CREDIT_NOTE -> W.doc_kind_credit_note
        DEBIT_NOTE -> W.doc_kind_debit_note
        else -> throw IllegalArgumentException("not a document kind: $kind")
    }

    /**
     * The step after [kind], or null at the end: quotation → sales order →
     * challan → invoice; a proforma → invoice. The notes have no next step.
     */
    fun next(kind: String): String? = when (kind) {
        QUOTATION -> SALES_ORDER
        SALES_ORDER -> CHALLAN
        PROFORMA, CHALLAN -> INVOICE
        else -> null
    }

    /** Whether [to] is a legal step from [from]: the chain's own next step, or straight to the invoice from any earlier one. */
    fun canConvert(from: String, to: String): Boolean =
        to in saved && from in saved && from != INVOICE && (next(from) == to || to == INVOICE)

    /**
     * A delivery challan says what went out, never what it costs — the driver
     * and the gate hand it over; the price is the invoice's. Every other kind
     * prints its rates and totals.
     */
    fun showsPrices(kind: String): Boolean = kind != CHALLAN

    /** Whether a row may carry [kind] at all (the print-only notes may not). */
    fun isSaved(kind: String): Boolean = kind in saved
}

/**
 * Where a document stands ([Invoice.status]). A status changes only from the
 * state that allows it, in the DAO (lesson 52): OPEN → ACCEPTED (the customer
 * agreed, [KhataDao.acceptDocument]); OPEN or ACCEPTED → CONVERTED (the next
 * step was made, [KhataDao.convertDocument]) or CANCELLED
 * ([KhataDao.cancelDocument]). CONVERTED and CANCELLED are final: such a
 * document is never edited again — what changed is written on the new one.
 */
object DocStatus {
    const val OPEN = "OPEN"
    const val ACCEPTED = "ACCEPTED"
    const val CONVERTED = "CONVERTED"
    const val CANCELLED = "CANCELLED"

    val ALL: List<String> = listOf(OPEN, ACCEPTED, CONVERTED, CANCELLED)

    /** The word for the status as a string name for [W]. */
    fun wordKey(status: String): String = when (status) {
        OPEN -> W.doc_status_open
        ACCEPTED -> W.doc_status_accepted
        CONVERTED -> W.doc_status_converted
        CANCELLED -> W.doc_status_cancelled
        else -> throw IllegalArgumentException("not a document status: $status")
    }

    /** A document still in the owner's hands: it may be edited, accepted, converted or cancelled. */
    fun isLive(status: String): Boolean = status == OPEN || status == ACCEPTED
}

/**
 * A document write the data layer refused, with the reason a screen can put
 * in the owner's language — [CounterRefused]'s shape.
 */
class DocRefused(val why: Why, message: String) : IllegalArgumentException(message) {
    enum class Why {
        /** No live document with that id (never written, or in the recycle bin). */
        NOT_FOUND,
        /** The document was converted or cancelled already: it cannot change, nor step on again. */
        NOT_LIVE,
        /** [to] is not a step the chain allows from the document's kind. */
        BAD_STEP,
        /** A kind no row may carry (the print-only notes), or not a kind at all. */
        BAD_KIND,
        /** The document is not OPEN (accept is for an open document only). */
        NOT_OPEN
    }
}
