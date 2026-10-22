package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.StringWriter
import java.nio.file.Files

/**
 * Who may do what, after the 22 Oct 2026 review, part 5: a restore keeps the
 * owner's PIN a copy says nothing of; customers brought in from a file and a
 * bill changed or deleted are written down with who did it; an owner's or a
 * manager's session on a counter ends after five idle minutes; a wait after
 * wrong PINs never outlasts fifteen minutes whatever the clock said; and a
 * book on a counter without an owner PIN takes the one the counter keeps, so
 * the owner is never locked out of their own counter.
 */
class SecurityReviewTest {

    /** A device's own keeping, in memory. */
    private class Prefs : Access.DeviceStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    private lateinit var dir: File
    private val books = ArrayList<KhataDatabase>()

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-security").toFile()
    }

    @After
    fun close() {
        Access.resetForTest()
        books.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun book(name: String): KhataDao = DesktopDatabase.open(dir.resolve("$name.db")).also { books += it }.khataDao()

    private fun staff(id: Long, name: String, role: String?) = Staff(id = id, name = name, monthlySalary = 1.0, role = role)

    /** The book as a backup copy, read back as a restore reads it. */
    private suspend fun copyOf(dao: KhataDao): BackupCodec.ParsedBackup {
        val text = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }.toString()
        return BackupCodec.parseText(text).second!!
    }

    @Test
    fun `a restore keeps the owner's PIN a copy says nothing of, and takes a copy's own whole`() = runBlocking {
        val here = book("here")
        val old = copyOf(here)
        val code = Access.setOwnerPin(here, "2468")
        here.setSetting(BookSettings.COUNTER_USED, "1")

        // A copy made before the owner had a PIN: the PIN, its recovery code and the counter's word stay.
        BackupCodec.restoreLedger(here, old)
        assertTrue(Access.ownerPin(here)!!.matches("2468"))
        assertTrue(Access.recoveryMatches(here, code))
        assertEquals("1", here.settingValue(BookSettings.COUNTER_USED))

        // A copy with an owner PIN of its own: that one, with its own recovery code.
        val other = book("other")
        val otherCode = Access.setOwnerPin(other, "1357")
        BackupCodec.restoreLedger(here, copyOf(other))
        assertTrue(Access.ownerPin(here)!!.matches("1357"))
        assertFalse(Access.ownerPin(here)!!.matches("2468"))
        assertTrue(Access.recoveryMatches(here, otherCode))
        assertFalse("the old code went with the old PIN", Access.recoveryMatches(here, code))

        // A live pairing takes the other device's book exactly.
        BackupCodec.restoreLedger(here, old, keepAccess = false)
        assertNull(Access.ownerPin(here))
        assertNull(here.settingValue(BookSettings.COUNTER_USED))
    }

    @Test
    fun `customers brought in from a file carry who brought them, and the log says so`() = runBlocking {
        val dao = book("import")
        Access.signIn(staff(7, "Imran", Role.MANAGER))
        val rows = listOf(CsvImport.Row("Ali", null, 1500.0), CsvImport.Row("Bilal", "03001234567", -200.0))
        assertEquals(2, dao.importOpeningBalances(rows, "Opening balance"))
        val entries = dao.allEntriesForBackup()
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.createdBy == "Imran" })
        val logged = dao.auditInOrder().single { it.action == AuditAction.IMPORT }
        assertEquals("Imran", logged.user)
        assertEquals("2", logged.detail)
    }

    @Test
    fun `a bill changed, a line saved and a bill deleted are each written down with who did it`() = runBlocking {
        val dao = book("bills")
        val engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
        dao.insertSupplierBill(
            LedgerEntry(partyId = engro, amount = 9000.0, isGiven = false, entryNumber = ""),
            SupplierBill(partyId = engro, totalAmount = 9000.0),
            listOf(BillItem(billId = 0, productName = "Urea", quantity = 2.0, unit = "bag", rate = 4500.0))
        )
        val bill = dao.allBillsForBackup().single()
        Access.signIn(staff(3, "Asad", Role.MANAGER))
        dao.editSupplierBill(bill.copy(totalAmount = 9500.0), "Engro")
        dao.saveBillLine(bill.id, BillItem(billId = bill.id, productName = "DAP", quantity = 1.0, unit = "bag", rate = 500.0))
        dao.deleteSupplierBill(bill.id)
        val rows = dao.auditInOrder().filter { it.refTable == "supplier_bills" }
        assertEquals(listOf(AuditAction.BILL_EDITED, AuditAction.BILL_EDITED, AuditAction.BILL_DELETED), rows.map { it.action })
        assertTrue(rows.all { it.user == "Asad" && it.refId == bill.id })
        assertEquals("9500.00", rows[0].detail)
        assertEquals("DAP 1.00", rows[1].detail)
        assertEquals("9500.00", rows[2].detail)
    }

    @Test
    fun `an owner's or a manager's session on a counter ends after five idle minutes, a cashier's stays`() {
        Access.store = Prefs()
        Access.switchCounterMode(true)
        val t = System.currentTimeMillis()
        Access.signInOwner()
        assertFalse(Access.expireIfIdle(t + Access.ELEVATED_IDLE_MS - 60_000))
        // A click: the five minutes start again from it.
        Access.touch(t + Access.ELEVATED_IDLE_MS - 60_000)
        assertFalse(Access.expireIfIdle(t + Access.ELEVATED_IDLE_MS + 60_000))
        assertTrue(Access.ownerIn)
        assertTrue(Access.expireIfIdle(t + 2 * Access.ELEVATED_IDLE_MS))
        assertTrue(Access.locked)

        Access.signIn(staff(3, "Asad", Role.MANAGER))
        assertTrue(Access.expireIfIdle(t + 3 * Access.ELEVATED_IDLE_MS))
        assertTrue(Access.locked)

        Access.signIn(staff(4, "Imran", Role.CASHIER))
        assertFalse("the till is the cashier's all day", Access.expireIfIdle(t + 100 * Access.ELEVATED_IDLE_MS))
        assertEquals("Imran", Access.userName)

        // The owner's own device: nothing ends by itself.
        Access.switchCounterMode(false)
        Access.signIn(staff(3, "Asad", Role.MANAGER))
        assertFalse(Access.expireIfIdle(t + 100 * Access.ELEVATED_IDLE_MS))
        assertEquals("Asad", Access.userName)
    }

    @Test
    fun `a wait after wrong PINs never outlasts fifteen minutes whatever the clock said`() {
        Access.store = Prefs()
        val now = System.currentTimeMillis()
        // Five misses typed while the clock read ten years ahead.
        val ahead = now + 10L * 365 * 24 * 3600 * 1000
        repeat(Access.MISSES_PER_WAIT) { Access.pinTried(false, ahead, Access.OWNER) }
        assertEquals(15 * 60_000L, Access.waitMillis(now, Access.OWNER))
        assertEquals(0L, Access.waitMillis(ahead + 16 * 60_000L, Access.OWNER))
    }

    @Test
    fun `a book on a counter without an owner PIN takes the one the counter keeps`() = runBlocking {
        Access.store = Prefs()
        val shopA = book("a")
        val code = Access.setOwnerPin(shopA, "2468")
        assertTrue("set here, kept here", Access.remembersOwner())
        Access.switchCounterMode(true)

        // A shop added on the counter (or a phone's book paired in): no PIN of its own.
        val shopB = book("b")
        val pin = Access.ownerPin(shopB)
        assertNotNull("the counter's owner can still sign in", pin)
        assertTrue(pin!!.matches("2468"))
        assertTrue("and the recovery code works there too", Access.recoveryMatches(shopB, code))
        assertEquals(listOf(AuditAction.OWNER_PIN_ADOPTED), shopB.auditInOrder().map { it.action })
        Access.ownerPin(shopB)
        assertEquals("taken once", 1, shopB.auditInOrder().size)

        // A shop with its own PIN keeps its own.
        val shopC = book("c")
        val salt = PinHash.salt()
        val codeSalt = PinHash.salt()
        shopC.saveOwnerPin(PinHash.hash("1357", salt), salt, PinHash.hash("ABCDEFGH", codeSalt), codeSalt, null)
        assertTrue(Access.ownerPin(shopC)!!.matches("1357"))
        assertFalse(Access.ownerPin(shopC)!!.matches("2468"))

        // On the owner's own device a book without one stays without.
        Access.switchCounterMode(false)
        assertNull(Access.ownerPin(book("d")))

        // A device the owner's PIN was never typed on has nothing to give, and keeps nothing from a book without one.
        val bare = Prefs()
        Access.store = bare
        Access.switchCounterMode(true)
        Access.rememberOwner(book("e"))
        assertFalse(Access.remembersOwner())
        assertNull(Access.ownerPin(book("f")))
        assertEquals(setOf("access_counter_mode"), bare.map.keys)
    }
}
