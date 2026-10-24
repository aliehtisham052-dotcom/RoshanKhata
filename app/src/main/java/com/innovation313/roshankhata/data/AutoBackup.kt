package com.innovation313.roshankhata.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Back up when the book changes, not when the clock says so.
 *
 * The daily sweep meant a shop could lose a whole day. A phone left in a
 * rickshaw at eleven, backed up at 8:40, loses everything written in between —
 * and in a ledger that lives only on this phone, that is the entire risk the
 * app exists to manage.
 *
 * The obvious answer, backing up constantly, is worse than it looks. This
 * owner's book exports at roughly 1.8 MB, because the whole ledger goes every
 * time; every half-hour would cost him more than a gigabyte a month of mobile
 * data. He would switch the feature off, and protection that gets switched off
 * protects nothing. So the trigger is the shop's own work, and the pace is set
 * by whose data is paying:
 *
 *  - The moment the app leaves the screen — the owner has written what he came
 *    to write and put the phone down. Not on every entry, which would upload
 *    five times for five sales.
 *  - Only if the ledger actually changed. DriveBackup already keeps a
 *    fingerprint and skips when it matches, so an idle day costs nothing.
 *  - On Wi-Fi, no more than every half hour. On mobile data, no more than
 *    every six hours. Same protection at home or in the shop; a sane bill on
 *    the road.
 *
 * The daily worker stays as the floor, for the day the app is force-stopped
 * and never leaves the screen cleanly.
 *
 * Honest limit, worth saying plainly: WorkManager decides when this actually
 * runs. "Half an hour" means "not sooner than half an hour, and after that when
 * Android allows it". Second-by-second safety is what a sync server buys, and
 * that was deliberately not built.
 */
object AutoBackup {

    private const val WORK_NAME = "backup_after_change"

    /** Wi-Fi, or anything else the system does not charge for by the megabyte. */
    private val UNMETERED_INTERVAL_MS = 30L * 60 * 1000

    /** Mobile data. Six hours keeps a 1.8 MB book at about 220 MB a month. */
    private val METERED_INTERVAL_MS = 6L * 60 * 60 * 1000

    /**
     * Called when the app goes to the background.
     *
     * REPLACE, not KEEP: if the owner opens and closes the app three times in a
     * minute, the last close is the one that matters and the earlier requests
     * are stale. The two-minute delay is the same idea in time — it lets a
     * quick "check something and come back" settle before anything uploads.
     */
    fun onAppBackgrounded(context: Context) {
        val request = OneTimeWorkRequestBuilder<Worker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** The left shop's own job (R21), one per shop: never the open shop's [WORK_NAME]. */
    internal fun leftWorkName(businessId: Long) = WORK_NAME + "_b" + businessId

    private const val KEY_BUSINESS = "business"

    /**
     * The owner switched away from [businessId] (23 Oct 2026, the review, R21; from
     * [Businesses.switchTo]): its Drive backup is asked for as the open shop's is when the app leaves
     * the screen — two minutes on, or once its interval has passed — by a job of its own, keyed by
     * the shop and carrying its id, so it never replaces the open shop's job or another shop's.
     */
    fun afterLeaving(context: Context, businessId: Long) {
        val last = BackupReminder.lastBackupAtOf(context, businessId)
        val due = if (last == 0L) 0L else last + minIntervalFor(context) - System.currentTimeMillis()
        enqueueLeft(context, businessId, maxOf(2L * 60 * 1000, due), ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueLeft(context: Context, businessId: Long, delayMs: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<LeftShopWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putLong(KEY_BUSINESS, businessId).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(leftWorkName(businessId), policy, request)
    }

    /**
     * How long must have passed since the last backup, on the connection this
     * phone has right now. Unknown counts as metered — when in doubt, assume
     * the owner is paying.
     */
    fun minIntervalFor(context: Context): Long {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return METERED_INTERVAL_MS
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return METERED_INTERVAL_MS
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return if (unmetered) UNMETERED_INTERVAL_MS else METERED_INTERVAL_MS
    }

    /**
     * A left shop's backup ([afterLeaving]). Only the shop named in its input is ever read or sent
     * ([DriveBackup.autoBackupLeftShop]); a shop open again is skipped, its own run covers it. Not
     * due yet (the connection changed since the switch): asked again at the time it is due, after
     * this run (APPEND_OR_REPLACE, so this running job is not cancelled by its own follow-up).
     */
    class LeftShopWorker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val ctx = applicationContext
            val businessId = inputData.getLong(KEY_BUSINESS, -1L)
            if (businessId <= 0L || ViewerMode.isOn(ctx)) return Result.success()
            try {
                when (val r = DriveBackup.autoBackupLeftShop(ctx, businessId, minIntervalFor(ctx))) {
                    is DriveBackup.AutoResult.NotYet -> enqueueLeft(
                        ctx, businessId,
                        maxOf(60_000L, r.at - System.currentTimeMillis()),
                        ExistingWorkPolicy.APPEND_OR_REPLACE
                    )
                    // The shop's own bell notes, as the daily sweep keeps them for the open shop; the
                    // owner sees them when he opens that shop.
                    is DriveBackup.AutoResult.Failed -> AlertNotes.backupFailed(ctx, businessId)
                    is DriveBackup.AutoResult.OtherPhone ->
                        if (DriveBackup.firstNoticeOf(ctx, r.conflict, businessId)) AlertNotes.otherPhone(ctx, businessId) else Unit
                    else -> Unit
                }
            } catch (e: Exception) {
                // As the open shop's run: no blind retry; the shop's next switch or opening tries again.
            }
            return Result.success()
        }
    }

    class Worker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val ctx = applicationContext
            // Never from a read-only phone; see DriveBackup.backup.
            if (ViewerMode.isOn(ctx)) return Result.success()
            return try {
                // Every gate that matters is already inside autoBackupIfDue —
                // switched on, Drive connected, data worth protecting, enough
                // time passed, and something actually changed. This adds only
                // the interval, because only this knows who is paying.
                // The shop first, then its book (23 Oct 2026, the review): the two are taken together,
                // and the backup stops if the owner switches shop while it runs.
                val businessId = Businesses.active(ctx).id
                DriveBackup.autoBackupIfDue(
                    ctx,
                    KhataDatabase.get(ctx).khataDao(),
                    minIntervalMs = minIntervalFor(ctx),
                    businessId = businessId
                )
                Result.success()
            } catch (e: Exception) {
                // Never retry blindly: the next time the owner closes the app
                // this runs again anyway, and the daily sweep is still behind
                // it. A failed upload is not worth a battery-draining backoff.
                Result.success()
            }
        }
    }
}
