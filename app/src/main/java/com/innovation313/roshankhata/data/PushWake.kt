package com.innovation313.roshankhata.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's wake-up for live sync (22 Oct 2026): when the computer leaves a
 * parcel at the relay while Roshan Khata is closed on the phone, the relay
 * asks Google's Firebase Cloud Messaging to deliver one word ("rk-wake") to
 * this phone, and the phone takes the parcel in the background
 * ([WakeService] → [roundNow]). The message carries nothing of the book but,
 * from a relay that says it, the book's random id ("k"), so the phone knows
 * which of its shops to wake ([WakeRoute], L010); the parcel itself stays
 * sealed with the book's key as before.
 *
 * Firebase is in the build only when CI had the owner's google-services file
 * (a GitHub secret, never in this public repo): without it [available] is
 * false and everything here is a no-op — sync then runs while the app is on
 * screen, as before.
 *
 * Nothing is asked of Firebase until a book's owner turned the relay on:
 * the manifest keeps Messaging's auto-init off, and [address] switches it on
 * only from a round that found the relay allowed. Turning the relay off on
 * this phone ([off]) deletes the token, so Google stops knowing it.
 */
object PushWake {

    private const val PREFS = "push_wake"
    private const val TOKEN = "token"
    /** At most one ask for a token in this long, when Firebase could not give one. */
    private const val ASK_GAP_MS = 10 * 60_000L

    @Volatile private var lastAsk = 0L

