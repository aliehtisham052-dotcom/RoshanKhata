package com.innovation313.roshankhata

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
import com.innovation313.roshankhata.data.DriveBackup
import com.innovation313.roshankhata.data.KhataDao
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
        val ctx = applicationContext
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            // The owner said no (or hasn't said yes yet). Respect it silently.
            return Result.success()
        }
        ensureChannel(ctx)

        val dao = KhataDatabase.get(ctx).khataDao()

        // Automatic backup first: if it is enabled, connected, due, and the
        // ledger has changed, this uploads silently. Success is deliberately
        // quiet — the backup screen shows the new "last backup" time; there is
        // no notification for a routine save. Only a genuine failure is worth
        // interrupting the owner, and a due-but-failed backup also asks the
        // system to retry with backoff.
        var autoBackupFailed = false
        when (DriveBackup.autoBackupIfDue(ctx, dao)) {
            is DriveBackup.AutoResult.Failed -> {
                autoBackupFailed = true
                notify(
                    ctx, ID_BACKUP, BackupActivity::class.java,
                    ctx.getString(R.string.notif_backup_failed_title),
                    ctx.getString(R.string.notif_backup_failed_body)
                )
            }
            else -> Unit // Skipped or BackedUp — stay silent.
        }

        val endOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val expiryWindow = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000

        // The shop that is open: its own screens can show every one of these,
        // so each notification lands on the list it is about.
        val here = countDue(dao, endOfToday, expiryWindow)
        if (here.cheques > 0) notify(
            ctx, ID_CHEQUES, ChequesActivity::class.java,
            ctx.getString(R.string.notif_cheques_title),
            ctx.getString(R.string.notif_cheques_body, here.cheques)
        )
        if (here.plans > 0) notify(
            ctx, ID_PLANS, PlansActivity::class.java,
            ctx.getString(R.string.notif_plans_title),
            ctx.getString(R.string.notif_plans_body, here.plans)
        )
        if (here.expiring > 0) notify(
            ctx, ID_EXPIRY, ExpiringActivity::class.java,
            ctx.getString(R.string.notif_expiry_title),
            ctx.getString(R.string.notif_expiry_body, here.expiring)
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
        val openId = Businesses.active(ctx).id
        for (biz in Businesses.list(ctx)) {
            if (biz.id == openId) continue
            // A shop whose file cannot be opened or read (a corrupt file, a
            // migration that is not written yet) must not take down the sweep
            // for the shops that CAN be read.
            var due: Due? = null
            var other: KhataDatabase? = null
            try {
                other = KhataDatabase.openOther(ctx, biz)
                if (other != null) due = countDue(other.khataDao(), endOfToday, expiryWindow)
            } catch (_: Exception) {
                due = null
            } finally {
                other?.close()
            }
            if (due == null) continue

            val shop = Businesses.displayName(ctx, biz)
                ?: ctx.getString(R.string.business_numbered, biz.id)
            if (due.cheques > 0) notify(
                ctx, ID_CHEQUES_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                ctx.getString(R.string.notif_cheques_title),
                ctx.getString(R.string.notif_cheques_body_named, shop, due.cheques)
            )
            if (due.plans > 0) notify(
                ctx, ID_PLANS_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                ctx.getString(R.string.notif_plans_title),
                ctx.getString(R.string.notif_plans_body_named, shop, due.plans)
            )
            if (due.expiring > 0) notify(
                ctx, ID_EXPIRY_BUSINESS + biz.id.toInt(), BusinessSwitchActivity::class.java,
                ctx.getString(R.string.notif_expiry_title),
                ctx.getString(R.string.notif_expiry_body_named, shop, due.expiring)
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
                ctx, ID_BACKUP, BackupActivity::class.java,
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
                    ?: ctx.getString(R.string.business_numbered, biz.id)
                notify(
                    ctx, ID_BACKUP_BUSINESS + biz.id.toInt(), BackupActivity::class.java,
                    ctx.getString(R.string.notif_backup_title),
                    ctx.getString(R.string.notif_backup_body_named, shop)
                )
            }
        }

        return Result.success()
    }

    /** What one shop's book owes the owner's attention today. */
    private data class Due(val cheques: Int, val plans: Int, val expiring: Int)

    /**
     * The same three questions asked of ANY shop's book, open or not.
     *
     * Pulled out of doWork so the closed shops are judged by exactly the rule
     * the open one is, rather than a second copy that drifts from it.
     */
    private suspend fun countDue(dao: KhataDao, endOfToday: Long, expiryWindow: Long): Due {
        // Cheques: still pending, written date reached or passed.
        val cheques = dao.allChequesForBackup().count {
            !it.isDeleted && it.status == ChequeStatus.PENDING && it.dueDate <= endOfToday
        }
        // Payment plans: open, with an agreed date that has arrived.
        val plans = dao.allPlansForBackup().count {
            !it.isDeleted && !it.isClosed &&
                it.nextDueDate != null && it.nextDueDate <= endOfToday
        }
        // Expiring stock: items with a recorded expiry inside the 60-day
        // window (or already past it), on bills that still exist.
        val liveBills = dao.allBillsForBackup()
            .filter { !it.isDeleted }.map { it.id }.toSet()
        val expiring = dao.allBillItemsForBackup().count {
            it.billId in liveBills && it.expiryDate != null && it.expiryDate <= expiryWindow
        }
        return Due(cheques, plans, expiring)
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
            ?: ctx.getString(R.string.business_numbered, openId)
        return ctx.getString(R.string.notif_backup_body_named, shop)
    }

    private fun notify(
        ctx: Context, id: Int, target: Class<*>, title: String, body: String
    ) {
        val intent = Intent(ctx, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
            // Needs a network: the daily sweep now also carries automatic
            // backup, which uploads to Drive. A connection is required for that
            // to mean anything; the reminder checks are cheap enough to ride
            // the same wake-up. WorkManager holds the run until the device is
            // online, and if backup still fails once running, the worker asks
            // for a backoff retry rather than giving up.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

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
