package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bill from a supplier: stock bought in, on credit or paid.
 *
 * Same rule as everywhere else in this app — THE LEDGER IS THE MONEY. A bill
 * does not hold a balance of its own. When stock arrives on credit, one ledger
 * entry is written recording what is now owed to that supplier, and the bill
 * points at it. Paying the bill writes one more entry, and no more. If the bill
 * kept its own running total alongside the ledger's, the two would drift, and
 * the owner would have two different answers to "what do I owe them?" — with no
 * way to tell which was true.
 *
 * What the bill adds, that a bare ledger entry cannot, is the paperwork: bill
 * number, batch numbers, expiry dates, what was actually in the delivery. That
 * is the part that matters when the Agriculture Department inspector walks in,
 * or when a batch turns out to be bad and needs tracing back to the supplier it
 * came from.
 */
@Entity(
    tableName = "supplier_bills",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("partyId")]
)
data class SupplierBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** The supplier. A party, like anyone else in the ledger. */
    val partyId: Long,

    /** The supplier's own bill/invoice number, as printed on their paper. */
    val billNumber: String? = null,

    /** Total value of the bill. */
    val totalAmount: Double,

    /** Date on the bill itself, which may not be the day it was entered. */
    val billDate: Long = System.currentTimeMillis(),

    /** When payment is due. Null if no terms were agreed. */
    val dueDate: Long? = null,

    /**
     * The ledger entry recording what this bill put on account.
     *
     * Null when the bill was paid in cash on delivery — nothing was owed, so
     * nothing belongs in the ledger against the supplier.
     */
    val ledgerEntryId: Long? = null,

    /** True when the stock was paid for outright rather than taken on credit. */
    val isPaidInFull: Boolean = false,

    val note: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * One line on a supplier bill: a product, its batch, and when it expires.
 *
 * The batch number and expiry are the reason this table exists at all.
 *
 * Expiry is money: an expired drum of pesticide is a total loss, but the same
 * drum flagged two months early can still be sold, discounted, or returned to
 * the supplier while they will still take it back.
 *
 * The batch number is protection. The Agriculture Department inspects pesticide
 * shops and takes samples, and a misbranded sample brings action under the
 * Insecticide Act. If a batch turns out bad, the dealer needs to show exactly
 * which supplier it came from, on what date, at what rate — from a record, not
 * from memory and a drawer full of paper.
 */
@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = SupplierBill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId")]
)
data class BillItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,

    /** Product name as the dealer knows it. */
    val productName: String,

    /**
     * Batch number, exactly as printed on the container.
     *
     * Optional, because a dealer entering an old bill from memory may simply
     * not have it — and a required field would only be filled with a guess,
     * which is worse than a blank. But the app asks for it every time, because
     * the day it is needed is the day it cannot be reconstructed.
     */
    val batchNumber: String? = null,

    /** When this batch expires. Optional for the same reason. */
    val expiryDate: Long? = null,

    val quantity: Double,

    /** Bottle, litre, kg, bag, packet. */
    val unit: String? = null,

    /** Purchase rate per unit. */
    val rate: Double? = null,

    val note: String? = null,

    val isDeleted: Boolean = false
)

/**
 * Line total, where a rate was recorded.
 *
 * An extension property, not a member. Room maps every property declared inside
 * an @Entity to a database column, and there is no lineTotal column — so
 * declaring it in the class body makes Room's code generation fail. Outside the
 * body it is just Kotlin, invisible to Room, and still reads the same at the
 * call site.
 */
val BillItem.lineTotal: Double?
    get() = rate?.let { it * quantity }

/** A bill with its supplier and what has actually been paid against it. */
data class BillSummary(
    val id: Long,
    val partyId: Long,
    val partyName: String,
    val billNumber: String?,
    val totalAmount: Double,
    val billDate: Long,
    val dueDate: Long?,
    val isPaidInFull: Boolean,
    val itemCount: Int,
    val note: String?
)

/**
 * A batch nearing or past its expiry.
 *
 * Deliberately carries the supplier's name and the bill it came on, because
 * "this expires soon" is only half the information the dealer needs. The other
 * half is who to return it to.
 */
data class ExpiringBatch(
    val itemId: Long,
    val productName: String,
    val batchNumber: String?,
    val expiryDate: Long,
    val quantity: Double,
    val unit: String?,
    val partyName: String,
    val billNumber: String?
) {
    /** Negative once it has expired. */
    val daysLeft: Int
        get() = ((expiryDate - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()

    val hasExpired: Boolean get() = daysLeft < 0
}

object ExpiryWindow {
    /**
     * How far ahead to warn.
     *
     * Sixty days is chosen to be useful rather than merely truthful: a warning
     * a week before expiry tells the dealer they have already lost the stock.
     * Two months is long enough to discount it, move it, or get it back to the
     * supplier while they will still accept a return.
     */
    const val WARN_DAYS = 60
    const val WARN_MS = WARN_DAYS * 24L * 60 * 60 * 1000
}
