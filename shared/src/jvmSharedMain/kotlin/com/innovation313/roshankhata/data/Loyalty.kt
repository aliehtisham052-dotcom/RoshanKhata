package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Loyalty points (schema 36): one row per change to a customer's points —
 * earned on a sale, spent on a sale, or set right by the owner. The balance
 * is their sum, as every balance in this app is. A void bins the rows its
 * sale wrote.
 */
@Entity(tableName = "loyalty", indices = [Index("partyId"), Index("saleId")])
data class LoyaltyEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val saleId: Long? = null,
    /** Signed: + earned, − spent. */
    val points: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null,
    val isDeleted: Boolean = false,
    val uid: String? = Uid.next()
)

/** The book's loyalty rule, read from its settings; null rates mean loyalty is off. */
data class LoyaltyRule(val pointsPer100: Double, val pointValue: Double) {
    /** Points a ticket of [amount] earns, to two places. */
    fun earned(amount: Double): Double = if (amount <= 0.0) 0.0 else LineMath.round(amount * pointsPer100 / 100.0)
    /** Money [points] are worth. */
    fun value(points: Double): Double = LineMath.round(points * pointValue)
    /** The most points a ticket of [amount] may spend: never more than the ticket is worth. */
    fun maxSpend(balance: Double, amount: Double): Double =
        if (pointValue <= 0.0) 0.0 else LineMath.round(minOf(balance, amount / pointValue).coerceAtLeast(0.0))
}
