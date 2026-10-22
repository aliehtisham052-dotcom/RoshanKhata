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

    /** A pairing that arrived with its book read whole, waiting for the owner's answer. */
    class Incoming(
        val offer: LiveWire.Offer,
        val file: File,
        val data: com.innovation313.roshankhata.data.BackupCodec.ParsedBackup,
        val ok: com.innovation313.roshankhata.data.BackupCodec.ImportResult.Ok
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

    /** The book now open: its live sync checked, and the computer listening when it syncs. */
    suspend fun attach(book: KhataDatabase, home: File, file: String) {
        db = book
        this.home = home
        this.file = file
        // The last book's letter never numbers this one's documents (NumberMark).
        NumberMark.mark = null
        val on = runCatching { LiveSync.check(book.syncSql(), install(home), marks(home, file)) }.getOrDefault(false)
        if (on) NumberMark.mark = runCatching { LiveSync.mark(book.syncSql()) }.getOrNull()
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
            var rounds = 0
            while (!stopped && generation == mine) {
                val d = db
                val now = System.currentTimeMillis()
                wait = if (d == null || LiveCloud.base == null || now - lastWifi < WIFI_QUIET_MS) CLOUD_MS
                // Cloudflare first, then Supabase, then the owner's hosting (22 Oct 2026); the later ones read every few rounds.
                else when (val r = runCatching { runBlocking { LiveCloud.roundAll(d.syncSql(), readOthers = ++rounds % OTHERS_EVERY == 0) } }.getOrNull()) {
                    is LiveCloud.Result.Done -> {
                        noteRound(d)
                        relayState.value = RelayState.Live(now, r.took)
                        if (r.took > 0 || r.more) CLOUD_MS else minOf(wait * 2, CLOUD_QUIET_MAX_MS)
                    }
                    LiveCloud.Result.Off, LiveCloud.Result.NotPaired -> { relayState.value = RelayState.Off; CLOUD_OFF_MS }
                    LiveCloud.Result.Halted -> { relayState.value = RelayState.Down("halted"); minOf(wait * 2, CLOUD_QUIET_MAX_MS) }
                    is LiveCloud.Result.Refused -> if (r.why == LiveRound.LOST) {
                        // The phone asks for lines this book let go of (its book went back to an older copy): only
                        // joining again mends it — the window then says so (OFF_RESTORED), as the WiFi road does.
                        runCatching { runBlocking { LiveSync.disable(d!!.syncSql(), LiveSync.OFF_RESTORED) } }
                        relayState.value = RelayState.Off
                        CLOUD_OFF_MS
                    } else { relayState.value = RelayState.Down(r.why); minOf(wait * 2, CLOUD_QUIET_MAX_MS) }
                    // Unreachable, or the round threw: the window says so instead of nothing (22 Oct 2026, the review).
                    else -> { relayState.value = RelayState.Down("unreachable"); minOf(wait * 2, CLOUD_QUIET_MAX_MS) }
                }
                try { Thread.sleep(wait) } catch (e: InterruptedException) { break }
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

    private const val CLOUD_MS = 15_000L
    /** The relays after the first are read every this many relay rounds (each costs a call on a free plan). */
    private const val OTHERS_EVERY = 8
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
        val read = runCatching {
            val f = PhoneSync.saveIncoming(h, o.backup)
            val (r, d) = com.innovation313.roshankhata.data.BackupCodec.parseFile(f)
            if (r is com.innovation313.roshankhata.data.BackupCodec.ImportResult.Ok && d != null) Incoming(o, f, d, r) else null
        }.getOrNull() ?: return false
        pairSecret = null
        offers.value = read
        return true
    }

    @Synchronized
    fun stop() {
        stopped = true
        runCatching { server?.close() }
        runCatching { extra?.close() }
        runCatching { seeker?.close() }
        server = null
        extra = null
        seeker = null
    }

    private fun key(): String? = db?.let { d -> runCatching { runBlocking { LiveSync.identity(d.syncSql())?.key } }.getOrNull() }

    private fun answer(r: JSONObject): JSONObject? {
        val d = db ?: return null
        // Stopped for everyone (LiveControl): the computer answers nothing, as the phone asks nothing.
        if (LiveControl.halted()) return null
        val reply = runBlocking { LiveRound.answer(d.syncSql(), r) } ?: return null
        lastWifi = System.currentTimeMillis()
        noteRound(d)
        return reply
    }

    /** After a round: this book's letter (it may have moved, LiveSync.settleMark) and its log's high line. */
    private fun noteRound(d: KhataDatabase) {
        runCatching {
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
        LiveSync.enable(sql, install(home), name(), adopt = offer.book to offer.key, mark = offer.mark, marks = marks(home, file))
        NumberMark.mark = LiveSync.mark(sql)
        LiveSync.putPeer(sql, offer.device, name = offer.name, sent = 0, recv = offer.seq, seen = System.currentTimeMillis())
        LiveSync.setPeerPair(sql, offer.device, offer.pair)
        db = book
        this.home = home
        this.file = file
        start()
        joined.value = joined.value + 1
    }
}

/**
 * The Live sync window: the code a phone scans to join (Backup → Live sync
 * with the computer → Join the computer), and how live sync stands.
 */
@Composable
fun LiveSyncDialog(book: KhataDatabase, onClose: () -> Unit) {
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
    DisposableEffect(Unit) {
        LiveDesk.pairSecret = secret
        onDispose {
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
                            runCatching { LiveSync.removePeer(book.syncSql(), device); LiveSync.state(book.syncSql()) }.getOrNull()
                        }
                    }
                }) { Text(word(W.live_remove), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ removing = null }) { Text(word(W.cancel)) } }
        )
    }

    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = word(W.live_title), state = rememberDialogState(width = fitW(560.dp), height = fitH(860.dp))) {
        Box(Modifier.fillMaxSize().background(c.surface)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(word(W.live_title), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(word(W.live_explain), color = c.textMuted, fontSize = 13.sp)
                Text(word(W.live_pc_steps), color = c.ink, fontSize = 14.sp)
                qr?.let { Image(it, contentDescription = word(W.live_title), modifier = Modifier.size(300.dp)) }
                if (!opening && (port == null || hosts.isEmpty())) Text(word(W.wifi_pc_no_network), color = c.owedToMe, fontSize = 14.sp)
                else if (port != null) Text(hosts.joinToString("  ·  ") + "  :" + port, color = c.textMuted, fontSize = 12.sp)
                val s = state
                val peers = s?.peers.orEmpty()
                if (s == null || !s.on) Text(
                    word(if (s?.offWhy == LiveSync.OFF_RESTORED) W.live_status_rejoin else W.live_status_off),
                    color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                else if (peers.isEmpty()) Text(word(W.live_pc_none), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                else {
                    // The relay road, as it stands: live, off, or down with the relay's own word.
                    val rs by LiveDesk.relayState.collectAsState()
                    if (relay) when (val r = rs) {
                        is LiveDesk.RelayState.Live -> Text(word(W.live_pc_relay_ok, Format.dateTime(r.at)), color = c.brandGreenText, fontSize = 13.sp)
                        is LiveDesk.RelayState.Down -> Text(word(W.live_pc_relay_down, r.why), color = c.owedToMe, fontSize = 13.sp)
                        else -> {}
                    }
                    Text(word(W.live_devices), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    peers.forEach { p ->
                        val name = p.name?.takeIf { it.isNotBlank() } ?: "—"
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                word(W.live_status_live, name, p.seen?.let { Format.dateTime(it) } ?: "—"),
                                color = c.brandGreenText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
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
