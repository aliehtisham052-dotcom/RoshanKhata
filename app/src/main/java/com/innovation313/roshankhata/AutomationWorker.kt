package com.innovation313.roshankhata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.Automations
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.ReminderLog
import com.innovation313.roshankhata.data.ShopCalendar
import com.innovation313.roshankhata.data.ViewerMode
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.refresh
import com.innovation313.roshankhata.ui.Format
import java.util.concurrent.TimeUnit

/**
 * The automations on the phone (31 Oct 2026, [Automations]): once an hour, whatever is due by the owner's
 * hours is said as a notification whose tap opens the place to act — Follow-up with its reminder queue
 * started, Close the day, a low-stock order draft, last month's Khata Report, Expiry. Hourly so each comes
 * near its hour without asking Android for exact alarms; [Automations.due] says each at most once a day (the
 * month's once a month), late rather than twice. No network: everything is read from the open shop's book.
 * A helper's read-only phone says nothing (the book is the owner's, as [ReminderWorker] keeps it).
 */
class AutomationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = com.innovation313.roshankhata.ui.AppLanguage.context(applicationContext)
        if (ViewerMode.isOn(ctx)) return Result.success()
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return Result.success()
        Currency.refresh(ctx)
        ShopCalendar.refresh(ctx)
        ensureChannel(ctx)
        val dao = KhataDatabase.get(ctx).khataDao()
        val notices = runCatching {
            Automations.noticesDue(
                dao, AndroidAutomationSettings(ctx), AndroidWords(ctx),
                money = { Format.money(it) },
                date = { DateWords.format("d MMM", it) },
                monthName = { DateWords.format("MMMM yyyy", it) },
                remindedToday = ReminderLog.openedToday(ctx)
            )
        }.getOrDefault(emptyList())
        notices.forEach { post(ctx, it) }
        return Result.success()
    }

    private fun post(ctx: Context, n: Automations.Notice) {
        val args = Bundle()
        val target = when (n.kind) {
            Automations.Kind.COLLECT -> { args.putBoolean(FollowUpActivity.EXTRA_START_QUEUE, true); FollowUpActivity::class.java }
            Automations.Kind.DAY_END -> { args.putBoolean(CashbookActivity.EXTRA_CLOSE_DAY, true); CashbookActivity::class.java }
            Automations.Kind.REORDER -> { args.putBoolean(OrdersActivity.EXTRA_START_LOW, true); OrdersActivity::class.java }
            Automations.Kind.MONTH -> {
                // The month the notice is about, fixed now: a tap next week still opens that month.
                val (from, to) = Automations.lastMonth(System.currentTimeMillis())
                args.putLong(LedgerReportActivity.EXTRA_FROM, from); args.putLong(LedgerReportActivity.EXTRA_TO, to)
                LedgerReportActivity::class.java
            }
            Automations.Kind.EXPIRY -> ExpiringActivity::class.java
        }
        // Through the gate, as every reminder goes: the lock first, Home under it, the place on top.
        val intent = Intent(ctx, GateActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, target.name)
            .putExtra(MainActivity.EXTRA_OPEN_ARGS, args)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val id = ID_BASE + n.kind.ordinal
        val pending = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission taken back between the check and the post.
        }
    }

    companion object {
        private const val CHANNEL_ID = "automations"
        private const val WORK_NAME = "automations_hourly"
        /** Clear of ReminderWorker's bands (1001-4999). */
        private const val ID_BASE = 6001

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, ctx.getString(R.string.auto_title), NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = ctx.getString(R.string.auto_help) }
                )
            }
        }

        /** Idempotent: safe on every launch. */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<AutomationWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** The automations' switches, hours and "last said" marks on this phone ([Automations.Settings]). */
class AndroidAutomationSettings(context: Context) : Automations.Settings {
    private val prefs = context.applicationContext.getSharedPreferences("automations", Context.MODE_PRIVATE)
    override fun isOn(kind: Automations.Kind) = prefs.getBoolean("on_${kind.key}", true)
    override fun hour(kind: Automations.Kind) = prefs.getInt("hour_${kind.key}", kind.defaultHour)
    override fun lastSaid(kind: Automations.Kind): Long? =
        if (prefs.contains("said_${kind.key}")) prefs.getLong("said_${kind.key}", 0L) else null
    override fun setSaid(kind: Automations.Kind, stamp: Long) { prefs.edit().putLong("said_${kind.key}", stamp).apply() }
    fun setOn(kind: Automations.Kind, on: Boolean) { prefs.edit().putBoolean("on_${kind.key}", on).apply() }
    fun setHour(kind: Automations.Kind, hour: Int) { prefs.edit().putInt("hour_${kind.key}", hour.coerceIn(0, 23)).apply() }
}
