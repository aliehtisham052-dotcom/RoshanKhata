package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.BookSettings
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LiveCloud
import com.innovation313.roshankhata.data.LiveControl
import com.innovation313.roshankhata.data.LiveRound
import com.innovation313.roshankhata.data.LiveCrew
import com.innovation313.roshankhata.data.LiveForget
import com.innovation313.roshankhata.data.LiveGive
import com.innovation313.roshankhata.data.LiveLines
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.LiveWire
import com.innovation313.roshankhata.data.NumberMark
import com.innovation313.roshankhata.data.WifiSync
import com.innovation313.roshankhata.data.syncSql
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Live sync on the computer (Marhala 1, 22 Oct 2026): the computer waits on
 * one port (LiveWire.PORT) for the phone's rounds, while this book syncs, and
 * for a phone that scanned its live code while the Live sync window is open.
 * The phone's book that arrives with a pairing goes to the usual restore
 * confirmation (Main: Picked.Ready with its offer); nothing is replaced
 * without the owner's yes, and only then does this computer join the book.
 */
object LiveDesk {

    @Volatile private var db: KhataDatabase? = null
    /** The open book's file name and the settings folder: where its log's high line is kept ([marks]). */
    @Volatile private var file: String? = null
    @Volatile private var home: File? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var stopped = false
    /** Each start's own number: a relay loop from an earlier start ends when it sees a newer one. */
    @Volatile private var generation = 0
    /** When a phone last did a round over the WiFi: the relay waits while the WiFi works. */
    @Volatile private var lastWifi = 0L

    /** The secret of the code on the screen right now; null when no Live sync window is open. */
    @Volatile var pairSecret: String? = null

    /** The Live sync window is open (its code on the screen). */
    fun windowOpen(): Boolean = pairSecret != null

    // ------------------------------------------------------------ giving the book to a phone (LiveGive)

    /** The give code's secret on the screen; taken (one use) the moment a phone asks with it. */
    private val giveSecret = java.util.concurrent.atomic.AtomicReference<String?>(null)
    /** Writes the open book to a file as a backup does, with the shop's name and profile (given by the window). */
    @Volatile private var exporter: (suspend (File) -> Unit)? = null

    /** How giving the book stands, for the Live sync window. */
    sealed interface Giving {
        object Idle : Giving
        /** The give code is on the screen. */
        data class Waiting(val secret: String) : Giving
        /** A phone asked with the code: the book is being made ready and sent. */
        data class Sending(val name: String) : Giving
        /** The phone has the book; it joins once its owner says yes there, the same [code] on both screens. */
        data class Sent(val name: String, val code: String) : Giving
        object Failed : Giving
    }
    val giving = MutableStateFlow<Giving>(Giving.Idle)

    /** A new give code: its secret, good for one phone. [export] writes the book for it. */
    fun openGive(export: suspend (File) -> Unit): String {
        val s = WifiSync.newSecret()
        exporter = export
        giveSecret.set(s)
        giving.value = Giving.Waiting(s)
        return s
    }

    /** The give code leaves the screen (the window closed): no phone can ask with it any more. */
    fun closeGive() {
        giveSecret.set(null)
        exporter = null
        giving.value = Giving.Idle
    }

    /**
     * A phone asked with the give code on the screen: the code is spent, and the book made ready as a phone
     * sender makes it ([LiveGive.prepare]); null refuses (another code, one already used, no book open).
     */
    private fun give(ask: LiveWire.Ask): LiveWire.Gift? {
        if (!giveSecret.compareAndSet(ask.secret, null)) return null
        val d = db
        val h = home
        val f = file
        val export = exporter
        if (d == null || h == null || f == null || export == null) { giving.value = Giving.Failed; return null }
        giving.value = Giving.Sending(ask.name)
        val sql = d.syncSql()
        val prepared = try {
            runBlocking { LiveGive.prepare(sql, d.khataDao(), install(h), name(), marks(h, f), ask.name, ask.host, File(h, "to-phone"), export) }
        } catch (e: Exception) {
            trouble("giving the book", e)
            NumberMark.mark = logged("the book's letter", null) { runBlocking { LiveSync.mark(sql) } }
            giving.value = Giving.Failed
            return null
        }
        // The book syncs from now on (if it did not): its letter numbers this computer's documents.
        NumberMark.mark = logged("the book's letter", null) { runBlocking { LiveSync.mark(sql) } }
        val code = LiveWire.pairCode(ask.secret, prepared.offer.pair!!)
        return LiveWire.Gift(prepared.offer, prepared.file) { kept ->
            logged("giving the book", Unit) { runBlocking { LiveGive.settle(sql, prepared, kept) } }
            if (!kept) NumberMark.mark = logged("the book's letter", null) { runBlocking { LiveSync.mark(sql) } }
            // Said only while the window still shows this give (closed, or a new code made: left as it is).
            if (giving.value is Giving.Sending) giving.value = if (kept) Giving.Sent(ask.name, code) else Giving.Failed
        }
    }

