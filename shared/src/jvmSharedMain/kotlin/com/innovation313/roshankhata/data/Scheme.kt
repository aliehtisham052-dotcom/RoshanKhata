package com.innovation313.roshankhata.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * C1 (2 Oct, v27): a company scheme the dealer is working towards — "buy
 * Rs 5 lakh from Syngenta by 31 Dec, get 2%", "100 bags, 5 bags free".
 *
 * Purchases are read from the supplier bills already in the app, between the
 * two dates, from the chosen supplier and/or of the chosen company's
 * products. Up to three slabs; the highest one reached is what is earned.
 * The claim is tracked until the credit note or rebate actually arrives, so
 * no earned scheme is forgotten.
 */
@Entity(tableName = "schemes")
data class Scheme(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** The supplier the purchases must come from; null = any supplier. */
    val partyId: Long? = null,
    /** The product company the purchases must be of; null = any company. */
    val company: String? = null,
    val startDate: Long,
    val endDate: Long,
    /** [SchemeMath.BY_VALUE] or [SchemeMath.BY_QTY]. */
    val measure: String,
    /** For a quantity scheme: the unit counted ("bag"); null = every unit. */
    val unit: String? = null,
    /** [SchemeMath.PERCENT], [SchemeMath.AMOUNT] or [SchemeMath.UNITS]. */
    val rewardKind: String,
    val target1: Double,
    val reward1: Double,
    val target2: Double? = null,
    val reward2: Double? = null,
    val target3: Double? = null,
    val reward3: Double? = null,
    /** What actually arrived; null while the claim is still open. */
    val claimedAmount: Double? = null,
    val claimedAt: Long? = null,
    val note: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object SchemeMath {
    const val BY_VALUE = "value"
    const val BY_QTY = "qty"
    const val PERCENT = "percent"
    const val AMOUNT = "amount"
    const val UNITS = "units"

    data class Slab(val target: Double, val reward: Double)

    data class Progress(
        val purchased: Double,
        /** Highest slab reached, or null. */
        val achieved: Slab?,
        /** The next slab up, or null when the top one is reached. */
        val next: Slab?,
        /** How much more to buy to reach [next]. */
        val toNext: Double?,
        /** What [achieved] is worth: Rs for PERCENT/AMOUNT, units for UNITS. */
        val earned: Double?
    )

    fun slabs(s: Scheme): List<Slab> =
        listOfNotNull(
            Slab(s.target1, s.reward1),
            if (s.target2 != null && s.reward2 != null) Slab(s.target2, s.reward2) else null,
            if (s.target3 != null && s.reward3 != null) Slab(s.target3, s.reward3) else null
        ).filter { it.target > 0 }.sortedBy { it.target }

    fun progress(s: Scheme, purchased: Double): Progress {
        val all = slabs(s)
        val achieved = all.lastOrNull { purchased + 1e-9 >= it.target }
        val next = all.firstOrNull { purchased + 1e-9 < it.target }
        val earned = achieved?.let {
            when (s.rewardKind) {
                // A percentage is of the purchase value; on a quantity scheme
                // it has no rupee base, so the form never offers that pair.
                PERCENT -> if (s.measure == BY_VALUE) LineMath.round(purchased * it.reward / 100.0) else null
                else -> it.reward
            }
        }
        return Progress(
            purchased = purchased,
            achieved = achieved,
            next = next,
            toNext = next?.let { LineMath.round(it.target - purchased) },
            earned = earned
        )
    }

    enum class Status { RUNNING, CLAIM_DUE, CLAIMED, MISSED }

    fun status(s: Scheme, p: Progress, now: Long): Status = when {
        s.claimedAmount != null -> Status.CLAIMED
        now <= s.endDate -> Status.RUNNING
        p.achieved != null -> Status.CLAIM_DUE
        else -> Status.MISSED
    }
}
