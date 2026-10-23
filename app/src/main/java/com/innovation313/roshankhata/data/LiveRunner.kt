package com.innovation313.roshankhata.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The phone's live sync with the computer over the shop's WiFi (Marhala 1,
 * 22 Oct 2026): a round every few seconds while the app is on the screen,
 * nothing at all when it is not. Started and stopped by the app as its
 * screens come and go (RoshanKhataApp); a round never blocks a screen.
 *
 * The book's own sync state lives in the book file (LiveSync); this device's
 * install id lives here, in the phone's preferences, so a book file copied
 * to another phone is never taken for this phone's. So does how far each
 * book's log had come ([marks]): a daily copy put back with an older log is
 * caught before its lines are reused.
 */
object LiveRunner {

    /** What the Backup screen shows. */
    sealed interface Status {
        object Off : Status
        /** On, but no computer joined yet. */
        object NotPaired : Status
        /** The book reached the computer; the owner has not said yes on it yet. */
        object Waiting : Status
        /** The computer answered, but it is not keeping this book right now (another business open there). */
        object NotHere : Status
        /** [relay]: through Innovation 313's relay on the internet, not the shop's WiFi. */
        data class Live(val at: Long, val computer: String?, val relay: Boolean = false) : Status
        object Unreachable : Status
        /** Away from the shop's WiFi, and no relay on the internet answered: [why] is the relay's own word (22 Oct 2026). */
        data class RelayDown(val why: String) : Status
        /** The other device runs a newer version of the app: its changes wait until this one is updated. */
        data class UpdateNeeded(val why: String) : Status
        /** The phone's key store did not answer (after an update, say): nothing is decided; asked again next round. */
        data class KeyBusy(val why: String) : Status
        /** Turned off for everyone by Innovation 313 ([LiveControl]); the book is untouched. */
        object Halted : Status
        /** The computer did not say yes in time: the pairing was given up. */
        object NotAccepted : Status
        /** The book was replaced by a restore or went back to an older copy: the devices must be joined again. */
        object Rejoin : Status
        data class Failed(val why: String) : Status
    }

    private const val PREFS = "live_sync"
    private const val INSTALL = "install"
    private const val ROUND_MS = 4_000L
    private const val FINGERPRINT_EVERY_MS = 30 * 60_000L
    /** Through the relay a round is slower and counted against a daily limit: every 15 s, longer while quiet. */
    private const val CLOUD_MS = 15_000L
    /** A pairing the computer's owner has not said yes to within this time is given up. */
    private const val PENDING_MS = 15 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val state = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> get() = state
    private var lastFingerprint = 0L
    private var lastSeek = 0L
    private var quietCloud = 0
    private var cloudRounds = 0
    /** Relay rounds that failed in a row: each waits twice as long, up to about 13 minutes. */
    private var relayMisses = 0
    /** The relays after the first are read every this many relay rounds (each costs a call on a free plan). */
    private const val OTHERS_EVERY = 8
    private const val SEEK_EVERY_MS = 30_000L
    /** How often a round looks whether the relay must be told this phone's wake-up address (a cheap check). */
    private const val WAKE_TELL_MS = 60_000L
    private const val WAKE_TELL_MAX_MS = 60 * 60_000L
    private var wakeTellWait = WAKE_TELL_MS
    private var lastWakeTell = 0L
    private val fingerprintBad = MutableStateFlow(false)
    /** The last check of the whole book found the two devices different. */
    val mismatch: StateFlow<Boolean> get() = fingerprintBad

    /** This install's own id, made once. */
    fun install(context: Context): String {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.getString(INSTALL, null)?.let { return it }
        val id = LiveSync.newHex(16)
        p.edit().putString(INSTALL, id).apply()
        return id
    }

