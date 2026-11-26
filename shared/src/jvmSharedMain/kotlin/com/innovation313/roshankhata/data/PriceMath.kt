package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The price-and-profit formulas of the Formula Book (F65–F69; Marhala 9, lane B, 26 Nov 2026): one function per figure, pure,
 * so Hudhud's knowledge answers ([HudhudKnowledge]) and any later screen work them out the same way (lesson 128).
 *
 * Every percent is worked in decimal arithmetic ([BigDecimal]) and handed back as a [Double], so 800 → 1,000 is exactly 20 and
 * 25, never 19.999…; the caller rounds only for showing. Nothing here reads the book: the figures are the ones it is given.
 *
 * Sources (the knowledge atoms of shared/hudhud/knowledge/atoms.tsv carry them with the day they were read): margin and markup as
 * the rates of markup on selling price and on cost (eCampusOntario, Business and Financial Math, 1.3 Markup); contribution
 * margin and break-even units (OpenStax, Principles of Accounting Vol. 2, 3.1 and 3.2); credit concentration as an idea
 * (OCC Comptroller's Handbook, Concentrations of Credit), measured here as a share of all money owed to the shop (D19).
 */
object PriceMath {

    private val HUNDRED = BigDecimal(100)
    private val MC = MathContext(16, RoundingMode.HALF_UP)

    /** A figure the formulas may take: finite and not below nought. */
    private fun usable(v: Double) = v.isFinite() && v >= 0.0

    /**
     * F66, margin %: (sale − cost) ÷ sale × 100 — the share of the sale price left after the cost. Negative is a loss. Null when
     * the sale price is not above nought, or a figure is not usable: no margin can be said of a sale at nothing.
     */
    fun marginPercent(cost: Double, sale: Double): Double? {
        if (!usable(cost) || !usable(sale) || sale <= 0.0) return null
        val c = BigDecimal.valueOf(cost)
        val s = BigDecimal.valueOf(sale)
        return s.subtract(c).multiply(HUNDRED).divide(s, MC).toDouble()
    }

    /**
     * F67, markup %: (sale − cost) ÷ cost × 100 — the profit as a share of the cost. Null when the cost is not above nought (a
     * markup on nothing has no size) or a figure is not usable.
     */
    fun markupPercent(cost: Double, sale: Double): Double? {
        if (!usable(cost) || !usable(sale) || cost <= 0.0) return null
        val c = BigDecimal.valueOf(cost)
        val s = BigDecimal.valueOf(sale)
        return s.subtract(c).multiply(HUNDRED).divide(c, MC).toDouble()
    }

    /** The profit on one sale, sale − cost, in decimal arithmetic. Null for a figure that is not usable. */
    fun profit(cost: Double, sale: Double): Double? {
        if (!usable(cost) || !usable(sale)) return null
        return BigDecimal.valueOf(sale).subtract(BigDecimal.valueOf(cost)).toDouble()
    }

    /**
     * F68, contribution per unit: price − variable cost per unit — what one unit sold leaves to pay the fixed costs, then
     * profit. Negative when the variable cost is above the price. Null for a figure that is not usable.
     */
    fun contribution(price: Double, variableCost: Double): Double? {
        if (!usable(price) || !usable(variableCost)) return null
        return BigDecimal.valueOf(price).subtract(BigDecimal.valueOf(variableCost)).toDouble()
    }

    /** F69's answer: the [exact] quotient and the [whole] units to sell, the quotient rounded UP (a part of a unit is not sold). */
    data class BreakEven(val exact: Double, val whole: Long)

    /**
     * F69, break-even units: fixed costs ÷ (price − variable cost per unit). Null when the price is not above the variable cost
     * (every unit sold loses or leaves nothing, so no number of sales ever covers the fixed costs) or a figure is not usable.
     */
    fun breakEvenUnits(fixedCosts: Double, price: Double, variableCost: Double): BreakEven? {
        if (!usable(fixedCosts) || !usable(price) || !usable(variableCost)) return null
        val per = BigDecimal.valueOf(price).subtract(BigDecimal.valueOf(variableCost))
        if (per.signum() <= 0) return null
        val q = BigDecimal.valueOf(fixedCosts).divide(per, MC)
        return BreakEven(q.toDouble(), q.setScale(0, RoundingMode.CEILING).toLong())
    }

    /**
     * F65's answer: the [index] of the largest balance owed to the shop in the list given, that [largest] balance, the [total]
     * owed to the shop and the [share] the largest holds of it, 0 to 1.
     */
    data class Concentration(val index: Int, val largest: Double, val total: Double, val share: Double)

    /**
     * F65, concentration: the largest single balance owed to the shop as a share of all money owed to the shop. [balances] are
     * parties' balances with the book's sign (above nought: the party owes the shop, F04); a balance the shop owes, a settled one
     * and one that is not finite count for nothing. Two equal largest: the first. Null when nothing is owed to the shop
     * ([Money.isPositive] on the total: half the shop's smallest coin). D19: only the share is shown; an alert comes later.
     */
    fun concentration(balances: List<Double>): Concentration? {
        var total = BigDecimal.ZERO
        var best = -1
        var bestValue = 0.0
        balances.forEachIndexed { i, b ->
            if (!b.isFinite() || !Money.isPositive(b)) return@forEachIndexed
            total = total.add(BigDecimal.valueOf(b))
            if (best < 0 || b > bestValue) {
                best = i
                bestValue = b
            }
        }
        if (best < 0 || !Money.isPositive(total.toDouble())) return null
        val share = BigDecimal.valueOf(bestValue).divide(total, MC).toDouble()
        return Concentration(best, bestValue, total.toDouble(), share)
    }
}
