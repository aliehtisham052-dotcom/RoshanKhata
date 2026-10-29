package com.innovation313.roshankhata.data

/**
 * What version 40 adds (29 Oct 2026, the documents), as the SQL both
 * migrations run — see [Schema32]:
 *
 *  - invoices.kind: which document the row is — a quotation, a proforma
 *    invoice, a sales order, a delivery challan or the invoice itself
 *    ([DocKind]). Every row written before is an invoice.
 *  - invoices.status: where the document stands — open, accepted, converted
 *    into the next step, or cancelled ([DocStatus]). Every row written before
 *    is open.
 *  - invoices.sourceDocId (+ its index): the document this one was made
 *    from — the quotation a sales order came out of, the challan an invoice
 *    was raised on. A plain row id, not a foreign key, as [Invoice.sourceEntryId]
 *    is: the source is never deleted by the chain, only marked converted.
 *
 * Why one table and three columns, not a documents table: a quotation, a
 * sales order and a delivery challan are the same document at an earlier
 * step — the same customer, the same lines, the same ten designs, the same
 * backup and the same sync. A second table would have needed a second
 * editor, a second list, a second place in the backup and a second set of
 * sync rules, all saying the same thing. The invoice IS the document; the
 * kind says which step it is at.
 */
object Schema40 {
    val statements: List<String> = listOf(
        "ALTER TABLE invoices ADD COLUMN kind TEXT NOT NULL DEFAULT 'INVOICE'",
        "ALTER TABLE invoices ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'",
        "ALTER TABLE invoices ADD COLUMN sourceDocId INTEGER",
        "CREATE INDEX IF NOT EXISTS `index_invoices_sourceDocId` ON `invoices` (`sourceDocId`)"
    )
    val newColumns: Map<String, List<String>> = mapOf(
        "invoices" to listOf("kind", "status", "sourceDocId")
    )

    /** The index names 40 creates, for the migration tests' rewind. */
    val newIndices: List<String> = listOf("index_invoices_sourceDocId")
}
