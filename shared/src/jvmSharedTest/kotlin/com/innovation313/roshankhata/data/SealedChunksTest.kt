package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * A book over the WiFi a record at a time (23 Oct 2026, the review report): it arrives whole and in order, the
 * head opens before anything else is read, and a stream changed, cut short or sealed with another secret does not.
 */
class SealedChunksTest {

    private val secret = WifiSync.newSecret()
    private val label = "TEST".toByteArray()

    private fun stream(head: ByteArray, body: ByteArray?, key: String = secret): ByteArray {
        val bytes = ByteArrayOutputStream()
        SealedChunks.write(DataOutputStream(bytes), WifiSync.chunkKey(key), label, head, body?.let { ByteArrayInputStream(it) })
        return bytes.toByteArray()
    }

    private fun reader(b: ByteArray) = SealedChunks.Reader(DataInputStream(ByteArrayInputStream(b)), WifiSync.chunkKey(secret), label)

    private fun readBody(b: ByteArray, cap: Long = Long.MAX_VALUE): ByteArray? {
        val r = reader(b)
        r.next(SealedChunks.MAX_HEAD) ?: return null
        val f = File.createTempFile("rk-chunks", ".bin")
        try {
            return try { if (r.readTo(f, cap)) f.readBytes() else null } catch (e: java.io.IOException) { null }
        } finally { f.delete() }
    }

    @Test
    fun `a body of several records arrives whole, and an exact multiple of a record too`() {
        for (size in listOf(0, 1, SealedChunks.CHUNK - 1, SealedChunks.CHUNK, 3 * SealedChunks.CHUNK, 3 * SealedChunks.CHUNK + 17)) {
            val body = ByteArray(size) { (it * 7 + 3).toByte() }
            val s = stream("head".toByteArray(), body)
            val r = reader(s)
            assertEquals("head", String(r.next(SealedChunks.MAX_HEAD)!!))
            assertArrayEquals("size $size", body, readBody(s))
        }
        // A head alone: the stream ends with it.
        val r = reader(stream("only".toByteArray(), null))
        assertEquals("only", String(r.next(SealedChunks.MAX_HEAD)!!))
        assertTrue(r.done)
    }

    @Test
    fun `another secret, a changed byte or a stream cut short does not open`() {
        val body = ByteArray(2 * SealedChunks.CHUNK + 5) { it.toByte() }
        val good = stream("h".toByteArray(), body)
        assertNull(reader(stream("h".toByteArray(), body, WifiSync.newSecret())).next(SealedChunks.MAX_HEAD))
        val changed = good.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertNull(readBody(changed))
        // The last record taken away: the one before it does not say it is the last, so the stream is not whole.
        val lastRecord = 4 + 1 + 5 + 16
        assertNull(readBody(good.copyOf(good.size - lastRecord)))
        // More than the cap: refused.
        assertNull(readBody(good, cap = SealedChunks.CHUNK.toLong()))
    }

    @Test
    fun `a head longer than the head's limit is refused before it is read`() {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(ByteArray(8))
        out.writeInt(200 * 1024 * 1024); out.writeByte(0)
        out.flush()
        // Nothing of the 200 MB is waited for: the length alone refuses it.
        assertNull(reader(bytes.toByteArray()).next(SealedChunks.MAX_HEAD))
        assertFalse(WifiSync.roomFor(WifiSync.MAX_BYTES.toLong() + 1, null))
    }
}
