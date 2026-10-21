package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A correction to the shelf (POS Phase 6, schema 35): a count that found
 * fewer (or more) than the book said, a bag torn, a bottle expired and
 * thrown out, a theft. Signed — plus puts goods on the shelf, minus takes
 * them off — and never without a reason. The stock sums add these to
 * bought − sold, so the figure on screen is the shelf as it was last found.
 * Append-only: a wrong adjustment is corrected by another, as a ledger is.
 */
@Entity(tableName = "stock_adjustments", indices = [Index("productId"), Index("timestamp")])
data class StockAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    /** Signed: +3 found three more, −2 two fewer. */
    val quantity: Double,
    val unit: String? = null,
    /** One of [AdjustReason]. */
    val reason: String,
    val note: String? = null,
    /** For a count: what the book said and what was found, so the row explains itself. */
    val bookQty: Double? = null,
    val countedQty: Double? = null,
    val createdBy: String? = null,
    val isDeleted: Boolean = false,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next(),

    /**
     * The batch this correction is of, when it is one batch's (schema 36): an
     * expired lot written off, so the batch's own count ([Fefo]) goes down
     * with the shelf's. Null for a correction of the product as a whole.
     */
    val billItemId: Long? = null
)

object AdjustReason {
    const val COUNT = "count"
    const val DAMAGE = "damage"
    const val EXPIRED = "expired"
    const val THEFT = "theft"
    const val OTHER = "other"
    val ALL = listOf(COUNT, DAMAGE, EXPIRED, THEFT, OTHER)
    /** Goods sent to or received from another shop of the owner's ([StockTransfer]); never chosen by hand. */
    const val TRANSFER = "transfer"
}

/**
 * A purchase order (POS Phase 6): what the shop asked a supplier for, before
 * any bill. Sent as a sheet or a message; when the goods arrive the order
 * becomes a supplier bill ([KhataDao.receiveOrder]) and is marked received.
 * Numbered PO-; a draft can be edited, a sent one only received or closed.
 */
@Entity(tableName = "purchase_orders", indices = [Index("partyId"), Index("status"), Index("timestamp")])
data class PurchaseOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNumber: String = "",
    val partyId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    /** One of [OrderStatus]. */
    val status: String = OrderStatus.DRAFT,
    val note: String? = null,
    /** The supplier bill this became when it arrived. */
    val billId: Long? = null,
    val createdBy: String? = null,
    val isDeleted: Boolean = false,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next()
)

object OrderStatus {
    const val DRAFT = "draft"
    const val SENT = "sent"
    const val RECEIVED = "received"
    const val CLOSED = "closed"
}

@Entity(
    tableName = "purchase_order_items",
    foreignKeys = [ForeignKey(entity = PurchaseOrder::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("orderId"), Index("productId")]
)
data class PurchaseOrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val lineNo: Int = 0,
    val productId: Long? = null,
    val itemName: String,
    val quantity: Double,
    val unit: String? = null,
    /** The rate expected, where known. */
    val rate: Double? = null,
    val isDeleted: Boolean = false
) {
    val lineTotal: Double? get() = rate?.let { SaleMath.lineTotal(quantity, it) }
}

data class PurchaseOrderWithLines(val order: PurchaseOrder, val items: List<PurchaseOrderItem>)

data class OrderSummary(
    val id: Long, val orderNumber: String, val partyId: Long, val partyName: String, val timestamp: Long,
    val status: String, val itemCount: Int
)

object OrderNumber {
    fun next(highest: Int): String = "PO-%06d".format(Digits.FIGURES, highest + 1)
}

/**
 * What the shelf is worth (POS Phase 6): on hand × what a unit cost the
 * shop — the product's cost price, else its last purchase rate. A product
 * whose cost nobody told the app is listed with no value, never a guessed
 * one; [known] says how much of the shelf the total covers.
 */
data class Valuation(val rows: List<Row>) {
    data class Row(val productId: Long, val name: String, val onHand: Double?, val unit: String?, val cost: Double?, val lowStockLevel: Double?) {
        val value: Double? get() = if (onHand != null && cost != null) LineMath.round(onHand * cost) else null
        val isLow: Boolean get() = onHand != null && lowStockLevel != null && onHand <= lowStockLevel
    }
    val total: Double get() = LineMath.round(rows.sumOf { it.value ?: 0.0 })
    val known: Int get() = rows.count { it.value != null }
    val low: List<Row> get() = rows.filter { it.isLow }

    companion object {
        suspend fun compute(dao: KhataDao): Valuation {
            val products = dao.productsOnce().associateBy { it.id }
            val stock = Stock.of(dao)
            return Valuation(stock.map { s ->
                val p = products[s.productId]
                // The owner's cost, else the weighted average purchase cost, else the last rate (Cost).
                Row(s.productId, s.name, s.onHand, s.unit, Cost.perUnit(dao, p) ?: dao.lastPurchaseRate(s.productId), p?.lowStockLevel)
            }.let { NameOrder.sortedBy(it) { r -> r.name } })
        }
    }
}

/** What version 35 adds (stock adjustments, purchase orders), as the SQL both migrations run — see [Schema32]. */
object Schema35 {
    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `stock_adjustments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, " +
            "`timestamp` INTEGER NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT, `reason` TEXT NOT NULL, `note` TEXT, `bookQty` REAL, " +
            "`countedQty` REAL, `createdBy` TEXT, `isDeleted` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_productId` ON `stock_adjustments` (`productId`)",
        "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_timestamp` ON `stock_adjustments` (`timestamp`)",
        "CREATE TABLE IF NOT EXISTS `purchase_orders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderNumber` TEXT NOT NULL, " +
            "`partyId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `note` TEXT, `billId` INTEGER, " +
            "`createdBy` TEXT, `isDeleted` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_orders_partyId` ON `purchase_orders` (`partyId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_orders_status` ON `purchase_orders` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_orders_timestamp` ON `purchase_orders` (`timestamp`)",
        "CREATE TABLE IF NOT EXISTS `purchase_order_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` INTEGER NOT NULL, " +
            "`lineNo` INTEGER NOT NULL, `productId` INTEGER, `itemName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT, `rate` REAL, " +
            "`isDeleted` INTEGER NOT NULL, FOREIGN KEY(`orderId`) REFERENCES `purchase_orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_purchase_order_items_orderId` ON `purchase_order_items` (`orderId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_order_items_productId` ON `purchase_order_items` (`productId`)"
    )
    val newTables = listOf("purchase_order_items", "purchase_orders", "stock_adjustments")
}
