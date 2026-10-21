package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.util.Calendar

/**
 * The part of the bell that comes from the ledger alone (20 Oct, moved out of
 * the phone's HomeAlerts so the computer's bell says the same): cheques due
 * today, instalments due or overdue, supplier bills due within three days,
 * stock expiring within a month, and a short week in the cash-flow outlook.
 * The phone adds what only it knows (its backups, an update) around these.
 * Read-only.
 */
object LedgerAlerts {

    enum class Kind { CHEQUE, PLAN, BILL, EXPIRING, STOCK, CASHFLOW }

    /** One row of the bell: what kind, a title, a quieter line under it, and an optional short tag ("Today", "Late"). */
    data class Alert(val kind: Kind, val title: String, val sub: String, val tag: String? = null)

    private const val DAY = 24L * 60 * 60 * 1000

    private fun today(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** In the phone's order: cheques, plans, bills, expiring stock, then the cash-flow gap last. */
    suspend fun compute(words: Words, dao: KhataDao, money: (Double) -> String): List<Alert> {
        val out = ArrayList<Alert>()
        val t0 = today()
        val t1 = t0 + DAY
        val parties = dao.partiesWithBalanceOnce()
        val names = parties.associate { it.id to it.name }

        dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && it.dueDate in t0 until t1 }.forEach { c ->
            out += Alert(Kind.CHEQUE,
                words.text(if (c.isReceived) W.na_cheque_in else W.na_cheque_out),
                (names[c.partyId] ?: "") + " · " + money(c.amount), words.text(W.na_tag_today))
        }
        dao.openPlansOnce().filter { (it.nextDueDate ?: Long.MAX_VALUE) < t1 && it.totalAmount - it.paidSoFar > 0.004 }.forEach { p ->
            val late = p.nextDueDate!! < t0
            val next = minOf(p.installmentAmount ?: (p.totalAmount - p.paidSoFar), p.totalAmount - p.paidSoFar)
            out += Alert(Kind.PLAN, words.text(if (late) W.na_plan_late else W.na_plan_due),
                p.partyName + " · " + money(next), words.text(if (late) W.na_tag_late else W.na_tag_today))
        }
        val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
        val (items, _) = CashFlow.items(emptyList(), emptyList(), emptyList(), dao.cashFlowBillsOnce(), owed)
        items.filter { it.kind == CashFlow.Kind.BILL && it.date < t0 + 3 * DAY }.forEach { b ->
            out += Alert(Kind.BILL, words.text(W.na_bill_due, DateWords.format("d MMM", b.date)),
                b.name + " · " + money(b.amount))
        }
        val expiring = dao.expiringBatchesOnce(t0 + 30 * DAY).size
        if (expiring > 0) out += Alert(Kind.EXPIRING, words.text(W.na_expiring, expiring.toString()), "")
        // Low stock (POS Phase 2): products at or under the level the owner set, where the shelf is known.
        val low = runCatching { Stock.of(dao) }.getOrDefault(emptyList())
        val levels = dao.productsOnce().filter { it.lowStockLevel != null }.associate { it.id to it.lowStockLevel!! }
        val lowRows = low.filter { s -> levels[s.productId]?.let { lvl -> s.onHand?.let { it <= lvl } } == true }
        if (lowRows.isNotEmpty()) out += Alert(Kind.STOCK, words.text(W.na_low_stock, lowRows.size.toString()),
            lowRows.take(3).joinToString(" · ") { it.name })

        val plans = dao.openPlansOnce().map { CashFlow.Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate) }
        val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }
            .map { CashFlow.Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived) }
        val (all, undated) = CashFlow.items(plans, cheques, emptyList(), dao.cashFlowBillsOnce(), owed)
        val o = CashFlow.outlook(all, undated, t0, 30)
        o.gapWeek?.let { out += Alert(Kind.CASHFLOW, words.text(W.na_gap, DateWords.format("d MMM", it.from), money(o.gap)), "") }
        return out
    }
}
