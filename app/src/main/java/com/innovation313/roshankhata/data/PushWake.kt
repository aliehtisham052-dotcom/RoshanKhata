package com.innovation313.roshankhata.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's wake-up for live sync (22 Oct 2026): when the computer leaves a
 * parcel at the relay while Roshan Khata is closed on the phone, the relay
 * asks Google's Firebase Cloud Messaging to deliver one word ("rk-wake") to
 * this phone, and the phone takes the parcel in the background
 * ([WakeService] → [roundNow]). The message carries nothing of the book; the
 * parcel itself stays sealed with the book's key as before.
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
        prefs(context).edit().remove(TOKEN).apply()
        if (!available(context)) return
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            fm.isAutoInitEnabled = false
            fm.deleteToken()
        }
    }

    /**
     * Tells the relay this phone's address for the open book when it changed
     * (a no-op otherwise; [LiveCloud.tellWake] keeps what it said).
     */
    /** True when the relay knows the phone's address now, or there is nothing to tell. */
    suspend fun tell(context: Context, sql: SyncSql): Boolean {
        if (!available(context)) return true
        if (!LiveCloud.allowed(sql)) return true
        val address = address(context) ?: return true
        return LiveCloud.tellWake(sql, address)
    }

    /**
     * What a wake-up does, on Firebase's own background thread: while the app
     * is on screen [LiveRunner] already runs, so nothing; otherwise a few
     * relay rounds for the open book, within Firebase's time for a message.
     */
    fun roundNow(context: Context) {
        if (LiveRunner.running) return
        val app = context.applicationContext
        try {
            runBlocking {
                withTimeoutOrNull(15_000L) {
                    val db = KhataDatabase.get(app)
                    val sql = db.syncSql()
                    if (LiveSync.identity(sql) == null) return@withTimeoutOrNull
                    LiveSync.check(sql, LiveRunner.install(app), LiveRunner.marks(app))
                    NumberMark.mark = LiveSync.mark(sql)
                    repeat(5) {
                        // The wake-up came from the first relay: that one is read; the others wait for the screen's rounds.
                        val r = LiveCloud.roundAll(sql, readOthers = false)
                        if (r !is LiveCloud.Result.Done) return@withTimeoutOrNull
                        LiveSync.noteMark(sql, LiveRunner.marks(app))
                        NumberMark.mark = LiveSync.mark(sql)
                        if (!r.more) return@withTimeoutOrNull
                    }
                }
            }
        } catch (e: CancellationException) {
            // Out of time: the rest comes at the next wake-up or when the app opens.
        } catch (e: Exception) {
            // A wake-up never crashes the phone's app; the next round tries again.
        }
    }
}