    /** A pairing that arrived with its book read whole, waiting for the owner's answer. */
    class Incoming(
        val offer: LiveWire.Offer,
        val file: File,
        val data: com.innovation313.roshankhata.data.BackupCodec.ParsedBackup,
        val ok: com.innovation313.roshankhata.data.BackupCodec.ImportResult.Ok,
        /**
         * The book file open when it arrived (khata.db / khata_b<id>.db): the question is asked of that business only
         * (23 Oct 2026, the review's R40: after a switch, a yes meant for one shop replaced another's book).
         */
        val bookFile: String? = null
    )

    /**
     * The pairing waiting for the owner's answer. It stays until the owner
     * answers (22 Oct 2026, the review: it was taken off at once, and a lock
     * or a sign-out in between lost the question while the phone waited).
     */
    val offers = MutableStateFlow<Incoming?>(null)

    /** Counts the pairings finished on this computer, so the open book reads itself again. */
    val joined = MutableStateFlow(0L)

    /** A phone's book the owner chose to keep as a new business: taken by that business's book when it opens. */
    @Volatile private var pending: Pair<LiveWire.Offer, com.innovation313.roshankhata.data.BackupCodec.ParsedBackup>? = null

    fun keepPending(offer: LiveWire.Offer, data: com.innovation313.roshankhata.data.BackupCodec.ParsedBackup) { pending = offer to data }

    @Synchronized
    fun takePending(): Pair<LiveWire.Offer, com.innovation313.roshankhata.data.BackupCodec.ParsedBackup>? = pending.also { pending = null }

