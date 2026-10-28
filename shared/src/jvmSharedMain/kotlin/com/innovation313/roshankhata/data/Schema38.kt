package com.innovation313.roshankhata.data

/**
 * What version 38 adds (22 Oct 2026, live sync Marhala 1), as the SQL both
 * migrations run — see [Schema32]:
 *
 *  - a uid on every table a sync can carry that did not have one yet
 *    (parties, products, plans and their instalments, staff and their days,
 *    schemes, day closes, and every line table), filled for every row
 *    already in the book, exactly as [Schema36] did for the counter's tables;
 *  - an index on uid for all 27 of them, so a sync finds a row by its uid
 *    without reading the whole table.
 *
 * Not given a uid: the audit log (each device keeps its own sealed chain),
 * the "not a duplicate" decisions (this device's own), and the book's
 * settings (already keyed by their name).
 */
object Schema38 {
    /** The tables that gain a uid here. */
    val newUidTables = listOf(
        "parties", "payment_plans", "installments", "bill_items", "products", "invoice_items", "entry_items",
        "day_close", "schemes", "staff", "staff_attendance", "sale_items", "sale_payments", "sale_return_items",
        "purchase_order_items"
    )

    /** Every table with a uid from 38 on, each indexed on it. */
    val uidTables: List<String> = Schema36.uidTables + listOf("loyalty", "shifts") + newUidTables

    val statements: List<String> =
        newUidTables.flatMap { t -> listOf("ALTER TABLE `$t` ADD COLUMN `uid` TEXT", Schema36.fillUid(t)) } +
            uidTables.map { t -> "CREATE INDEX IF NOT EXISTS `index_${t}_uid` ON `$t` (`uid`)" }

    val newColumns: Map<String, List<String>> = newUidTables.associateWith { listOf("uid") }

    /** The index names 38 creates, for the migration tests' rewind. */
    val newIndices: List<String> = uidTables.map { t -> "index_${t}_uid" }
}
