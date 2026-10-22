package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LiveCloud
import com.innovation313.roshankhata.data.LiveRound
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.LiveWire
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
    @Volatile private var server: ServerSocket? = null
    @Volatile private var stopped = false
    /** Each start's own number: a relay loop from an earlier start ends when it sees a newer one. */
    @Volatile private var generation = 0

    /** The secret of the code on the screen right now; null when no Live sync window is open. */
    @Volatile var pairSecret: String? = null

    /** A pairing that arrived and waits for the owner's confirmation. */
    val offers = MutableStateFlow<LiveWire.Offer?>(null)

    /** This computer's own install id, made once and kept beside the books. */
    fun install(home: File): String {
        val f = File(home, "live.install")
        runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.length == 32 }?.let { return it }
        val id = LiveSync.newHex(16)
        runCatching { home.mkdirs(); f.writeText(id) }
        return id
    }

    fun name(): String = PhoneSync.computerName().ifBlank { "Computer" }

    /** The book now open: its live sync checked, and the computer listening when it syncs. */
    suspend fun attach(book: KhataDatabase, home: File) {
        db = book
        val on = runCatching { LiveSync.check(book.syncSql(), install(home)) }.getOrDefault(false)
        if (on) start() else if (pairSecret == null) stop()
    }

    /** Listens (on [LiveWire.PORT] when it is free); the port, or null when nothing could be opened. */
    @Synchronized
    fun start(): Int? {
        server?.let { if (!it.isClosed) return it.localPort }
        val s = runCatching { ServerSocket(LiveWire.PORT) }.getOrNull() ?: runCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = s
        stopped = false
        thread(isDaemon = true, name = "live-sync") {
            LiveWire.serve(
                s,
                stop = { stopped },
                pairSecret = { pairSecret },
                onOffer = { o -> pairSecret = null; offers.value = o; true },
                bookKey = { key() },
                onRound = { r -> answer(r) }
            )
        }
        // The relay, for a phone away from the shop's WiFi (Marhala 2): nothing at all until it has an address.
        val mine = ++generation
        thread(isDaemon = true, name = "live-cloud") {
            while (!stopped && generation == mine) {
                val d = db
                if (d != null && LiveCloud.base != null) runCatching { runBlocking { LiveCloud.round(d.syncSql()) } }
                try { Thread.sleep(CLOUD_MS) } catch (e: InterruptedException) { break }
            }
        }
        return s.localPort
    }

    private const val CLOUD_MS = 15_000L

    @Synchronized
    fun stop() {
        stopped = true
        runCatching { server?.close() }
        server = null
    }

    private fun key(): String? = db?.let { d -> runCatching { runBlocking { LiveSync.identity(d.syncSql())?.key } }.getOrNull() }

    private fun answer(r: JSONObject): JSONObject? = db?.let { d -> runBlocking { LiveRound.answer(d.syncSql(), r) } }

    /**
     * After the owner said yes to the phone's book (it is restored): this
     * computer joins that book's live sync, having read the phone's log up
     * to the line the book was taken at.
     */
    suspend fun join(book: KhataDatabase, home: File, offer: LiveWire.Offer) {
        val sql = book.syncSql()
        LiveSync.enable(sql, install(home), name(), adopt = offer.book to offer.key)
        LiveSync.putPeer(sql, offer.device, name = offer.name, sent = 0, recv = offer.seq, seen = System.currentTimeMillis())
        start()
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
    val port = remember { LiveDesk.start() }
    var state by remember { mutableStateOf<LiveSync.State?>(null) }
    val qr: ImageBitmap? = remember {
        if (port == null || hosts.isEmpty()) null
        else runCatching {
            PhoneSync.qr(LiveWire.inviteText(LiveWire.Invite(hosts, port, secret, PhoneSync.computerName()))).toComposeImageBitmap()
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
                if (port == null || hosts.isEmpty()) Text(word(W.wifi_pc_no_network), color = c.owedToMe, fontSize = 14.sp)
                else Text(hosts.joinToString("  ·  ") + "  :" + port, color = c.textMuted, fontSize = 12.sp)
                val s = state
                val peers = s?.peers.orEmpty()
                if (s == null || !s.on) Text(word(W.live_status_off), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                else if (peers.isEmpty()) Text(word(W.live_pc_none), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                else peers.forEach { p ->
                    Text(
                        word(W.live_status_live, p.name?.takeIf { it.isNotBlank() } ?: "—", p.seen?.let { Format.dateTime(it) } ?: "—"),
                        color = c.brandGreenText, fontSize = 15.sp, fontWeight = FontWeight.Medium
                    )
                }
                if ((s?.conflicts ?: 0) > 0) Text(word(W.live_conflicts, s!!.conflicts), color = c.textMuted, fontSize = 13.sp)
                Text(word(W.wifi_pc_firewall), color = c.textMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s?.on == true) FluentButton(word(W.live_stop), {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { LiveSync.disable(book.syncSql()) } }
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
