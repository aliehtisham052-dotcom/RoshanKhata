package com.innovation313.roshankhata.data

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.GateActivity
import com.innovation313.roshankhata.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Waris ki Chaabi on the phone (4 Nov 2026): the shared [Waris] steps over the open book's settings, the book as the
 * helper's copy writes it (secrets = false), the owner's warning as a notification, and the heir's copy marked so
 * Home says whose it is. The owner's side is never run on a helper's read-only phone.
 */
object WarisAndroid {

    const val CHANNEL_ID = "waris"
    private const val PREFS = "waris"
    private const val KEY_HEIR_COPY = "heir_copy_shop"
    private const val KEY_HEIR_COPY_AT = "heir_copy_at"
    /** Clear of ReminderWorker's bands (1001-4999) and the automations' (6001+). */
    private const val ID_BASE = 7300

    /** The open book's settings (book_settings), the store both sides keep their keys in. */
    class BookStore(private val dao: KhataDao) : Waris.Store {
        override suspend fun get(key: String): String? = dao.settingValue(key)
        override suspend fun put(key: String, value: String?) = dao.setSetting(key, value)
    }

    fun dao(context: Context): KhataDao = KhataDatabase.get(context).khataDao()

    fun owner(context: Context): Waris.Owner = Waris.Owner(BookStore(dao(context)))
    fun heir(context: Context): Waris.Heir = Waris.Heir(BookStore(dao(context)))

