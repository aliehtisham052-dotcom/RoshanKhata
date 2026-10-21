package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Where a cheque stands.
 *
 * A cheque is not money until it clears. Keeping it in its own state — rather
 * than posting it to the ledger the day it is handed over — is the whole point:
 * a bounced cheque that was already booked as payment quietly corrupts the
 * balance, and the owner finds out weeks later.
 */
object ChequeStatus {
    /** Held, not yet banked. Not counted as money. */
    const val PENDING = 0
    /** Banked and cleared. This is the moment it becomes money. */
    const val CLEARED = 1
    /** Bounced. Still owed — the debt never went away. */
    const val BOUNCED = 2
    /** Cancelled or returned by agreement. */
    const val CANCELLED = 3
}

@Entity(
    tableName = "cheques",
    foreignKeys = [
        ForeignKey(
            entity = Party::class,
            parentColumns = ["id"],
            childColumns = ["partyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("partyId"), Index("uid")]
)
data class Cheque(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,

    val amount: Double,

    /**
     * true  = received from the party (they are paying me)
     * false = issued to the party (I am paying them)
     */
    val isReceived: Boolean,

    val chequeNumber: String? = null,
    val bankName: String? = null,

    /** The date written on the cheque — when it can be banked. */
    val dueDate: Long,

    val status: Int = ChequeStatus.PENDING,

    /** When it cleared or bounced. Null while still pending. */
    val settledAt: Long? = null,

    /**
     * The ledger entry created when this cheque cleared. Held so that if the
     * owner later corrects a mistaken "cleared", we know exactly which entry
     * to take back out — rather than leaving a phantom payment behind.
     */
    val ledgerEntryId: Long? = null,

    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

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
)

/** A cheque together with the name of the party it belongs to. */
data class ChequeWithParty(
    val id: Long,
    val partyId: Long,
    val partyName: String,
    val partyPhone: String?,
    val amount: Double,
    val isReceived: Boolean,
    val chequeNumber: String?,
    val bankName: String?,
    val dueDate: Long,
    val status: Int,
    val note: String?
)
