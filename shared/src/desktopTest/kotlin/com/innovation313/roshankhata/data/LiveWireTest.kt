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
        val sealed = LiveWire.seal(key, "Rs 5,000".toByteArray())
        assertEquals("Rs 5,000", String(LiveWire.open(key, sealed)!!))
        assertNull(LiveWire.open(LiveSync.newHex(32), sealed))
        val changed = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(LiveWire.open(key, changed))
    }

    @Test
    fun `the phone finds its computer again by the book's tag, and no other`() {
        val key = LiveSync.newHex(32)
        val socket = java.net.DatagramSocket(0, InetAddress.getLoopbackAddress())
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = thread { LiveWire.answerSeeks(socket, { stop.get() }, { key }) }
        try {
            val here = listOf(InetAddress.getLoopbackAddress())
            assertEquals("127.0.0.1", LiveWire.seek(key, socket.localPort, here, 2000))
            assertNull(LiveWire.seek(LiveSync.newHex(32), socket.localPort, here, 500))
        } finally {
            stop.set(true); socket.close(); worker.join(3000)
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
}