    /** The book through the helper copy's own path: the backup format, every secret left out. */
    fun book(context: Context): Waris.Book {
        val app = context.applicationContext
        return Waris.Book { out ->
            val w = java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8), 64 * 1024)
            Backup.exportTo(app, dao(app), w, secrets = false)
            w.flush()
        }
    }

    fun shopName(context: Context): String =
        BusinessProfile.businessName(context)?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name)

    /** Who may run the owner's side here: not a helper's read-only phone, and only the owner on a counter. */
    fun ownerMay(context: Context): Boolean = !ViewerMode.isOn(context) && Access.can(Access.Permission.BACKUP)

    /**
     * The status, when its six hours are up (or [force]): merged from every relay, refusals the relays missed sent
     * again, a request still waiting told as a notification; and the heir's "still here", at most once a day per
     * link. Off the main thread; the merged view (or null when nothing was read).
     */
    suspend fun checkIfDue(context: Context, force: Boolean = false): Map<String, Waris.HeirView>? = withContext(Dispatchers.IO) {
        if (ViewerMode.isOn(context)) return@withContext null
        var views: Map<String, Waris.HeirView>? = null
        runCatching {
            val owner = owner(context)
            if (owner.state() != null) {
                if (force || System.currentTimeMillis() - owner.statusAt() >= Waris.STATUS_EVERY_MS) {
                    views = owner.status()
                    views?.let { notifyAsked(context, owner, it) }
                }
            } else if (owner.offPending()) owner.retry(null)
        }
        runCatching {
            val heir = heir(context)
            for (l in heir.links()) {
                val was = l.open
                val now = heir.alive(l)
                if (now.open && !was) notifyOpen(context, now)
            }
        }
        views
    }

    /** The weekly run (WarisWorker): status, a new copy when the book changed or a week went by, what was left undone. */
    suspend fun weekly(context: Context) = withContext(Dispatchers.IO) {
        if (ViewerMode.isOn(context)) return@withContext
        val owner = owner(context)
        runCatching {
            if (owner.state() != null) {
                owner.snapshot(book(context), force = false)
                owner.status()?.let { notifyAsked(context, owner, it) }
            } else if (owner.offPending()) owner.retry(null)
        }
        checkIfDue(context, force = false)
    }

    // ------------------------------------------------------------------ notifications

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.waris_title), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.waris_channel_help) }
            )
        }
    }

    /** Each request said once (by heir and the moment asked); its tap opens Home, which asks the owner. */
    private suspend fun notifyAsked(context: Context, owner: Waris.Owner, views: Map<String, Waris.HeirView>) {
        val ctx = com.innovation313.roshankhata.ui.AppLanguage.context(context.applicationContext)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val heirs = owner.heirs()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (v in views.values.filter { it.pending }) {
            val mark = "said_${v.hid}_${v.askedAt}"
            if (prefs.getBoolean(mark, false)) continue
            val name = heirs.firstOrNull { it.hid == v.hid }?.name ?: continue
            val at = v.openAt ?: continue
            post(ctx, ID_BASE + (v.hid.hashCode() and 0xFF), ctx.getString(R.string.waris_asked_title),
                ctx.getString(R.string.waris_asked_body, com.innovation313.roshankhata.ui.Format.isolate(name), com.innovation313.roshankhata.ui.Format.dateOnly(at)))
            prefs.edit().putBoolean(mark, true).apply()
        }
    }

    private fun notifyOpen(context: Context, link: Waris.Link) {
        val ctx = com.innovation313.roshankhata.ui.AppLanguage.context(context.applicationContext)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        post(ctx, ID_BASE + 300 + (link.hid.hashCode() and 0xFF), ctx.getString(R.string.waris_title),
            ctx.getString(R.string.waris_link_open_note, com.innovation313.roshankhata.ui.Format.isolate(link.shop)))
    }

    private fun post(ctx: Context, id: Int, title: String, body: String) {
        ensureChannel(ctx)
        val intent = Intent(ctx, GateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            // A name stays off the lock screen: its public face is the title alone.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(ctx, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_notify)
                .setColor(ContextCompat.getColor(ctx, R.color.brand_green)).setContentTitle(title).build())
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    // ------------------------------------------------------------------ the heir's copy

    /**
     * The shop whose heir's copy this read-only phone shows; null when it shows none (or is not read-only). Kept with
     * the moment that copy was loaded ([ViewerMode.refreshedAt]): a helper's copy loaded later is never called an heir's.
     */
    fun heirCopyOf(context: Context): String? {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong(KEY_HEIR_COPY_AT, -1L)
        if (!ViewerMode.isOn(context) || at != ViewerMode.refreshedAt(context)) return null
        return p.getString(KEY_HEIR_COPY, null)
    }

    private fun markHeirCopy(context: Context, shop: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (shop == null) { remove(KEY_HEIR_COPY); remove(KEY_HEIR_COPY_AT) }
            else { putString(KEY_HEIR_COPY, shop); putLong(KEY_HEIR_COPY_AT, ViewerMode.refreshedAt(context)) }
        }.commit()
    }

    sealed class HeirCopy {
        class Loaded(val outcome: ViewerSync.Outcome) : HeirCopy()
        class NotOpened(val failed: Waris.Opened.Failed) : HeirCopy()
    }

    /**
     * The heir's book: opened from the relays into a private file, then taken in exactly as a helper's copy made
     * from a backup FILE is ([ViewerSync.load], source file), so the phone shows the shop's book read-only and is
     * left the usual way (Helper's phone → Stop). The file is deleted once read.
     */
    suspend fun openHeirCopy(activity: Activity, link: Waris.Link): HeirCopy = withContext(Dispatchers.IO) {
        val dir = java.io.File(activity.filesDir, "waris").apply { mkdirs() }
        val file = java.io.File(dir, "heir-copy.txt")
        try {
            val opened = file.outputStream().buffered(64 * 1024).use { heir(activity).open(link, it) }
            if (opened is Waris.Opened.Failed) return@withContext HeirCopy.NotOpened(opened)
            val text = file.readText(Charsets.UTF_8)
            file.delete()
            val outcome = ViewerSync.load(activity, text, ViewerMode.SOURCE_FILE, null, 1L)
            if (outcome is ViewerSync.Outcome.Loaded || outcome is ViewerSync.Outcome.LoadedWithoutPhotos) markHeirCopy(activity, link.shop)
            HeirCopy.Loaded(outcome)
        } finally {
            file.delete()
        }
    }
}
