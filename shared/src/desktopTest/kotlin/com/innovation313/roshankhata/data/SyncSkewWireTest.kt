package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The WiFi road between two versions (24 Nov 2026, stage 5 / B10): an offer says its schema, a pairing across schemas is
 * refused at the door before any book is replaced, and a computer that answers a round with its one-byte "no" (a wire it
 * does not speak) is told to the owner as another version, not as "not keeping this book".
 */
class SyncSkewWireTest {

    private fun offer(schema: Int?) = LiveWire.Offer(
        book = "a".repeat(32), key = "b".repeat(64), device = "c".repeat(16), name = "Phone", seq = 3,
        backup = ByteArray(0), schema = schema
    )

    @Test
    fun `an offer's head carries this build's schema and an older head says none`() {
        val head = LiveWire.decodeHead(LiveWire.offerHead(offer(null)))!!
        assertEquals(KHATA_DB_VERSION, head.schema)
        assertTrue(head.sameSchema)
        // A head from a build that did not say (before 24 Nov 2026) is taken as it was: no refusal on a guess.
        val bare = org.json.JSONObject(String(LiveWire.offerHead(offer(null))).trim()).apply { remove("schema") }
        val old = LiveWire.decodeHead(bare.toString().toByteArray())!!
        assertNull(old.schema)
        assertTrue(old.sameSchema)
        assertFalse(offer(KHATA_DB_VERSION - 1).sameSchema)
        assertTrue(offer(KHATA_DB_VERSION).sameSchema)
    }

    @Test
    fun `a round answered with the one-byte no is another version, not a computer that does not keep the book`() {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val t = thread {
            server.accept().use { s ->
                val i = s.getInputStream()
                // The whole frame is read first (the wire's: "RKL1", a length, the sealed bytes), so the answer is not lost to a reset.
                val d = java.io.DataInputStream(i)
                d.readFully(ByteArray(4))
                d.readFully(ByteArray(d.readInt()))
                s.getOutputStream().write(LiveWire.BAD); s.getOutputStream().flush()
            }
        }
        try {
            val key = LiveSync.newHex(32)
            val a = LiveWire.round(listOf("127.0.0.1"), server.localPort, key, org.json.JSONObject().put("dev", "x"))
            assertTrue("$a", a is LiveWire.Answer.Got)
            assertEquals(LiveWire.WIRE_OTHER, (a as LiveWire.Answer.Got).reply.getString("refused"))
        } finally {
            server.close(); t.join(3000)
        }
    }
}
