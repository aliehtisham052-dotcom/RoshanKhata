package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Goods brought back (POS Phase 3, 21 Oct 2026, schema 33): a credit note.
 *
 * As with a sale, the money lives where it always has. A customer's return
 * is a khata "I got" carrying the goods as its lines (the stock sums already
 * count an "I got" with goods as a return), and a cash refund is an "I gave"
 * in cash against it; a walk-in's refund is cashbook money out, and the
 * walk-in's goods are counted back from [SaleReturnItem]. The return row is
 * the document: its number (R-), the sale it came off where there was one,
 * the reason, and the ids of what it posted, so a void can bin exactly those.
 *
 * Immutable once posted, like a sale: a wrong return is voided and made again.
 *
 * [refundMethod]: one of [PaymentMethod] for money handed back, or
 * [ACCOUNT] when the credit stays on the customer's khata (the only choice a
 * walk-in does not have — there is no account to hold it).
 */
@Entity(
    tableName = "sale_returns",
    indices = [Index("saleId"), Index("partyId"), Index("timestamp"), Index("uid")]
)
data class SaleReturn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Set by [KhataDao.postReturn] ("R-000012"); the value passed in is ignored. */
    val returnNumber: String = "",
    /** The sale the goods came off, where the owner found it. Null = no receipt (an owner's decision). */
    val saleId: Long? = null,
    val partyId: Long? = null,
    val customerName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** Σ line totals: what the goods are worth back. */
    val total: Double,
    /** How the value went back: cash / bank / cheque / online, or [ACCOUNT]. */
    val refundMethod: String = ACCOUNT,
    /** The money actually handed back now (0 when it all stayed on the account). */
    val refundAmount: Double = 0.0,
    val reason: String,
    val note: String? = null,
    /** One of [SaleStatus.DONE] or [SaleStatus.VOID]. */
    val status: String = SaleStatus.DONE,
    /** The khata "I got" (goods back) this return posted, for a customer. */
    val ledgerEntryId: Long? = null,
    /** The khata "I gave" (the cash refund), or the cashbook row for a walk-in. */
    val refundEntryId: Long? = null,
    val refundCashId: Long? = null,
    val createdBy: String? = null,
    val voidReason: String? = null,
    val voidedAt: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next()
) {
    companion object {
        /** The credit stays on the customer's account. */
        const val ACCOUNT = "account"
    }
}

/** One line of a return: which sold line it reverses (where known), and how much of it. */
@Entity(
    tableName = "sale_return_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleReturn::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("returnId"), Index("saleItemId"), Index("productId"), Index("uid")]
)
data class SaleReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val lineNo: Int = 0,
    /** The sale line this comes back against; null for a return with no receipt. */
    val saleItemId: Long? = null,
    val productId: Long? = null,
    val itemName: String,
    val quantity: Double,
    val unit: String? = null,
    /** Per unit, as it was sold (the discount it carried is already in it). */
    val rate: Double,
    val billItemId: Long? = null,
    val isDeleted: Boolean = false,
    /** This row's identity across devices and backups (schema 38, live sync). */
    val uid: String? = Uid.next()
) {
    val lineTotal: Double get() = SaleMath.lineTotal(quantity, rate)
}

/** A return as the list shows it. */
data class ReturnSummary(
    val id: Long,
    val returnNumber: String,
    val saleNumber: String?,
    val customerName: String?,
    val timestamp: Long,
    val total: Double,
    val refundMethod: String,
    val refundAmount: Double,
    val status: String
)

/** How much of a sold line has already come back. */
data class ReturnedQty(val saleItemId: Long, val qty: Double)

object ReturnNumber {
    const val PREFIX = "R-"
    fun next(highest: Int): String = PREFIX + "%06d".format(Digits.FIGURES, highest + 1) + NumberMark.suffix()
}

/** The checks a return must pass before anything is written. */
object ReturnMath {
    enum class Problem { NO_LINES, BAD_QUANTITY, OVER_SOLD, BAD_REFUND, WALK_IN_NEEDS_CASH, NO_REASON, BAD_METHOD }

