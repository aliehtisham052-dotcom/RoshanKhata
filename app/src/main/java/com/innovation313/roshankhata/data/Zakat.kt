package com.innovation313.roshankhata.data

/**
 * Zakat reference calculator.
 *
 * IMPORTANT: this is a reference aid, not a fatwa. Zakat has genuine fiqhi
 * detail — the nisab standard, the treatment of doubtful debts, the hawl
 * (lunar year), and what counts as a deductible liability all vary between
 * scholars and schools. Every screen using this must show the disclaimer and
 * direct the user to a qualified scholar for a binding ruling.
 *
 * Defaults follow the mainstream Hanafi position:
 *  - Rate: 2.5% (1/40)
 *  - Nisab: the silver standard (612.36 g), which is lower than gold and so
 *    brings more people into the obligation — the position generally taken as
 *    more careful toward the rights of the poor.
 *  - Trade receivables you are confident of collecting are counted each year.
 *  - Doubtful debts are excluded here, since many scholars hold Zakat on them
 *    falls due only once actually received.
 *  - Qarz-e-Hasna you have *given out* is still your wealth and, when you
 *    expect it back, is treated like a certain receivable.
 *  - What you owe others is deducted.
 */
object Zakat {

    const val RATE = 0.025
    const val NISAB_SILVER_GRAMS = 612.36
    const val NISAB_GOLD_GRAMS = 87.48

    /** Nisab value in currency, given today's silver price per gram. */
    fun nisabFromSilverPrice(pricePerGram: Double): Double =
        NISAB_SILVER_GRAMS * pricePerGram

    /** Nisab value in currency, given today's gold price per gram. */
    fun nisabFromGoldPrice(pricePerGram: Double): Double =
        NISAB_GOLD_GRAMS * pricePerGram

    /**
     * Net Zakatable wealth from the ledger, plus any cash/stock the owner adds.
     *
     * @param includeDoubtful if the owner chooses to be cautious and count
     *        doubtful debts too, this can be switched on.
     */
    fun zakatableWealth(
        inputs: ZakatInputs,
        cashAndStock: Double,
        includeDoubtful: Boolean = false
    ): Double {
        val receivables = inputs.certainReceivables +
                inputs.qarzeHasnaGiven +
                (if (includeDoubtful) inputs.doubtfulReceivables else 0.0)

        val net = cashAndStock + receivables - inputs.payables
        return if (net > 0) net else 0.0
    }

    /**
     * Turn each customer's ledger into the four figures Zakat works from.
     *
     * The rule is per customer, and it is the whole point of this function.
     * A customer whose entries come out negative overall is somebody the shop
     * OWES: that is a liability, deducted, and none of their categories can be
     * a receivable. A customer in credit is an asset, and it is their own
     * total that caps what may be counted from them — never more than they
     * actually owe.
     *
     * Where a customer in credit has a category in the red (a doubtful entry
     * settled, say, against certain ones outstanding), that red reduces every
     * category in proportion. Proportion rather than an order of preference,
     * because choosing which category to shrink first would be quietly
     * choosing whether the owner pays more Zakat or less, and that is not a
     * decision for arithmetic to make on their behalf.
     *
     * Summed across the whole book instead, as this used to be, five hundred
     * owed by one customer and three hundred owed to another read as two
     * hundred receivable and nothing payable. The Zakat came out the same, but
     * the screen told the owner something untrue about their own book.
     */
    fun fromParties(parties: List<PartyZakatBalance>): ZakatInputs {
        var certain = 0.0
        var doubtful = 0.0
        var qarz = 0.0
        var payables = 0.0

        for (p in parties) {
            val total = p.certain + p.doubtful + p.qarz

            if (total <= 0.0) {
                // The shop owes them. Deducted whole; nothing to collect here.
                payables += -total
                continue
            }

            val credit = maxOf(p.certain, 0.0) +
                    maxOf(p.doubtful, 0.0) +
                    maxOf(p.qarz, 0.0)

            // credit is at least total whenever total is positive, so this
            // only ever scales down, and never below zero.
            val share = if (credit > 0.0) total / credit else 0.0

            certain += maxOf(p.certain, 0.0) * share
            doubtful += maxOf(p.doubtful, 0.0) * share
            qarz += maxOf(p.qarz, 0.0) * share
        }

        return ZakatInputs(
            certainReceivables = certain,
            doubtfulReceivables = doubtful,
            qarzeHasnaGiven = qarz,
            payables = payables
        )
    }

    /** Zakat due — zero unless wealth has reached nisab. */
    fun zakatDue(zakatableWealth: Double, nisab: Double): Double =
        if (nisab > 0 && zakatableWealth >= nisab) zakatableWealth * RATE else 0.0

    fun meetsNisab(zakatableWealth: Double, nisab: Double): Boolean =
        nisab > 0 && zakatableWealth >= nisab
}
