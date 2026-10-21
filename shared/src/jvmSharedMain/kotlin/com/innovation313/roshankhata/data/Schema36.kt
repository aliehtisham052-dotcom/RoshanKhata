package com.innovation313.roshankhata.data

/**
 * What version 36 adds (21 Oct 2026, the second night of the counter), as the
 * SQL both migrations run — see [Schema32]. One version for everything the
 * critical review asked for, so phones and computers take one step:
 *
 *  - book_settings: the book's own settings (the owner's counter PIN, prices
 *    with tax, loyalty, the stock rule);
 *  - loyalty, shifts: two new ledgers;
 *  - products: packs (carton = 12), tax rate, variants;
 *  - sale_items: the line's own tax;
 *  - audit_log: the hash chain;
 *  - uid on the tables a counter writes, filled for every existing row, so a
 *    restore can say exactly what it would lose and a merge can match rows.
 */
object Schema36 {
    /** The tables that gain a uid, filled here for every row already in the book. */
    val uidTables = listOf(
        "transactions", "cashbook", "cheques", "supplier_bills", "invoices", "sales",
        "sale_returns", "stock_adjustments", "purchase_orders", "staff_payments"
    )

    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `book_settings` (`key` TEXT NOT NULL, `value` TEXT, PRIMARY KEY(`key`))",
        "CREATE TABLE IF NOT EXISTS `loyalty` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `partyId` INTEGER NOT NULL, " +
            "`saleId` INTEGER, `points` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT, `isDeleted` INTEGER NOT NULL, `uid` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_loyalty_partyId` ON `loyalty` (`partyId`)",
        "CREATE INDEX IF NOT EXISTS `index_loyalty_saleId` ON `loyalty` (`saleId`)",
        "CREATE TABLE IF NOT EXISTS `shifts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user` TEXT, `device` TEXT, " +
            "`openedAt` INTEGER NOT NULL, `openingFloat` REAL NOT NULL, `closedAt` INTEGER, `counted` REAL, `expected` REAL, `note` TEXT, `uid` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_shifts_openedAt` ON `shifts` (`openedAt`)",
        "ALTER TABLE products ADD COLUMN packUnit TEXT",
        "ALTER TABLE products ADD COLUMN packSize REAL",
        "ALTER TABLE products ADD COLUMN packPrice REAL",
        "ALTER TABLE products ADD COLUMN taxRate REAL",
        "ALTER TABLE products ADD COLUMN parentId INTEGER",
        "ALTER TABLE products ADD COLUMN variant TEXT",
        "CREATE INDEX IF NOT EXISTS `index_products_parentId` ON `products` (`parentId`)",
        "ALTER TABLE sale_items ADD COLUMN taxRate REAL",
        "ALTER TABLE sale_items ADD COLUMN taxAmount REAL",
        "ALTER TABLE sale_items ADD COLUMN taxIncluded INTEGER",
        "ALTER TABLE audit_log ADD COLUMN hash TEXT"
    ) + uidTables.flatMap { t ->
        listOf("ALTER TABLE $t ADD COLUMN uid TEXT", fillUid(t))
    }

    /** Gives every row of [table] that has none a uid, as Uid.next() would. */
    fun fillUid(table: String) = "UPDATE $table SET uid = lower(hex(randomblob(16))) WHERE uid IS NULL"

    val newTables = listOf("book_settings", "loyalty", "shifts")

    val newColumns: Map<String, List<String>> = mapOf(
        "products" to listOf("packUnit", "packSize", "packPrice", "taxRate", "parentId", "variant"),
        "sale_items" to listOf("taxRate", "taxAmount", "taxIncluded"),
        "audit_log" to listOf("hash")
    ) + uidTables.associateWith { listOf("uid") }
}
