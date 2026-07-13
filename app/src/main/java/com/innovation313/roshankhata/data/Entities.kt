package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A customer or supplier. */
@Entity(tableName = "parties")
data class Party(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String? = null,
    /** true = customer, false = supplier */
    val isCustomer: Boolean = true,
    /** Local file path to the party's photo, if set. Never leaves the device. */
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/** A single ledger entry against a party. */
@Entity(
    tableName = "transactions",
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
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    /** Always positive. Direction is carried by [isGiven]. */
    val amount: Double,
    /**
     * true  = "I Gave"  (money/goods went out; party owes me)
     * false = "I Got"   (money/goods came in; reduces what party owes)
     */
    val isGiven: Boolean,
    val note: String? = null,
    /** Human-facing reference number, e.g. "RK-000123". Unique per entry. */
    val entryNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/** Party plus its computed outstanding balance, for list display. */
data class PartyWithBalance(
    val id: Long,
    val name: String,
    val phone: String?,
    val isCustomer: Boolean,
    val photoPath: String?,
    /**
     * Positive  => party owes me (I should receive)
     * Negative  => I owe party (I should pay)
     */
    val balance: Double
)
