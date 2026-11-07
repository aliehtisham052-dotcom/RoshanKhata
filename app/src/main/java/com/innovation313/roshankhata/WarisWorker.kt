package com.innovation313.roshankhata

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.innovation313.roshankhata.data.WarisAndroid
import java.util.concurrent.TimeUnit

/**
 * Waris ki Chaabi's background run (4 Nov 2026), unique work "waris", only with a network: for every shop on the phone
 * that keeps a vault — the status from every relay (a request said as a notification, a refusal a relay missed sent
 * again), every write a relay missed done again, and a new locked copy when a week went by; for the books this phone
 * is heir to, "still here" at most once a day. Home also checks every six hours while the app is used
 * ([WarisAndroid.checkIfDue]).
 *
 * Twice a day since the security review (7 Nov 2026, W6): once a week was no shorter than the shortest wait (7 days),
 * so the owner could hear of a request after the book had opened; and every shop and the phone's own books behind a
 * read-only copy are read, not only the open shop. A refusal some relay did not take is asked again at once by a
 * one-time run of its own ("waris_refuse", [retryRefusal]), backing off until every relay has it.
 */
class WarisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_REFUSE, false)) {
            val owed = runCatching { WarisAndroid.retryRefusals(applicationContext) }.getOrDefault(true)
            return if (owed) Result.retry() else Result.success()
        }
        runCatching { WarisAndroid.daily(applicationContext) }
        return Result.success()
    }

    /**
     * Asked only of the refusal's expedited run on Android 11 and older, where WorkManager runs it as a foreground
     * service and shows this while it works.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val ctx = applicationContext
        WarisAndroid.ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, WarisAndroid.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(ctx.getString(R.string.waris_title))
            .setContentText(ctx.getString(R.string.waris_working))
            .setOngoing(true)
            .build()
        return ForegroundInfo(FOREGROUND_ID, n)
    }

    companion object {
        private const val WORK_NAME = "waris"
        private const val REFUSE_NAME = "waris_refuse"
        private const val KEY_REFUSE = "refuse"
        /** Below WarisAndroid's notices (7300 and up), clear of ReminderWorker's and the automations' bands. */
        private const val FOREGROUND_ID = 7290

        private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Idempotent: safe on every launch (UPDATE moves an install's weekly run to twice a day). */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<WarisWorker>(12, TimeUnit.HOURS)
                .setConstraints(online())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * A refusal some relay did not take (W6): asked again as soon as there is a network, then backing off (30 s,
         * doubling, as WorkManager allows) until every relay has it. A new refusal starts it afresh ([restart]); Home
         * only makes sure one is waiting, never cutting one short.
         */
        fun retryRefusal(ctx: Context, restart: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<WarisWorker>()
                .setInputData(workDataOf(KEY_REFUSE to true))
                .setConstraints(online())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                REFUSE_NAME, if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
