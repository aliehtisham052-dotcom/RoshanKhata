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
import com.innovation313.roshankhata.MainActivity
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.WarisActivity
import com.innovation313.roshankhata.WarisWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Waris ki Chaabi on the phone (4 Nov 2026): the shared [Waris] steps over the open book's settings, the book as the
 * helper's copy writes it (secrets = false), the owner's warning as a notification, and the heir's copy marked so
 * Home says whose it is. The owner's side is never run on a helper's read-only phone's copy.
 *
 * The security review (7 Nov 2026): the background run ([daily]) covers every shop on the phone, open or not, and the
 * phone's own books while it shows a read-only copy (W6); a copy is sealed only from the shop it was taken for (W8).
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

    /**
     * The open book through the helper copy's own path: the backup format, every secret left out. Pinned to the shop
     * open now (the security review, W8): if another shop or a read-only copy is open by the time it is written, or
     * is opened while it is, nothing goes up ([Waris.BookMoved]) — one shop's book is never sealed for another's heirs.
     */
    fun book(context: Context): Waris.Book {
        val app = context.applicationContext
        val business = Businesses.active(app).id
        val viewer = ViewerMode.isOn(app)
        val dao = dao(app)
        fun same() = !viewer && !ViewerMode.isOn(app) && Businesses.active(app).id == business
        return Waris.Book { out ->
            if (!same()) throw Waris.BookMoved()
            val w = java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8), 64 * 1024)
            Backup.exportTo(app, dao, w, secrets = false)
            w.flush()
            // Switched while it was written: the profile may be the other shop's.
            if (!same()) throw Waris.BookMoved()
        }
    }

    /**
     * A shop that is not open, through a handle of its own for the write alone, with its own name and profile
     * ([Backup.exportToCacheFor]'s way). Open by now (or deleted): nothing is written; its own runs seal it.
     */
    private fun bookOf(context: Context, business: Businesses.Business): Waris.Book {
        val app = context.applicationContext
        return Waris.Book { out ->
            val db = KhataDatabase.openOther(app, business) ?: throw Waris.BookMoved()
            try {
                val w = java.io.BufferedWriter(java.io.OutputStreamWriter(out, Charsets.UTF_8), 64 * 1024)
                BackupCodec.exportTo(
                    db.khataDao(), BusinessProfile.nameOf(app, business.id),
                    BackupCodec.profileToJson(BusinessProfile.settingsOf(app, business.id)), w, secrets = false
                )
                w.flush()
            } finally {
                db.close()
            }
        }
    }

    /**
     * A closed shop's Waris keys (W6): read once, used by the shared steps while its book stays closed, then only the
     * keys that changed are written back — each only where nobody changed it meanwhile.
     */
    private class HeldKeys(val first: Map<String, String>) : Waris.Store {
        val held = HashMap(first)
        override suspend fun get(key: String): String? = held[key]
        override suspend fun put(key: String, value: String?) { if (value == null) held.remove(key) else held[key] = value }
    }

    private suspend fun keysOf(context: Context, business: Businesses.Business): Map<String, String>? {
        val db = KhataDatabase.openOther(context, business) ?: return null
        return try {
            db.khataDao().allSettingsForBackup().mapNotNull { s -> s.value?.takeIf { Waris.isSecret(s.key) }?.let { s.key to it } }.toMap()
        } finally {
            db.close()
        }
    }

    private suspend fun writeBack(context: Context, business: Businesses.Business, keys: HeldKeys) {
        val changed = (keys.first.keys + keys.held.keys).filter { keys.first[it] != keys.held[it] }
        if (changed.isEmpty()) return
        // Open by now: the open shop's own book has the last word.
        val db = KhataDatabase.openOther(context, business) ?: return
        try {
            val dao = db.khataDao()
            for (k in changed) if (dao.settingValue(k) == keys.first[k]) dao.setSetting(k, keys.held[k])
        } finally {
            db.close()
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
                    views?.let { notifyAsked(context, Businesses.active(context), owner, it) }
                }
            } else if (owner.offPending()) owner.retry(null)
        }
        runCatching { heirSteps(context, heir(context)) }
        views
    }

    /** One background run at a time: the daily run and a refusal's retry never write a shop's keys together. */
    private val runLock = Mutex()

    /**
     * The background run (WarisWorker, twice a day — the security review, W6: weekly was no shorter than the shortest
     * wait, so a warning could come after the book had opened): every shop on this phone, the open one through its own
     * book and the others through a handle of their own (as DriveBackup does for a shop left), and the phone's own
     * books while it shows a helper's or an heir's read-only copy (they are only set aside). For each vault: its
     * status, a request told by a notification of that shop's; what a relay missed, done again; a new locked copy once
     * a week went by (sooner when W moved). Then the heir's "still here" for the links each book keeps.
     */
    suspend fun daily(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runLock.withLock {
            for (business in Businesses.list(app)) runCatching { runFor(app, business, full = true) }
        }
    }

    /**
     * A refusal some relay has not taken yet (W6), asked again for every shop that owes one; true while any is still
     * owed, so the work tries again, backing off.
     */
    suspend fun retryRefusals(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runLock.withLock {
            var owed = false
            for (business in Businesses.list(app)) {
                if (runCatching { runFor(app, business, full = false) }.getOrDefault(true)) owed = true
            }
            owed
        }
    }

    /** One shop's run ([full]: the daily one; else only a refusal still owed); true while a refusal is owed there. */
    private suspend fun runFor(app: Context, business: Businesses.Business, full: Boolean): Boolean {
        if (!ViewerMode.isOn(app) && Businesses.active(app).id == business.id) {
            val store = BookStore(dao(app))
            val owner = Waris.Owner(store)
            if (full || owner.refuseLeft().isNotEmpty()) ownerSteps(app, business, owner, book(app), full)
            if (full) heirSteps(app, Waris.Heir(store))
            return owner.refuseLeft().isNotEmpty()
        }
        val keys = keysOf(app, business) ?: return false
        if (keys.isEmpty() || (!full && keys[Waris.K_REFUSE_LEFT] == null)) return false
        val held = HeldKeys(keys)
        val owner = Waris.Owner(held)
        ownerSteps(app, business, owner, bookOf(app, business), full)
        if (full) heirSteps(app, Waris.Heir(held))
        writeBack(app, business, held)
        return owner.refuseLeft().isNotEmpty()
    }

    private suspend fun ownerSteps(app: Context, business: Businesses.Business, owner: Waris.Owner, book: Waris.Book, full: Boolean) {
        val st = owner.state()
        if (st == null) {
            if (full && owner.offPending()) owner.retry(null)
            return
        }
        owner.status()?.let { notifyAsked(app, business, owner, it) }
        if (!full) return
        // A new locked copy once a week went by; else what a relay missed (W moved, a copy a relay lacks: retry sees it).
        if (System.currentTimeMillis() - st.snapAt >= Waris.SNAP_EVERY_MS) owner.snapshot(book, force = false)
        else owner.retry(book)
    }

    private suspend fun heirSteps(context: Context, heir: Waris.Heir) {
        for (l in heir.links()) {
            val was = l.open
            val now = heir.alive(l)
            if (now.open && !was) notifyOpen(context, now)
        }
    }

    /** The owner's refusal from a screen; a relay it did not reach is asked again in the background until it has it (W6). */
    suspend fun refuse(context: Context, hid: String): Waris.Result {
        val owner = owner(context)
        val result = owner.refuse(hid)
        if (owner.refuseLeft().isNotEmpty()) WarisWorker.retryRefusal(context.applicationContext)
        return result
    }

    /**
     * "<name> asked to open your book. It opens on <day> unless you refuse." — or, once that day has come, that it
     * is open to them now and a refusal closes it (W9: it went on naming a day already past).
     */
    fun askedWords(context: Context, name: String, openAt: Long, now: Long = System.currentTimeMillis()): String {
        val who = com.innovation313.roshankhata.ui.Format.isolate(name)
        return if (openAt <= now) context.getString(R.string.waris_asked_open_body, who)
        else context.getString(R.string.waris_asked_body, who, com.innovation313.roshankhata.ui.Format.dateOnly(openAt))
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

    /**
     * Each request said once (by heir and the moment asked), as a notification of its own shop (its name beside the
     * title, W6); its tap opens Waris ki Chaabi there — another shop is chosen first (MainActivity).
     */
    private suspend fun notifyAsked(context: Context, business: Businesses.Business, owner: Waris.Owner, views: Map<String, Waris.HeirView>) {
        val ctx = com.innovation313.roshankhata.ui.AppLanguage.context(context.applicationContext)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val heirs = owner.heirs()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shop = BusinessProfile.nameOf(ctx, business.id)
        for (v in views.values.filter { it.pending }) {
            val mark = "said_${v.hid}_${v.askedAt}"
            if (prefs.getBoolean(mark, false)) continue
            val name = heirs.firstOrNull { it.hid == v.hid }?.name ?: continue
            val at = v.openAt ?: continue
            post(ctx, ID_BASE + (v.hid.hashCode() and 0xFF), ctx.getString(R.string.waris_asked_title), askedWords(ctx, name, at), shop, business.id)
            prefs.edit().putBoolean(mark, true).apply()
        }
    }

    private fun notifyOpen(context: Context, link: Waris.Link) {
        val ctx = com.innovation313.roshankhata.ui.AppLanguage.context(context.applicationContext)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        post(ctx, ID_BASE + 300 + (link.hid.hashCode() and 0xFF), ctx.getString(R.string.waris_title),
            ctx.getString(R.string.waris_link_open_note, com.innovation313.roshankhata.ui.Format.isolate(link.shop)))
    }

    private fun post(ctx: Context, id: Int, title: String, body: String, shop: String? = null, business: Long? = null) {
        ensureChannel(ctx)
        val intent = Intent(ctx, GateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (business != null) {
            // The owner's notice opens the place to refuse. On a read-only phone no shop is chosen: the screen says
            // to stop the copy first.
            intent.putExtra(MainActivity.EXTRA_OPEN, WarisActivity::class.java.name)
            if (!ViewerMode.isOn(ctx)) intent.putExtra(MainActivity.EXTRA_OPEN_BUSINESS, business)
        }
        val pending = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(ContextCompat.getColor(ctx, R.color.brand_green))
            .setContentTitle(title)
            .setContentText(body)
            .apply { if (!shop.isNullOrBlank()) setSubText(shop) }
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
            val outcome = ViewerSync.loadFile(activity, file, ViewerMode.SOURCE_FILE, null, 1L)
            file.delete()
            if (outcome is ViewerSync.Outcome.Loaded || outcome is ViewerSync.Outcome.LoadedWithoutPhotos) markHeirCopy(activity, link.shop)
            HeirCopy.Loaded(outcome)
        } finally {
            file.delete()
        }
    }
}
