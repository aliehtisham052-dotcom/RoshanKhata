package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.R

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

    enum class Kind { CHEQUE, PLAN, BILL, EXPIRING, STOCK, DRAWER, BACKUP, BACKUP_FAILED, BACKUP_OTHER_PHONE, CASHFLOW, UPDATED, SYNC }

    /** One row of the bell: what kind, a title, a quieter line under it, and an optional short tag ("Today", "Late", "Failed"). */
    data class Alert(val kind: Kind, val title: String, val sub: String, val tag: String? = null)

    private const val DAY = 24L * 60 * 60 * 1000

    suspend fun compute(context: Context, dao: KhataDao, money: (Double) -> String): List<Alert> {
        val out = ArrayList<Alert>()
        // The ledger's own alerts come from :shared (LedgerAlerts, 20 Oct), so the
        // computer's bell says the same; the cash-flow gap still comes after backup.
        val conflicts = runCatching { LiveSync.moneyConflicts(KhataDatabase.get(context).syncSql()) }.getOrDefault(0)
        val ledger = LedgerAlerts.compute(AndroidWords(context), dao, money, conflicts)
            .map { a ->
                val kind = when (a.kind) {
                    LedgerAlerts.Kind.CHEQUE -> Kind.CHEQUE
                    LedgerAlerts.Kind.PLAN -> Kind.PLAN
                    LedgerAlerts.Kind.BILL -> Kind.BILL
                    LedgerAlerts.Kind.EXPIRING -> Kind.EXPIRING
                    LedgerAlerts.Kind.STOCK -> Kind.STOCK
                    LedgerAlerts.Kind.DRAWER -> Kind.DRAWER
                    LedgerAlerts.Kind.CASHFLOW -> Kind.CASHFLOW
                    LedgerAlerts.Kind.SYNC -> Kind.SYNC
                }
                Alert(kind, a.title, a.sub, a.tag)
            }
        out += ledger.filter { it.kind != Kind.CASHFLOW }
        val parties = dao.partiesWithBalanceOnce()
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
        // "Never backed up" waits for a first customer (9 Oct): on a new, empty
        // book it lit the bell with a 1 before the owner had written anything.
        else if (last == 0L) { if (parties.isNotEmpty()) out += Alert(Kind.BACKUP, context.getString(R.string.na_backup_never),
            context.getString(R.string.na_backup_sub)) }
        else {
            val days = ((System.currentTimeMillis() - last) / DAY).toInt()
            if (days >= 7) out += Alert(Kind.BACKUP, context.getString(R.string.na_backup, days.toString()),
                context.getString(R.string.na_backup_sub), context.getString(R.string.na_tag_days, days.toString()))
        }
        out += ledger.filter { it.kind == Kind.CASHFLOW }
        // Once per update, last: the app is new, and About says what changed.
        if (AlertNotes.updatePending(context)) out += Alert(Kind.UPDATED,
            context.getString(R.string.na_updated, com.innovation313.roshankhata.BuildConfig.VERSION_NAME),
            context.getString(R.string.na_updated_sub))
        return out
    }
}