    /** A return refused by [check] inside the DAO, carrying the problem for the screen's words (23 Oct 2026). */
    class Refused(val problem: Problem) : IllegalArgumentException("return refused: $problem")

    /**
     * What one unit of [item] was actually paid for on [sale] (21 Oct 2026, moved here 23 Oct so the
     * form and the DAO share it): the line's own discount, and the ticket's discount, tax on top and
     * round-off spread over the lines by their worth. A return gives back this, not the shelf price.
     */
    fun paidRate(sale: Sale, item: SaleItem): Double {
        val share = sale.subtotal.takeIf { it > 0.0 }?.let { sale.grandTotal / it } ?: 1.0
        return if (item.quantity > 0.0) item.lineTotal * share / item.quantity else item.rate * share
    }

    /**
     * [soldLeft]: per sale line, what was sold minus what already came back;
     * a line with no sale line (no receipt) is not held to any figure.
     */
    fun check(
        lines: List<SaleReturnItem>,
        refundMethod: String,
        refundAmount: Double,
        total: Double,
        hasParty: Boolean,
        reason: String,
        soldLeft: Map<Long, Double>
    ): Problem? {
        val live = lines.filter { !it.isDeleted }
        if (live.isEmpty()) return Problem.NO_LINES
        if (reason.isBlank()) return Problem.NO_REASON
        for (l in live) {
            if (l.quantity <= 0.0 || l.quantity.isNaN()) return Problem.BAD_QUANTITY
            l.saleItemId?.let { id -> soldLeft[id]?.let { left -> if (l.quantity > left + 0.000001) return Problem.OVER_SOLD } }
        }
        if (refundMethod != SaleReturn.ACCOUNT && refundMethod !in PaymentMethod.ALL) return Problem.BAD_METHOD
        if (refundAmount < 0.0 || refundAmount > total + 0.005) return Problem.BAD_REFUND
        if (!hasParty && (refundMethod == SaleReturn.ACCOUNT || refundAmount < total - 0.005)) return Problem.WALK_IN_NEEDS_CASH
        return null
    }

    fun total(lines: List<SaleReturnItem>): Double = LineMath.round(lines.filter { !it.isDeleted }.sumOf { it.lineTotal })
}

/** What version 33 adds (returns), as the SQL both migrations run — see [Schema32]. */
object Schema33 {
    val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `sale_returns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`returnNumber` TEXT NOT NULL, `saleId` INTEGER, `partyId` INTEGER, `customerName` TEXT, `timestamp` INTEGER NOT NULL, " +
            "`total` REAL NOT NULL, `refundMethod` TEXT NOT NULL, `refundAmount` REAL NOT NULL, `reason` TEXT NOT NULL, `note` TEXT, " +
            "`status` TEXT NOT NULL, `ledgerEntryId` INTEGER, `refundEntryId` INTEGER, `refundCashId` INTEGER, `createdBy` TEXT, " +
            "`voidReason` TEXT, `voidedAt` INTEGER, `isDeleted` INTEGER NOT NULL, `deletedAt` INTEGER)",
        "CREATE INDEX IF NOT EXISTS `index_sale_returns_saleId` ON `sale_returns` (`saleId`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_returns_partyId` ON `sale_returns` (`partyId`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_returns_timestamp` ON `sale_returns` (`timestamp`)",
        "CREATE TABLE IF NOT EXISTS `sale_return_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`returnId` INTEGER NOT NULL, `lineNo` INTEGER NOT NULL, `saleItemId` INTEGER, `productId` INTEGER, `itemName` TEXT NOT NULL, " +
            "`quantity` REAL NOT NULL, `unit` TEXT, `rate` REAL NOT NULL, `billItemId` INTEGER, `isDeleted` INTEGER NOT NULL, " +
            "FOREIGN KEY(`returnId`) REFERENCES `sale_returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_sale_return_items_returnId` ON `sale_return_items` (`returnId`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_return_items_saleItemId` ON `sale_return_items` (`saleItemId`)",
        "CREATE INDEX IF NOT EXISTS `index_sale_return_items_productId` ON `sale_return_items` (`productId`)"
    )
    val newTables = listOf("sale_return_items", "sale_returns")
}
