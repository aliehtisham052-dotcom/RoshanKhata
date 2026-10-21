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
@Entity(tableName = "loyalty", indices = [Index("partyId"), Index("saleId"), Index("uid")])
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

/** The book's loyalty rule, from its two settings: both set and above nothing, or loyalty is off. */
object Loyalty {
    fun rule(perHundred: String?, value: String?): LoyaltyRule? {
        val p = perHundred?.trim()?.let { Digits.parse(it) } ?: return null
        val v = value?.trim()?.let { Digits.parse(it) } ?: return null
        return if (p > 0.0 && v > 0.0) LoyaltyRule(p, v) else null
    }

    suspend fun rule(dao: KhataDao): LoyaltyRule? =
        rule(dao.settingValue(BookSettings.LOYALTY_POINTS_PER_100), dao.settingValue(BookSettings.LOYALTY_POINT_VALUE))

    /** What a ticket's points rows say: spent and earned. */
    fun ofSale(rows: List<LoyaltyEntry>): Pair<Double, Double> =
        LineMath.round(-rows.filter { it.points < 0 }.sumOf { it.points }) to LineMath.round(rows.filter { it.points > 0 }.sumOf { it.points })

    /** A ticket's points as its receipt says them: spent (and their worth), earned, and the customer's points now. */
    data class OnReceipt(val used: Double, val usedValue: Double, val earned: Double, val balance: Double)

    /** Null when the ticket touched no points (loyalty off, a walk-in, nothing earned or spent). */
    suspend fun onReceipt(dao: KhataDao, sale: Sale): OnReceipt? {
        val partyId = sale.partyId ?: return null
        val rows = dao.loyaltyOfSale(sale.id)
        if (rows.isEmpty()) return null
        val (used, earned) = ofSale(rows)
        val value = rule(dao)?.value(used) ?: 0.0
        return OnReceipt(used, value, earned, LineMath.round(dao.loyaltyBalance(partyId)))
    }
}