    /** Whether this build carries Firebase (CI had the google-services file). */
    fun available(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context.applicationContext).isNotEmpty() }.getOrDefault(false)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A new token from Firebase ([WakeService.onNewToken]); the relay is told at the next round. */
    fun saveToken(context: Context, token: String) {
        prefs(context).edit().putString(TOKEN, token).apply()
    }

    /**
     * This phone's wake-up address, or null while there is none yet. The first
     * call (from a round with the relay on) switches Messaging on and asks for
     * one; it arrives through [saveToken] and is told at a later round.
     */
    fun address(context: Context): String? {
        if (!available(context)) return null
        prefs(context).getString(TOKEN, null)?.let { return it }
        val now = System.currentTimeMillis()
        if (now - lastAsk < ASK_GAP_MS) return null
        lastAsk = now
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            fm.isAutoInitEnabled = true
            fm.token.addOnSuccessListener { t -> if (!t.isNullOrBlank()) saveToken(context, t) }
        }
        return null
    }

    /** The relay turned off on this phone: Firebase forgets this phone's token, and it is not asked again. */
    fun off(context: Context) {
        // Offline, deleteToken fails: the deletion stays owed and is tried again from [tell] (8 Nov 2026 review L40).
        prefs(context).edit().remove(TOKEN).putBoolean("delete_owed", true).apply()
        deleteOwed(context)
    }

    private fun deleteOwed(context: Context) {
        if (!available(context)) return
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            fm.isAutoInitEnabled = false
            fm.deleteToken().addOnSuccessListener { prefs(context).edit().remove("delete_owed").apply() }
        }
    }

    /**
     * Tells the relay this phone's address for the open book when it changed
     * (a no-op otherwise; [LiveCloud.tellWake] keeps what it said).
     */
    /** True when the relay knows the phone's address now, or there is nothing to tell. */
    suspend fun tell(context: Context, sql: SyncSql): Boolean {
        if (!available(context)) return true
        if (prefs(context).getBoolean("delete_owed", false) && !LiveCloud.allowed(sql)) deleteOwed(context)
        // The relay switched off (on this phone or the other device): the relay forgets this phone's address (R53).
        if (!LiveCloud.allowed(sql)) return LiveCloud.tellWake(sql, null)
        val address = address(context) ?: return true
        return LiveCloud.tellWake(sql, address)
    }

    /** One shop that is not open at a time (L010): its own handle, opened, used and closed before the next. */
    private val otherShops = Mutex()

    /**
     * A parcel carried inside the push ("rk-parcel"): written in on Firebase's background thread without a
     * relay call; a round follows only when something before it is missing. While the app is on screen its own
     * rounds already run (and its live line is nudged), so the open shop takes nothing here. A parcel the open
     * shop's key does not open may be for a shop that is not open (L010): each such shop with its relay on is
     * tried, one at a time, on its own file; only the right book's key opens it.
     */
    fun takeCarried(context: Context, from: String, body: String, named: String? = null) {
        val app = context.applicationContext
        val book = WakeRoute.named(named)
        val ask: String? = try {
            within<String?>(11_000L, null) {
                withTimeoutOrNull(10_000L) {
                    val db = KhataDatabase.get(app)
                    val sql = db.syncSql()
                    val openBook = LiveSync.identity(sql)?.book
                    if (WakeRoute.forOpen(book, openBook)) {
                        if (!LiveRunner.running) {
                            LiveSync.check(sql, LiveRunner.install(app), LiveRunner.marks(app))
                            when (LiveCloud.takeCarried(sql, from, body)) {
                                LiveCloud.Carried.TOOK -> {
                                    LiveSync.noteMark(sql, LiveRunner.marks(app))
                                    NumberMark.mark = LiveSync.mark(sql)
                                    return@withTimeoutOrNull null
                                }
                                LiveCloud.Carried.ASK -> return@withTimeoutOrNull openBook
                                LiveCloud.Carried.NOT_MINE -> Unit
                            }
                        } else if (book != null) {
                            // Named for the open shop, on screen: the runner's own rounds and its live line take it.
                            return@withTimeoutOrNull null
                        }
                        // Unnamed while on screen: the open shop's line would have carried its own parcel (the relay
                        // pushes only to a device with no open line), so it is most likely another shop's.
                    }
                    if (!WakeRoute.readOthers(book, openBook) || ViewerMode.isOn(app)) return@withTimeoutOrNull null
                    var asked: String? = null
                    eachOther(app, book, openBook) { other, otherSql, marks ->
                        when (LiveCloud.takeCarried(otherSql, from, body)) {
                            LiveCloud.Carried.TOOK -> { LiveSync.noteMark(otherSql, marks); true }
                            LiveCloud.Carried.ASK -> { asked = other.book; true }
                            LiveCloud.Carried.NOT_MINE -> false
                        }
                    }
                    asked
                // Timed out (the book was busy): no round on top of it — the two waits together outran Firebase's
                // time for a message (R56); the next wake-up or the app's own rounds fetch it.
                }
            }
        } catch (e: Exception) {
            // Not known which shop it was for: a round for the open one, as before.
            ""
        }
        if (ask != null) roundNow(app, ask.ifEmpty { null })
    }

    /**
     * What a wake-up does, on Firebase's own background thread, for the book the relay names ([named], the
     * message's "k"; null from a relay that does not say): the open shop's relay rounds unless the app is on screen
     * ([LiveRunner] already runs them), then — for a message naming another book, or naming none — the shops that
     * are not open whose relay is on ([WakeRoute.others]), one at a time on a handle of their own (L010). All of it
     * within Firebase's time for a message.
     */
    fun roundNow(context: Context, named: String? = null) {
        val app = context.applicationContext
        val book = WakeRoute.named(named)
        try {
            within<Unit>(9_000L, Unit) {
                // 8 s: with a carried parcel's 10 s before it, still inside the ~20 s Firebase gives a message (R56).
                withTimeoutOrNull(8_000L) {
                    val db = KhataDatabase.get(app)
                    val sql = db.syncSql()
                    val openBook = LiveSync.identity(sql)?.book
                    if (WakeRoute.forOpen(book, openBook) && !LiveRunner.running) {
                        LiveSync.check(sql, LiveRunner.install(app), LiveRunner.marks(app))
                        NumberMark.mark = LiveSync.mark(sql)
                        rounds(sql, LiveRunner.marks(app), still = { KhataDatabase.isOpen(db) }) {
                            NumberMark.mark = LiveSync.mark(sql)
                        }
                    }
                    if (!WakeRoute.readOthers(book, openBook) || ViewerMode.isOn(app)) return@withTimeoutOrNull
                    eachOther(app, book, openBook) { other, otherSql, marks ->
                        rounds(otherSql, marks, still = { other.business.file != Businesses.activeDbFile(app) }) {}
                        // Named: that one shop only. Unnamed: every shop the route chose gets its turn.
                        book != null
                    }
                }
            }
        } catch (e: CancellationException) {
            // Out of time: the rest comes at the next wake-up or when the app opens.
        } catch (e: Exception) {
            // A wake-up never crashes the phone's app; the next round tries again.
        }
    }

    /**
     * Up to five relay rounds for one book, the first relay only (the wake-up came from it; the others wait for the
     * screen's rounds), while [still] holds — the book open (the open shop), or not opened by a switch meanwhile (a
     * shop that is not open: its own runner takes over then).
     */
    private suspend fun rounds(sql: SyncSql, marks: LiveSync.LogMarks, still: () -> Boolean, after: suspend () -> Unit) {
        repeat(5) {
            if (!still()) return
            val r = LiveCloud.roundAll(sql, readOthers = false)
            if (r !is LiveCloud.Result.Done) return
            LiveSync.noteMark(sql, marks)
            after()
            if (!r.more) return
        }
    }

    /** A shop that is not open, with the book id it syncs. */
    private class Other(val business: Businesses.Business, val book: String)

    /**
     * [act] for each shop that is not open which [WakeRoute.others] chooses, in its order, one at a time on a handle of
     * its own that is closed before the next (and never the open shop's file: [KhataDatabase.openOther] refuses it);
     * [act] answering true ends the walk. A shop opened by a switch meanwhile is passed over: its own runner takes it.
     */
    private suspend fun eachOther(
        app: Context, named: String?, openBook: String?,
        act: suspend (Other, SyncSql, LiveSync.LogMarks) -> Boolean
    ) = otherShops.withLock {
        val active = Businesses.activeDbFile(app)
        val closed = Businesses.list(app).filter { it.file != active }
        if (closed.isEmpty()) return@withLock
        // What each closed shop syncs, read from its own file and closed again.
        val seen = ArrayList<Pair<Businesses.Business, WakeRoute.Shop>>()
        for (b in closed) {
            val db = runCatching { KhataDatabase.openOther(app, b) }.getOrNull() ?: continue
            try {
                val sql = db.syncSql()
                val id = runCatching { LiveSync.identity(sql) }.getOrNull() ?: continue
                seen += b to WakeRoute.Shop(b.id, id.book, runCatching { LiveCloud.allowed(sql) }.getOrDefault(false))
                // Named and found: no other file needs opening.
                if (named != null && id.book == named) break
            } finally {
                db.close()
            }
        }
        val chosen = WakeRoute.others(seen.map { it.second }, named, openBook)
        for (shop in chosen) {
            val b = seen.first { it.second.id == shop.id }.first
            if (b.file == Businesses.activeDbFile(app)) continue
            val db = runCatching { KhataDatabase.openOther(app, b) }.getOrNull() ?: continue
            val stop = try {
                val sql = db.syncSql()
                val marks = LiveRunner.marks(app, b.file)
                LiveSync.check(sql, LiveRunner.install(app), marks)
                // The book in the file is still the one chosen (a restore may have put another there meanwhile).
                if (LiveSync.identity(sql)?.book != shop.book) false else act(Other(b, shop.book!!), sql, marks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            } finally {
                db.close()
            }
            if (stop) break
        }
    }

    /**
     * [block] with a limit that holds (8 Nov 2026 review, L150): withTimeoutOrNull cannot stop a blocking HTTP call, so
     * the limits of 8 and 10 s were not true. The work runs on a thread of its own and the caller (Firebase's, which
     * has about 20 s) stops waiting at [ms]; the thread is interrupted and its work ends with its call.
     */
    private fun <T> within(ms: Long, onTimeout: T, block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T {
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        val job = pool.submit<T> { runBlocking { block() } }
        return try {
            job.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            job.cancel(true); onTimeout
        } finally {
            pool.shutdown()
        }
    }
}
