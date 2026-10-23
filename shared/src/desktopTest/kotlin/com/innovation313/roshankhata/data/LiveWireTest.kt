package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread

/**
 * Live sync over a real socket (Marhala 1): the computer's server and the
 * phone's rounds, as on the shop's WiFi but on this machine's loopback.
 */
class LiveWireTest {

    @Test
    fun `a sealed round trip opens only with the book's key`() {
        val key = LiveSync.newHex(32)
        val sealed = LiveWire.seal(key, "Rs 5,000".toByteArray(), LiveWire.Road.REQUEST)
        assertEquals("Rs 5,000", String(LiveWire.open(key, sealed, LiveWire.Road.REQUEST)!!))
        assertNull(LiveWire.open(LiveSync.newHex(32), sealed, LiveWire.Road.REQUEST))
        val changed = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(LiveWire.open(key, changed, LiveWire.Road.REQUEST))
        // A request played back as the reply, or pushed through the relay, does not open: each road its own key.
        assertNull(LiveWire.open(key, sealed, LiveWire.Road.REPLY))
        assertNull(LiveWire.open(key, sealed, LiveWire.Road.RELAY))
    }

    /** The road keys' HKDF-SHA256 is RFC 5869's: its test case A.1, the first block of OKM. */
    @Test
    fun `the road keys come from RFC 5869's HKDF`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf",
            LiveWire.hkdf(ikm, info, salt).joinToString("") { "%02x".format(it) }
        )
        // With the app's own salt: the same in, the same out; another label, another key.
        val a = LiveWire.hkdf(ikm, "x".toByteArray()); val b = LiveWire.hkdf(ikm, "y".toByteArray())
        assertArrayEquals(a, LiveWire.hkdf(ikm, "x".toByteArray()))
        assertTrue(!a.contentEquals(b))
    }

    @Test
    fun `the phone finds its computer again by proof of the book's key, with the port it serves on`() {
        val key = LiveSync.newHex(32)
        val socket = java.net.DatagramSocket(0, InetAddress.getLoopbackAddress())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = thread { LiveWire.answerSeeks(socket, { stop.get() }, { key }, { 51234 }) }
        try {
            val here = listOf(InetAddress.getLoopbackAddress())
            assertEquals(LiveWire.Found("127.0.0.1", 51234), LiveWire.seek(key, socket.localPort, here, 2000))
            // A phone of another book is not answered as this one's: the proof is made with the key.
            assertNull(LiveWire.seek(LiveSync.newHex(32), socket.localPort, here, 500))
        } finally {
            stop.set(true); socket.close(); worker.join(3000)
        }
    }

    @Test
    fun `the computer's answer is good for one call only, and nothing on the wire repeats`() {
        val key = LiveSync.newHex(32)
        val a = LiveWire.hereTag(key, ByteArray(16) { 1 }, 47313)
        val b = LiveWire.hereTag(key, ByteArray(16) { 2 }, 47313)
        val c = LiveWire.hereTag(key, ByteArray(16) { 1 }, 47314)
        assertTrue(!a.contentEquals(b) && !a.contentEquals(c))
        assertTrue(a.contentEquals(LiveWire.hereTag(key, ByteArray(16) { 1 }, 47313)))
    }

    @Test
    fun `a round frame longer than any round is let go before anything is read`() {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val key = LiveSync.newHex(32)
        val worker = thread { LiveWire.serve(server, { stop.get() }, { null }, { false }, { key }) { null } }
        try {
            java.net.Socket("127.0.0.1", server.localPort).use { s ->
                s.soTimeout = 5000
                val out = java.io.DataOutputStream(s.getOutputStream())
                out.write("RKL1".toByteArray()); out.writeInt(Int.MAX_VALUE); out.flush()
                // Answered with nothing at once, not after reading two gigabytes.
                assertEquals(0, java.io.DataInputStream(s.getInputStream()).readInt())
            }
            // A pairing frame while no code is on the screen is refused without being read.
            java.net.Socket("127.0.0.1", server.localPort).use { s ->
                s.soTimeout = 5000
                val out = java.io.DataOutputStream(s.getOutputStream())
                out.write("RKA1".toByteArray()); out.writeInt(100 * 1024 * 1024); out.flush()
                assertEquals(LiveWire.BAD, s.getInputStream().read())
            }
        } finally {
            stop.set(true); runCatching { server.close() }; worker.join(5000)
        }
    }

    /**
     * 23 Oct 2026 (the handover): a pairing frame was gathered whole — up to 256 MB — before the code's secret was
     * checked. Now its identity is a small sealed record, opened first: a stranger who announces a big one, or
     * whose small one does not open, is refused at once, and the old one-frame pairing is refused unread.
     */
    @Test
    fun `a stranger's pairing frame is let go before anything big is read, while the code is on the screen`() {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val secret = WifiSync.newSecret()
        val offered = java.util.concurrent.atomic.AtomicInteger(0)
        val spool = Files.createTempDirectory("rk-spool").toFile()
        val worker = thread { LiveWire.serve(server, { stop.get() }, { secret }, { offered.incrementAndGet(); true }, { null }, spool) { null } }
        try {
            // A head record that says 200 MB.
            java.net.Socket("127.0.0.1", server.localPort).use { s ->
                s.soTimeout = 5000
                val out = java.io.DataOutputStream(s.getOutputStream())
                out.write("RKA2".toByteArray()); out.write(ByteArray(8)); out.writeInt(200 * 1024 * 1024); out.writeByte(0); out.flush()
                assertEquals(LiveWire.BAD, s.getInputStream().read())
            }
            // A small head sealed with another secret (whoever photographed an older code): refused on the head alone.
            java.net.Socket("127.0.0.1", server.localPort).use { s ->
                s.soTimeout = 5000
                val out = java.io.DataOutputStream(s.getOutputStream().buffered())
                out.write("RKA2".toByteArray())
                val o = LiveWire.Offer("b".repeat(32), LiveSync.newHex(32), "d1", "Phone", 7, ByteArray(0))
                SealedChunks.write(out, WifiSync.chunkKey(WifiSync.newSecret()), "RKA2".toByteArray(), LiveWire.offerHead(o), null)
                assertEquals(LiveWire.BAD, s.getInputStream().read())
            }
            // The one-frame pairing of before, with the right secret: refused unread (update both devices).
            java.net.Socket("127.0.0.1", server.localPort).use { s ->
                s.soTimeout = 5000
                val sealed = WifiSync.seal(secret, LiveWire.encodeOffer(LiveWire.Offer("b".repeat(32), LiveSync.newHex(32), "d1", "Phone", 7, ByteArray(10))))
                val out = java.io.DataOutputStream(s.getOutputStream())
                out.write("RKA1".toByteArray()); out.writeInt(sealed.size); out.write(sealed); out.flush()
                assertEquals(LiveWire.BAD, s.getInputStream().read())
            }
            assertEquals(0, offered.get())
            // Nothing a refused frame brought stays on the disk.
            Thread.sleep(200)
            assertEquals(0, spool.listFiles()?.size ?: 0)
        } finally {
            stop.set(true); runCatching { server.close() }; worker.join(5000); spool.deleteRecursively()
        }
    }

    @Test
    fun `a book sent from its file arrives whole, by WiFi and in a pairing, as the old frames did`() {
        val dir = Files.createTempDirectory("rk-stream").toFile()
        try {
            // A book bigger than one read, with every byte value in it.
            val book = ByteArray(300_000) { (it * 31 + 7).toByte() }
            val file = dir.resolve("book.txt").apply { writeBytes(book) }
            val secret = WifiSync.newSecret()

            val wifi = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            val got = java.util.concurrent.atomic.AtomicReference<java.io.File?>(null)
            val receiver = thread { got.set(WifiSync.receive(wifi, secret, spool = dir.resolve("spool"))) }
            try {
                val sent = WifiSync.sendFile(WifiSync.Pairing(listOf("127.0.0.1"), wifi.localPort, secret), file)
                assertTrue("$sent", sent is WifiSync.Outcome.Sent)
                receiver.join(10_000)
                assertArrayEquals(book, got.get()!!.readBytes())
            } finally { runCatching { wifi.close() } }

            val pairServer = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            val stop = java.util.concurrent.atomic.AtomicBoolean(false)
            val offered = java.util.concurrent.atomic.AtomicReference<LiveWire.Offer?>(null)
            val worker = thread { LiveWire.serve(pairServer, { stop.get() }, { secret }, { o -> offered.set(o); true }, { null }) { null } }
            try {
                val o = LiveWire.Offer("b".repeat(32), LiveSync.newHex(32), "d1", "Phone", 7, ByteArray(0), "B", "aa11bb22", marks = listOf("A", "B", "C"))
                val sent = LiveWire.sendOfferFile(LiveWire.Invite(listOf("127.0.0.1"), pairServer.localPort, secret), o, file)
                assertTrue("$sent", sent is LiveWire.Sent.Taken)
                val back = offered.get()!!
                assertEquals(o.book, back.book); assertEquals(o.key, back.key); assertEquals(7L, back.seq)
                assertEquals("B", back.mark); assertEquals("aa11bb22", back.pair)
                assertEquals(listOf("A", "B", "C"), back.marks)
                // The book arrived onto the disk (23 Oct 2026), never gathered in memory.
                assertEquals(0, back.backup.size)
                assertArrayEquals(book, back.file!!.readBytes())
                back.file!!.delete()
                // Both screens show the same four digits for this pairing, and another pairing gets another code.
                val code = (sent as LiveWire.Sent.Taken).code
                assertTrue("$code", code != null && Regex("\\d{4}").matches(code))
                assertEquals(code, back.code)
                assertEquals(code, LiveWire.pairCode(secret, "aa11bb22"))
                assertTrue(LiveWire.pairCode(secret, "aa11bb23") != code || LiveWire.pairCode(WifiSync.newSecret(), "aa11bb22") != code)
            } finally {
                stop.set(true); runCatching { pairServer.close() }; worker.join(5000)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the invite and the offer read back as written`() {
        val i = LiveWire.Invite(listOf("192.168.1.5"), LiveWire.PORT, LiveSync.newHex(32), "SHOP-PC")
        assertEquals(i, LiveWire.parseInvite(LiveWire.inviteText(i)))
        assertNull(LiveWire.parseInvite(WifiSync.pairingText(WifiSync.Pairing(i.hosts, i.port, i.secret))))
        val o = LiveWire.Offer("b".repeat(32), LiveSync.newHex(32), "d1", "Phone", 7, "{\"version\": 14}".toByteArray())
        val back = LiveWire.decodeOffer(LiveWire.encodeOffer(o))!!
        assertEquals(o.book, back.book); assertEquals(o.key, back.key); assertEquals(o.seq, back.seq)
        assertArrayEquals(o.backup, back.backup)
    }

    @Test
    fun `a phone's entry reaches the computer and the computer's reaches the phone in rounds`() {
        val dir = Files.createTempDirectory("rk-wire").toFile()
        val phone = DesktopDatabase.open(dir.resolve("phone.db"))
        val pc = DesktopDatabase.open(dir.resolve("pc.db"))
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        try {
            val phoneSql = phone.syncSql()
            val pcSql = pc.syncSql()
            val id = runBlocking { LiveSync.enable(phoneSql, "install-phone", "Phone") }
            runBlocking {
                LiveSync.enable(pcSql, "install-pc", "PC", adopt = id.book to id.key)
                LiveSync.putPeer(pcSql, id.device, name = "Phone", sent = 0, recv = 0)
                LiveRound.setTarget(phoneSql, LiveRound.Target(listOf("127.0.0.1"), server.localPort, 0, "PC"))
            }
            val worker = thread {
                LiveWire.serve(server, { stop.get() }, { null }, { false }, { id.key }) { r -> runBlocking { LiveRound.answer(pcSql, r) } }
            }
            runBlocking {
                phone.khataDao().insertParty(Party(name = "Ali"))
                pc.khataDao().insertParty(Party(name = "Bilal"))
                repeat(2) {
                    val req = LiveRound.request(phoneSql, fingerprint = it == 1)!!
                    val a = LiveWire.round(listOf("127.0.0.1"), server.localPort, id.key, req)
                    assertTrue("$a", a is LiveWire.Answer.Got)
                    val took = LiveRound.take(phoneSql, req, (a as LiveWire.Answer.Got).reply, a.host)
                    assertNull(took.refused)
                    if (it == 1) assertEquals(true, took.fingerprintMatches)
                }
                assertEquals(setOf("Ali", "Bilal"), pc.khataDao().allPartiesForBackup().map { it.name }.toSet())
                assertEquals(setOf("Ali", "Bilal"), phone.khataDao().allPartiesForBackup().map { it.name }.toSet())
                // A device that does not hold the key is answered with nothing.
                val stranger = LiveWire.round(listOf("127.0.0.1"), server.localPort, LiveSync.newHex(32), LiveRound.request(phoneSql)!!)
                assertEquals(LiveWire.Answer.NotHere, stranger)
            }
            stop.set(true)
            worker.join(5000)
        } finally {
            stop.set(true)
            runCatching { server.close() }
            phone.close(); pc.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a pairing waits for the computer's yes, and an older pairing of the book is not answered as the new one`() {
        val dir = Files.createTempDirectory("rk-wire-pair").toFile()
        val phone = DesktopDatabase.open(dir.resolve("phone.db"))
        val pc = DesktopDatabase.open(dir.resolve("pc.db"))
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val pcKey = java.util.concurrent.atomic.AtomicReference<String?>(null)
        try {
            val phoneSql = phone.syncSql()
            val pcSql = pc.syncSql()
            val id = runBlocking { LiveSync.enable(phoneSql, "install-phone", "Phone") }
            runBlocking {
                phone.khataDao().insertParty(Party(name = "Ali"))
                LiveRound.setPending(phoneSql, LiveRound.Target(listOf("127.0.0.1"), server.localPort, LiveSync.lastSeq(phoneSql), "PC", pair = "aa11bb22"), wasOff = true)
            }
            val worker = thread {
                LiveWire.serve(server, { stop.get() }, { null }, { false }, { pcKey.get() }) { r -> runBlocking { LiveRound.answer(pcSql, r) } }
            }
            runBlocking {
                // Not said yes to yet: the computer does not hold the book, and answers nothing.
                val early = LiveWire.round(listOf("127.0.0.1"), server.localPort, id.key, LiveRound.request(phoneSql)!!)
                assertEquals(LiveWire.Answer.NotHere, early)
                assertNull(LiveRound.target(phoneSql))
                // The owner says yes on the computer: it joins this pairing (LiveDesk.join's steps).
                LiveSync.enable(pcSql, "install-pc", "PC", adopt = id.book to id.key, mark = "B")
                LiveSync.putPeer(pcSql, id.device, name = "Phone", sent = 0, recv = LiveRound.pending(phoneSql)!!.from)
                LiveSync.setPeerPair(pcSql, id.device, "aa11bb22")
                pcKey.set(id.key)
                pc.khataDao().insertParty(Party(name = "Bilal"))
                val req = LiveRound.request(phoneSql)!!
                val a = LiveWire.round(listOf("127.0.0.1"), server.localPort, id.key, req)
                assertTrue("$a", a is LiveWire.Answer.Got)
                val took = LiveRound.take(phoneSql, req, (a as LiveWire.Answer.Got).reply, a.host)
                assertTrue(took.joined)
                assertNull(LiveRound.pending(phoneSql))
                val t = LiveRound.target(phoneSql)!!
                assertEquals("aa11bb22", t.pair)
                assertEquals(LiveSync.identity(pcSql)!!.device, t.device)
                assertTrue(phone.khataDao().allPartiesForBackup().any { it.name == "Bilal" })
                // A request of another pairing of the same book (an older one, or another phone's) is not answered.
                val other = LiveRound.request(phoneSql)!!.put("pair", "cc33dd44")
                assertEquals(LiveWire.Answer.NotHere, LiveWire.round(listOf("127.0.0.1"), server.localPort, id.key, other))
            }
        } finally {
            stop.set(true)
            runCatching { server.close() }
            phone.close(); pc.close()
            dir.deleteRecursively()
        }
    }
}
