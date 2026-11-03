package com.innovation313.roshankhata.data

/**
 * What version 42 changes (3 Nov 2026, the speed work), as the SQL both migrations run — see [Schema32].
 * One index, in place of another; no column, no row touched:
 *
 *  - transactions (partyId, isDeleted, timestamp, isGiven, amount, pairedEntryId, isQarzeHasna) replaces
 *    transactions (partyId, isDeleted, timestamp). Every party's balance (the Khata list, Home, the bell,
 *    every page's customer list, read again after every save), who owes, how each customer pays (Home's
 *    late count, Follow-up) and one customer's live balance read only these columns: the old index found a
 *    party's lines and then fetched each line from the table, one place on disk per line, and in a shop's
 *    book a customer's lines lie among everyone else's. The new index holds the columns themselves, so
 *    those reads never touch the table. It starts with the old one's three columns, so everything the old
 *    one served — a customer's page newest first, his newest 50 — it serves the same way.
 *
 * Measured on a copy of the schema with the big book written by the clock (10,000 customers, 300,000
 * lines): every party's balance 285 → 126 ms, the owing customers' lines 504 → 227 ms ([KhataDao.ledgerPointsOf]),
 * the file 1.8 MB larger (3%), writes unchanged.
 *
 * The new index is created before the old one is dropped. Each name and its columns are Room's own for the
 * entity's @Index, or the book fails Room's check on opening.
 */
object Schema42 {
    const val COVERING = "index_transactions_partyId_isDeleted_timestamp_isGiven_amount_pairedEntryId_isQarzeHasna"
    const val REPLACED = "index_transactions_partyId_isDeleted_timestamp"

    val statements: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `$COVERING` ON `transactions` " +
            "(`partyId`, `isDeleted`, `timestamp`, `isGiven`, `amount`, `pairedEntryId`, `isQarzeHasna`)",
        "DROP INDEX IF EXISTS `$REPLACED`"
    )

    /** The index names 42 creates, for the migration tests' rewind. */
    val newIndices: List<String> = listOf(COVERING)

    /** What 42 dropped, as version 41 had it: the rewind puts it back. */
    val droppedIndices: Map<String, String> = mapOf(
        REPLACED to "CREATE INDEX IF NOT EXISTS `$REPLACED` ON `transactions` (`partyId`, `isDeleted`, `timestamp`)"
    )
}