    /** How far the log of the book in [file] had come, per book id, kept in this phone's preferences. */
    fun marks(context: Context, file: String = Businesses.activeDbFile(context)): LiveSync.LogMarks =
        PrefsMarks(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE), file)

    private class PrefsMarks(private val p: SharedPreferences, private val file: String) : LiveSync.LogMarks {
        private fun k(book: String) = "mark|$file|$book"
        override fun get(book: String): Long? = if (p.contains(k(book))) p.getLong(k(book), 0L) else null
        override fun put(book: String, seq: Long) { p.edit().putLong(k(book), seq).apply() }
    }

    fun deviceName(): String = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim().ifBlank { "Phone" }.take(40)

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            var checked: KhataDatabase? = null
            while (isActive) {
                val wait = try {
                    val db = KhataDatabase.get(app)
                    val sql = db.syncSql()
                    if (checked !== db) {
                        LiveSync.check(sql, install(app), marks(app))
                        NumberMark.mark = LiveSync.mark(sql)
                        checked = db
                    }
                    once(app, db, sql)
                } catch (e: CancellationException) {
                    // The app left the screen: that is not a failure to show (22 Oct 2026, the review).
                    throw e
                } catch (e: Exception) {
                    state.value = Status.Failed(e.message ?: e.javaClass.simpleName)
                    ROUND_MS * 3
                }
                delay(wait)
            }
        }
    }

    /** Whether rounds are running (the app is on screen); a wake-up then does nothing ([PushWake.roundNow]). */
    val running: Boolean get() = job?.isActive == true

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** One round; how long to wait before the next. */
    private suspend fun once(app: Context, db: KhataDatabase, sql: SyncSql): Long {
        if (LiveSync.identity(sql) == null) {
            LiveSync.lockTrouble?.let { state.value = Status.KeyBusy(it); return ROUND_MS * 3 }
            state.value = if (LiveSync.offReason(sql) == LiveSync.OFF_RESTORED) Status.Rejoin else Status.Off
            return ROUND_MS * 3
        }
        val now = System.currentTimeMillis()
        val pending = LiveRound.pending(sql)
        if (pending != null && now - (pending.since ?: now) > PENDING_MS) {
            // Not said yes to in time (cancelled there, or never seen): given up; off again if it was off before.
            if (LiveRound.dropPending(sql)) {
                LiveSync.disable(sql)
                NumberMark.mark = null
            }
            state.value = Status.NotAccepted
            return ROUND_MS * 3
        }
        val target = pending ?: LiveRound.target(sql) ?: run { state.value = Status.NotPaired; return ROUND_MS * 3 }
        if (LiveControl.halted()) { state.value = Status.Halted; return 60_000L }
        // The relay is told where to ring this phone while the app is closed (Firebase, when the build has it).
        if (pending == null && now - lastWakeTell > wakeTellWait) {
            lastWakeTell = now
            // Not told (the relay down, refusing): asked again after a growing wait, 1 min doubling to an hour —
            // not one relay call a minute for ever (23 Oct 2026, the second review).
            val told = runCatching { PushWake.tell(app, sql) }.getOrDefault(false)
            wakeTellWait = if (told) WAKE_TELL_MS else minOf(wakeTellWait * 2, WAKE_TELL_MAX_MS)
        }
        val key = LiveSync.identity(sql)?.key ?: return ROUND_MS
        val check = pending == null && now - lastFingerprint > FINGERPRINT_EVERY_MS
        val request = try {
            LiveRound.request(sql, fingerprint = check) ?: return ROUND_MS
        } catch (e: LiveSync.Lost) {
            // The computer asked for lines this phone let go of (its book went back to an older copy): only joining
            // again mends it — said as the relay road says it, not as a raw failure every round (23 Oct 2026).
            runCatching { LiveCloud.leaveAll(sql) }
            LiveSync.disable(sql, LiveSync.OFF_RESTORED)
            NumberMark.mark = null
            state.value = Status.Rejoin
            return ROUND_MS * 3
        }
        // While a pairing waits, only the computer that took it is asked (not the one this phone knew before).
        val peer = if (pending != null) null else LiveSync.state(sql).peers.let { ps ->
            target.device?.let { d -> ps.firstOrNull { it.device == d } } ?: ps.firstOrNull()
        }
        val hosts = (listOfNotNull(peer?.host) + target.hosts).distinct()
        return when (val a = LiveWire.round(hosts, target.port, key, request)) {
            is LiveWire.Answer.Got -> {
                // The book was closed while the computer answered (a restore, a switch, an erase): the answer is
                // for a book that is no longer open; written through the old handle it would reopen the old file.
                if (!KhataDatabase.isOpen(db)) return ROUND_MS
                val took = LiveRound.take(sql, request, a.reply, a.host)
                took.refused?.let { why ->
                    if (why == LiveRound.LOST) {
                        // The computer no longer has what this phone asks for: this book went back to an older copy.
                        runCatching { LiveCloud.leaveAll(sql) }
                        LiveSync.disable(sql, LiveSync.OFF_RESTORED)
                        NumberMark.mark = null
                        state.value = Status.Rejoin
                        return ROUND_MS * 3
                    }
                    state.value = if (why.startsWith("schema")) Status.UpdateNeeded(why) else Status.Failed(why)
                    return 60_000L
                }
                LiveSync.noteMark(sql, marks(app))
                NumberMark.mark = LiveSync.mark(sql)
                // Only a check made while nothing was on its way counts; a busy round asks again in the next.
                took.fingerprintMatches?.let { same ->
                    fingerprintBad.value = !same
                    // A difference is looked at again in five minutes, not thirty.
                    lastFingerprint = if (same) now else now - FINGERPRINT_EVERY_MS + 5 * 60_000L
                }
                if (took.joined) fingerprintBad.value = false
                quietCloud = 0
                state.value = Status.Live(System.currentTimeMillis(), peer?.name ?: target.computer)
                if (took.more) 200L else ROUND_MS
            }
            LiveWire.Answer.NotHere -> {
                state.value = if (pending != null) Status.Waiting else Status.NotHere
                ROUND_MS * 2
            }
            LiveWire.Answer.Unreachable -> {
                if (pending != null) { state.value = Status.Unreachable; return ROUND_MS * 2 }
                // The computer may have a new address on the same WiFi (a day off, a router restart): it is
                // called for, at most twice a minute, and kept once it proves it holds this book's key.
                if (now - lastSeek > SEEK_EVERY_MS) {
                    lastSeek = now
                    val found = LiveWire.seek(key)
                    if (found != null && (found.host !in hosts || found.port != target.port)) {
                        LiveRound.setTarget(sql, target.copy(hosts = (listOf(found.host) + target.hosts).distinct().take(4), port = found.port))
                        peer?.let { LiveSync.putPeer(sql, it.device, host = found.host, port = found.port) }
                        return 200L
                    }
                }
                // Not on the shop's WiFi: the relay, when the owner turned it on for this book (Marhala 2).
                // Cloudflare first, then Supabase, then the owner's hosting; the later ones read every few rounds.
                cloudRounds++
                when (val r = LiveCloud.roundAll(sql, readOthers = cloudRounds % OTHERS_EVERY == 0)) {
                    is LiveCloud.Result.Done -> {
                        relayMisses = 0
                        LiveSync.noteMark(sql, marks(app))
                        NumberMark.mark = LiveSync.mark(sql)
                        state.value = Status.Live(System.currentTimeMillis(), peer?.name ?: target.computer, relay = true)
                        // Quiet rounds wait longer (15 s, 30 s, then a minute): a daily limit is shared by every book.
                        quietCloud = if (r.took > 0 || r.more) 0 else minOf(quietCloud + 1, 2)
                        if (r.more) 1000L else CLOUD_MS shl quietCloud
                    }
                    LiveCloud.Result.Halted -> { state.value = Status.Halted; 60_000L }
                    LiveCloud.Result.Off, LiveCloud.Result.NotPaired -> { state.value = Status.Unreachable; ROUND_MS * 3 }
                    else -> if (r is LiveCloud.Result.Refused && r.why == LiveRound.LOST) {
                        // The other device asks for lines this book let go of: only joining again mends it (as on WiFi).
                        runCatching { LiveCloud.leaveAll(sql) }
                        LiveSync.disable(sql, LiveSync.OFF_RESTORED)
                        NumberMark.mark = null
                        state.value = Status.Rejoin
                        ROUND_MS * 3
                    } else if (r is LiveCloud.Result.Refused && r.why.startsWith("schema")) {
                        state.value = Status.UpdateNeeded(r.why)
                        60_000L
                    } else {
                        // The relay is the trouble, not the WiFi: said as such, and asked again after a growing wait
                        // (12 s doubling to 10 min — 22 Oct 2026, the review: every phone hammered every relay at 12 s).
                        state.value = Status.RelayDown(if (r is LiveCloud.Result.Refused) r.why else "unreachable")
                        relayMisses = minOf(relayMisses + 1, 6)
                        (ROUND_MS * 3) shl relayMisses
                    }
                }
            }
        }
    }
}