    /** This computer's own install id, made once and kept beside the books. */
    fun install(home: File): String {
        val f = File(home, "live.install")
        runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.length == 32 }?.let { return it }
        val id = LiveSync.newHex(16)
        runCatching { home.mkdirs(); f.writeText(id) }
        return id
    }

    fun name(): String = PhoneSync.computerName().ifBlank { "Computer" }

    /**
     * How far the log of the book in [file] had come, per book id, kept in
     * [home] (live.marks): a book file put back from an older copy (File
     * History, a USB copy) is caught when it opens and stops syncing instead
     * of handing out line numbers the phone has already passed.
     */
    fun marks(home: File, file: String): LiveSync.LogMarks = FileMarks(File(home, "live.marks"), file)

    private val MARKS_LOCK = Any()

    private class FileMarks(private val store: File, private val file: String) : LiveSync.LogMarks {
        private fun k(book: String) = "$file|$book"
        private fun load() = java.util.Properties().also { p -> runCatching { store.inputStream().use { p.load(it) } } }
        override fun get(book: String): Long? = synchronized(MARKS_LOCK) { load().getProperty(k(book))?.toLongOrNull() }
        override fun put(book: String, seq: Long) {
            synchronized(MARKS_LOCK) {
                val p = load()
                if (p.getProperty(k(book)) == seq.toString()) return
                p.setProperty(k(book), seq.toString())
                runCatching {
                    store.parentFile?.mkdirs()
                    val tmp = File(store.path + ".tmp")
                    tmp.outputStream().use { p.store(it, null) }
                    replaceAtomically(tmp, store)
                }
            }
        }
    }

    /**
     * A live-sync failure a round passed over, written to logs/errors.log ([CrashLog]) — kept to a few lines an
     * hour by [com.innovation313.roshankhata.data.LiveTrouble] (23 Oct 2026, the handover: they were dropped unread).
     */
    private fun trouble(where: String, e: Throwable) {
        com.innovation313.roshankhata.data.LiveTrouble.note(where, e)
    }

    /** [block], its failure written down ([trouble]) and [fallback] given instead — as the runCatching it replaced. */
    private inline fun <T> logged(where: String, fallback: T, block: () -> T): T =
        try { block() } catch (e: Throwable) { trouble(where, e); fallback }

    /** The shared engine's failures (a WiFi round, a pairing) go to this computer's log too. */
    private fun sinkTo(home: File) {
        com.innovation313.roshankhata.data.LiveTrouble.sink = { where, e ->
            CrashLog.write(home, "$where (not a crash: live sync went on)", e)
        }
    }

    /** The book now open: its live sync checked, and the computer listening when it syncs. */
    suspend fun attach(book: KhataDatabase, home: File, file: String) {
        db = book
        this.home = home
        this.file = file
        sinkTo(home)
        // The last book's relay time and WiFi mark are not this one's (23 Oct 2026, the second review).
        relayState.value = RelayState.Unknown
        lastWifi = 0L
        // The last book's letter never numbers this one's documents (NumberMark).
        NumberMark.mark = null
        val on = logged("opening the book", false) { LiveSync.check(book.syncSql(), install(home), marks(home, file)) }
        if (on) NumberMark.mark = logged("the book's letter", null) { LiveSync.mark(book.syncSql()) }
        if (on) start() else if (pairSecret == null) stop()
    }

    /** Listens (on [LiveWire.PORT] when it is free); the port, or null when nothing could be opened. Blocking for up to two seconds. */
    @Synchronized
    fun start(): Int? {
        server?.let { if (!it.isClosed) return it.localPort }
        // The phone looks for the computer on LiveWire.PORT only: a moment after the app was closed Windows may
        // still hold it, so it is asked for a few times (reusing the address) before any other port is taken.
        var s: ServerSocket? = null
        for (attempt in 0 until 5) {
            s = runCatching {
                ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(LiveWire.PORT)) }
            }.getOrNull()
            if (s != null) break
            try { Thread.sleep(400) } catch (e: InterruptedException) { break }
        }
        val sock: ServerSocket = s ?: runCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = sock
        stopped = false
        serveOn(sock)
        // A phone that lost this computer's address calls for it on the same port (UDP): answered here, with
        // proof of the book's key and the port the rounds are served on.
        runCatching {
            java.net.DatagramSocket(null as java.net.SocketAddress?).apply {
                reuseAddress = true; broadcast = true; bind(java.net.InetSocketAddress(LiveWire.PORT))
            }
        }.getOrNull()?.let { u ->
            seeker = u
            thread(isDaemon = true, name = "live-seek") { LiveWire.answerSeeks(u, { stopped }, { key() }, { servedPort() }) }
        }
        // Not on LiveWire.PORT: a phone already joined will not find this port, so the right one is asked
        // for again every ten seconds and served as well once it is free.
        if (sock.localPort != LiveWire.PORT) {
            val mineNow = generation + 1
            thread(isDaemon = true, name = "live-port") {
                while (!stopped && generation == mineNow) {
                    try { Thread.sleep(10_000) } catch (e: InterruptedException) { break }
                    val p = runCatching { ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(LiveWire.PORT)) } }.getOrNull()
                    if (p != null) {
                        synchronized(this) { if (stopped) runCatching { p.close() } else { extra = p; serveOn(p) } }
                        break
                    }
                }
            }
        }
        // The relay, for a phone away from the shop's WiFi (Marhala 2): only when the owner turned it on for
        // this book, never while live sync is stopped for everyone, and only while the phone has not been seen
        // on the WiFi for a while; quiet rounds wait longer and longer (22 Oct 2026, the review: every computer
        // asked every 15 s, all day, against one daily limit shared by every book).
        val mine = ++generation
        thread(isDaemon = true, name = "live-cloud") {
            var wait = CLOUD_MS
            while (!stopped && generation == mine) {
                val d = db
                val now = System.currentTimeMillis()
                // Marked before the round: a nudge landing while it runs still wakes the wait after it.
                val since = LiveLines.newsMark()
                // The kill switch is read here (every 6 h, on this thread) even while the phone is on the WiFi and the
                // relay is not needed: the connection threads only read the cached word (23 Oct 2026).
                logged("the kill switch", false) { LiveControl.halted(now) }
                // A book given to a phone whose word never came in 15 minutes: given up, as the phone gives up its own.
                if (d != null) {
                    val off = logged("a given book's wait", false) {
                        runBlocking { LiveGive.expire(d.syncSql(), now) { LiveCloud.leaveAll(d.syncSql()) } }
                    }
                    if (off) NumberMark.mark = null
                }
                // The phone on this WiFi talks to the computer directly — unless the book has other devices too (another
                // phone away from the shop): each device sends only its own changes, so this one keeps the relay going.
                val alone = d == null || logged("live state", 0) { runBlocking { LiveSync.state(d.syncSql()).peers.size } } <= 1
                wait = if (d == null || LiveCloud.base == null || (now - lastWifi < WIFI_QUIET_MS && alone)) CLOUD_MS
                // Cloudflare first, then the owner's hosting (22 Oct 2026).
                // The later relays are read by time (LiveCloud.othersDue, every two minutes at most), not every eighth
                // round: with the line open a round comes only every five minutes (23 Oct 2026, the review's R83).
                else when (val r = logged("relay round", null) { runBlocking { LiveCloud.roundAll(d.syncSql(), readOthers = LiveCloud.othersDue(d.syncSql(), now)) } }) {
                    is LiveCloud.Result.Done -> {
                        noteRound(d)
                        relayState.value = RelayState.Live(now, r.took)
                        // A device whose parcels come by the owner's hosting: no line, nudge or Firebase there wakes
                        // this computer for its changes, so the HTTP clock (15 s up to a minute) stands (R83).
                        val lineless = runCatching { runBlocking { LiveCloud.peerOnLinelessRoad(d.syncSql()) } }.getOrDefault(false)
                        when {
                            r.more -> 1000L
                            r.took > 0 -> CLOUD_MS
                            lineless -> minOf(wait * 2, CLOUD_MS * 4)
                            // The live line wakes the wait on news: on a clock only every five minutes, to be sure.
                            LiveLines.anyOpen() -> CLOUD_QUIET_MAX_MS
                            else -> minOf(wait * 2, CLOUD_QUIET_MAX_MS)
                        }
                    }
                    LiveCloud.Result.Off, LiveCloud.Result.NotPaired -> { relayState.value = RelayState.Off; CLOUD_OFF_MS }
                    LiveCloud.Result.Halted -> { relayState.value = RelayState.Down(HALTED); minOf(wait * 2, CLOUD_QUIET_MAX_MS) }
                    is LiveCloud.Result.Refused -> if (r.why == LiveRound.LOST) {
                        // The phone asks for lines this book let go of (its book went back to an older copy): only
                        // joining again mends it — the window and the bell then say so (OFF_RESTORED), as on the phone.
                        logged("leaving the relays", Unit) { runBlocking { LiveCloud.leaveAll(d!!.syncSql()) } }
                        logged("turning live sync off", Unit) { runBlocking { LiveSync.disable(d!!.syncSql(), LiveSync.OFF_RESTORED) } }
                        NumberMark.mark = null
                        relayState.value = RelayState.Off
                        CLOUD_OFF_MS
                    } else { relayState.value = RelayState.Down(r.why); minOf(wait * 2, CLOUD_QUIET_MAX_MS) }
                    // Unreachable, or the round threw: the window says so instead of nothing (22 Oct 2026, the review).
                    else -> { relayState.value = RelayState.Down("unreachable"); minOf(wait * 2, CLOUD_QUIET_MAX_MS) }
                }
                try {
                    if (d != null && LiveLines.anyOpen()) {
                        // News: the relay's nudge, or this computer writing something (its log grew).
                        val before = logged("the log's line", 0L) { runBlocking { LiveSync.lastSeq(d.syncSql()) } }
                        // Its own log looked at every 5 s, not every second (R129), as on the phone.
                        LiveLines.waitForNews(wait, since, checkMs = 5_000L) { runBlocking { LiveSync.lastSeq(d.syncSql()) } > before }
                    } else Thread.sleep(wait)
                } catch (e: InterruptedException) { break }
            }
        }
        return sock.localPort
    }

    /** How the relay road stands, for the Live sync window: never silent about a relay that is down. */
    sealed interface RelayState {
        object Unknown : RelayState
        object Off : RelayState
        data class Live(val at: Long, val took: Int) : RelayState
        data class Down(val why: String) : RelayState
    }
    val relayState = MutableStateFlow<RelayState>(RelayState.Unknown)
    /** [RelayState.Down]'s word for the kill switch: the window says live sync is paused, not that a relay is down. */
    const val HALTED = "halted"

    private const val CLOUD_MS = 15_000L
    /** A quiet relay is asked at most every five minutes. */
    private const val CLOUD_QUIET_MAX_MS = 5 * 60_000L
    /** The relay not turned on for this book: looked at again every minute. */
    private const val CLOUD_OFF_MS = 60_000L
    /** The phone did a WiFi round this recently: the relay is not needed. */
    private const val WIFI_QUIET_MS = 2 * 60_000L

    /** The port the phone knows, opened late after another was taken first. */
    @Volatile private var extra: ServerSocket? = null
    /** Where phones' calls for this computer are answered. */
    @Volatile private var seeker: java.net.DatagramSocket? = null

    /** The port rounds are served on: the phone's own when it is open, else the one taken instead. */
    private fun servedPort(): Int = extra?.takeIf { !it.isClosed }?.localPort ?: server?.localPort ?: LiveWire.PORT

    private fun serveOn(sock: ServerSocket) {
        thread(isDaemon = true, name = "live-sync") {
            LiveWire.serve(
                sock,
                stop = { stopped },
                pairSecret = { pairSecret },
                onOffer = { o -> takeOffer(o) },
                bookKey = { key() },
                // A pairing's book arrives onto this computer's own folder, never into memory (23 Oct 2026).
                spool = home?.let { File(it, "from-phone") },
                // A phone that scanned the give code asks for this book (LiveGive).
                giveSecret = { giveSecret.get() },
                onGive = { a -> give(a) },
                onRound = { r -> answer(r) }
            )
        }
    }

    /**
     * A phone's pairing: its book saved and read whole before the phone is
     * told it was taken (22 Oct 2026, the review: "taken" was said before
     * the book was even read, and a book that did not read left the phone
     * waiting for ever). One pairing per code on the screen.
     */
    private fun takeOffer(o: LiveWire.Offer): Boolean {
        val h = home ?: return false
        val read = logged("a pairing's book", null) {
            val arrived = o.file
            val f = if (arrived != null) PhoneSync.saveIncomingFile(h, arrived) else PhoneSync.saveIncoming(h, o.backup)
            val (r, d) = com.innovation313.roshankhata.data.BackupCodec.parseFile(f)
            if (r is com.innovation313.roshankhata.data.BackupCodec.ImportResult.Ok && d != null) Incoming(o.withFile(null), f, d, r, file) else null
        } ?: return false
        pairSecret = null
        offers.value = read
        return true
    }

    /** The book is about to close (a switch, the app closing): nothing of live sync holds it any more. */
    @Synchronized
    fun detach(book: KhataDatabase) {
        if (db !== book) return
        // A give code shown for this book gives nothing once it closed.
        giveSecret.set(null)
        stop()
        db = null
        relayState.value = RelayState.Unknown
    }

    @Synchronized
    fun stop() {
        stopped = true
        runCatching { LiveLines.closeAll() }
        runCatching { server?.close() }
        runCatching { extra?.close() }
        runCatching { seeker?.close() }
        server = null
        extra = null
        seeker = null
    }

    private fun key(): String? = db?.let { d -> logged("the book's key", null) { runBlocking { LiveSync.identity(d.syncSql())?.key } } }

    private fun answer(r: JSONObject): JSONObject? {
        val d = db ?: return null
        // Stopped for everyone (LiveControl): the computer answers nothing, as the phone asks nothing. The word as
        // last read — the network read is the cloud thread's, never a phone's connection waiting 10 s for GitHub.
        if (LiveControl.haltedCached()) return null
        val reply = runBlocking { LiveRound.answer(d.syncSql(), r) } ?: return null
        lastWifi = System.currentTimeMillis()
        noteRound(d)
        return reply
    }

    /** After a round: this book's letter (it may have moved, LiveSync.settleMark) and its log's high line. */
    private fun noteRound(d: KhataDatabase) {
        logged("after a round", Unit) {
            runBlocking {
                val sql = d.syncSql()
                NumberMark.mark = LiveSync.mark(sql)
                val f = file
                val h = home
                if (f != null && h != null) LiveSync.noteMark(sql, marks(h, f))
            }
        }
    }

    /**
     * After the owner said yes to the phone's book (it is restored): this
     * computer joins that book's live sync, having read the phone's log up
     * to the line the book was taken at, for that pairing only.
     */
    suspend fun join(book: KhataDatabase, home: File, file: String, offer: LiveWire.Offer) {
        val sql = book.syncSql()
        sinkTo(home)
        // The sender, and every other device it knew as far as the copy held of each (LiveCrew, 23 Oct 2026); one
        // transaction, so a failure leaves the book as the restore made it, not half joined.
        LiveCrew.join(sql, install(home), name(), offer, offer.peers, marks(home, file))
        NumberMark.mark = LiveSync.mark(sql)
        db = book
        this.home = home
        this.file = file
        start()
        joined.value = joined.value + 1
    }
}

