package com.innovation313.roshankhata.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * D14 (25 Nov 2026): which backup is locked by default, the switch, the hint, and that a wrong password hands nothing over.
 * The table of ways is written out here as the owner decided it, so a way added or changed in [BackupProtection.Way]
 * fails this test until somebody decides what it does.
 */
class BackupProtectionTest {
    private val rounds = BackupSeal.MIN_ITERATIONS
    private val pw = "Ali Raza 2026"
    private val set = BackupPassword.State.Set(pw, "id01")

    private class MemoryStore : BackupProtection.Store {
        val values = HashMap<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }

    // ---- the table ----

    @Test fun `the table of ways is the owner's decision, one by one`() {
        val expected = mapOf(
            BackupProtection.Way.DRIVE to false,          // D14.1: Google Drive has no password
            BackupProtection.Way.DOWNLOADS to true,
            BackupProtection.Way.SHARE to true,
            BackupProtection.Way.TO_COMPUTER to true,
            BackupProtection.Way.HELPER_FILE to false,    // no secrets in it; its lock would hand the helper the owner's password
            BackupProtection.Way.BACK_UP_NOW to true,
            BackupProtection.Way.OUTSIDE_FOLDER to true,
            BackupProtection.Way.FOR_PHONE to true,
            BackupProtection.Way.DAILY_COPY to false      // D14.5: the app's own folder, unchanged
        )
        assertEquals("a way was added or removed: decide it here", expected.keys, BackupProtection.Way.values().toSet())
        for ((way, locked) in expected) {
            assertEquals("$way by default", locked, BackupProtection.wanted(way, filesProtected = true))
            assertFalse("$way with the switch off", BackupProtection.wanted(way, filesProtected = false))
        }
    }

    @Test fun `drive and the daily copies are plain whatever the password and the switch`() {
        for (way in listOf(BackupProtection.Way.DRIVE, BackupProtection.Way.DAILY_COPY)) for (on in listOf(true, false)) for (state in listOf(
            BackupPassword.State.None, set, BackupPassword.State.Unreadable("lock")
        )) assertTrue("$way $on $state", BackupProtection.plan(way, on, state, "hint") is BackupProtection.Plan.Plain)
    }

    @Test fun `a file that leaves the device is sealed with the kept password and its hint`() {
        for (way in BackupProtection.Way.values().filter { it.leavesDevice && it.protectedByDefault }) {
            val plan = BackupProtection.plan(way, true, set, "  my   first shop  ")
            assertTrue("$way: $plan", plan is BackupProtection.Plan.Seal)
            plan as BackupProtection.Plan.Seal
            assertEquals(pw, plan.password)
            assertEquals("my first shop", plan.hint)
        }
    }

    @Test fun `the first file asks for a password, it is never written plain meanwhile`() {
        for (way in listOf(BackupProtection.Way.DOWNLOADS, BackupProtection.Way.SHARE, BackupProtection.Way.TO_COMPUTER, BackupProtection.Way.OUTSIDE_FOLDER))
            assertTrue(BackupProtection.plan(way, true, BackupPassword.State.None, null) is BackupProtection.Plan.NeedFirstPassword)
    }

    @Test fun `a password that cannot be read stops the file, never makes it open`() {
        val plan = BackupProtection.plan(BackupProtection.Way.DOWNLOADS, true, BackupPassword.State.Unreadable("not this device's"), null)
        assertTrue(plan is BackupProtection.Plan.Unavailable)
    }

    @Test fun `with the switch off a file is open, with or without a password`() {
        for (state in listOf(BackupPassword.State.None, set, BackupPassword.State.Unreadable("x")))
            assertTrue(BackupProtection.plan(BackupProtection.Way.SHARE, false, state, "hint") is BackupProtection.Plan.Plain)
    }

    // ---- the switch and the hint ----

    @Test fun `protection is on until the owner turns it off, and an unreadable store means on`() {
        val store = MemoryStore()
        assertTrue(BackupProtection.filesProtected(store))
        BackupProtection.setFilesProtected(store, false)
        assertFalse(BackupProtection.filesProtected(store))
        BackupProtection.setFilesProtected(store, true)
        assertTrue(BackupProtection.filesProtected(store))
        val broken = object : BackupProtection.Store {
            override fun read(key: String): String? = throw java.io.IOException("x")
            override fun write(key: String, value: String?) = Unit
        }
        assertTrue(BackupProtection.filesProtected(broken))
        assertNull(BackupProtection.hint(broken))
    }

    @Test fun `planFor reads the switch and the hint from the store`() {
        val store = MemoryStore()
        BackupProtection.setHint(store, "shop name + year")
        var plan = BackupProtection.planFor(BackupProtection.Way.DOWNLOADS, store, set) as BackupProtection.Plan.Seal
        assertEquals("shop name + year", plan.hint)
        BackupProtection.setFilesProtected(store, false)
        assertTrue(BackupProtection.planFor(BackupProtection.Way.DOWNLOADS, store, set) is BackupProtection.Plan.Plain)
    }

    @Test fun `the status the screens say`() {
        assertEquals(BackupProtection.Status.PROTECTED, BackupProtection.status(true, set))
        assertEquals(BackupProtection.Status.NEEDS_PASSWORD, BackupProtection.status(true, BackupPassword.State.None))
        assertEquals(BackupProtection.Status.UNREADABLE, BackupProtection.status(true, BackupPassword.State.Unreadable("x")))
        assertEquals(BackupProtection.Status.OPEN, BackupProtection.status(false, set))
        assertEquals(BackupProtection.Status.OPEN, BackupProtection.status(false, BackupPassword.State.None))
    }

