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
        val code = Access.setOwnerPin(here, "246813")
        here.setSetting(BookSettings.COUNTER_USED, "1")

        // A copy made before the owner had a PIN: the PIN, its recovery code and the counter's word stay.
        BackupCodec.restoreLedger(here, old)
        assertTrue(Access.ownerPin(here)!!.matches("246813"))
        assertTrue(Access.recoveryMatches(here, code))
        assertEquals("1", here.settingValue(BookSettings.COUNTER_USED))

        // A copy with an owner PIN of its own: that one, with its own recovery code.
        val other = book("other")
        val otherCode = Access.setOwnerPin(other, "135792")
        BackupCodec.restoreLedger(here, copyOf(other))
        assertTrue(Access.ownerPin(here)!!.matches("135792"))
        assertFalse(Access.ownerPin(here)!!.matches("246813"))
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
        val rows = listOf(CsvImport.Row("Ali", null, 1500.0), CsvImport.Row("Bilal", "0000 0000000", -200.0))
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
        val code = Access.setOwnerPin(shopA, "246813")
        assertTrue("set here, kept here", Access.remembersOwner())
        Access.switchCounterMode(true)

        // A shop added on the counter (or a phone's book paired in): no PIN of its own.
        val shopB = book("b")
        val pin = Access.ownerPin(shopB)
        assertNotNull("the counter's owner can still sign in", pin)
        assertTrue(pin!!.matches("246813"))
        assertTrue("and the recovery code works there too", Access.recoveryMatches(shopB, code))
        assertEquals(listOf(AuditAction.OWNER_PIN_ADOPTED), shopB.auditInOrder().map { it.action })
        Access.ownerPin(shopB)
        assertEquals("taken once", 1, shopB.auditInOrder().size)

        // A shop with its own PIN keeps its own.
        val shopC = book("c")
        val salt = PinHash.salt()
        val codeSalt = PinHash.salt()
        shopC.saveOwnerPin(PinHash.hash("135792", salt), salt, PinHash.hash("ABCDEFGH", codeSalt), codeSalt, null)
        assertTrue(Access.ownerPin(shopC)!!.matches("135792"))
        assertFalse(Access.ownerPin(shopC)!!.matches("246813"))

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

    @Test
    fun `an owner's PIN is 6 to 8 digits, and a short one typed right is asked to be changed`() = runBlocking {
        val dao = book("short")
        assertFalse(PinHash.validOwner("2468"))
        assertTrue(PinHash.validOwner("246813"))
        assertTrue(PinHash.valid("2468"))
        assertTrue("a short PIN is refused when set", runCatching { Access.setOwnerPin(dao, "2468") }.isFailure)
        Access.ownerPinTyped("2468")
        assertTrue(Access.ownerPinShort)
        Access.setOwnerPin(dao, "246813")
        assertFalse("a new PIN clears the ask", Access.ownerPinShort)
        Access.ownerPinTyped("246813")
        assertFalse(Access.ownerPinShort)
    }

    @Test
    fun `a day closed twice keeps what the first close counted in the log`() = runBlocking {
        val dao = book("close")
        Access.signIn(staff(3, "Asad", Role.MANAGER))
        dao.saveDayClose(DayClose(day = 20_000L, opening = 0.0, cashIn = 1000.0, cashOut = 0.0, counted = 990.0))
        dao.saveDayClose(DayClose(day = 20_000L, opening = 0.0, cashIn = 1000.0, cashOut = 0.0, counted = 1000.0))
        assertEquals(1, dao.allDayClosesForBackup().size)
        val rows = dao.auditInOrder().filter { it.action == AuditAction.DAY_CLOSED }
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.user == "Asad" })
        assertEquals("990.00", rows[0].detail)
        assertEquals("1,000.00 (990.00 / -10.00)", rows[1].detail)
    }

    @Test
    fun `the cashbook and the counter's tables go out and come back a page at a time, every row`() = runBlocking {
        val dao = book("big")
        // More rows than one page (2,000) of the cashbook, and a ticket with its line and payment.
        repeat(2_345) { i -> dao.insertCashEntry(CashEntry(amount = 10.0 + i, isIncome = i % 2 == 0, category = "Test")) }
        val p = dao.insertProduct(Product(name = "Urea", nameKey = ProductName.key("Urea"), normalisedName = ProductName.normalised("Urea")))
        dao.postSale(
            Sale(timestamp = System.currentTimeMillis(), subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, productId = p, itemName = "Urea", quantity = 1.0, unit = "bag", rate = 4500.0)),
            listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = 4500.0))
        )
        val copy = copyOf(dao)
        assertEquals(dao.allCashForBackup().size, copy.cash.size)
        assertEquals(dao.allCashForBackup().map { it.uid }, copy.cash.map { it.uid })
        assertEquals(1, copy.sales.size)
        assertEquals(1, copy.saleItems.size)
        assertEquals(1, copy.salePayments.size)
        assertEquals(dao.allAuditForBackup().map { it.hash }, copy.audit.map { it.hash })
        val back = book("big-restored")
        BackupCodec.restoreLedger(back, copy)
        assertEquals(dao.allCashForBackup().size, back.allCashForBackup().size)
        assertEquals(1, back.allSalesForBackup().size)
    }

    @Test
    fun `a device id copied into the settings file does not make a counter the owner's`() = runBlocking {
        val dao = book("counter")
        val phone = "0123456789abcdef0123456789abcdef"
        // A counter was used for this book, and the owner's phone is one of the owner's devices.
        dao.setSetting(BookSettings.COUNTER_USED, "1")
        dao.setSetting(BookSettings.OWNER_DEVICES, phone)
        // On the counter computer someone copies the phone's id out of a daily copy into the
        // settings file and clears counter mode there.
        val prefs = Prefs()
        prefs.put("access_device_id", phone)
        Access.machineId = { "w0000000000000000000000000000001" }
        Access.store = prefs
        assertFalse(Access.counterMode)
        assertEquals("which computer this is comes from the machine", "w0000000000000000000000000000001", Access.deviceId())
        assertTrue("the book makes it a counter again", Access.applyBook(dao))
        assertTrue(Access.locked)
        // The owner turning counter mode off here (with the PIN) makes this machine theirs.
        Access.switchCounterMode(false)
        Access.recordCounterMode(dao, false)
        Access.store = prefs
        assertFalse(Access.applyBook(dao))
        assertTrue(Access.isOwner)
    }

    @Test
    fun `the helper's copy leaves the PINs out, the owner's own backup keeps them`() = runBlocking {
        val dao = book("helper")
        Access.setOwnerPin(dao, "246813")
        val salt = PinHash.salt()
        dao.insertStaff(Staff(name = "Imran", monthlySalary = 1.0, role = Role.CASHIER, pinHash = PinHash.hash("5678", salt), pinSalt = salt))
        val own = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it) }.toString()
        val helper = StringWriter().also { BackupCodec.exportTo(dao, "Shop", JSONObject(), it, secrets = false) }.toString()
        assertTrue(own.contains(BookSettings.OWNER_PIN_HASH) && own.contains(BookSettings.OWNER_RECOVERY_HASH))
        BookSettings.OWNER_PIN_KEYS.forEach { assertFalse(it, helper.contains(it)) }
        val copy = BackupCodec.parseText(helper).second!!
        assertEquals("Imran", copy.staff.single().name)
        assertNull(copy.staff.single().pinHash)
        assertNull(copy.staff.single().pinSalt)
    }

    @Test
    fun `an entry's bill photo is still linked after a restore`() = runBlocking {
        val dao = book("photo")
        val ali = dao.insertParty(Party(name = "Ali"))
        dao.insertEntryNumbered(LedgerEntry(partyId = ali, amount = 900.0, isGiven = true, entryNumber = "", billPhotoPath = "/data/files/bills/bill_1.jpg"))
        val copy = copyOf(dao)
        val other = book("photo-restored")
        BackupCodec.restoreLedger(other, copy)
        assertEquals("/data/files/bills/bill_1.jpg", other.allEntriesForBackup().single().billPhotoPath)
    }

    /**
     * The sync key at rest (22 Oct 2026, the live-sync review): on Windows it is
     * sealed by DPAPI for the signed-in user and reads back; where there is no
     * lock (this test on Linux) it is kept as it is. A sealed key from another
     * device, or a torn one, reads as nothing — never as a wrong key.
     */
    @Test
    fun `sync key at rest seals where it can and reads back`() {
        val key = "0123456789abcdef".repeat(4)
        val stored = KeyAtRest.store(key)
        assertEquals(key, KeyAtRest.read(stored))
        val windows = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")
        assertEquals(windows, KeyAtRest.isWrapped(stored))
        if (windows) {
            assertFalse(stored.contains(key))
            // Sealed twice, two different ciphertexts; both open.
            assertEquals(key, KeyAtRest.read(KeyAtRest.store(key)))
        }
        assertNull(KeyAtRest.read(null))
        assertNull(KeyAtRest.read("w1:not-base64!"))
        assertNull(KeyAtRest.read("w1:" + B64.encode(ByteArray(40) { 7 })))
        assertNull(KeyAtRest.read("abc"))   // a plain key that is not a key
    }

    /** A book whose key is sealed for another device turns live sync off on opening, telling why. */
    @Test
    fun `a key sealed elsewhere turns live sync off`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("sealed-elsewhere.db")).also { books += it }
        val sql = db.syncSql()
        LiveSync.enable(sql, install = "inst-1", name = "A")
        assertNotNull(LiveSync.identity(sql))
        sql.write { exec("UPDATE sync_meta SET v = ? WHERE k = 'key'", listOf("w1:" + B64.encode(ByteArray(48) { 1 }))) }
        assertFalse(LiveSync.check(sql, "inst-1"))
        assertNull(LiveSync.identity(sql))
        assertEquals(LiveSync.OFF_RESTORED, LiveSync.offReason(sql))
    }

    /**
     * A lock that does not answer (the Keystore after an OS update, JNA quarantined) decides nothing: the book stays
     * paired, the identity waits, and the next open with the lock back finds everything as it was (23 Oct 2026).
     */
    @Test
    fun `a lock that does not answer leaves live sync as it is`() = runBlocking {
        val db = DesktopDatabase.open(dir.resolve("lock-busy.db")).also { books += it }
        val sql = db.syncSql()
        // A lock that seals (so the key is kept wrapped), then stops answering.
        val xor = object : KeyAtRest.Lock {
            var answering = true
            override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            override fun unwrap(wrapped: ByteArray): ByteArray? {
                if (!answering) throw KeyLockUnavailable("busy")
                return wrapped.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            }
        }
        KeyAtRest.lock = xor
        try {
            val id = LiveSync.enable(sql, install = "inst-1", name = "A")
            LiveSync.putPeer(sql, "pc-dev", name = "PC", sent = 0, recv = 0)
            assertTrue(KeyAtRest.isWrapped(sql.read { rows("SELECT v FROM sync_meta WHERE k = 'key'", types = "T").single()[0] as String }))
            xor.answering = false
            // Opening with the lock busy: still on, nothing dropped, the trouble said; no identity to round with.
            assertTrue(LiveSync.check(sql, "inst-1"))
            assertNull(LiveSync.identity(sql))
            assertEquals("busy", LiveSync.lockTrouble)
            assertNull(LiveSync.offReason(sql))
            assertEquals(1, LiveSync.state(sql).peers.size)
            // The lock back: the same identity, the same peer, the trouble gone.
            xor.answering = true
            assertEquals(id.book, LiveSync.identity(sql)!!.book)
            assertNull(LiveSync.lockTrouble)
        } finally { KeyAtRest.resetLock() }
    }
}
