package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R
import java.util.Calendar

/**
 * What needs the owner's eye today (6 Oct) — worked out on the phone from the
 * ledger itself; no server sends anything. Cheques due today, instalments due
 * or overdue, supplier bills due within three days, stock expiring within a
 * month, a backup older than a week, and a short week in the cash-flow
 * outlook. Since 7 Oct also the two things the daily worker could only say
 * as a phone notification — an automatic backup that failed, a backup found
 * written by another phone — and, once per update, that the app is new.
 * Everything the owner is told lands here, so the bell is the one list.
 * Read-only.
 */
object HomeAlerts {

    enum class Kind { CHEQUE, PLAN, BILL, EXPIRING, BACKUP, BACKUP_FAILED, BACKUP_OTHER_PHONE, CASHFLOW, UPDATED }

    /** One row of the bell: what kind, a title, a quieter line under it, and an optional short tag ("Today", "Late", "Failed"). */
    data class Alert(val kind: Kind, val title: String, val sub: String, val tag: String? = null)

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
                (names[c.partyId] ?: "") + " \u00b7 " + money(c.amount), context.getString(R.string.na_tag_today))
        }
        dao.openPlansOnce().filter { it.nextDueDate != null && it.nextDueDate < t1 && it.totalAmount - it.paidSoFar > 0.004 }.forEach { p ->
            val late = p.nextDueDate!! < t0
            val next = minOf(p.installmentAmount ?: (p.totalAmount - p.paidSoFar), p.totalAmount - p.paidSoFar)
            out += Alert(Kind.PLAN, context.getString(if (late) R.string.na_plan_late else R.string.na_plan_due),
                p.partyName + " \u00b7 " + money(next), context.getString(if (late) R.string.na_tag_late else R.string.na_tag_today))
        }
        val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
        val (items, _) = CashFlow.items(emptyList(), emptyList(), emptyList(), dao.cashFlowBillsOnce(), owed)
        items.filter { it.kind == CashFlow.Kind.BILL && it.date < t0 + 3 * DAY }.forEach { b ->
            out += Alert(Kind.BILL, context.getString(R.string.na_bill_due, DateWords.format("d MMM", b.date)),
                b.name + " \u00b7 " + money(b.amount))
        }
        val expiring = dao.expiringBatchesOnce(t0 + 30 * DAY).size
        if (expiring > 0) out += Alert(Kind.EXPIRING, context.getString(R.string.na_expiring, expiring.toString()), "")
        // Backup, in order of urgency: another phone holds it, the automatic
        // one failed, or it is simply old. Each clears itself the moment a
        // newer backup exists, because the fact it reports is then past.
        val last = BackupReminder.lastBackupAt(context)
        val otherAt = AlertNotes.otherPhoneAt(context)
        val failedAt = AlertNotes.backupFailedAt(context)
        if (otherAt > last) out += Alert(Kind.BACKUP_OTHER_PHONE, context.getString(R.string.notif_backup_other_phone_title),
            context.getString(R.string.na_backup_other_phone_sub))
        else if (failedAt > last) out += Alert(Kind.BACKUP_FAILED, context.getString(R.string.notif_backup_failed_title),
            context.getString(R.string.na_backup_failed_sub), context.getString(R.string.na_tag_failed))
        else if (last == 0L) out += Alert(Kind.BACKUP, context.getString(R.string.na_backup_never),
            context.getString(R.string.na_backup_sub))
        else {
            val days = ((System.currentTimeMillis() - last) / DAY).toInt()
            if (days >= 7) out += Alert(Kind.BACKUP, context.getString(R.string.na_backup, days.toString()),
                context.getString(R.string.na_backup_sub), context.getString(R.string.na_tag_days, days.toString()))
        }
        val plans = dao.openPlansOnce().map { CashFlow.Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate) }
        val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }
            .map { CashFlow.Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived) }
        val (all, undated) = CashFlow.items(plans, cheques, emptyList(), dao.cashFlowBillsOnce(), owed)
        val o = CashFlow.outlook(all, undated, t0, 30)
        o.gapWeek?.let { out += Alert(Kind.CASHFLOW, context.getString(R.string.na_gap, DateWords.format("d MMM", it.from), money(o.gap)), "") }
        // Once per update, last: the app is new, and About says what changed.
        if (AlertNotes.updatePending(context)) out += Alert(Kind.UPDATED,
            context.getString(R.string.na_updated, com.innovation313.roshankhata.BuildConfig.VERSION_NAME),
            context.getString(R.string.na_updated_sub))
        return out
    }
}
