package com.innovation313.roshankhata

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.innovation313.roshankhata.data.WarisAndroid
import java.util.concurrent.TimeUnit

/**
 * Waris ki Chaabi's weekly run (4 Nov 2026), unique work "waris", only with a network: for the open shop's book,
 * when it has a vault — the status from every relay (a request said as a notification, a refusal a relay missed sent
 * again), a new locked copy when the book changed since the last or a week went by, and every write a relay missed
 * done again; for the books this phone is heir to, "still here" at most once a day. Home also checks every six
 * hours while the app is used ([WarisAndroid.checkIfDue]). A helper's read-only phone does nothing.
 */
class WarisWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { WarisAndroid.weekly(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "waris"

        /** Idempotent: safe on every launch. */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<WarisWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
