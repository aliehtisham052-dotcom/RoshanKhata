package com.innovation313.roshankhata.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
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

    class Worker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            val ctx = applicationContext
            return try {
                // Every gate that matters is already inside autoBackupIfDue —
                // switched on, Drive connected, data worth protecting, enough
                // time passed, and something actually changed. This adds only
                // the interval, because only this knows who is paying.
                DriveBackup.autoBackupIfDue(
                    ctx,
                    KhataDatabase.get(ctx).khataDao(),
                    minIntervalMs = minIntervalFor(ctx)
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