    @Test fun `a hint is one short plain line, and never holds the password`() {
        assertNull(BackupSeal.cleanHint(null))
        assertNull(BackupSeal.cleanHint("  \n\t "))
        assertEquals("line one line two", BackupSeal.cleanHint("line one\nline two"))
        assertEquals(BackupSeal.MAX_HINT_CHARS, BackupSeal.cleanHint("x".repeat(500))!!.length)
        assertTrue(BackupSeal.hintGivesAway("it is ali RAZA 2026!", pw))
        assertFalse(BackupSeal.hintGivesAway("my first shop", pw))
        assertFalse(BackupProtection.hintAllowed("the password is Ali Raza 2026", pw))
        assertTrue(BackupProtection.hintAllowed(null, pw))
    }

    // ---- the file ----

    private fun seal(plain: ByteArray, hint: String?, password: String = pw): ByteArray {
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, password, rounds, hint = hint).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun read(sealed: ByteArray, passwords: List<String>): BackupSeal.Open = BackupSeal.open(ByteArrayInputStream(sealed), passwords)

    @Test fun `a file with a hint shows it before any password and opens with the password`() {
        val plain = ByteArray(200_000) { (it * 13).toByte() }
        val sealed = seal(plain, "shop name and the year")
        assertTrue(BackupSeal.isSealed(sealed))
        assertEquals("shop name and the year", BackupSeal.hintOf(ByteArrayInputStream(sealed)))
        assertEquals("shop name and the year", BackupSeal.hintOf(sealed))
        assertTrue(read(sealed, emptyList()) is BackupSeal.Open.NeedPassword)
        assertTrue(read(sealed, listOf("Tariq Butt 300")) is BackupSeal.Open.WrongPassword)
        val ok = read(sealed, listOf(pw)) as BackupSeal.Open.Ok
        val out = ByteArrayOutputStream(); ok.plain.copyTo(out)
        assertArrayEquals(plain, out.toByteArray())
    }

    @Test fun `no hint is the old RKX1 file, and a hint of only spaces is none`() {
        for (hint in listOf(null, "", "   ")) {
            val sealed = seal(byteArrayOf(1, 2, 3), hint)
            assertEquals('1'.code.toByte(), sealed[3])
            assertNull(BackupSeal.hintOf(sealed))
        }
        assertEquals('2'.code.toByte(), seal(byteArrayOf(1), "a hint")[3])
    }

    @Test fun `a hint in Urdu script survives, and a plain backup or an RKX1 file has no hint`() {
        val hint = "دکان کا نام اور سال"
        assertEquals(hint, BackupSeal.hintOf(ByteArrayInputStream(seal(byteArrayOf(5), hint))))
        assertNull(BackupSeal.hintOf(ByteArrayInputStream("{\"format\":\"RoshanKhata\"}".toByteArray())))
        assertNull(BackupSeal.hintOf(ByteArrayInputStream(ByteArray(0))))
    }

    @Test fun `an edited hint or header, a cut file and added bytes never open, and a wrong password reads nothing`() {
        val sealed = seal(ByteArray(70_000) { it.toByte() }, "first shop")
        // Every byte of the header, hint included, is guarded.
        for (i in 0 until BackupSeal.HEADER + 2 + "first shop".length) {
            val c = sealed.copyOf(); c[i] = (c[i].toInt() xor 1).toByte()
            val o = read(c, listOf(pw))
            val stopped = o !is BackupSeal.Open.Ok || try { o.plain.readBytes(); false } catch (e: BackupSeal.Damaged) { true }
            assertTrue("byte $i", stopped)
        }
        assertTrue(read(sealed.copyOf(BackupSeal.HEADER + 5), listOf(pw)) is BackupSeal.Open.Damaged)
        // The wrong password: no stream is handed over at all.
        val wrong = read(sealed, listOf("Tariq Butt 300", "another one"))
        assertTrue(wrong is BackupSeal.Open.WrongPassword)
    }

    @Test fun `a hint longer than the format allows in a crafted file is refused unread`() {
        val sealed = seal(byteArrayOf(9), "ok").copyOf()
        sealed[BackupSeal.HEADER] = 0x7F; sealed[BackupSeal.HEADER + 1] = 0x00
        assertTrue(read(sealed, listOf(pw)) is BackupSeal.Open.Damaged)
        assertNull(BackupSeal.hintOf(sealed))
    }

    @Test fun `the writer carries the hint, and plain stays plain with none`() {
        val out = ByteArrayOutputStream()
        BackupSeal.writer(out, pw, rounds, "year of the shop").use { it.write("{\"a\":1}") }
        assertEquals("year of the shop", BackupSeal.hintOf(out.toByteArray()))
        val plain = ByteArrayOutputStream()
        BackupSeal.writer(plain, null, rounds, "ignored").use { it.write("{\"a\":1}") }
        assertEquals("{\"a\":1}", plain.toString(Charsets.UTF_8.name()))
    }

    @Test fun `a plan that seals gives a file that a restore reads only with its password`() {
        val plan = BackupProtection.plan(BackupProtection.Way.SHARE, true, set, "hint")
        plan as BackupProtection.Plan.Seal
        val out = ByteArrayOutputStream()
        BackupSeal.sealing(out, plan.password, rounds, hint = plan.hint).use { it.write("book".toByteArray()) }
        val file = out.toByteArray()
        assertTrue(read(file, listOf("wrong password!")) is BackupSeal.Open.WrongPassword)
        assertEquals("book", String((read(file, listOf(pw)) as BackupSeal.Open.Ok).plain.readBytes()))
    }

}
