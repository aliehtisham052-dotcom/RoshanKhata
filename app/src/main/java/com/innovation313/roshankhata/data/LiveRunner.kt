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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible

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
    /** With the live line open, a quiet book still makes one round this often, in case a nudge was lost. */
    private const val LINE_QUIET_MS = 5 * 60_000L
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
                // Marked before the round: a nudge that lands while it runs still wakes the wait after it.
                val since = LiveLines.newsMark()
                var sqlNow: SyncSql? = null
                val wait = try {
                    val db = KhataDatabase.get(app)
                    val sql = db.syncSql()
                    if (checked !== db) {
                        LiveSync.check(sql, install(app), marks(app))
                        NumberMark.mark = LiveSync.mark(sql)
                        checked = db
                    }
                    sqlNow = sql
                    once(app, db, sql)
                } catch (e: CancellationException) {
                    // The app left the screen: that is not a failure to show (22 Oct 2026, the review).
                    throw e
                } catch (e: Exception) {
                    state.value = Status.Failed(e.message ?: e.javaClass.simpleName)
                    ROUND_MS * 3
                }
                // With the relay's live line open, the next round is when there is news — the relay's nudge, or
                // this phone writing something — not on a clock (23 Oct 2026): a quiet book asks nothing.
                val s = sqlNow
                if (LiveLines.anyOpen() && s != null) {
                    val before = runCatching { LiveSync.lastSeq(s) }.getOrDefault(0L)
                    runInterruptible { LiveLines.waitForNews(wait, since) { runBlocking { LiveSync.lastSeq(s) } > before } }
                } else delay(wait)
            }
        }
    }

    /** Whether rounds are running (the app is on screen); a wake-up then does nothing ([PushWake.roundNow]). */
    val running: Boolean get() = job?.isActive == true

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        // The app left the screen: the live line goes too (a sleeping line costs nothing, but a closed app holds
        // none); Firebase reaches the phone from here on.
        runCatching { LiveLines.closeAll() }
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
            LiveCrew.dropCopyHold(sql)
            if (LiveRound.dropPending(sql)) {
                LiveSync.disable(sql)
                NumberMark.mark = null
            }
            state.value = Status.NotAccepted
            return ROUND_MS * 3
        }
        // A pairing with a phone waits for its yes through the relay (a phone serves no WiFi rounds): meanwhile the
        // computer this phone had, if any, is still the WiFi road (23 Oct 2026, several devices per shop).
        val wifi = if (pending != null && !pending.phone) pending else LiveRound.target(sql)
        if (LiveControl.halted()) { state.value = Status.Halted; return 60_000L }
        val peersNow = LiveSync.state(sql).peers
        // No computer on the WiFi road: phones that met through the relay alone (or a phone's yes awaited there).
        val target = wifi ?: run {
            if (peersNow.isEmpty() && pending?.phone != true) { state.value = Status.NotPaired; return ROUND_MS * 3 }
            tellWake(app, sql, now, pending)
            return relayRound(app, sql, peersNow.firstOrNull()?.name, null, side = false)
        }
        tellWake(app, sql, now, pending)
        val wifiPending = pending?.takeIf { !it.phone }
        val key = LiveSync.identity(sql)?.key ?: return ROUND_MS
        val check = wifiPending == null && now - lastFingerprint > FINGERPRINT_EVERY_MS
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
        val peer = if (wifiPending != null) null else peersNow.let { ps ->
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
                // More than the computer in this book (another phone), or a phone's yes awaited: the relay too, so
                // the devices away from this WiFi hear this phone's changes (each device sends only its own).
                val others = LiveSync.state(sql).peers.size > 1 || pending?.phone == true
                val side = if (others && sideDue(sql, now)) relayRound(app, sql, peer?.name, target.computer, side = true) else Long.MAX_VALUE
                minOf(if (took.more) 200L else ROUND_MS, side)
            }
            LiveWire.Answer.NotHere -> {
                state.value = if (wifiPending != null) Status.Waiting else Status.NotHere
                ROUND_MS * 2
            }
            LiveWire.Answer.Unreachable -> {
                if (wifiPending != null) { state.value = Status.Unreachable; return ROUND_MS * 2 }
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
                relayRound(app, sql, peer?.name, target.computer, side = false)
            }
        }
    }

    private var sideAt = 0L
    private var sideSeq = -1L
    private var sideNews = -1L

    /**
     * Whether the relay round beside a WiFi round is due: only when there is something to say or hear — this
     * phone wrote something, the relay nudged its line — or once a minute (five with a line open). WiFi rounds come
     * every few seconds; the relay is not asked as often.
     */
    private suspend fun sideDue(sql: SyncSql, now: Long): Boolean {
        val seq = runCatching { LiveSync.lastSeq(sql) }.getOrDefault(0L)
        val news = LiveLines.newsMark()
        val quiet = if (LiveLines.anyOpen()) LINE_QUIET_MS else 60_000L
        if (seq == sideSeq && news == sideNews && now - sideAt < quiet) return false
        sideAt = now; sideSeq = seq; sideNews = news
        return true
    }

    /** The relay is told where to ring this phone while the app is closed (Firebase, when the build has it). */
    private suspend fun tellWake(app: Context, sql: SyncSql, now: Long, pending: LiveRound.Target?) {
        if (pending != null && !pending.phone) return
        if (now - lastWakeTell <= wakeTellWait) return
        lastWakeTell = now
        // Not told (the relay down, refusing): asked again after a growing wait, 1 min doubling to an hour —
        // not one relay call a minute for ever (23 Oct 2026, the second review).
        val told = runCatching { PushWake.tell(app, sql) }.getOrDefault(false)
        wakeTellWait = if (told) WAKE_TELL_MS else minOf(wakeTellWait * 2, WAKE_TELL_MAX_MS)
    }

    /**
     * One relay round (Cloudflare first, then the owner's hosting; the later ones read every few rounds) and how
     * long to wait after it. [side]: the WiFi round already said how things stand — a relay round run beside it
     * for the shop's other devices changes the status only to say that the book must be joined again.
     */
    private suspend fun relayRound(app: Context, sql: SyncSql, peerName: String?, computer: String?, side: Boolean): Long {
        cloudRounds++
        return when (val r = LiveCloud.roundAll(sql, readOthers = cloudRounds % OTHERS_EVERY == 0)) {
            is LiveCloud.Result.Done -> {
                relayMisses = 0
                LiveSync.noteMark(sql, marks(app))
                NumberMark.mark = LiveSync.mark(sql)
                if (!side) state.value = Status.Live(System.currentTimeMillis(), peerName ?: computer, relay = true)
                // Quiet rounds wait longer (15 s, 30 s, then a minute): a daily limit is shared by every book.
                quietCloud = if (r.took > 0 || r.more) 0 else minOf(quietCloud + 1, 2)
                when {
                    r.more -> 1000L
                    side -> Long.MAX_VALUE
                    // A live line wakes the wait on news; on a clock only once in a while, to be sure.
                    LiveLines.anyOpen() -> LINE_QUIET_MS
                    else -> CLOUD_MS shl quietCloud
                }
            }
            LiveCloud.Result.Halted -> { if (!side) state.value = Status.Halted; 60_000L }
            LiveCloud.Result.Off, LiveCloud.Result.NotPaired -> { if (!side) state.value = Status.Unreachable; if (side) Long.MAX_VALUE else ROUND_MS * 3 }
            else -> if (r is LiveCloud.Result.Refused && r.why == LiveRound.LOST) {
                // The other device asks for lines this book let go of: only joining again mends it (as on WiFi).
                runCatching { LiveCloud.leaveAll(sql) }
                LiveSync.disable(sql, LiveSync.OFF_RESTORED)
                NumberMark.mark = null
                state.value = Status.Rejoin
                ROUND_MS * 3
            } else if (side) {
                relayMisses = minOf(relayMisses + 1, 6)
                Long.MAX_VALUE
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
