package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A payment plan against a party: an amount they have agreed to pay off over
 * time, in instalments.
 *
 * This is a PROMISE, not a debt. The debt already lives in the ledger, put
 * there when the goods went out. A plan is only the arrangement for clearing
 * it — so creating one must not add a single rupee to what anyone owes, and
 * recording a payment against it posts exactly one ledger entry, not two.
 * Getting this wrong would double-count the debt, and the customer would be
 * chased for money they never owed.
 */
@Entity(
    tableName = "payment_plans",
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
data class PaymentPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,

    /** The total the party has agreed to clear under this plan. */
    val totalAmount: Double,

    /** What they said they would pay each time. A guide, not a rule. */
    val installmentAmount: Double? = null,

    val note: String? = null,

    /** When the next payment is expected. Null if no date was agreed. */
    val nextDueDate: Long? = null,

    val createdAt: Long = System.currentTimeMillis(),

    val isClosed: Boolean = false,
    val closedAt: Long? = null,

    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * One payment made against a plan.
 *
 * Each carries the id of the ledger entry it created, so the two can never
 * drift apart: undo the payment and the ledger entry goes with it.
 */
@Entity(
    tableName = "installments",
    foreignKeys = [
        ForeignKey(
            entity = PaymentPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,

    val amount: Double,

    /** The ledger entry this payment created. The single source of truth. */
    val ledgerEntryId: Long,

    val paidAt: Long = System.currentTimeMillis(),
    val note: String? = null,

    val isDeleted: Boolean = false
)

/** A plan with its progress, for display. */
data class PlanProgress(
    val id: Long,
    val partyId: Long,
    val partyName: String,
    val totalAmount: Double,
    val installmentAmount: Double?,
    val paidSoFar: Double,
    val nextDueDate: Long?,
    val note: String?,
    val isClosed: Boolean
) {
    val remaining: Double get() = (totalAmount - paidSoFar).coerceAtLeast(0.0)

    val percentPaid: Int
        get() = if (totalAmount <= 0) 0
        else ((paidSoFar / totalAmount) * 100).toInt().coerceIn(0, 100)
}
