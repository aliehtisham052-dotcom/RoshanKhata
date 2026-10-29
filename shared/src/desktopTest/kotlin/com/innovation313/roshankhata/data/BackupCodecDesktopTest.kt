package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A phone's backup, read on the computer (KMP Phase 4c). The file is the
 * format the phone writes (version 7, nulls written as null, numbers as
 * the phone prints them); the computer reads it with org.json + Gson where
 * the phone uses Android's own. BackupRoundTripTest covers the phone side.
 */
class BackupCodecDesktopTest {

    private val v7 = BackupFixtures.v7

    @Test
    fun `a phone backup reads on the computer, row for row`() {
        val (result, data) = BackupCodec.parseText(v7)
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        data!!
        assertEquals(1, data.parties.size)
        assertEquals("Ali (علی)", data.parties[0].name)
        assertEquals("0300 1234567", data.parties[0].phone)
        assertNull(data.parties[0].village)
        val e = data.entries.single()
        assertEquals(4598.0, e.amount, 0.0)
        assertTrue(e.isGiven)
        assertNull("a null creator stays null, not the word null", e.createdBy)
        val line = data.entryItems.single()
        assertEquals("Urea", line.itemName)
        assertEquals(2.5, line.quantity!!, 0.0)
        assertEquals(1839.2, line.rate!!, 0.0)
        assertEquals("Bhatti Traders", data.businessName)
        val p = data.businessProfile!!
        assertEquals("Lappay Wali", p.businessAddress)
        assertNull(p.bankName)
        assertEquals("hijri", p.calendar)
        assertTrue(p.photoOnStatement)
        assertEquals("Bhatti Traders", BackupCodec.businessNameOf(v7))
    }

