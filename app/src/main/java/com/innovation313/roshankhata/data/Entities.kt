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
    /**
     * The most the owner is willing to let this party owe.
     *
     * Null means no limit is set — and that is the honest default. A limit
     * invented on the owner's behalf would be a number they never agreed to,
     * warning them against their own business decisions.
     */
    val creditLimit: Double? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * How confident the owner is of collecting a receivable.
 * This matters for Zakat: scholars treat a debt you're sure of differently
 * from one you may never see again.
 */
object Recovery {
    /** Confident of collection — commonly treated as Zakat-liable each year. */
    const val CERTAIN = 0
    /** Doubtful — many scholars hold Zakat is due only once it is actually received. */
    const val DOUBTFUL = 1
}

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
    /** Human-facing reference number, e.g. "RK-000123". */
    val entryNumber: String,
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * Qarz-e-Hasna: a benevolent, interest-free loan.
     * Flagged so it is never mixed into trade receivables, and so no
     * interest or late-fee logic can ever be applied to it.
     */
    val isQarzeHasna: Boolean = false,

    /** One of [Recovery.CERTAIN] or [Recovery.DOUBTFUL]. */
    val recovery: Int = Recovery.CERTAIN,

    /**
     * What actually moved, alongside the money — e.g. 5 bags of urea,
     * 2 litres of pesticide. An agri-dealer's ledger is not only rupees;
     * the goods matter as much as the amount. Optional: a plain cash
     * entry leaves these null.
     */
    val itemName: String? = null,
    val quantity: Double? = null,
    /** Free text so it fits any trade: bag, litre, kg, maund, packet, piece. */
    val unit: String? = null,

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
    val balance: Double,
    /** Timestamp of the most recent entry, or 0 if the ledger is empty. */
    val lastActivity: Long = 0,
    val creditLimit: Double? = null
)

/** Receivables split by recovery confidence, for the Zakat screen. */
data class ZakatInputs(
    val certainReceivables: Double,
    val doubtfulReceivables: Double,
    val qarzeHasnaGiven: Double,
    val payables: Double
)

/** One row of the "top products" insight. */
data class ProductStat(
    val name: String,
    val qty: Double,
    val unit: String?,
    val lines: Int
)

/** One row of the "top customers" insight. */
data class CustomerStat(
    val name: String,
    val total: Double
)
