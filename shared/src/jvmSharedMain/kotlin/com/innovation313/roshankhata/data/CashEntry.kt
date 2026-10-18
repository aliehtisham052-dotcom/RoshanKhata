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
    val deletedAt: Long? = null
)

/** Totals over a window, for the cashbook header. */
data class CashTotals(
    val income: Double,
    val expense: Double
) {
    val net: Double get() = income - expense
}
