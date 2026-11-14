package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.refresh

import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.openOther

import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.string

import com.innovation313.roshankhata.data.Digits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.innovation313.roshankhata.data.BackupReminder
import com.innovation313.roshankhata.data.Businesses
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.DriveBackup
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.AlertNotes
import com.innovation313.roshankhata.data.KhataDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * A once-a-day sweep over what the owner already trusts the app to know:
 * pending cheques past their written date, payment plans whose agreed day has
 * arrived, stock inside its 60-day expiry window, and the week-old backup
 * nudge. Everything is computed on the phone from the local database — no
 * server, no network, and nothing fires unless there is genuinely something
 * to say. One notification per topic, never one per item, so a busy week
 * cannot turn the shade into spam.
 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The app's language, not the phone's: on Android 12 and lower the
        // application context keeps the phone's (23 Oct review, R10).
        val ctx = com.innovation313.roshankhata.ui.AppLanguage.context(applicationContext)
        Currency.refresh(ctx) // notifications print money; wear the shop's sign
        com.innovation313.roshankhata.data.ShopCalendar.refresh(ctx)
        // A helper's read-only phone holds a copy, not the book. Reminders
        // are the owner's to send, from the phone that keeps the book.
        if (com.innovation313.roshankhata.data.ViewerMode.isOn(ctx)) return Result.success()
        // Notifications off is the owner's "no" to being told things — not to the automatic backup
        // (21 Oct 2026, the critical review: with notifications off, the Drive backup never ran).
        val canNotify = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        if (canNotify) ensureChannel(ctx)

        // The shop, then its book: the backup and its bell notes are this shop's (23 Oct 2026, the review).
        val businessId = com.innovation313.roshankhata.data.Businesses.active(ctx).id
        val dao = KhataDatabase.get(ctx).khataDao()

        // Automatic backup first: if it is enabled, connected, due, and the
        // ledger has changed, this uploads silently. Success is deliberately
        // quiet — the backup screen shows the new "last backup" time; there is
        // no notification for a routine save. Only a genuine failure is worth
        // interrupting the owner, and a due-but-failed backup also asks the
        // system to retry with backoff.
        var autoBackupFailed = false
        // Offline: no backup this run, and no word of one (the change-triggered backup waits for a network itself).
        // A backup that throws — the phone's storage full, the commonest real failure — is a failed backup, said
        // as one; it no longer ends the sweep before the cheque, plan and expiry notices (3 Nov 2026 audit, M18).
        val autoResult = if (!com.innovation313.roshankhata.data.AutoBackup.online(ctx)) DriveBackup.AutoResult.Skipped
            else try { DriveBackup.autoBackupIfDue(ctx, dao, businessId = businessId) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { DriveBackup.AutoResult.Failed(e) }
        when (val auto = autoResult) {
            is DriveBackup.AutoResult.Failed -> {
                autoBackupFailed = true
                AlertNotes.backupFailed(ctx, businessId)
                if (canNotify) notify(
                    ctx, ID_BACKUP, AlertsActivity::class.java,
                    ctx.getString(R.string.notif_backup_failed_title),
                    ctx.getString(R.string.notif_backup_failed_body)
                )
            }
            is DriveBackup.AutoResult.OtherPhone -> {
                // Not a failure to retry: another phone wrote this shop's
                // backup last, and only the owner may replace it. Said once
                // per occurrence; the backup screen explains and asks.
                if (DriveBackup.firstNoticeOf(ctx, auto.conflict, businessId)) {
                    AlertNotes.otherPhone(ctx, businessId)
                    if (canNotify) notify(
                        ctx, ID_BACKUP, AlertsActivity::class.java,
                        ctx.getString(R.string.notif_backup_other_phone_title),
                        ctx.getString(R.string.notif_backup_other_phone_body)
                    )
                    // Same notification slot as the weekly backup nudge below:
                    // this run has said the more important thing, so the
                    // nudge must not replace it.
                    autoBackupFailed = true
                }
            }
            else -> Unit // Skipped or BackedUp — stay silent.
        }
        // The owner said no to notifications (or has not said yes yet): the rest are notifications.
        if (!canNotify) return Result.success()

        // The shop that is open: its own screens can show every one of these,
        // so each notification lands on the list it is about.
        val here = countDue(dao)
        // A count gone to nothing takes its notice with it (3 Nov 2026 audit, M21): yesterday's "2 cheques" stood.
        // Said by the Cheque reminders automation when it is on, with the amounts and who (the same sentence
        // the Expiry one fixed, M14: the daily sweep and the automation each said it); then the sweep's own
        // line is taken down too, so a notice posted before the owner turned the automation on does not stay.
        val chequesByAutomation = AndroidAutomationSettings(ctx).isOn(com.innovation313.roshankhata.data.Automations.Kind.CHEQUES)
        cancelIfNone(ctx, ID_CHEQUES, if (chequesByAutomation) 0 else here.cheques)
        val expiryByAutomation = AndroidAutomationSettings(ctx).isOn(com.innovation313.roshankhata.data.Automations.Kind.EXPIRY)
        cancelIfNone(ctx, ID_PLANS, here.plans)
        cancelIfNone(ctx, ID_EXPIRY, if (expiryByAutomation) 0 else here.expiring)
        if (here.cheques > 0 && !chequesByAutomation) notify(
            ctx, ID_CHEQUES, AlertsActivity::class.java,
            ctx.getString(R.string.notif_cheques_title),
            Digits.quantity(ctx.resources, R.plurals.notif_cheques_body, here.cheques, here.cheques),
            business = businessId
        )
        if (here.plans > 0) notify(
            ctx, ID_PLANS, AlertsActivity::class.java,
            ctx.getString(R.string.notif_plans_title),
            Digits.quantity(ctx.resources, R.plurals.notif_plans_body, here.plans, here.plans),
            business = businessId
        )
        // Said by the 10:00 Expiry automation when it is on, with the batch and its day (M14: said twice before).
        if (here.expiring > 0 && !expiryByAutomation) notify(
            ctx, ID_EXPIRY, AlertsActivity::class.java,
            ctx.getString(R.string.notif_expiry_title),
            Digits.quantity(ctx.resources, R.plurals.notif_expiry_body, here.expiring, here.expiring),
            business = businessId
        )

        // EVERY OTHER SHOP. Until now the sweep saw only the open one, so a
        // cheque due in a shop the owner had not opened for a week passed in
        // silence — the money was the same money, the book was just closed.
        // Each shop is read through its own short-lived handle
        // (KhataDatabase.openOther) so the singleton the open screens use is
        // never disturbed, and closed again immediately.
        //
        // These notifications name the shop and open the business switcher,
        // not the cheque list: the cheque list can only ever show the shop
        // that is currently open, so sending the owner there would show them
        // the wrong book. Switching first is the step that actually helps.
        // The shop read once, at the start (3 Nov 2026 audit, L18): read again here, a switch during the upload
        // counted the old shop twice and the new one never.
        val openId = businessId
        for (biz in Businesses.list(ctx)) {
            if (biz.id == openId) continue
            // A shop whose file cannot be opened or read (a corrupt file, a
            // migration that is not written yet) must not take down the sweep
            // for the shops that CAN be read.
            var due: Due? = null
            var other: KhataDatabase? = null
            try {
                other = KhataDatabase.openOther(ctx, biz)
                if (other != null) due = countDue(other.khataDao())
            } catch (_: Exception) {
                due = null
            } finally {
                other?.close()
            }
            if (due == null) continue
            cancelIfNone(ctx, ID_CHEQUES_BUSINESS + biz.id.toInt(), due.cheques)
            cancelIfNone(ctx, ID_PLANS_BUSINESS + biz.id.toInt(), due.plans)
            cancelIfNone(ctx, ID_EXPIRY_BUSINESS + biz.id.toInt(), due.expiring)

            val shop = Businesses.displayName(ctx, biz)
                ?: Digits.string(ctx.resources, R.string.business_numbered, biz.id)
            if (due.cheques > 0) notify(
                ctx, ID_CHEQUES_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                ctx.getString(R.string.notif_cheques_title),
                Digits.quantity(ctx.resources, 
                    R.plurals.notif_cheques_body_named, due.cheques, shop, due.cheques
                )
            )
            if (due.plans > 0) notify(
                ctx, ID_PLANS_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                ctx.getString(R.string.notif_plans_title),
                Digits.quantity(ctx.resources, 
                    R.plurals.notif_plans_body_named, due.plans, shop, due.plans
                )
            )
            if (due.expiring > 0) notify(
                ctx, ID_EXPIRY_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                ctx.getString(R.string.notif_expiry_title),
                Digits.quantity(ctx.resources, 
                    R.plurals.notif_expiry_body_named, due.expiring, shop, due.expiring
                )
            )
        }

        // Backup: same rule the home screen uses — data exists, and a week of
        // silence since the last backup (or never backed up at all). Skipped
        // when auto-backup already notified a failure this run, so the owner is
        // not told twice about the same thing; and auto-backup's own success
        // moves the last-backup time, so this naturally goes quiet once the
        // daily upload is doing its job.
        val hasData = dao.totalEntryCount() > 0
        if (!autoBackupFailed) {
            val businesses = Businesses.list(ctx)

            // The open shop keeps the fuller rule, because its book can
            // actually be counted here.
            if (BackupReminder.isReminderDue(ctx, hasData)) notify(
                ctx, ID_BACKUP, AlertsActivity::class.java,
                ctx.getString(R.string.notif_backup_title),
                backupBody(ctx, businesses, openId)
            )

            // Every other shop is asked only for its timestamp — opening each
            // database from a background worker to count entries is not worth
            // the risk. Without this a closed shop could sit unprotected for
            // months and never be mentioned, which is the whole reason the
            // owner asked. Each gets its own notification id so one shop's
            // nudge cannot replace another's.
            for (biz in businesses) {
                if (biz.id == openId) continue
                if (!BackupReminder.isLapsedBackup(ctx, biz.id)) continue
                val shop = Businesses.displayName(ctx, biz)
                    ?: Digits.string(ctx.resources, R.string.business_numbered, biz.id)
                // The switcher, not Backup (3 Nov 2026 audit, M20): Backup backs up the OPEN shop, so the shop
                // named here stayed unprotected while another was backed up.
                notify(
                    ctx, ID_BACKUP_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                    ctx.getString(R.string.notif_backup_title),
                    ctx.getString(R.string.notif_backup_body_named, shop)
                )
            }
        }

        return Result.success()
    }

    /** Takes a notice down when what it counted is gone. */
    private fun cancelIfNone(ctx: Context, id: Int, count: Int) {
        if (count == 0) runCatching { NotificationManagerCompat.from(ctx).cancel(id) }
    }

    /** What one shop's book owes the owner's attention today. */
    private data class Due(val cheques: Int, val plans: Int, val expiring: Int)

    /**
     * The same three questions asked of ANY shop's book, open or not.
     *
     * Pulled out of doWork so the closed shops are judged by exactly the rule
     * the open one is, rather than a second copy that drifts from it.
     */
    private suspend fun countDue(dao: KhataDao): Due {
        // The bell's own rules (LedgerAlerts.dueCounts, 3 Nov 2026 audit, M14): the notice counted every dated bill
        // line as expiring — sold out, written off, years past — and plans with nothing left, so the bell it
        // opened showed none of it.
        val due = com.innovation313.roshankhata.data.LedgerAlerts.dueCounts(dao)
        return Due(due.cheques, due.plans, due.expiring)
    }

    /**
     * The nudge's wording for the open shop.
     *
     * With one shop the plain sentence is right and naming it would be noise.
     * With several it is the only useful part: "your last backup" leaves the
     * owner unable to tell which book is unprotected, which is exactly the
     * confusion reported.
     */
    private fun backupBody(
        ctx: Context,
        businesses: List<Businesses.Business>,
        openId: Long
    ): String {
        if (businesses.size <= 1) return ctx.getString(R.string.notif_backup_body)
        val shop = businesses.firstOrNull { it.id == openId }
            ?.let { Businesses.displayName(ctx, it) }
            ?: Digits.string(ctx.resources, R.string.business_numbered, openId)
        return ctx.getString(R.string.notif_backup_body_named, shop)
    }

    private fun notify(
        ctx: Context, id: Int, target: Class<*>, title: String, body: String,
        /** The shop the notice is about, when it opens that shop's own screen (3 Nov 2026 audit, M19). */
        business: Long? = null
    ) {
        // Through the gate, never straight to the screen. Opened directly, the
        // screen skipped App Lock (Backup, with Erase all data, opened with no
        // fingerprint) and stood alone in its task, so Back closed the app.
        // The gate checks the lock, the home screen opens, and the screen the
        // reminder is about opens on top of it: Back lands on the home screen.
        // Not CLEAR_TASK (3 Nov 2026 audit, M16): it wiped a half-rung ticket or a half-typed entry under it.
        val intent = Intent(ctx, GateActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, target.name)
            .apply { if (business != null) putExtra(MainActivity.EXTRA_OPEN_BUSINESS, business) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            // A shop's name and counts only behind the lock (3 Nov 2026 audit, M22): its public face is the title.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(ctx, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_notify)
                    .setColor(ContextCompat.getColor(ctx, R.color.brand_green)).setContentTitle(title).build()
            )
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    companion object {
        private const val CHANNEL_ID = "reminders"
        private const val WORK_NAME = "daily_reminders"
        private const val ID_CHEQUES = 1001
        private const val ID_PLANS = 1002
        private const val ID_EXPIRY = 1003
        private const val ID_BACKUP = 1004

        /**
         * Base for the per-business backup nudges. Well clear of the fixed ids
         * above so that adding a shop can never overwrite the cheque or expiry
         * notification — the id is the base plus the business's own number.
         */
        private const val ID_BACKUP_BUSINESS = 1100

        /**
         * The same idea for the other three topics, one band each so a shop's
         * cheque nudge can never overwrite its own expiry nudge, nor another
         * shop's. The gap of 1000 leaves far more room than any owner will
         * ever need shops.
         */
        private const val ID_CHEQUES_BUSINESS = 2000
        private const val ID_PLANS_BUSINESS = 3000
        private const val ID_EXPIRY_BUSINESS = 4000

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = ctx.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        ctx.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = ctx.getString(R.string.notif_channel_desc)
                    }
                )
            }
        }

        /** Idempotent: safe to call on every launch. */
        fun schedule(ctx: Context) {
            // No network needed (3 Nov 2026 audit, M17): the sweep's cheque, plan and expiry notices are read from
            // the book on the phone, and a phone offline for days heard none of them. Its backup checks for a
            // network itself ([AutoBackup.online]) and is skipped without one.
            val constraints = Constraints.Builder().build()

            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.HOURS
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
    }
}
