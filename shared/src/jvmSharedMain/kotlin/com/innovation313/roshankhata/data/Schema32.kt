package com.innovation313.roshankhata.data

/**
 * What version 32 adds (the counter, POS Phase 1, 21 Oct 2026), as the SQL
 * both migrations run — the phone's MIGRATION_31_32 (SupportSQLiteDatabase)
 * and the computer's (SQLiteConnection). One list, so the two books cannot
 * drift: a column added to one and not the other would open on one device
 * and crash on the other.
 *
 * Every statement is written the way Room itself writes the schema (the
 * names it derives for indexes, the NOT NULLs it expects), because Room
 * compares the migrated tables with the entities at open and refuses any
 * difference. SalesMigrationTest opens a real version-31 file through it.
 */
object Schema32 {
    val statements: List<String> = listOf(
        // The counter's own tables.
        "CREATE TABLE IF NOT EXISTS `sales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`saleNumber` TEXT NOT NULL, `partyId` INTEGER, `customerName` TEXT, `timestamp` INTEGER NOT NULL, " +
            "`subtotal` REAL NOT NULL, `discountAmount` REAL NOT NULL, `taxPercent` REAL, `taxAmount` REAL NOT NULL, " +
            "`roundOff` REAL NOT NULL, `grandTotal` REAL NOT NULL, `paidTotal` REAL NOT NULL, `status` TEXT NOT NULL, " +
            "`note` TEXT, `ledgerEntryId` INTEGER, `invoiceId` INTEGER, `createdBy` TEXT, `voidReason` TEXT, " +
            "`voidedAt` INTEGER, `isDeleted` INTEGER NOT NULL, `deletedAt` INTEGER)",
        "CREATE INDEX IF NOT EXISTS `index_sales_partyId` ON `sales` (`partyId`)",
        "CREATE INDEX IF NOT EXISTS `index_sales_timestamp` ON `sales` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_sales_status` ON `sales` (`status`)",

        "CREATE TABLE IF NOT EXISTS `sale_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`saleId` INTEGER NOT NULL, `lineNo` INTEGER NOT NULL, `productId` INTEGER, `itemName` TEXT NOT NULL, " +
            "`quantity` REAL NOT NULL, `unit` TEXT, `rate` REAL NOT NULL, `discount` REAL NOT NULL, " +
            "`billItemId` INTEGER, `costRate` REAL, `isDeleted` INTEGER NOT NULL, " +
            "FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_sale_items_saleId` ON `sale_items` (`saleId`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_items_productId` ON `sale_items` (`productId`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_items_billItemId` ON `sale_items` (`billItemId`)",

        "CREATE TABLE IF NOT EXISTS `sale_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`saleId` INTEGER NOT NULL, `method` TEXT NOT NULL, `amount` REAL NOT NULL, `reference` TEXT, " +
            "`ledgerEntryId` INTEGER, `cashEntryId` INTEGER, " +
            "FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_sale_payments_saleId` ON `sale_payments` (`saleId`)",

        "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, `user` TEXT, `action` TEXT NOT NULL, `refTable` TEXT, `refId` INTEGER, `detail` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_audit_log_timestamp` ON `audit_log` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_audit_log_refTable_refId` ON `audit_log` (`refTable`, `refId`)",

        // The columns the counter adds to the tables that were here already.
        "ALTER TABLE transactions ADD COLUMN saleId INTEGER",
        "ALTER TABLE cashbook ADD COLUMN paymentMethod TEXT",
        "ALTER TABLE cashbook ADD COLUMN saleId INTEGER",
        "ALTER TABLE products ADD COLUMN barcode TEXT",
        "ALTER TABLE products ADD COLUMN costPrice REAL",
        "ALTER TABLE products ADD COLUMN lowStockLevel REAL",
        "CREATE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)"
    )

    /** The tables version 32 created, children first — for a test taking a file back to 31. */
    val newTables = listOf("sale_payments", "sale_items", "sales", "audit_log")

    /** The columns version 32 added, by table — for the same test. */
    val newColumns = mapOf(
        "transactions" to listOf("saleId"),
        "cashbook" to listOf("paymentMethod", "saleId"),
        "products" to listOf("barcode", "costPrice", "lowStockLevel")
    )
}
