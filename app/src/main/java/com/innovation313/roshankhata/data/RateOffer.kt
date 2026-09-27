package com.innovation313.roshankhata.data

/**
 * What the add-entry screen may offer from a product's rate — decided here,
 * away from the screen, so every rule below is pinned by RateOfferTest.
 *
 *  - Goods going OUT to a CUSTOMER only. Money coming in is not a function of
 *    any rate, and on a supplier's account these selling prices are the wrong
 *    prices entirely: [Result.Hidden].
 *  - A product with no rate at all offers nothing and shows no chips.
 *  - The chosen rate type is honoured exactly. Udhar chosen with no udhar rate
 *    recorded is [Result.NotSet] — never the cash price quietly passed off as
 *    the credit one (the old behaviour).
 *  - A rate is per the product's own unit. A different typed unit (25 kg of a
 *    product priced per bori) is [Result.UnitMismatch], never 25 × the bori
 *    price. Units compare trimmed and case-blind: "Bori" is "bori".
 *  - The total is worked out by [LineMath], to the paisa.
 */
object RateOffer {

    sealed class Result {
        /** Not applicable here: hide the whole rate section. */
        object Hidden : Result()
        /** The chosen rate type has no price recorded for this product. */
        object NotSet : Result()
        data class UnitMismatch(val productUnit: String, val typedUnit: String) : Result()
        /** Everything but a quantity: show the chips, offer nothing yet. */
        object NeedQuantity : Result()
        data class Offer(val rate: Double, val total: Double) : Result()
    }

    fun decide(
        isGiven: Boolean,
        isCustomer: Boolean,
        creditPrice: Double?,
        cashPrice: Double?,
        productUnit: String?,
        rateType: String,
        quantity: Double?,
        typedUnit: String
    ): Result {
        val credit = creditPrice?.takeIf { it > 0.0 }
        val cash = cashPrice?.takeIf { it > 0.0 }
        if (!isGiven || !isCustomer || (credit == null && cash == null)) return Result.Hidden

        val rate = (if (rateType == RateType.CASH) cash else credit) ?: return Result.NotSet

        val pUnit = productUnit?.trim()?.takeIf { it.isNotEmpty() }
        val tUnit = typedUnit.trim()
        if (pUnit != null && tUnit.isNotEmpty() && !tUnit.equals(pUnit, ignoreCase = true)) {
            return Result.UnitMismatch(pUnit, tUnit)
        }

        if (quantity == null || quantity <= 0.0) return Result.NeedQuantity
        val total = LineMath.lineTotal(quantity, rate) ?: return Result.NeedQuantity
        return Result.Offer(rate, total)
    }

    /**
     * Just the price that applies to one unit of the product for a sale to
     * this party at this rate type, or why none does — for filling the Rate
     * box before a quantity is known. Same rules as [decide]; an [Result.Offer]
     * here carries the price as both rate and total (one unit).
     */
    fun price(
        isCustomer: Boolean,
        creditPrice: Double?,
        cashPrice: Double?,
        productUnit: String?,
        rateType: String,
        typedUnit: String
    ): Result = decide(
        isGiven = true,
        isCustomer = isCustomer,
        creditPrice = creditPrice,
        cashPrice = cashPrice,
        productUnit = productUnit,
        rateType = rateType,
        quantity = 1.0,
        typedUnit = typedUnit
    )
}
