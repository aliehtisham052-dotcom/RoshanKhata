package com.innovation313.roshankhata.data

/** One khata entry as the aging needs it: whose, how much, which way, when. */
data class AgingMove(val partyId: Long, val amount: Double, val isGiven: Boolean, val timestamp: Long)

/**
 * Dues by age (21 Oct 2026, the critical review): what each party still owes
 * — or is owed — split by how long ago it was given: 0–30, 31–60, 61–90 and
 * over 90 days. A shopkeeper counts a payment against the oldest debt, so
 * what is still owed is the NEWEST of what was given: the balance is laid
 * over the party's "I gave" entries from the newest back ([pieces]), and each
 * piece keeps its day. A supplier I owe is the same with their "I got"
 * entries. Every figure is summed from the entries; nothing is stored.
 */
object Aging {

    /** The buckets' last days: 0–30, 31–60, 61–90; past the last, over 90. */
    val LIMITS = intArrayOf(30, 60, 90)
    const val BUCKETS = 4
    private const val DAY = 24L * 60 * 60 * 1000

    data class Row(
        val partyId: Long,
        val name: String,
        val phone: String?,
        val isCustomer: Boolean,
        /** The whole balance, as a positive figure (which way is the list it is in). */
        val balance: Double,
        /** The balance by age, [BUCKETS] figures that add up to it. */
        val buckets: List<Double>,
        /** The day of the oldest piece still owed. */
        val oldest: Long?
    ) {
        val over90: Double get() = buckets[BUCKETS - 1]
    }

    data class Report(val receivable: List<Row>, val payable: List<Row>, val now: Long) {
        val receivableTotals: List<Double> get() = totals(receivable)
        val payableTotals: List<Double> get() = totals(payable)
        private fun totals(rows: List<Row>) = (0 until BUCKETS).map { b -> LineMath.round(rows.sumOf { it.buckets[b] }) }
    }

    /** Which bucket a day falls in, counted back from [now]. */
    fun bucketOf(timestamp: Long, now: Long): Int {
        val days = ((now - timestamp).coerceAtLeast(0L) / DAY).toInt()
        val i = LIMITS.indexOfFirst { days <= it }
        return if (i < 0) BUCKETS - 1 else i
    }

    /**
     * [outstanding] laid over the moves that made it, newest first, as (day,
     * amount) pieces. A balance is never more than what was given, so it is
     * always covered; should the moves fall short, the rest is put on the
     * oldest day rather than lost.
     */
    fun pieces(newestFirst: List<Pair<Long, Double>>, outstanding: Double): List<Pair<Long, Double>> {
        var left = LineMath.round(outstanding)
        val out = ArrayList<Pair<Long, Double>>()
        for ((day, amount) in newestFirst) {
            if (left <= 0.005) break
            val take = LineMath.round(minOf(amount, left))
            if (take <= 0.0) continue
            out += day to take
            left = LineMath.round(left - take)
        }
        if (left > 0.005) out += (newestFirst.lastOrNull()?.first ?: 0L) to left
        return out
    }

    fun compute(parties: List<PartyWithBalance>, moves: List<AgingMove>, now: Long = System.currentTimeMillis()): Report {
        val byParty = moves.groupBy { it.partyId }
        fun row(p: PartyWithBalance, owedToMe: Boolean): Row {
            val made = byParty[p.id].orEmpty().filter { it.isGiven == owedToMe }.sortedByDescending { it.timestamp }
            val pieces = pieces(made.map { it.timestamp to it.amount }, kotlin.math.abs(p.balance))
            val sums = DoubleArray(BUCKETS)
            pieces.forEach { (day, amount) -> sums[bucketOf(day, now)] += amount }
            return Row(p.id, p.name, p.phone, p.isCustomer, LineMath.round(kotlin.math.abs(p.balance)), sums.map { LineMath.round(it) }, pieces.minOfOrNull { it.first })
        }
        val order = compareByDescending<Row> { it.over90 }.thenByDescending { it.balance }
        return Report(
            receivable = parties.filter { it.balance > 0.005 }.map { row(it, true) }.sortedWith(order),
            payable = parties.filter { it.balance < -0.005 }.map { row(it, false) }.sortedWith(order),
            now = now
        )
    }

    suspend fun compute(dao: KhataDao, now: Long = System.currentTimeMillis()): Report =
        compute(dao.partiesWithBalanceOnce(), dao.agingMoves(), now)
}
