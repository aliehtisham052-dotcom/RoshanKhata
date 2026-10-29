package com.innovation313.roshankhata.data

/**
 * A credit note and a debit note drawn from the rows that already hold the
 * money — NEVER saved (29 Oct 2026, the documents).
 *
 * A credit note is what the shop owes a customer back: his return
 * ([SaleReturn] and its lines) already posted the goods and the refund.
 * A debit note is what a supplier owes the shop back: the goods sent back to
 * him are a khata "I gave" on his account with the lines as [EntryItem]s
 * (BatchReturnDialog, the Expiry page's send-back). Writing either into the
 * invoices table would hold the same return twice, in two places that could
 * disagree; so each is an [Invoice] built in memory for the ten designs, with
 * the return's own number (R-000012, RK-000041) and never an INV- one. The
 * kinds [DocKind.CREDIT_NOTE] and [DocKind.DEBIT_NOTE] exist for the heading
 * alone; [KhataDao.saveInvoiceWithItems] refuses them.
 */
object NoteDocument {

    /**
     * A customer's return as a credit note. The lines are what came back, at
     * the rate he was charged; the total is what he is credited. The reason
     * and the return's note go under the lines; how the value went back
     * (cash, or left on his account) is the return's own record and the
     * khata's, so the note's "received" stays empty.
     */
    fun creditNote(ret: SaleReturn, items: List<SaleReturnItem>, party: Party? = null): Pair<Invoice, List<InvoiceItem>> {
        val invoice = Invoice(
            id = ret.id,
            kind = DocKind.CREDIT_NOTE,
            status = if (ret.status == SaleStatus.VOID) DocStatus.CANCELLED else DocStatus.OPEN,
            invoiceNumber = ret.returnNumber,
            customerName = ret.customerName?.takeIf { it.isNotBlank() } ?: party?.name.orEmpty(),
            customerPhone = party?.phone,
            invoiceDate = ret.timestamp,
            createdAt = ret.timestamp,
            note = listOfNotNull(ret.reason.takeIf { it.isNotBlank() }, ret.note?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null },
            uid = ret.uid
        )
        val lines = items.filter { !it.isDeleted }.sortedWith(compareBy({ it.lineNo }, { it.id })).map {
            InvoiceItem(id = it.id, invoiceId = ret.id, itemName = it.itemName, quantity = it.quantity, unit = it.unit, rate = it.rate, uid = it.uid)
        }
        return invoice to lines
    }

    /**
     * Goods sent back to a supplier as a debit note: the "I gave" [entry] on
     * his account with its goods [lines]. A line with no rate is printed at
     * the rate that makes it equal the entry's amount when it is the only
     * one, else at 0 ([EntryInvoice.rows]' own rule); an entry with no lines
     * is one row at the entry's amount.
     */
    fun debitNote(entry: LedgerEntry, lines: List<EntryItem>, party: Party?): Pair<Invoice, List<InvoiceItem>> {
        val invoice = Invoice(
            id = entry.id,
            kind = DocKind.DEBIT_NOTE,
            status = DocStatus.OPEN,
            invoiceNumber = entry.entryNumber,
            customerName = party?.name.orEmpty(),
            customerPhone = party?.phone,
            invoiceDate = entry.timestamp,
            createdAt = entry.timestamp,
            note = entry.note?.takeIf { it.isNotBlank() },
            sourceEntryId = entry.id,
            uid = entry.uid
        )
        val rows = EntryInvoice.rows(entry, lines).map { it.copy(invoiceId = entry.id) }
        return invoice to rows
    }
}
