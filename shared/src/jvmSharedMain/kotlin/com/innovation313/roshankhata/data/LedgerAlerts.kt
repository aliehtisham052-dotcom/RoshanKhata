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

    enum class Kind { CHEQUE, PLAN, BILL, EXPIRING, STOCK, DRAWER, CASHFLOW, SYNC }

    /** One row of the bell: what kind, a title, a quieter line under it, and an optional short tag ("Today", "Late"). */
    data class Alert(val kind: Kind, val title: String, val sub: String, val tag: String? = null)

    private const val DAY = 24L * 60 * 60 * 1000

    private fun today(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** The three counts the phone's daily notice says, by this bell's own rules (3 Nov 2026 audit, M14). */
    data class Due(val cheques: Int, val plans: Int, val expiring: Int)

    /**
     * What the daily notice counts, by the rules [compute] shows (3 Nov 2026 audit, M14): the notice had its own —
     * every dated bill line as "expiring", sold out or written off, and plans with nothing left to pay — so it
     * named things the bell it opens did not show. Any shop's book, open or not.
     */
    suspend fun dueCounts(dao: KhataDao): Due {
        val t0 = today()
        val t1 = t0 + DAY
        // Cheques by the one rule ([ChequesDue]) the bell and the Cheque reminders automation also use.
        val endToday = ChequesDue.endOfToday(System.currentTimeMillis())
        val cheques = dao.pendingChequesOnce().count { ChequesDue.isDue(it, endToday) }
        val plans = dao.openPlansOnce().count { (it.nextDueDate ?: Long.MAX_VALUE) < t1 && it.totalAmount - it.paidSoFar > 0.004 }
        val low = runCatching { Stock.of(dao) }.getOrDefault(emptyList())
        val expiring = runCatching { Fefo.expiring(dao, t0 + ExpiryWindow.WARN_MS, low.associate { it.productId to it.onHand }).size }.getOrDefault(0)
        return Due(cheques, plans, expiring)
    }

    /** In the phone's order: cheques, plans, bills, expiring stock, then the cash-flow gap last. */
    /**
     * [syncConflicts]: rows carrying money that both devices changed at once (LiveSync.moneyConflicts) — said
     * here, not only in the Live sync window, because the amount kept may not be the one the owner meant
     * (22 Oct 2026, the review).
     */
    suspend fun compute(words: Words, dao: KhataDao, money: (Double) -> String, syncConflicts: Int = 0, syncOff: Boolean = false): List<Alert> {
        val out = ArrayList<Alert>()
        // Live sync ended by a restore or an older copy (LiveSync.OFF_RESTORED): said in the bell on both devices,
        // not only inside the Live sync screen (23 Oct 2026, the second review).
        if (syncOff) out += Alert(Kind.SYNC, words.text(W.na_sync_off), words.text(W.na_sync_off_sub))
        if (syncConflicts > 0) out += Alert(Kind.SYNC, words.text(W.na_sync_conflict, syncConflicts.toString()), words.text(W.na_sync_conflict_sub))
        val t0 = today()
        val t1 = t0 + DAY
        val parties = dao.partiesWithBalanceOnce()
        val names = parties.associate { it.id to it.name }

        // Due today, or past its day and still pending (3 Nov 2026 audit, M14: a cheque one day late left the bell
        // while it was still to be banked).
        // The rule is [ChequesDue]'s, shared with the Cheque reminders automation and the daily count (14 Nov 2026).
        val now = System.currentTimeMillis()
        val endToday = ChequesDue.endOfToday(now)
        dao.pendingChequesOnce().filter { ChequesDue.isDue(it, endToday) }.forEach { c ->
            out += Alert(Kind.CHEQUE,
                words.text(if (c.isReceived) W.na_cheque_in else W.na_cheque_out),
                (names[c.partyId] ?: "") + " · " + money(c.amount), words.text(if (ChequesDue.isLate(c, now)) W.na_tag_late else W.na_tag_today))
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
        // The shelf once, for the expiring batches and the low stock below.
        val low = runCatching { Stock.of(dao) }.getOrDefault(emptyList())
        // Batches that still hold something, by the one rule (Marhala 3, 25 Oct 2026: every dated batch was
        // counted, sold out or written off, so the row never went away), within the one window every page warns
        // in (ExpiryWindow, 60 days: Marhala 4, the audit — the bell said 30 where Bills and Expiry said 60).
        val expiring = runCatching { Fefo.expiring(dao, t0 + ExpiryWindow.WARN_MS, low.associate { it.productId to it.onHand }).size }.getOrDefault(0)
        if (expiring > 0) out += Alert(Kind.EXPIRING, words.text(W.na_expiring, expiring.toString()), "")
        // Low stock (POS Phase 2): products at or under the level the owner set, where the shelf is known.
        val levels = dao.productsOnce().filter { it.lowStockLevel != null }.associate { it.id to it.lowStockLevel!! }
        val lowRows = low.filter { s -> levels[s.productId]?.let { lvl -> s.onHand?.let { it <= lvl } } == true }
        if (lowRows.isNotEmpty()) out += Alert(Kind.STOCK, words.text(W.na_low_stock, lowRows.size.toString()),
            lowRows.take(3).joinToString(" · ") { it.name })

        // Yesterday's drawer (POS Phase 4): money moved yesterday and no count was made for it.
        val yesterday = DayCloseMath.dayOf(t0) - 1
        val last = dao.lastDayClose()
        // L81 (8 Nov 2026 review): a khata-only shop has no counter tickets; cash that moved counts as well.
        val yesterdayMoved = dao.doneSaleCount(t0 - DAY, t0) > 0 ||
            dao.cashbookCashBetween(true, t0 - DAY, t0) + dao.cashbookCashBetween(false, t0 - DAY, t0) +
            dao.khataCashBetween(true, t0 - DAY, t0) + dao.khataCashBetween(false, t0 - DAY, t0) > 0.0
        if ((last == null || last.day < yesterday) && yesterdayMoved) {
            out += Alert(Kind.DRAWER, words.text(W.z_yesterday_open), "")
        }

        val plans = dao.openPlansOnce().map { CashFlow.Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate) }
        val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }
            .map { CashFlow.Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived) }
        // Promised payments count, as on the Cash flow page the row opens (3 Nov 2026 audit, L13: the bell said
        // "short" for a week the page showed covered by a promise).
        val balance = parties.associate { it.id to it.balance }
        val harvests = dao.cashFlowHarvestsOnce().map { it.copy(balance = balance[it.partyId] ?: 0.0) }
        val (all, undated) = CashFlow.items(plans, cheques, harvests, dao.cashFlowBillsOnce(), owed)
        val o = CashFlow.outlook(all, undated, t0, 30)
        o.gapWeek?.let { out += Alert(Kind.CASHFLOW, words.text(W.na_gap, DateWords.format("d MMM", it.from), money(o.gap)), "") }
        return out
    }
}