    @Test
    fun `an older file without goods lines gets them rebuilt as on the phone`() {
        val v6 = v7.replace("\"version\": 7", "\"version\": 6")
            .replace(Regex("""(?s),\s*"entryItems": \[.*?\]"""), "")
            .replace(Regex(""""itemName": null,\s*"quantity": null, "unit": null"""),
                """"itemName": "DAP", "quantity": 1, "unit": "bag"""")
        val (result, data) = BackupCodec.parseText(v6)
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        // Each line is its own row (schema 38: its own uid), so the rule is held to everything else.
        assertEquals(listOf(EntryItem.fromLegacy(data!!.entries.single())).filterNotNull().map { it.copy(uid = null) }, data.entryItems.map { it.copy(uid = null) })
        assertEquals("DAP", data.entryItems.single().itemName)
    }

    @Test
    fun `a phone's shop profile lands in the computer's settings and back out the same`() {
        val dir = java.nio.file.Files.createTempDirectory("rk-settings").toFile()
        try {
            val file = java.io.File(dir, "shop.properties")
            val profile = BackupCodec.parseText(v7).second!!.businessProfile!!
            BackupCodec.restoreProfile(FileShopSettings(file), profile)

            // A fresh read of the file: what a restart of the app would see.
            val again = FileShopSettings(file)
            assertEquals("Bhatti Traders", again.businessName())
            assertEquals("Lappay Wali", again.businessAddress())
            assertNull(again.bankName())
            assertEquals(ShopCalendar.Kind.HIJRI, again.calendar())
            assertEquals("Rs", again.currency())
            assertNull(again.tradeKey())
            assertTrue(again.photoOnStatement())
            assertEquals(ShopCalendar.Kind.HIJRI, ShopCalendar.current)

            val out = BackupCodec.profileToJson(again)
            assertEquals("Bhatti Traders", out.getString("businessName"))
            assertTrue(out.isNull("bankName"))
            assertEquals("hijri", out.getString("calendar"))
            assertEquals("Rs", out.getString("currency"))
            assertTrue(out.getBoolean("photoOnStatement"))
        } finally {
            ShopCalendar.useForTest(ShopCalendar.Kind.GREGORIAN)
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a payment's reference survives export and restore on the computer`() {
        // Format 16 (28 Oct 2026, schema 39): the transfer id, TID or cheque number a khata or
        // cashbook payment came with goes out with the row and comes back on it; a row with none
        // comes back with none. The rows are compared whole, their uids aside (lesson 31).
        val dir = java.nio.file.Files.createTempDirectory("rk-ref").toFile()
        val here = DesktopDatabase.open(dir.resolve("here.db"))
        val there = DesktopDatabase.open(dir.resolve("there.db"))
        try {
            kotlinx.coroutines.runBlocking {
                val dao = here.khataDao()
                val ali = dao.insertParty(Party(name = "Ali", isCustomer = true))
                dao.insertEntryWithItems(
                    LedgerEntry(partyId = ali, amount = 1200.0, isGiven = false, entryNumber = "", paymentMethod = PaymentMethod.ONLINE, paymentRef = "TID 7781234509"),
                    emptyList()
                )
                dao.insertEntryWithItems(LedgerEntry(partyId = ali, amount = 400.0, isGiven = false, entryNumber = ""), emptyList())
                dao.insertCashEntry(CashEntry(amount = 300.0, isIncome = true, category = CashCategory.SALE, paymentMethod = PaymentMethod.BANK, paymentRef = "RAAST 20261028-0042"))
                dao.insertCashEntry(CashEntry(amount = 150.0, isIncome = false, category = "Fuel"))

                val text = java.io.StringWriter().also { BackupCodec.exportTo(dao, "Shop", org.json.JSONObject(), it) }.toString()
                val (result, data) = BackupCodec.parseText(text)
                assertTrue("$result", result is BackupCodec.ImportResult.Ok)
                assertEquals(BackupCodec.FORMAT_VERSION, data!!.version)
                assertTrue(text.contains("\"paymentRef\": \"TID 7781234509\"") || text.contains("\"paymentRef\":\"TID 7781234509\""))
                BackupCodec.restoreLedger(there.khataDao(), data)

                val o = there.khataDao()
                assertEquals(
                    dao.allEntriesForBackup().sortedBy { it.id }.map { it.copy(uid = null) },
                    o.allEntriesForBackup().sortedBy { it.id }.map { it.copy(uid = null) }
                )
                assertEquals(
                    dao.allCashForBackup().sortedBy { it.id }.map { it.copy(uid = null) },
                    o.allCashForBackup().sortedBy { it.id }.map { it.copy(uid = null) }
                )
                assertEquals("TID 7781234509", o.allEntriesForBackup().single { it.amount == 1200.0 }.paymentRef)
                assertNull("none recorded, none restored", o.allEntriesForBackup().single { it.amount == 400.0 }.paymentRef)
                assertEquals("RAAST 20261028-0042", o.allCashForBackup().single { it.isIncome }.paymentRef)
                assertNull(o.allCashForBackup().single { !it.isIncome }.paymentRef)
            }
        } finally {
            here.close(); there.close(); dir.deleteRecursively()
        }
    }

    /**
     * Format 17 (29 Oct 2026, schema 40): a document's kind, status and the
     * document it was made from go out and come back; a file from before 17
     * reads every invoice as an open invoice made from nothing.
     */
    @Test
    fun `a converted pair of documents goes out and comes back linked, and an older file reads as invoices`() {
        val dir = java.nio.file.Files.createTempDirectory("rk-backup-docs").toFile()
        val here = DesktopDatabase.open(dir.resolve("here.db"))
        val there = DesktopDatabase.open(dir.resolve("there.db"))
        try {
            kotlinx.coroutines.runBlocking {
                val dao = here.khataDao()
                val lines = listOf(InvoiceItem(invoiceId = 0, itemName = "Urea", quantity = 10.0, unit = "bag", rate = 4650.0))
                val q = dao.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.QUOTATION), lines)
                val so = dao.convertDocument(q, DocKind.SALES_ORDER, by = "Owner")
                dao.cancelDocument(dao.saveInvoiceWithItems(Invoice(customerName = "Bashir", kind = DocKind.PROFORMA), lines), by = "Owner")
                dao.saveInvoiceWithItems(Invoice(customerName = "Chaudhry"), lines)

                val text = java.io.StringWriter().also { BackupCodec.exportTo(dao, "Shop", org.json.JSONObject(), it) }.toString()
                val (result, data) = BackupCodec.parseText(text)
                assertTrue("$result", result is BackupCodec.ImportResult.Ok)
                assertEquals(BackupCodec.FORMAT_VERSION, data!!.version)
                assertTrue(data.version >= 17)
                BackupCodec.restoreLedger(there.khataDao(), data)
                val o = there.khataDao()
                // Row for row, the random uids aside (lesson 31).
                assertEquals(
                    dao.allInvoicesForBackup().sortedBy { it.id }.map { it.copy(uid = null) },
                    o.allInvoicesForBackup().sortedBy { it.id }.map { it.copy(uid = null) }
                )
                val order = o.getInvoice(so)!!
                assertEquals(DocKind.SALES_ORDER, order.kind)
                assertEquals(DocStatus.OPEN, order.status)
                assertEquals("a restore keeps every id, so the link points where it did", q, order.sourceDocId)
                assertEquals(DocStatus.CONVERTED, o.getInvoice(q)!!.status)
                assertEquals(DocStatus.CANCELLED, o.allInvoicesForBackup().single { it.customerName == "Bashir" }.status)
                assertEquals(listOf("Urea"), o.invoiceItems(so).map { it.itemName })

                // The same file as an older release wrote it: no kind, status or source on any invoice.
                val root = org.json.JSONObject(text)
                root.put("version", 16)
                val arr = root.getJSONArray("invoices")
                for (i in 0 until arr.length()) arr.getJSONObject(i).apply { remove("kind"); remove("status"); remove("sourceDocId") }
                val older = root.toString()
                val (oldResult, oldData) = BackupCodec.parseText(older)
                assertTrue("$oldResult", oldResult is BackupCodec.ImportResult.Ok)
                assertEquals(4, oldData!!.invoices.size)
                assertTrue(oldData.invoices.all { it.kind == DocKind.INVOICE && it.status == DocStatus.OPEN && it.sourceDocId == null })
            }
        } finally {
            here.close(); there.close(); dir.deleteRecursively()
        }
    }

    @Test
    fun `refusals are the same kinds as on the phone`() {
        fun why(text: String) = (BackupCodec.parseText(text).first as BackupCodec.ImportResult.Failed).why
        assertEquals(BackupCodec.ImportResult.Why.NOT_OURS, why("""{"format": "SomethingElse", "version": 1}"""))
        assertEquals(BackupCodec.ImportResult.Why.TOO_NEW, why(v7.replace("\"version\": 7", "\"version\": 99")))
        assertEquals(BackupCodec.ImportResult.Why.UNREADABLE, why("not json at all {"))
        assertEquals(BackupCodec.ImportResult.Why.INCOMPLETE, why(v7.replace("\"partyId\": 7", "\"partyId\": 8")))
    }
}
