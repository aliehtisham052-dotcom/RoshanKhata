package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread

/**
 * A sealed envelope of a NEWER wire version (BC-272, 25 Nov 2026). Today's envelope is "nonce + ciphertext" with no
 * plaintext version, so a newer wire's envelope read as a wrong key on an older build. The safe design: a plaintext mark
 * ("RKW" + one version byte above this build's) that is looked for ONLY after [LiveWire.open] failed, and then said as
 * "update this device" (the version-refusal words), never as a wrong key. Nothing emits the mark yet: these tests hand-make
 * a "newer" envelope.
 */
class WireNewerVersionTest {

    private fun newer(version: Int, tail: Int = 40) = byteArrayOf('R'.code.toByte(), 'K'.code.toByte(), 'W'.code.toByte(), version.toByte()) + ByteArray(tail) { 7 }

    @Test
    fun `only a mark with a version above this build's is a newer wire`() {
        assertEquals(LiveWire.WIRE_VERSION + 1, LiveWire.newerWire(newer(LiveWire.WIRE_VERSION + 1)))
        assertEquals(200, LiveWire.newerWire(newer(200)))
        assertNull(LiveWire.newerWire(newer(LiveWire.WIRE_VERSION)))
        assertNull(LiveWire.newerWire(newer(1)))
        // No mark, a wrong mark, too short: a wrong key stays a wrong key.
        assertNull(LiveWire.newerWire(ByteArray(64) { 3 }))
        assertNull(LiveWire.newerWire(byteArrayOf('R'.code.toByte(), 'K'.code.toByte(), 'X'.code.toByte(), 9)))
        assertNull(LiveWire.newerWire(ByteArray(0)))
        assertNull(LiveWire.newerWire(byteArrayOf('R'.code.toByte(), 'K'.code.toByte(), 'W'.code.toByte())))
    }

    @Test
    fun `today's envelope still opens both ways and is never taken for a newer one`() {
        val key = LiveSync.newHex(32)
        for (road in LiveWire.Road.values()) {
            val sealed = LiveWire.seal(key, "hello".toByteArray(), road)
            assertEquals("hello", String(LiveWire.open(key, sealed, road)!!))
            assertNull(LiveWire.newerWire(sealed))
        }
        // A newer envelope does not open here, and says what it is only when asked after.
        assertNull(LiveWire.open(key, newer(3), LiveWire.Road.RELAY))
    }

    @Test
    fun `the refusal words name this device as the one to update`() {
        assertTrue(LiveSync.needsUpdate(LiveWire.WIRE_NEWER))
        assertTrue(LiveSync.needsUpdate(LiveWire.WIRE_OLDER))
        assertEquals(LiveWhy.Update.THIS, LiveWhy.skew(LiveWire.WIRE_NEWER)!!.update)
        assertEquals(LiveWhy.Update.OTHER, LiveWhy.skew(LiveWire.WIRE_OLDER)!!.update)
        assertEquals(W_THIS, LiveWhy.wordFor(LiveWire.WIRE_NEWER))
        // The old refusals are as they were.
        assertEquals(LiveWhy.Update.EITHER, LiveWhy.skew(LiveWire.WIRE_OTHER)!!.update)
    }

    @Test
    fun `over the shop's WiFi a newer reply is told, a wrong key is not`() {
        fun asked(reply: ByteArray): LiveWire.Answer {
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            val t = thread {
                server.accept().use { s ->
                    val d = DataInputStream(s.getInputStream())
                    d.readFully(ByteArray(4)); d.readFully(ByteArray(d.readInt()))
                    val o = java.io.DataOutputStream(s.getOutputStream())
                    o.writeInt(reply.size); o.write(reply); o.flush()
                }
            }
            try { return LiveWire.round(listOf("127.0.0.1"), server.localPort, LiveSync.newHex(32), JSONObject().put("dev", "x")) }
            finally { server.close(); t.join(3000) }
        }
        val a = asked(newer(LiveWire.WIRE_VERSION + 1))
        assertTrue("$a", a is LiveWire.Answer.Got)
        assertEquals(LiveWire.WIRE_NEWER, (a as LiveWire.Answer.Got).reply.getString("refused"))
        assertTrue(asked(ByteArray(64) { 3 }) is LiveWire.Answer.NotHere)
    }

    private val W_THIS = com.innovation313.roshankhata.words.W.live_skew_this

    // ---- the relay road ----

    /** A relay that holds one parcel, and gives it back to every take / round. */
    private class OneParcel(val body: ByteArray) : LiveCloud.Http {
        override fun call(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, ByteArray> {
            val o = JSONObject().put("ok", true).put("top", 1)
            val action = url.substringAfterLast('/').substringBefore('?')
            if (action == "round" || action == "take") {
                val p = JSONObject().put("seq", 1).put("from", "d".repeat(16)).put("body", B64.encode(this.body))
                o.put("parcels", JSONArray().put(p)).put("more", false).put("seq", JSONObject.NULL)
            }
            return 200 to o.toString().toByteArray()
        }
    }

    private lateinit var dir: File
    private lateinit var db: KhataDatabase

    @Before fun open() {
        dir = Files.createTempDirectory("rk-wire").toFile()
        db = DesktopDatabase.open(dir.resolve("a.db"))
        runBlocking {
            db.khataDao().setSetting(BookSettings.LIVE_RELAY, "1")
            val id = LiveSync.enable(db.syncSql(), "install-a", "A")
            LiveSync.putPeer(db.syncSql(), "d".repeat(16), name = "B", sent = 0, recv = 0)
            assertTrue(id.key.isNotBlank())
        }
    }

    @After fun close() { db.close(); dir.deleteRecursively() }

    @Test
    fun `a parcel of a newer wire makes the relay round say update this device`() = runBlocking {
        val r = LiveCloud.round(db.syncSql(), OneParcel(newer(LiveWire.WIRE_VERSION + 1)), "https://relay.test", 1_000_000L) { false }
        assertTrue("$r", r is LiveCloud.Result.Refused)
        assertEquals(LiveWire.WIRE_NEWER, (r as LiveCloud.Result.Refused).why)
    }

    @Test
    fun `a parcel sealed with another key stays passed over, not a version refusal`() = runBlocking {
        val r = LiveCloud.round(db.syncSql(), OneParcel(ByteArray(64) { 3 }), "https://relay.test", 1_000_000L) { false }
        assertFalse("$r", r is LiveCloud.Result.Refused && LiveSync.needsUpdate(r.why))
    }

    @Test
    fun `a newer parcel carried by the push asks for a round`() = runBlocking {
        val id = LiveSync.identity(db.syncSql())!!
        val dev = "d".repeat(16)
        assertEquals(LiveCloud.Carried.ASK, LiveCloud.takeCarried(db.syncSql(), dev, B64.encode(newer(5)), 1_000_000L))
        assertEquals(LiveCloud.Carried.NOT_MINE, LiveCloud.takeCarried(db.syncSql(), dev, B64.encode(ByteArray(64) { 3 }), 1_000_000L))
        assertTrue(id.book.isNotBlank())
    }
}
