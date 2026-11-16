package com.innovation313.roshankhata.data

/**
 * What version 45 adds (16 Nov 2026, the audit's D-02 and T34), as the SQL both migrations run — see [Schema32]:
 *
 *  - transactions.entryKind: what an entry is beyond its direction ([EntryKind]): NORMAL, or OPENING — a balance owed
 *    from before the book, which every balance and statement counts and no sales figure does. Every entry written
 *    before is NORMAL, the imported opening balances of an older book included: nothing written then says which
 *    entry was one, so they stay as they were counted until the owner marks them (the receipt's "Counted as").
 *  - products.otherNames: the other names the shop knows a product by, in another script or as people say them
 *    ([ProductFind.otherNamesOf]). Every product written before has none (null).
 *
 * Both columns travel in a live sync (the column list is read from the table) and in the backup (format 20). No index:
 * the sales sums already read the rows of their date window, and a product's names are searched in memory.
 */
object Schema45 {
    val statements: List<String> = listOf(
        "ALTER TABLE transactions ADD COLUMN entryKind TEXT NOT NULL DEFAULT 'NORMAL'",
        "ALTER TABLE products ADD COLUMN otherNames TEXT"
    )
    val newColumns: Map<String, List<String>> = mapOf(
        "transactions" to listOf("entryKind"),
        "products" to listOf("otherNames")
    )
}
