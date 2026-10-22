package com.innovation313.roshankhata.data

import android.content.Context
import android.os.Build
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
 * to another phone is never taken for this phone's.
 */
object LiveRunner {

    /** What the Backup screen shows. */
    sealed interface Status {
        object Off : Status
        /** On, but no computer joined yet. */
        object NotPaired : Status
        /** The computer has the book but the owner has not said yes on it yet. */
        object Waiting : Status
        data class Live(val at: Long, val computer: String?) : Status
        object Unreachable : Status
        /** Turned off for everyone by Innovation 313 ([LiveControl]); the book is untouched. */
        object Halted : Status
        data class Failed(val why: String) : Status
    }

    private const val PREFS = "live_sync"
    private const val INSTALL = "install"
    private const val ROUND_MS = 4_000L
    private const val FINGERPRINT_EVERY_MS = 30 * 60_000L
    /** Through the relay a round is slower and counted against a daily limit: every 15 s. */
    private const val CLOUD_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val state = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> get() = state
    private var lastFingerprint = 0L
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

    fun deviceName(): String = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim().ifBlank { "Phone" }.take(40)

    @Synchronized
    fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            var checked: KhataDatabase? = null
            while (isActive) {
                val wait = runCatching {
                    val db = KhataDatabase.get(app)
                    val sql = db.syncSql()
                    if (checked !== db) { LiveSync.check(sql, install(app)); checked = db }
                    once(sql)
                }.getOrElse { e -> state.value = Status.Failed(e.message ?: e.javaClass.simpleName); ROUND_MS * 3 }
                delay(wait)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** One round; how long to wait before the next. */
    private suspend fun once(sql: SyncSql): Long {
        if (LiveSync.identity(sql) == null) { state.value = Status.Off; return ROUND_MS * 3 }
        val target = LiveRound.target(sql) ?: run { state.value = Status.NotPaired; return ROUND_MS * 3 }
        if (LiveControl.halted()) { state.value = Status.Halted; return 60_000L }
        val key = LiveSync.identity(sql)?.key ?: return ROUND_MS
        val now = System.currentTimeMillis()
        val check = now - lastFingerprint > FINGERPRINT_EVERY_MS
        val request = LiveRound.request(sql, fingerprint = check) ?: return ROUND_MS
        val peer = LiveSync.state(sql).peers.firstOrNull()
        val hosts = (listOfNotNull(peer?.host) + target.hosts).distinct()
        return when (val a = LiveWire.round(hosts, target.port, key, request)) {
            is LiveWire.Answer.Got -> {
                val took = LiveRound.take(sql, request, a.reply, a.host)
                took.refused?.let { why -> state.value = Status.Failed(why); return 60_000L }
                // Only a check made while nothing was on its way counts; a busy round asks again in the next.
                took.fingerprintMatches?.let { same ->
                    fingerprintBad.value = !same
                    // A difference is looked at again in five minutes, not thirty.
                    lastFingerprint = if (same) now else now - FINGERPRINT_EVERY_MS + 5 * 60_000L
                }
                state.value = Status.Live(System.currentTimeMillis(), peer?.name ?: target.computer)
                if (took.more) 200L else ROUND_MS
            }
            LiveWire.Answer.NotHere -> { state.value = Status.Waiting; ROUND_MS * 2 }
            LiveWire.Answer.Unreachable -> {
                // Not on the shop's WiFi: the relay, once it has an address (Marhala 2; closed until then).
                when (LiveCloud.round(sql)) {
                    is LiveCloud.Result.Done -> { state.value = Status.Live(System.currentTimeMillis(), peer?.name ?: target.computer); CLOUD_MS }
                    else -> { state.value = Status.Unreachable; ROUND_MS * 3 }
                }
            }
        }
    }
}