/**
 * The Live sync window: the code a phone scans to join (Backup → Live sync
 * with the computer → Join the computer), and how live sync stands. With
 * [export] (the book written as a backup is), it can also show a give code:
 * a new phone takes this computer's book (LiveGive, 23 Oct 2026).
 */
@Composable
fun LiveSyncDialog(book: KhataDatabase, onClose: () -> Unit, export: (suspend (File) -> Unit)? = null) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val secret = remember { WifiSync.newSecret() }
    val hosts = remember { PhoneSync.lanAddresses() }
    // The port is opened away from the window's thread (it may wait up to two seconds for Windows to let go
    // of it, 22 Oct 2026, the review: the window froze while it did).
    var port by remember { mutableStateOf<Int?>(null) }
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        port = withContext(Dispatchers.IO) { LiveDesk.start() }
        opening = false
    }
    var state by remember { mutableStateOf<LiveSync.State?>(null) }
    // The relay on the internet: one switch for the book (BookSettings.LIVE_RELAY), off until the owner turns it on.
    var relay by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        relay = withContext(Dispatchers.IO) { runCatching { book.khataDao().settingValue(BookSettings.LIVE_RELAY) == "1" }.getOrDefault(false) }
    }
    // The conflicts opened for reading (empty until "See what changed").
    var views by remember { mutableStateOf<List<LiveSync.ConflictView>>(emptyList()) }
    // A joined device the owner is about to remove: (device id, its name).
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    val qr: ImageBitmap? = remember(port) {
        val p = port
        if (p == null || hosts.isEmpty()) null
        else runCatching {
            PhoneSync.qr(LiveWire.inviteText(LiveWire.Invite(hosts, p, secret, PhoneSync.computerName()))).toComposeImageBitmap()
        }.getOrNull()
    }
    // Giving this computer's book to a phone: the give code in place of the join code while it is shown.
    val giving by LiveDesk.giving.collectAsState()
    val giveQr: ImageBitmap? = remember(port, giving) {
        val p = port
        val g = giving
        if (p == null || hosts.isEmpty() || g !is LiveDesk.Giving.Waiting) null
        else runCatching {
            PhoneSync.qr(LiveWire.inviteText(LiveWire.Invite(hosts, p, g.secret, PhoneSync.computerName(), give = true))).toComposeImageBitmap()
        }.getOrNull()
    }
    // The book given: the relay was turned on for it before the copy (LiveGive.prepare).
    LaunchedEffect(giving) { if (giving is LiveDesk.Giving.Sent) relay = true }
    DisposableEffect(Unit) {
        LiveDesk.pairSecret = secret
        onDispose {
            LiveDesk.closeGive()
            if (LiveDesk.pairSecret == secret) LiveDesk.pairSecret = null
            // Not syncing a book: no reason to keep a port open once the window is gone.
            CoroutineScope(Dispatchers.IO).launch {
                if (runCatching { LiveSync.identity(book.syncSql()) }.getOrNull() == null) LiveDesk.stop()
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            state = withContext(Dispatchers.IO) { runCatching { LiveSync.state(book.syncSql()) }.getOrNull() }
            delay(2000)
        }
    }

    removing?.let { (device, name) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(word(W.live_remove_confirm, name)) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    removing = null
                    scope.launch {
                        state = withContext(Dispatchers.IO) {
                            runCatching { LiveForget.device(book.syncSql(), device); LiveSync.state(book.syncSql()) }.getOrNull()
                        }
                    }
                }) { Text(word(W.live_remove), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ removing = null }) { Text(word(W.cancel)) } }
        )
    }

    AppDialog(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = word(W.live_title), state = rememberDialogState(width = fitW(560.dp), height = fitH(860.dp))) {
        Box(Modifier.fillMaxSize().background(c.surface)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(word(W.live_title), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(word(W.live_explain), color = c.textMuted, fontSize = 13.sp)
                val g = giving
                if (g is LiveDesk.Giving.Idle) {
                    Text(word(W.live_pc_steps), color = c.ink, fontSize = 14.sp)
                    qr?.let { Image(it, contentDescription = word(W.live_title), modifier = Modifier.size(300.dp)) }
                    if (export != null && port != null && hosts.isNotEmpty()) {
                        FluentButton(word(W.live_give), { LiveDesk.openGive(export) }, kind = ButtonKind.STANDARD)
                    }
                } else {
                    Text(word(W.live_give), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(word(W.live_give_steps), color = c.ink, fontSize = 14.sp)
                    giveQr?.let { Image(it, contentDescription = word(W.live_give), modifier = Modifier.size(300.dp)) }
                    when (g) {
                        is LiveDesk.Giving.Sending -> Text(
                            word(W.live_give_sending, Format.isolate(g.name.ifBlank { "—" })), color = c.ink, fontSize = 14.sp
                        )
                        is LiveDesk.Giving.Sent -> Text(
                            word(W.live_give_sent, Format.isolate(g.name.ifBlank { "—" }), g.code),
                            color = c.brandGreenText, fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                        LiveDesk.Giving.Failed -> Text(word(W.live_give_failed), color = c.owedToMe, fontSize = 14.sp)
                        else -> {}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ((g is LiveDesk.Giving.Sent || g == LiveDesk.Giving.Failed) && export != null) {
                            FluentButton(word(W.live_give_new_code), { LiveDesk.openGive(export) }, kind = ButtonKind.STANDARD)
                        }
                        if (g !is LiveDesk.Giving.Sending) FluentButton(word(W.back), { LiveDesk.closeGive() }, kind = ButtonKind.SUBTLE)
                    }
                }
                if (!opening && (port == null || hosts.isEmpty())) Text(word(W.wifi_pc_no_network), color = c.owedToMe, fontSize = 14.sp)
                else if (port != null) Text(hosts.joinToString("  ·  ") + "  :" + port, color = c.textMuted, fontSize = 12.sp)
                val s = state
                val peers = s?.peers.orEmpty()
                val lock = LiveSync.lockTrouble
                if (s != null && s.on && s.identity == null && lock != null) Text(
                    word(W.live_status_key_busy, lock), color = c.owedToMe, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                else if (s == null || !s.on) Text(
                    word(if (s?.offWhy == LiveSync.OFF_RESTORED) W.live_status_rejoin else W.live_status_off),
                    color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                else if (peers.isEmpty()) Text(word(W.live_pc_none), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                else {
                    // The relay road, as it stands: live, off, or down with the relay's own word.
                    val rs by LiveDesk.relayState.collectAsState()
                    if (relay) when (val r = rs) {
                        is LiveDesk.RelayState.Live -> Text(word(W.live_pc_relay_ok, Format.dateTime(r.at)), color = c.brandGreenText, fontSize = 13.sp)
                        // The kill switch is its own sentence; a reason code in the owner's language (LiveWhy, as the phone).
                        is LiveDesk.RelayState.Down -> Text(
                            if (r.why == LiveDesk.HALTED) word(W.live_status_halted)
                            else word(W.live_pc_relay_down, Format.isolate(com.innovation313.roshankhata.data.LiveWhy.text(com.innovation313.roshankhata.words.LocalWords.current, r.why))),
                            color = c.owedToMe, fontSize = 13.sp
                        )
                        else -> {}
                    }
                    Text(word(W.live_devices), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    val now = System.currentTimeMillis()
                    peers.forEach { p ->
                        val name = p.name?.takeIf { it.isNotBlank() } ?: "—"
                        // "Live" only for a device heard within its daily heartbeat (25 Oct 2026, the audit: a phone
                        // silent for weeks was shown green and "Live"); else when it was last seen, red past a week.
                        val heard = p.seen?.let { Format.dateTime(it) }
                        val (line, colour) = when (peerState(p.seen, now)) {
                            PeerState.LIVE -> word(W.live_status_live, name, heard ?: "—") to c.brandGreenText
                            PeerState.QUIET -> (name + "  ·  " + word(W.live_device_seen, heard ?: "—")) to c.ink
                            PeerState.STALE -> (name + "  ·  " + word(W.live_device_seen, heard ?: "—")) to c.owedToMe
                            PeerState.NEVER -> (name + "  ·  " + word(W.live_device_never)) to c.textMuted
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                line,
                                color = colour, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            FluentButton(word(W.live_remove), { removing = p.device to name }, kind = ButtonKind.SUBTLE)
                        }
                    }
                }
                if (s != null && s.on) {
                    // Also through the internet: the owner's choice, said plainly (22 Oct 2026, the review).
                    Row(
                        Modifier.fillMaxWidth().press {
                            val next = !relay
                            relay = next
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { book.khataDao().setSetting(BookSettings.LIVE_RELAY, if (next) "1" else null) }
                                }
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(relay, onCheckedChange = null)
                        Text(word(W.live_relay), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(word(W.live_relay_help), color = c.textMuted, fontSize = 12.sp)
                    if (s.held > 0) Text(word(W.live_held, s.held), color = c.owedToMe, fontSize = 13.sp)
                }
                if ((s?.conflicts ?: 0) > 0) {
                    Text(word(W.live_conflicts, s!!.conflicts), color = c.textMuted, fontSize = 13.sp)
                    FluentButton(word(W.live_conflicts_show), {
                        scope.launch { views = withContext(Dispatchers.IO) { runCatching { LiveSync.conflictViews(book.syncSql()) }.getOrDefault(emptyList()) } }
                    }, kind = ButtonKind.STANDARD)
                }
                // What changed on both at once: each field as it is now and as the other version had it.
                views.forEach { v ->
                    Column(
                        Modifier.fillMaxWidth().background(c.pageBg, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(tableWord(v.table) + "  ·  " + Format.dateTime(v.at), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        v.fields.take(8).forEach { (field, now, saved) ->
                            Text(field, color = c.textMuted, fontSize = 12.sp)
                            Text(word(W.live_conflict_now, now ?: "—"), color = c.ink, fontSize = 13.sp)
                            Text(word(W.live_conflict_saved, saved ?: "—"), color = c.owedToMe, fontSize = 13.sp)
                        }
                        FluentButton(word(W.live_conflict_clear), {
                            scope.launch {
                                withContext(Dispatchers.IO) { runCatching { LiveSync.clearConflict(book.syncSql(), v.id) } }
                                views = views.filter { it.id != v.id }
                                state = withContext(Dispatchers.IO) { runCatching { LiveSync.state(book.syncSql()) }.getOrNull() }
                            }
                        }, kind = ButtonKind.SUBTLE)
                    }
                }
                Text(word(W.wifi_pc_firewall), color = c.textMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s?.on == true) FluentButton(word(W.live_stop), {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { LiveCloud.leaveAll(book.syncSql()) }
                                runCatching { LiveSync.disable(book.syncSql()) }
                            }
                            NumberMark.mark = null
                            LiveDesk.stop()
                            onClose()
                        }
                    }, kind = ButtonKind.DANGER)
                    FluentButton(word(W.close), onClose, kind = ButtonKind.STANDARD)
                }
            }
        }
    }
}

/** A synced table in the owner's words where the app has them; otherwise its own name. */
@Composable
private fun tableWord(table: String): String = when (table) {
    "parties" -> word(W.customer)
    "transactions" -> word(W.nav_khata)
    "cashbook" -> word(W.cashbook)
    "cheques" -> word(W.cheques)
    "invoices" -> word(W.invoices)
    "supplier_bills" -> word(W.supplier_bills)
    "products" -> word(W.tile_products)
    "staff" -> word(W.staff_title)
    else -> table
}

/** How a joined device stands in the Live sync window, by when it was last heard. */
internal enum class PeerState { LIVE, QUIET, STALE, NEVER }

/**
 * Live within a day and two hours (a device says something at least daily,
 * [com.innovation313.roshankhata.data.LiveCloud.HEARTBEAT_MS]); quiet up to a
 * week; stale past [com.innovation313.roshankhata.data.LiveCloud.STALE_MS],
 * when the others stop holding their changes for it.
 */
internal fun peerState(seen: Long?, now: Long): PeerState = when {
    seen == null -> PeerState.NEVER
    now - seen <= com.innovation313.roshankhata.data.LiveCloud.HEARTBEAT_MS + 2 * 3_600_000L -> PeerState.LIVE
    now - seen <= com.innovation313.roshankhata.data.LiveCloud.STALE_MS -> PeerState.QUIET
    else -> PeerState.STALE
}
