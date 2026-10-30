package com.innovation313.roshankhata.data

/**
 * What version 41 adds (30 Oct 2026, the speed review), as the SQL both
 * migrations run — see [Schema32]. Indexes only, no column:
 *
 *  - transactions (isDeleted, timestamp): every date window over the whole
 *    book — sales and their count, gave/got, top customers and products,
 *    profit lines, the khata's cash, today's entries, the recent eight. With
 *    no index starting at the date each of these read the whole table, and
 *    Sale Insights asked about twenty of them on opening (a shop with 300k
 *    entries read some six million rows).
 *  - transactions (entryNumber): "is this number used" in a merge and the
 *    numbering of every new entry.
 *  - cashbook (isDeleted, timestamp): the cashbook's page, the day's and the
 *    drawer's sums (the 23 Oct note "the day sums still scan the cashbook").
 *  - invoices (isDeleted, kind, invoiceDate): the Documents page's kind pills
 *    and its newest-first window.
 *
 * Each index's name and columns are Room's own for the entity's @Index, or the
 * book fails Room's check on opening.
 */
object Schema41 {
    val statements: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `index_transactions_isDeleted_timestamp` ON `transactions` (`isDeleted`, `timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_transactions_entryNumber` ON `transactions` (`entryNumber`)",
        "CREATE INDEX IF NOT EXISTS `index_cashbook_isDeleted_timestamp` ON `cashbook` (`isDeleted`, `timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_invoices_isDeleted_kind_invoiceDate` ON `invoices` (`isDeleted`, `kind`, `invoiceDate`)"
    )

    /** The index names 41 creates, for the migration tests' rewind. */
    val newIndices: List<String> = listOf(
        "index_transactions_isDeleted_timestamp",
        "index_transactions_entryNumber",
        "index_cashbook_isDeleted_timestamp",
        "index_invoices_isDeleted_kind_invoiceDate"
    )
}
