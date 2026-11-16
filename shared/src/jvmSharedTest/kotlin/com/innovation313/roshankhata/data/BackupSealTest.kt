package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The locked backup (17 Nov 2026, [BackupSeal]): what goes in comes out byte for byte with the right password, a wrong
 * one is told apart from damage, and nothing changed, moved, cut or added opens. The fewest rounds the format allows
 * keep the tests quick; the rounds are read from the file, so this is the same path as a real backup's.
 */
class BackupSealTest {
    private val rounds = BackupSeal.MIN_ITERATIONS
    private val pw = "Ali Raza 2026"

    private fun seal(plain: ByteArray, password: String = pw): ByteArray {
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, password, rounds).use { s -> s.write(plain) }
        return out.toByteArray()
    }

    private fun readAll(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(7919) // not a divisor of a record: reads cross record edges
        while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    private fun unseal(sealed: ByteArray, passwords: List<String> = listOf(pw)): ByteArray {
        val o = BackupSeal.open(ByteArrayInputStream(sealed), passwords)
        assertTrue("opened: $o", o is BackupSeal.Open.Ok)
        return readAll((o as BackupSeal.Open.Ok).plain)
    }

    private fun bytes(n: Int) = ByteArray(n) { (it * 31 + 7).toByte() }

    /** True when reading [sealed] to its end with [pw] fails at the open or as damage. */
    private fun refused(sealed: ByteArray): Boolean {
        val o = BackupSeal.open(ByteArrayInputStream(sealed), listOf(pw))
        if (o !is BackupSeal.Open.Ok) return true
        return try { readAll(o.plain); false } catch (e: BackupSeal.Damaged) { true }
    }

    @Test fun `every length comes back byte for byte, across record edges`() {
        val c = BackupSeal.CHUNK
        for (n in listOf(0, 1, 2, c - 1, c, c + 1, 2 * c, 3 * c + 7)) {
            val plain = bytes(n)
            val sealed = seal(plain)
            assertTrue(BackupSeal.isSealed(sealed))
            assertArrayEquals("length $n", plain, unseal(sealed))
        }
    }

    @Test fun `a byte at a time writes the same file a block does`() {
        val plain = bytes(BackupSeal.CHUNK + 300)
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, pw, rounds).use { s -> plain.forEach { b -> s.write(b.toInt()) } }
        assertArrayEquals(plain, unseal(out.toByteArray()))
    }

    @Test fun `no password asks, a wrong one is wrong, the right one among others opens`() {
        val sealed = seal(bytes(100))
        assertTrue(BackupSeal.open(ByteArrayInputStream(sealed), emptyList()) is BackupSeal.Open.NeedPassword)
        assertTrue(BackupSeal.open(ByteArrayInputStream(sealed), listOf("Bashir Ahmad 1")) is BackupSeal.Open.WrongPassword)
        assertArrayEquals(bytes(100), unseal(sealed, listOf("Bashir Ahmad 1", pw)))
    }

    @Test fun `spaces at the ends and the letters' form do not change the password`() {
        val composed = "Café Noor 77"            // é as one letter
        val decomposed = "  Café Noor 77 "     // e + accent, spaces around
        val sealed = seal(bytes(10), composed)
        assertArrayEquals(bytes(10), unseal(sealed, listOf(decomposed)))
    }

    @Test fun `the same book sealed twice is two different files`() {
        assertFalse(seal(bytes(500)).contentEquals(seal(bytes(500))))
    }

    @Test fun `a plain backup is not taken for a locked one`() {
        assertFalse(BackupSeal.isSealed("{\"format\": \"RoshanKhata\"}".toByteArray()))
        assertFalse(BackupSeal.isSealed("RKX".toByteArray()))
        val plain = java.io.BufferedInputStream(ByteArrayInputStream("{ }".toByteArray()))
        assertFalse(BackupSeal.startsSealed(plain))
        assertEquals('{'.code, plain.read()) // left where it was
        val locked = java.io.BufferedInputStream(ByteArrayInputStream(seal(bytes(5))))
        assertTrue(BackupSeal.startsSealed(locked))
        assertEquals('R'.code, locked.read())
    }

    @Test fun `every byte of the header and the check record is guarded`() {
        val sealed = seal(bytes(3000))
        for (i in 0 until BackupSeal.HEADER + 4 + 1 + 24 + 16) {
            val bad = sealed.copyOf()
            bad[i] = (bad[i].toInt() xor 0x01).toByte()
            assertTrue("byte $i", refused(bad))
        }
    }

    @Test fun `a changed byte in the book is damage, never the wrong password`() {
        val sealed = seal(bytes(2 * BackupSeal.CHUNK + 50))
        val bad = sealed.copyOf()
        bad[bad.size - 40] = (bad[bad.size - 40].toInt() xor 0x10).toByte()
        val o = BackupSeal.open(ByteArrayInputStream(bad), listOf(pw))
        assertTrue(o is BackupSeal.Open.Ok)
        try { readAll((o as BackupSeal.Open.Ok).plain); fail("read a changed file") } catch (e: BackupSeal.Damaged) { }
    }

    @Test fun `a file cut short, one with bytes added, one with a record dropped or moved does not open`() {
        val c = BackupSeal.CHUNK
        val sealed = seal(bytes(3 * c + 10))
        assertTrue("cut", refused(sealed.copyOf(sealed.size - 1)))
        assertTrue("cut at a record's edge", refused(sealed.copyOf(sealed.size - (10 + 16 + 5))))
        assertTrue("added", refused(sealed + byteArrayOf(0)))
        // Records 1 and 2 are full: each is 4 + 1 + CHUNK + 16 bytes after the header and the check record.
        val first = BackupSeal.HEADER + 4 + 1 + 24 + 16
        val rec = 4 + 1 + c + 16
        val r1 = sealed.copyOfRange(first, first + rec)
        val r2 = sealed.copyOfRange(first + rec, first + 2 * rec)
        val head = sealed.copyOfRange(0, first)
        val tail = sealed.copyOfRange(first + 2 * rec, sealed.size)
        assertTrue("dropped", refused(head + r2 + tail))
        assertTrue("swapped", refused(head + r2 + r1 + tail))
        assertTrue("repeated", refused(head + r1 + r1 + r2 + tail))
        assertArrayEquals(bytes(3 * c + 10), unseal(head + r1 + r2 + tail))
    }

    @Test fun `a file that asks too many rounds, or another key maker, is refused before any work`() {
        val sealed = seal(bytes(10))
        val many = sealed.copyOf()
        // Iterations sit at bytes 5..8: 0x7FFFFFFF rounds would keep a phone busy for hours.
        many[5] = 0x7F; many[6] = -1; many[7] = -1; many[8] = -1
        assertTrue(BackupSeal.open(ByteArrayInputStream(many), listOf(pw)) is BackupSeal.Open.Damaged)
        val kdf = sealed.copyOf(); kdf[4] = 2
        assertTrue(BackupSeal.open(ByteArrayInputStream(kdf), listOf(pw)) is BackupSeal.Open.Damaged)
        try { BackupSeal.sealing(ByteArrayOutputStream(), pw, BackupSeal.MAX_ITERATIONS + 1); fail() } catch (e: IllegalArgumentException) { }
        try { BackupSeal.sealing(ByteArrayOutputStream(), pw, BackupSeal.MIN_ITERATIONS - 1); fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun `the written rounds are the format's own and never below the PIN's`() {
        assertTrue(BackupSeal.ITERATIONS >= 120_000)
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, pw).close()
        val h = out.toByteArray()
        val rounds = ((h[5].toInt() and 0xFF) shl 24) or ((h[6].toInt() and 0xFF) shl 16) or ((h[7].toInt() and 0xFF) shl 8) or (h[8].toInt() and 0xFF)
        assertEquals(BackupSeal.ITERATIONS, rounds)
    }

    @Test fun `the writer is plain without a password and locked with one`() {
        val plain = ByteArrayOutputStream()
        BackupSeal.writer(plain, null).use { w -> w.write("{\"a\": 1}") }
        assertEquals("{\"a\": 1}", plain.toString("UTF-8"))
        val locked = ByteArrayOutputStream()
        BackupSeal.writer(locked, pw, rounds).use { w -> w.write("{\"a\": \"علی\"}") }
        assertTrue(BackupSeal.isSealed(locked.toByteArray()))
        assertFalse(locked.toString("ISO-8859-1").contains("\"a\""))
        assertEquals("{\"a\": \"علی\"}", String(unseal(locked.toByteArray()), Charsets.UTF_8))
    }

    @Test fun `finish passes a whole file and stops a file with bytes added`() {
        val sealed = seal(bytes(10))
        val ok = BackupSeal.open(ByteArrayInputStream(sealed), listOf(pw)) as BackupSeal.Open.Ok
        BackupSeal.finish(ok.plain)
        val added = BackupSeal.open(ByteArrayInputStream(sealed + byteArrayOf(1, 2)), listOf(pw)) as BackupSeal.Open.Ok
        try { BackupSeal.finish(added.plain); fail() } catch (e: BackupSeal.Damaged) { }
    }

    // ---------- the kept password ----------

    /** A stand-in lock: XOR with a secret byte behind a mark; another device's mark does not open. */
    private class FakeLock(val mark: Byte = 0x5A, var refuse: Boolean = false, var busy: Boolean = false, var none: Boolean = false) : BackupPassword.Lock {
        override fun wrap(plain: ByteArray): ByteArray? {
            if (busy) throw Busy()
            if (none) return null
            return byteArrayOf(mark) + plain.map { (it.toInt() xor 0x33).toByte() }
        }
        override fun unwrap(wrapped: ByteArray): ByteArray? {
            if (busy) throw Busy()
            if (refuse || wrapped.isEmpty() || wrapped[0] != mark) return null
            return wrapped.copyOfRange(1, wrapped.size).map { (it.toInt() xor 0x33).toByte() }.toByteArray()
        }
    }

    /** The lock did not answer (the platforms throw KeyLockUnavailable; any throw is read the same way). */
    private class Busy : RuntimeException("busy")

    private class MemoryKeep(var value: String? = null) : BackupPassword.Keep {
        override fun read() = value
        override fun write(value: String?) { this.value = value }
    }

    @Test fun `a password is kept sealed, never plain, and read back`() {
        val keep = MemoryKeep(); val lock = FakeLock()
        assertTrue(BackupPassword.state(keep, lock) is BackupPassword.State.None)
        assertEquals(BackupPassword.Saved.OK, BackupPassword.save(keep, lock, " Tariq Butt 300 "))
        val kept = keep.value!!
        assertFalse(kept.contains("Tariq"))
        val s = BackupPassword.state(keep, lock) as BackupPassword.State.Set
        assertEquals("Tariq Butt 300", s.password)
        assertEquals("Tariq Butt 300", BackupPassword.forSealing(s))
        assertEquals("|pw:" + s.id, BackupPassword.mark(s))
    }

    @Test fun `every set is a new mark, so a changed password sends the copy again`() {
        val keep = MemoryKeep(); val lock = FakeLock()
        BackupPassword.save(keep, lock, "password one")
        val a = (BackupPassword.state(keep, lock) as BackupPassword.State.Set).id
        BackupPassword.save(keep, lock, "password one")
        val b = (BackupPassword.state(keep, lock) as BackupPassword.State.Set).id
        assertNotEquals(a, b)
        assertEquals("", BackupPassword.mark(BackupPassword.State.None))
    }

    @Test fun `too short or too long is refused and nothing is kept`() {
        val keep = MemoryKeep(); val lock = FakeLock()
        assertEquals(BackupPassword.Saved.TOO_SHORT, BackupPassword.save(keep, lock, "1234567"))
        assertEquals(BackupPassword.Saved.TOO_SHORT, BackupPassword.save(keep, lock, "   abc1234   ".take(9)))
        assertEquals(BackupPassword.Saved.TOO_LONG, BackupPassword.save(keep, lock, "x".repeat(BackupPassword.MAX + 1)))
        assertEquals(null, keep.value)
        assertEquals(BackupPassword.Saved.OK, BackupPassword.check("12345678"))
        // Eight letters of any script count as eight, not as their UTF-16 units.
        assertEquals(BackupPassword.Saved.OK, BackupPassword.check("علیرضابش"))
    }

    @Test fun `a device with no lock keeps nothing rather than a plain password`() {
        val keep = MemoryKeep()
        assertEquals(BackupPassword.Saved.NO_LOCK, BackupPassword.save(keep, FakeLock(none = true), "a good password"))
        assertEquals(null, keep.value)
    }

    @Test fun `a password that cannot be read stops the backup, never makes it plain`() {
        val keep = MemoryKeep(); val lock = FakeLock()
        BackupPassword.save(keep, lock, "a good password")
        for (other in listOf(FakeLock(mark = 0x11), FakeLock(refuse = true), FakeLock(busy = true))) {
            val s = BackupPassword.state(keep, other)
            assertTrue("$other", s is BackupPassword.State.Unreadable)
            try { BackupPassword.forSealing(s); fail("sealed with nothing") } catch (e: BackupPassword.Unavailable) { }
            assertEquals("|pw:?", BackupPassword.mark(s))
        }
        assertTrue(BackupPassword.state(MemoryKeep("rubbish"), lock) is BackupPassword.State.Unreadable)
        assertTrue(BackupPassword.state(MemoryKeep("p1:abc:%%%"), lock) is BackupPassword.State.Unreadable)
    }

    @Test fun `the sync key sealed by the same lock is never read as a password`() {
        val lock = FakeLock()
        val syncKey = lock.wrap("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".toByteArray())!!
        val keep = MemoryKeep("p1:0011223344556677:" + B64.encode(syncKey))
        assertTrue(BackupPassword.state(keep, lock) is BackupPassword.State.Unreadable)
    }

    @Test fun `removed is none`() {
        val keep = MemoryKeep(); val lock = FakeLock()
        BackupPassword.save(keep, lock, "a good password")
        BackupPassword.remove(keep)
        assertTrue(BackupPassword.state(keep, lock) is BackupPassword.State.None)
        assertEquals(null, BackupPassword.forSealing(BackupPassword.State.None))
    }

    @Test fun `a lock that fails to answer while saving is not swallowed`() {
        try { BackupPassword.save(MemoryKeep(), FakeLock(busy = true), "a good password"); fail() } catch (e: Busy) { }
    }
}
