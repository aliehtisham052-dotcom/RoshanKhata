package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import java.util.TimeZone

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

    /** Today's first moment, by the calendar. */
    private fun today(): Long = DayMath.startOfDay(System.currentTimeMillis())

    /**
     * The days the bell counts by (16 Nov 2026, audit D-05): the calendar's, never a fixed 24 hours — on the day the clocks
     * move a day is 23 or 25 hours, and "today + 24 h" ended today an hour early or late. [t0] is today's first moment.
     */
    internal fun tomorrowStart(t0: Long, tz: TimeZone = TimeZone.getDefault()): Long = DaySpans.nextDayStart(t0, tz)

    /**
     * Yesterday's drawer, as Close the day files it (16 Nov 2026, audit D-05): the working day before the one [now] is in
     * ([DayCloseMath.businessDayOf], 04:00 to 04:00), its number and its moments as `[from, to)` ([DaySpans.workingDay]). The
     * bell said "yesterday's drawer was not counted" at 02:00 for a day whose till was still open, and counted the cash of a
     * fixed 24 hours from midnight, against a close filed by the working day.
     */
    internal fun yesterdaysDrawer(now: Long, tz: TimeZone = TimeZone.getDefault()): Pair<Long, Pair<Long, Long>> {
        val day = DayCloseMath.businessDayOf(now, tz) - 1
        return day to DaySpans.workingDay(day, tz)
    }

    /**
     * A read that may fail without taking the whole bell with it: its answer, or null when it threw — except cancellation, which
     * always goes on up (a cancelled read is no empty read). The caller says the null ([unreadRow]); it never reads it as "none".
     */
    private inline fun <T : Any> readOrNull(block: () -> T): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * The one row that says the shelf could not be read (24 Nov 2026, BC-256): the stock and expiry rows are missing because the
     * read failed, not because nothing is low or expiring. It is a [Kind.STOCK] row (a kind with a screen, so the platforms'
     * exhaustive maps need nothing new) tagged "Failed", in the words the assistant uses for a book it could not read.
     */
    internal fun unreadRow(words: Words): Alert = Alert(Kind.STOCK, words.text(W.hudhud_read_failed), "", words.text(W.na_tag_failed))

    /** The three counts the phone's daily notice says, by this bell's own rules (3 Nov 2026 audit, M14). */
    data class Due(val cheques: Int, val plans: Int, val expiring: Int)

    /**
     * What the daily notice counts, by the rules [compute] shows (3 Nov 2026 audit, M14): the notice had its own —
     * every dated bill line as "expiring", sold out or written off, and plans with nothing left to pay — so it
     * named things the bell it opens did not show. Any shop's book, open or not.
     */
    suspend fun dueCounts(dao: KhataDao): Due {
        val t0 = today()
        val t1 = tomorrowStart(t0)
        // Cheques by the one rule ([ChequesDue]) the bell and the Cheque reminders automation also use.
        val endToday = ChequesDue.endOfToday(System.currentTimeMillis())
        val cheques = dao.pendingChequesOnce().count { ChequesDue.isDue(it, endToday) }
        val plans = dao.openPlansOnce().count { (it.nextDueDate ?: Long.MAX_VALUE) < t1 && it.totalAmount - it.paidSoFar > Money.halfCoin }
        // A shelf that could not be read is no "nothing expiring" (24 Nov 2026, BC-256): the exception goes up, so a daily sweep
        // that cannot count leaves yesterday's notice standing instead of cancelling it for a count of nought.
        val low = Stock.of(dao)
        val expiring = Fefo.expiring(dao, ExpiryWindow.cutoff(System.currentTimeMillis()), low.associate { it.productId to it.onHand }).size
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
        val t1 = tomorrowStart(t0)
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
        dao.openPlansOnce().filter { (it.nextDueDate ?: Long.MAX_VALUE) < t1 && it.totalAmount - it.paidSoFar > Money.halfCoin }.forEach { p ->
            val late = p.nextDueDate!! < t0
            val next = minOf(p.installmentAmount ?: (p.totalAmount - p.paidSoFar), p.totalAmount - p.paidSoFar)
            out += Alert(Kind.PLAN, words.text(if (late) W.na_plan_late else W.na_plan_due),
                p.partyName + " · " + money(next), words.text(if (late) W.na_tag_late else W.na_tag_today))
        }
        val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
        val (items, _) = CashFlow.items(emptyList(), emptyList(), emptyList(), dao.cashFlowBillsOnce(), owed)
        items.filter { it.kind == CashFlow.Kind.BILL && it.date < DaySpans.dayStartAfter(t0, 3) }.forEach { b ->
            out += Alert(Kind.BILL, words.text(W.na_bill_due, DateWords.format("d MMM", b.date)),
                b.name + " · " + money(b.amount))
        }
        // The shelf once, for the expiring batches and the low stock below. A shelf that could not be read is told as that
        // (one row, [unreadRow]) and never as "nothing expiring, nothing low" (24 Nov 2026, BC-256); the bell's other parts,
        // already read above and below, still show.
        val low = readOrNull { Stock.of(dao) }
        // Batches that still hold something, by the one rule (Marhala 3, 25 Oct 2026: every dated batch was
        // counted, sold out or written off, so the row never went away), within the one window every page warns
        // in (ExpiryWindow, 60 days: Marhala 4, the audit — the bell said 30 where Bills and Expiry said 60).
        val expiring = low?.let { shelf -> readOrNull { Fefo.expiring(dao, ExpiryWindow.cutoff(System.currentTimeMillis()), shelf.associate { it.productId to it.onHand }).size } }
        if (low == null || expiring == null) out += unreadRow(words)
        if (expiring != null && expiring > 0) out += Alert(Kind.EXPIRING, words.text(W.na_expiring, expiring.toString()), "")
        // Low stock (POS Phase 2): products at or under the level the owner set, where the shelf is known.
        if (low != null) {
            val levels = dao.productsOnce().filter { it.lowStockLevel != null }.associate { it.id to it.lowStockLevel!! }
            val lowRows = low.filter { s -> Valuation.isLow(s.onHand, levels[s.productId]) }
            if (lowRows.isNotEmpty()) out += Alert(Kind.STOCK, words.text(W.na_low_stock, lowRows.size.toString()),
                lowRows.take(3).joinToString(" · ") { it.name })
        }

        // Yesterday's drawer (POS Phase 4): money moved yesterday and no count was made for it — yesterday's working day, as
        // Close the day files a count, and its moments as [from, to) (the four cash sums through DaySpans' wrappers).
        val (yesterday, span) = yesterdaysDrawer(now)
        val (yFrom, yTo) = span
        val last = dao.lastDayClose()
        // L81 (8 Nov 2026 review): a khata-only shop has no counter tickets; cash that moved counts as well.
        val yesterdayMoved = dao.doneSaleCount(yFrom, yTo) > 0 ||
            dao.cashbookCashOver(true, yFrom, yTo) + dao.cashbookCashOver(false, yFrom, yTo) +
            dao.khataCashOver(true, yFrom, yTo) + dao.khataCashOver(false, yFrom, yTo) > 0.0
        if ((last == null || last.day < yesterday) && yesterdayMoved) {
            out += Alert(Kind.DRAWER, words.text(W.z_yesterday_open), "")
        }

        // The Cash flow page's own loader (BC-285): promised payments count, as on the page the row opens (3 Nov 2026 audit, L13).
        val (all, undated) = CashFlow.load(dao, parties)
        val o = CashFlow.outlook(all, undated, t0, 30)
        o.gapWeek?.let { out += Alert(Kind.CASHFLOW, words.text(W.na_gap, DateWords.format("d MMM", it.from), money(o.gap)), "") }
        return out
    }
}
