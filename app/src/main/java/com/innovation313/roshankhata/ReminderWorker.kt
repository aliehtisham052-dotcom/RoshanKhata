package com.innovation313.roshankhata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.innovation313.roshankhata.data.BackupReminder
import com.innovation313.roshankhata.data.ChequeStatus
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
        val endOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val expiryWindow = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000

        // Cheques: still pending, written date reached or passed.
        val chequesDue = dao.allChequesForBackup().count {
            !it.isDeleted && it.status == ChequeStatus.PENDING && it.dueDate <= endOfToday
        }
        if (chequesDue > 0) notify(
            ctx, ID_CHEQUES, ChequesActivity::class.java,
            ctx.getString(R.string.notif_cheques_title),
            ctx.getString(R.string.notif_cheques_body, chequesDue)
        )

        // Payment plans: open, with an agreed date that has arrived.
        val plansDue = dao.allPlansForBackup().count {
            !it.isDeleted && !it.isClosed &&
                it.nextDueDate != null && it.nextDueDate <= endOfToday
        }
        if (plansDue > 0) notify(
            ctx, ID_PLANS, PlansActivity::class.java,
            ctx.getString(R.string.notif_plans_title),
            ctx.getString(R.string.notif_plans_body, plansDue)
        )

        // Expiring stock: items with a recorded expiry inside the 60-day window
        // (or already past it), on bills that still exist.
        val liveBills = dao.allBillsForBackup()
            .filter { !it.isDeleted }.map { it.id }.toSet()
        val expiringCount = dao.allBillItemsForBackup().count {
            it.billId in liveBills && it.expiryDate != null && it.expiryDate <= expiryWindow
        }
        if (expiringCount > 0) notify(
            ctx, ID_EXPIRY, ExpiringActivity::class.java,
            ctx.getString(R.string.notif_expiry_title),
            ctx.getString(R.string.notif_expiry_body, expiringCount)
        )

        // Backup: same rule the home screen uses — data exists, and a week of
        // silence since the last backup (or never backed up at all).
        val hasData = dao.totalEntryCount() > 0
        if (BackupReminder.isReminderDue(ctx, hasData)) notify(
            ctx, ID_BACKUP, BackupActivity::class.java,
            ctx.getString(R.string.notif_backup_title),
            ctx.getString(R.string.notif_backup_body)
        )

        return Result.success()
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
