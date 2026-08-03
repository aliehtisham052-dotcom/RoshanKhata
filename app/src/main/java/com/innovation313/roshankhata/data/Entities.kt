package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A customer or supplier. */
@Entity(
    tableName = "parties",
    indices = [Index(value = ["qrToken"], unique = true)]
)
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

    /**
     * The customer's QR identity — what their card carries.
     *
     * A random token and not the row id, because a printed card outlives the
     * database that issued it: a restore or an import that renumbered the ids
     * would kill every card already in a customer's pocket. The token never
     * changes for as long as the customer exists.
     *
     * It is also all the card carries. No name, no phone, no balance — a lost
     * card tells its finder nothing, because the token only means something
     * inside this app on the owner's phone.
     */
    val qrToken: String? = QrTag.newToken(),

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
    indices = [Index("partyId"), Index("productId"), Index("billItemId")]
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

    /**
     * A photograph of the bill or slip this entry came from, kept on this
     * phone under the app's own files.
     *
     * Deliberately left out of statements and shares, the same way a
     * customer's photo is: a bill often carries another buyer's name or a
     * rate the owner would rather not forward with it.
     */
    val billPhotoPath: String? = null,

    /**
     * The product this sale was of, once it is known.
     *
     * Nullable, and null on every entry written before products existed. The
     * free-text [itemName] above is untouched and remains what the owner
     * typed; this is the anchor that lets the same goods be counted, traced to
     * a batch, and found again next season. No foreign key on purpose — see
     * the note on [Product].
     */
    val productId: Long? = null,

    /**
     * The supplier bill line this sale came out of, where the owner knows it.
     *
     * Carries the batch number with it, so a bad batch can be traced to the
     * customers who received it. Nullable: most sales will not be tagged, and
     * for those the fallback is which customers bought the product while that
     * batch was in stock.
     */
    val billItemId: Long? = null,

    /**
     * Who wrote this entry, once a shop has more than one pair of hands.
     *
     * Null on every entry so far, and nothing sets it yet — deliberately. A
     * column costs a migration whenever it arrives; arriving now means the
     * entries written between today and the day staff logins exist already
     * have somewhere for the answer to go, instead of a stretch of ledger
     * that can never be attributed. Free text rather than a foreign key: a
     * name written on an entry should outlive the person leaving the shop.
     */
    val createdBy: String? = null,

    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * One ledger entry with its customer's name attached — for the whole-ledger
 * report, which crosses every party at once rather than living inside one
 * party's own screen the way [LedgerEntry] normally does.
 */
data class EntryWithParty(
    val id: Long,
    val partyId: Long,
    val partyName: String,
    val amount: Double,
    val isGiven: Boolean,
    val note: String?,
    val entryNumber: String,
    val timestamp: Long
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

/**
 * Name and phone only, for the invoice customer field's suggestion list.
 * See [KhataDao.allPartiesForInvoice] — deliberately not carrying an id;
 * this has never been a link to a party row, just a convenience lookup.
 */
data class PartyNameAndPhone(
    val name: String,
    val phone: String?
)

/**
 * An entry's id and its bill-photo path — just enough for the image restore to
 * re-point each path at where the file now lives, without loading whole rows.
 */
data class EntryBillPhoto(
    val id: Long,
    val billPhotoPath: String?
)

/** Receivables split by recovery confidence, for the Zakat screen. */
data class ZakatInputs(
    val certainReceivables: Double,
    val doubtfulReceivables: Double,
    val qarzeHasnaGiven: Double,
    val payables: Double
)

/**
 * One customer's ledger, split the three ways Zakat cares about.
 *
 * Kept per customer rather than summed across the book, because a total
 * cannot tell the difference between money owed to you and money you owe.
 * Five hundred from one customer and three hundred owed to another is not
 * two hundred receivable and nothing payable — it is both, and Zakat treats
 * them as two different things.
 *
 * Each figure is positive when the customer owes the shop and negative when
 * the shop owes the customer, the same sign convention as everywhere else.
 */
data class PartyZakatBalance(
    val partyId: Long,
    val certain: Double,
    val doubtful: Double,
    val qarz: Double
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
