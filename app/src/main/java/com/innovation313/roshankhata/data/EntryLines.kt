package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One goods line as the add-entry form holds it, before it is saved.
 *
 * Carries the product's two prices and unit alongside the line, so that when
 * the owner switches the Udhar/Naqd chip every line whose rate came from the
 * product can be re-priced at once — and [rateEdited] marks a rate he typed
 * himself (a bargain), which a chip switch must never overwrite.
 */
data class LineDraft(
    val itemName: String?,
    val quantity: Double?,
    val unit: String?,
    val rate: Double?,
    val productId: Long? = null,
    val billItemId: Long? = null,
    val creditPrice: Double? = null,
    val cashPrice: Double? = null,
    val productUnit: String? = null,
    val rateEdited: Boolean = false,
    /** A free item (a company scheme): leaves the shelf, costs nothing. */
    val isBonus: Boolean = false
) {
    /** The saved line, or null if nothing about goods was filled in. */
    fun toItem(): EntryItem? =
        EntryItem.ofGoods(itemName, quantity, unit, productId, billItemId, if (isBonus) null else rate)
            ?.copy(isBonus = isBonus)

    /** What this line comes to, to the paisa — null when qty or rate is missing. */
    fun total(): Double? = LineMath.lineTotal(quantity, rate, isBonus)

    /** Complete enough to go into the list: a name and a quantity above zero. */
    fun isComplete(): Boolean = !itemName.isNullOrBlank() && quantity != null && quantity > 0.0

    /**
     * This line at the other rate type. Only a rate that came from the
     * product moves; a rate the owner typed stays exactly as he typed it.
     * No price for the new type (or a different unit) leaves the rate empty
     * — unknown, never the other type's price.
     */
    fun repriced(rateType: String, isCustomer: Boolean): LineDraft {
        if (rateEdited || isBonus || productId == null) return this
        val price = RateOffer.price(isCustomer, creditPrice, cashPrice, productUnit, rateType, unit.orEmpty())
        return copy(rate = (price as? RateOffer.Result.Offer)?.rate)
    }
}

/** Totals across a form's lines, and how the written amount compares. */
object EntryLines {

    /**
     * The lines added up — null when there are none, or when any line's total
     * is unknown (a missing rate cannot be treated as zero).
     */
    fun total(lines: List<LineDraft>): Double? {
        if (lines.isEmpty()) return null
        var sum = BigDecimal.ZERO
        for (line in lines) {
            val t = line.total() ?: return null
            sum = sum.add(BigDecimal.valueOf(t))
        }
        return sum.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    sealed class AmountCheck {
        object Equal : AmountCheck()
        /** Written less than the lines: a discount or a round-off. */
        data class Less(val by: Double) : AmountCheck()
        data class More(val by: Double) : AmountCheck()
    }

    /** The written amount against the lines' total, compared to the paisa. */
    fun compare(total: Double, amount: Double): AmountCheck {
        val diff = BigDecimal.valueOf(amount).subtract(BigDecimal.valueOf(total))
            .setScale(2, RoundingMode.HALF_UP)
        return when (diff.signum()) {
            0 -> AmountCheck.Equal
            -1 -> AmountCheck.Less(diff.negate().toDouble())
            else -> AmountCheck.More(diff.toDouble())
        }
    }
}
