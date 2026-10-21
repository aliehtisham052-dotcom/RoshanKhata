package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Money in and out of the business that is not tied to any customer or
 * supplier: rent, electricity, wages, a walk-in cash sale, fuel.
 *
 * Deliberately a SEPARATE table from the ledger. A cashbook entry has no
 * counterparty and no running balance against anyone — folding it into the
 * party ledger would either invent a phantom party or corrupt a real one's
 * balance. Keeping them apart is what lets both stay truthful.
 */
@Entity(tableName = "cashbook")
data class CashEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val amount: Double,

    /** true = money came in, false = money went out. */
    val isIncome: Boolean,

    /** Rent, Electricity, Wages, Fuel, Cash Sale — free text, owner's own words. */
    val category: String,

    val note: String? = null,

    val timestamp: Long = System.currentTimeMillis(),

    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,

    /**
     * How the money moved, one of [PaymentMethod]'s keys, when it is known
     * (POS Phase 1, v32). Null on every row written before, and on any row
     * the owner did not say — which the drawer count treats as cash, as it
     * always has. A walk-in's bank transfer is written here as "bank" so the
     * evening's count does not look for it in the drawer.
     */
    val paymentMethod: String? = null,

    /** The counter sale that posted this row, if one did; see [LedgerEntry.saleId]. */
    val saleId: Long? = null,

    /**
     * This row's identity across copies of the book (schema 36): a random id
     * given once, kept by every backup and restore, the same on the phone and
     * the computer for a row that came from one copy. What a restore compares
     * to say what would be lost, and what a merge matches rows by. Null only
     * for a moment on a row restored from an old file; restoreAll fills it.
     */
    val uid: String? = Uid.next()
)

/**
 * The one category the app writes itself (POS Phase 1): a walk-in counter
 * sale's money. A fixed key, not a translated word, so a cashbook restored
 * in another language still knows which rows the counter wrote; the screens
 * show it in the app's language (W.cash_category_sale).
 */
object CashCategory {
    const val SALE = "Sale"
    /** A walk-in's refund over the counter (POS Phase 3). */
    const val RETURN = "Return"
}

/** Totals over a window, for the cashbook header. */
data class CashTotals(
    val income: Double,
    val expense: Double
) {
    val net: Double get() = income - expense
}
