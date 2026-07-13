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

    /** Zakat due — zero unless wealth has reached nisab. */
    fun zakatDue(zakatableWealth: Double, nisab: Double): Double =
        if (nisab > 0 && zakatableWealth >= nisab) zakatableWealth * RATE else 0.0

    fun meetsNisab(zakatableWealth: Double, nisab: Double): Boolean =
        nisab > 0 && zakatableWealth >= nisab
}
