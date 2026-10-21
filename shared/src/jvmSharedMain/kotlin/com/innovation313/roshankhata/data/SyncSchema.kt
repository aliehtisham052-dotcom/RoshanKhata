package com.innovation313.roshankhata.data

/**
 * Which tables a live sync carries, and how each one points at the others
 * (live sync Marhala 1, 22 Oct 2026).
 *
 * A row points at another by its local id (partyId, saleId…), and that id is
 * this device's own: the same customer may be 7 here and 12 on the computer.
 * So every pointer travels as the uid of the row it points at ([fks]) and is
 * turned back into this device's id when it lands.
 *
 * [localOnly] columns name a file on this device (a photo) and never travel.
 * [natural] columns are a unique key of their own (a product's name, a day's
 * close): the same thing made on two devices meets on that key and becomes
 * one row, not a refused insert.
 *
 * Not carried: the audit log (each device keeps its own sealed chain) and
 * the "not a duplicate" decisions (this device's own). The book's settings
 * travel by their key.
 *
 * The order is parents before children, the order a first copy is written in.
 */
object SyncSchema {

    class Table(
        val name: String,
        val fks: Map<String, String> = emptyMap(),
        val localOnly: Set<String> = emptySet(),
        val natural: List<String> = emptyList()
    ) {
        /** book_settings: the row's identity is its key, not a uid. */
        val keyed: Boolean get() = name == SETTINGS
        val idColumn: String get() = if (keyed) "key" else "uid"
    }

    const val SETTINGS = "book_settings"

    val tables: List<Table> = listOf(
        Table("parties", localOnly = setOf("photoPath"), natural = listOf("qrToken")),
        Table("products", fks = mapOf("parentId" to "products"), natural = listOf("nameKey")),
        Table("staff"),
        Table("schemes", fks = mapOf("partyId" to "parties")),
        Table("day_close", natural = listOf("day")),
        Table("payment_plans", fks = mapOf("partyId" to "parties")),
        Table(
            "transactions",
            fks = mapOf(
                "partyId" to "parties", "productId" to "products", "billItemId" to "bill_items",
                "pairedEntryId" to "transactions", "saleId" to "sales"
            ),
            localOnly = setOf("billPhotoPath")
        ),
        Table("entry_items", fks = mapOf("entryId" to "transactions", "productId" to "products", "billItemId" to "bill_items")),
        Table("installments", fks = mapOf("planId" to "payment_plans", "ledgerEntryId" to "transactions")),
        Table("cheques", fks = mapOf("partyId" to "parties", "ledgerEntryId" to "transactions")),
        Table("cashbook", fks = mapOf("saleId" to "sales")),
        Table("supplier_bills", fks = mapOf("partyId" to "parties", "ledgerEntryId" to "transactions"), localOnly = setOf("photoPath")),
        Table("bill_items", fks = mapOf("billId" to "supplier_bills", "productId" to "products")),
        Table("invoices", fks = mapOf("sourceEntryId" to "transactions")),
        Table("invoice_items", fks = mapOf("invoiceId" to "invoices")),
        Table("sales", fks = mapOf("partyId" to "parties", "ledgerEntryId" to "transactions", "invoiceId" to "invoices")),
        Table("sale_items", fks = mapOf("saleId" to "sales", "productId" to "products", "billItemId" to "bill_items")),
        Table("sale_payments", fks = mapOf("saleId" to "sales", "ledgerEntryId" to "transactions", "cashEntryId" to "cashbook")),
        Table(
            "sale_returns",
            fks = mapOf(
                "saleId" to "sales", "partyId" to "parties", "ledgerEntryId" to "transactions",
                "refundEntryId" to "transactions", "refundCashId" to "cashbook"
            )
        ),
        Table(
            "sale_return_items",
            fks = mapOf("returnId" to "sale_returns", "saleItemId" to "sale_items", "productId" to "products", "billItemId" to "bill_items")
        ),
        Table("stock_adjustments", fks = mapOf("productId" to "products", "billItemId" to "bill_items")),
        Table("purchase_orders", fks = mapOf("partyId" to "parties", "billId" to "supplier_bills")),
        Table("purchase_order_items", fks = mapOf("orderId" to "purchase_orders", "productId" to "products")),
        Table("staff_attendance", fks = mapOf("staffId" to "staff"), natural = listOf("staffId", "day")),
        Table("staff_payments", fks = mapOf("staffId" to "staff")),
        Table("loyalty", fks = mapOf("partyId" to "parties", "saleId" to "sales")),
        Table("shifts"),
        Table(SETTINGS)
    )

    private val byName: Map<String, Table> = tables.associateBy { it.name }

    fun table(name: String): Table? = byName[name]

    /** The tables that carry a uid (every one but the settings). */
    val uidTables: List<Table> get() = tables.filter { !it.keyed }
}
