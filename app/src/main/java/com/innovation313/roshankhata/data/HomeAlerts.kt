package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R
import java.util.Calendar

/**
 * What needs the owner's eye today (6 Oct) — worked out on the phone from the
 * ledger itself; no server sends anything. Cheques due today, instalments due
 * or overdue, supplier bills due within three days, stock expiring within a
 * month, a backup older than a week, and a short week in the cash-flow
 * outlook. Read-only.
 */
object HomeAlerts {

    enum class Kind { CHEQUE, PLAN, BILL, EXPIRING, BACKUP, CASHFLOW }

    data class Alert(val kind: Kind, val title: String, val sub: String)

    private const val DAY = 24L * 60 * 60 * 1000

    private fun today(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    suspend fun compute(context: Context, dao: KhataDao, money: (Double) -> String): List<Alert> {
        val out = ArrayList<Alert>()
        val t0 = today()
        val t1 = t0 + DAY
        val parties = dao.partiesWithBalanceOnce()
        val names = parties.associate { it.id to it.name }

        dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && it.dueDate in t0 until t1 }.forEach { c ->
            out += Alert(Kind.CHEQUE,
                context.getString(if (c.isReceived) R.string.na_cheque_in else R.string.na_cheque_out),
                (names[c.partyId] ?: "") + " \u00b7 " + money(c.amount))
        }
        dao.openPlansOnce().filter { it.nextDueDate != null && it.nextDueDate < t1 && it.totalAmount - it.paidSoFar > 0.004 }.forEach { p ->
            val late = p.nextDueDate!! < t0
            val next = minOf(p.installmentAmount ?: (p.totalAmount - p.paidSoFar), p.totalAmount - p.paidSoFar)
            out += Alert(Kind.PLAN, context.getString(if (late) R.string.na_plan_late else R.string.na_plan_due),
                p.partyName + " \u00b7 " + money(next))
        }
        val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
        val (items, _) = CashFlow.items(emptyList(), emptyList(), emptyList(), dao.cashFlowBillsOnce(), owed)
        items.filter { it.kind == CashFlow.Kind.BILL && it.date < t0 + 3 * DAY }.forEach { b ->
            out += Alert(Kind.BILL, context.getString(R.string.na_bill_due, DateWords.format("d MMM", b.date)),
                b.name + " \u00b7 " + money(b.amount))
        }
        val expiring = dao.expiringBatchesOnce(t0 + 30 * DAY).size
        if (expiring > 0) out += Alert(Kind.EXPIRING, context.getString(R.string.na_expiring, expiring.toString()), "")
        val last = BackupReminder.lastBackupAt(context)
        if (last == 0L) out += Alert(Kind.BACKUP, context.getString(R.string.na_backup_never), "")
        else {
            val days = ((System.currentTimeMillis() - last) / DAY).toInt()
            if (days >= 7) out += Alert(Kind.BACKUP, context.getString(R.string.na_backup, days.toString()), "")
        }
        val plans = dao.openPlansOnce().map { CashFlow.Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate) }
        val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }
            .map { CashFlow.Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived) }
        val (all, undated) = CashFlow.items(plans, cheques, emptyList(), dao.cashFlowBillsOnce(), owed)
        val o = CashFlow.outlook(all, undated, t0, 30)
        o.gapWeek?.let { out += Alert(Kind.CASHFLOW, context.getString(R.string.na_gap, DateWords.format("d MMM", it.from), money(o.gap)), "") }
        return out
    }
}
