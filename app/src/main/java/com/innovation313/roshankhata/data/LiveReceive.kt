package com.innovation313.roshankhata.data

import android.content.Context
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * A phone taking a shop's book from another phone (23 Oct 2026, the owner:
 * "ek dukan ke teen mobile"). Until now only the computer could show the live
 * code and take a book; now a phone can too.
 *
 * While the window is open this phone listens on the shop's WiFi for one
 * pairing frame, sealed with the code's one-time secret ([LiveWire.serve],
 * the computer's own code path), keeps the book that arrives in its cache and
 * hands it to the screen, which asks the owner (the four-digit code, what this
 * phone would lose) before anything is replaced. A phone serves no rounds
 * after: once joined ([LiveCrew.join]) it meets the shop's other devices
 * through the relay, with its live line and Firebase.
 */
class LiveReceive(private val context: Context) {

    /** A book that arrived: the offer (with the pairing code) and the copy, read and checked. */
    class Arrived(val offer: LiveWire.Offer, val file: File, val counts: BackupCodec.ImportResult.Ok, val data: BackupCodec.ParsedBackup)

    @Volatile private var stopped = false
    @Volatile private var secret: String? = null
    private var server: ServerSocket? = null

    /** This phone's addresses on the WiFi; empty when it is on none. */
    fun lanAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            // Mobile data's interfaces (rmnet, ccmni…) are never a way for a phone on the same WiFi.
            .filter { n -> listOf("rmnet", "ccmni", "pdp", "radio").none { (n.name ?: "").lowercase().startsWith(it) } }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .take(4)
    }.getOrDefault(emptyList())

    /**
     * Opens the code: the QR's text, or null when this phone is on no WiFi. [onArrived] is called (on a
     * background thread) once with the book that came; a frame that does not open is refused and forgotten.
     */
    fun open(deviceName: String, onArrived: (Arrived) -> Unit): String? {
        val hosts = lanAddresses()
        if (hosts.isEmpty()) return null
        val s = runCatching {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) }
        }.getOrNull() ?: return null
        server = s
        val code = LiveSync.newHex(32)
        secret = code
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        thread(isDaemon = true, name = "live-receive") {
            LiveWire.serve(
                s,
                stop = { stopped },
                pairSecret = { secret },
                onOffer = { offer ->
                    val read = runCatching {
                        val f = File(dir, "live-receive.json")
                        // The book arrived onto the disk record by record (LiveWire, 23 Oct 2026): moved here, never read whole.
                        val arrived = offer.file
                        if (arrived != null) {
                            f.delete()
                            if (!arrived.renameTo(f)) { arrived.copyTo(f, overwrite = true); arrived.delete() }
                        } else f.writeBytes(offer.backup)
                        val (r, d) = BackupCodec.parseFile(f)
                        if (r is BackupCodec.ImportResult.Ok && d != null) Arrived(offer.withFile(null), f, r, d) else null
                    }.getOrNull()
                    if (read != null) {
                        // One pairing per code: the secret goes once a book came.
                        secret = null
                        onArrived(read)
                        true
                    } else false
                },
                bookKey = { null },
                spool = dir,
                onRound = { null }
            )
        }
        return LiveWire.inviteText(LiveWire.Invite(hosts, s.localPort, code, deviceName, phone = true))
    }

    /** Stops listening; the arrived copy, if any, is removed from the cache. */
    fun close() {
        stopped = true
        secret = null
        runCatching { server?.close() }
        runCatching { File(context.cacheDir, "backups/live-receive.json").delete() }
    }

    companion object {
        /**
         * The owner said yes: this phone's book kept as a copy first (no copy, no replace), then replaced by
         * the shop's, then joined to the shop's devices. Returns false when the copy could not be kept.
         */
        suspend fun take(context: Context, arrived: Arrived): Boolean {
            val app = context.applicationContext
            if (!Snapshots.keepNow(app)) return false
            val dao = KhataDatabase.get(app).khataDao()
            Backup.restore(app, dao, arrived.data)
            val sql = KhataDatabase.get(app).syncSql()
            LiveCrew.join(sql, LiveRunner.install(app), LiveRunner.deviceName(), arrived.offer, arrived.offer.peers, LiveRunner.marks(app))
            NumberMark.mark = LiveSync.mark(sql)
            runCatching { arrived.file.delete() }
            // The rounds begin at once (through the relay: a phone has no WiFi road of its own).
            LiveRunner.stop()
            LiveRunner.start(app)
            return true
        }
    }
}
