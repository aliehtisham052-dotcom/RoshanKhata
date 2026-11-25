package com.innovation313.roshankhata.data

/**
 * Cash flow (6 Oct 2026): what is promised to come IN and what is due to go
 * OUT over the next 30, 60 or 90 days, from dates the ledger already holds.
 *
 * Only what has a date counts — there is no guessing:
 *  IN  · an open instalment plan's next due date (its next instalment, never
 *        more than what is left on it); a pending cheque to receive; a harvest
 *        promise (agri shops) for that customer's balance, unless the
 *        customer is already on a plan (the plan is the more exact promise).
 *  OUT · a pending cheque the shop wrote; a credit bill's due date. A supplier
 *        bill keeps no balance of its own, so what the shop still owes that
 *        supplier is laid over the bills NEWEST FIRST: payments clear the
 *        oldest bills, so the owed money sits on the latest ones. A cheque
 *        already written to that supplier is taken off first, so the same
 *        debt is not counted as both a bill and a cheque.
 *
 * In and out are kept apart throughout. The one place they meet is the warning
 * for the first week where, counting from today, more is due out than has been
 * promised in by then — the whole point of looking ahead. A promise that has
 * already been broken (an IN date in the past) is listed but not relied on;
 * a bill already past its date is due now, so it counts in the first week.
 *
 * Plain Kotlin, no Android, read-only. CashFlowTest pins each rule.
 */
object CashFlow {

    enum class Kind(val isIn: Boolean) { PLAN(true), CHEQUE_IN(true), HARVEST(true), BILL(false), CHEQUE_OUT(false) }

    data class Item(val date: Long, val partyId: Long, val name: String, val amount: Double, val kind: Kind)

    data class Plan(val partyId: Long, val name: String, val left: Double, val instalment: Double?, val due: Long?)
    data class Cheque(val partyId: Long, val name: String, val amount: Double, val due: Long, val toReceive: Boolean)
    data class Harvest(val partyId: Long, val name: String, val at: Long, val balance: Double)
    data class Bill(val partyId: Long, val partyName: String, val totalAmount: Double, val billDate: Long, val dueDate: Long?, val isPaidInFull: Boolean)

    data class Week(val from: Long, val to: Long, val coming: Double, val going: Double)

    data class Outlook(
        val items: List<Item>,
        val overdue: List<Item>,
        val weeks: List<Week>,
        val coming: Double,
        val going: Double,
        /** First week where, counting from today, out runs ahead of in; null if none. */
        val gapWeek: Week?,
        /** By how much, at the end of [gapWeek]. */
        val gap: Double,
        /**
     * THE ONE LOADER (25 Nov 2026, BC-285): the instalments, pending cheques of a party still in the book, promised days, credit
     * bills and what the shop owes each supplier, read from the book and handed to [items]. The phone's and the computer's
     * Cash flow page, the bell's cash gap and the assistant's "next 30 days" each kept their own copy of these lines.
     * [parties] is [KhataDao.partiesWithBalanceOnce]: a negative balance is what the shop owes that party.
     */
    suspend fun load(dao: KhataDao, parties: List<PartyWithBalance>): Pair<List<Item>, Int> {
        val names = parties.associate { it.id to it.name }
        val balance = parties.associate { it.id to it.balance }
        val plans = dao.openPlansOnce().map { Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate) }
        val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }
            .map { Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived) }
        val harvests = dao.cashFlowHarvestsOnce().map { it.copy(balance = balance[it.partyId] ?: 0.0) }
        val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
        return items(plans, cheques, harvests, dao.cashFlowBillsOnce(), owed)
    }

    /** Credit bills with money still owed but no due date: left out, counted so the screen can say so. */
        val undatedBills: Int
    )

    private const val DAY = 24L * 60 * 60 * 1000
    private fun pos(v: Double) = v > 0.004

    fun items(
        plans: List<Plan>, cheques: List<Cheque>, harvests: List<Harvest>,
        bills: List<Bill>, owedToSupplier: Map<Long, Double>
    ): Pair<List<Item>, Int> {
        val out = ArrayList<Item>()
        val chequeIn = cheques.filter { it.toReceive }.groupBy { it.partyId }.mapValues { e -> e.value.sumOf { it.amount } }
        val chequeOut = cheques.filter { !it.toReceive }.groupBy { it.partyId }.mapValues { e -> e.value.sumOf { it.amount } }

        cheques.forEach { c ->
            if (pos(c.amount)) out += Item(c.due, c.partyId, c.name, c.amount, if (c.toReceive) Kind.CHEQUE_IN else Kind.CHEQUE_OUT)
        }
        val onPlan = HashSet<Long>()
        plans.forEach { p ->
            onPlan += p.partyId
            val due = p.due ?: return@forEach
            // A cheque from the same customer is most likely this instalment.
            val left = p.left - (chequeIn[p.partyId] ?: 0.0)
            if (!pos(left)) return@forEach
            val next = minOf(p.instalment?.takeIf { pos(it) } ?: left, left)
            out += Item(due, p.partyId, p.name, next, Kind.PLAN)
        }
        harvests.forEach { h ->
            if (h.partyId in onPlan) return@forEach
            val amount = h.balance - (chequeIn[h.partyId] ?: 0.0)
            if (pos(amount)) out += Item(h.at, h.partyId, h.name, amount, Kind.HARVEST)
        }
        var undated = 0
        bills.filter { !it.isPaidInFull }.groupBy { it.partyId }.forEach { (pid, own) ->
            var owed = (owedToSupplier[pid] ?: 0.0) - (chequeOut[pid] ?: 0.0)
            for (b in own.sortedByDescending { it.billDate }) {
                if (!pos(owed)) break
                val take = minOf(b.totalAmount, owed)
                owed -= take
                if (b.dueDate == null) { undated++; continue }
                if (pos(take)) out += Item(b.dueDate, pid, b.partyName, take, Kind.BILL)
            }
        }
        return out.sortedBy { it.date } to undated
    }

    fun outlook(all: List<Item>, undated: Int, today: Long, days: Int): Outlook {
        val end = today + days * DAY
        val ahead = all.filter { it.date in today until end }
        val overdue = all.filter { it.date < today }
        val weeks = ArrayList<Week>()
        var from = today
        while (from < end) {
            val to = minOf(from + 7 * DAY, end)
            val inWeek = ahead.filter { it.date in from until to }
            // Bills already late are due now: they weigh on the first week.
            val lateOut = if (from == today) overdue.filter { !it.kind.isIn }.sumOf { it.amount } else 0.0
            weeks += Week(from, to,
                coming = inWeek.filter { it.kind.isIn }.sumOf { it.amount },
                going = inWeek.filter { !it.kind.isIn }.sumOf { it.amount } + lateOut)
            from = to
        }
        var cin = 0.0
        var cout = 0.0
        var gapWeek: Week? = null
        var gap = 0.0
        for (w in weeks) {
            cin += w.coming
            cout += w.going
            if (gapWeek == null && pos(cout - cin)) { gapWeek = w; gap = cout - cin }
        }
        return Outlook(ahead, overdue, weeks, weeks.sumOf { it.coming }, weeks.sumOf { it.going }, gapWeek, gap, undated)
    }
}
