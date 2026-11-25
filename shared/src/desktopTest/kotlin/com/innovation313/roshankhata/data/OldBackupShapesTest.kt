package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Backups of formats 1 to 6, in the shapes their writers really wrote ([OldBackupShapes], each from its format's own
 * export code in the history), open on today's build (25 Nov 2026, plan item B2, data safety). Until now these formats
 * were tested only as format 7's shape under a lower number ([OldBooksBackupFormatsTest]); a field or a table the old
 * writers named differently, or a row they wrote without something today's reader requires, would not have been seen.
 *
 * Each file is read on both paths (the whole file, and a page at a time as a big file is) and restored into a fresh book.
 * Then: every row is back, in the bin where it was in the bin; each customer's live balance is what the old book said; the
 * goods written on an entry itself (formats 1 to 6 had no goods lines) come back as one goods line each, by the same rule
 * the database migration uses ([EntryItem.fromLegacy]); what a format had is kept and what came later reads as nothing.
 */
class OldBackupShapesTest {

    private lateinit var dir: File

    @Before
    fun open() {
        Access.resetForTest()
        dir = Files.createTempDirectory("rk-old-shapes").toFile()
    }

    @After
    fun close() {
        BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        Access.resetForTest()
        dir.deleteRecursively()
    }

    private fun read(file: File, paged: Boolean): Pair<BackupCodec.ImportResult, BackupCodec.ParsedBackup?> {
        BackupCodec.pagedFromBytes = if (paged) 0L else Long.MAX_VALUE
        try {
            return BackupCodec.parseFile(file)
        } finally {
            BackupCodec.pagedFromBytes = BackupCodec.PAGED_RESTORE_BYTES
        }
    }

    @Test
    fun `the shapes are formats 1 to 6, each carrying its own number`() {
        val versions = OldBackupShapes.all.map { (_, text) -> JSONObject(text).getInt("version") }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 6), versions)
        // The last format-6 file is the only one that names its shop at the top (f2d07767).
        assertEquals(listOf("format6Last"), OldBackupShapes.all.filter { JSONObject(it.second).has("businessName") }.map { it.first })
    }

    @Test
    fun `every format from 1 to 6 parses, restores on both paths and keeps the old balances`() = runBlocking {
        for ((name, text) in OldBackupShapes.all) {
            val v = JSONObject(text).getInt("version")
            val file = dir.resolve("$name.txt").apply { writeText(text) }
            for (paged in listOf(false, true)) {
                val label = "$name (format $v), paged=$paged"
                val (result, parsed) = read(file, paged)
                assertTrue("$label: $result", result is BackupCodec.ImportResult.Ok)
                val ok = result as BackupCodec.ImportResult.Ok
                assertEquals("$label: parties counted", 4, ok.parties)
                assertEquals("$label: entries counted", 8, ok.entries)
                assertEquals("$label: cash rows counted", 3, ok.cash)
                assertEquals("$label: cheques counted", 1, ok.cheques)
                assertEquals("$label: the format the file says", v, parsed!!.version)

                // What only some formats had, as the parser hands it on.
                assertEquals("$label: shop name at the top", if (name == "format6Last") "Al-Noor Traders" else null, parsed.businessName)
                val profile = parsed.businessProfile
                if (v >= 5) {
                    assertEquals("$label: profile name", "Al-Noor Traders", profile!!.businessName)
                    assertEquals("$label: profile address", "Main Bazar", profile.businessAddress)
                    assertEquals("$label: profile terms", "Goods once sold are not returned.", profile.termsAndConditions)
                    assertTrue("$label: photo on statement", profile.photoOnStatement)
                    assertNull("$label: no currency before format 6's last weeks", profile.currency)
                } else {
                    assertNull("$label: no profile before format 5", profile)
                }

                val db = DesktopDatabase.open(dir.resolve("restored-$name-$paged.db"))
                try {
                    BackupCodec.restoreLedger(db.khataDao(), parsed)
                    expectBook(db.khataDao(), v, name, label)
                } finally { db.close() }
            }
        }
    }

    private suspend fun expectBook(d: KhataDao, v: Int, name: String, label: String) {
        val parties = d.allPartiesForBackup().associateBy { it.id }
        assertEquals("$label: parties", setOf(1L, 2L, 3L, 4L), parties.keys)
        assertEquals("$label: Ali", "Ali Raza", parties.getValue(1).name)
        assertEquals("$label: Ali's phone", "0300 1234567", parties.getValue(1).phone)
        assertEquals("$label: Ali's limit", 10_000.0, parties.getValue(1).creditLimit!!, 0.0)
        assertTrue("$label: Noor is a supplier", !parties.getValue(3).isCustomer)
        assertTrue("$label: the binned customer stays in the bin", parties.getValue(4).isDeleted)
        assertEquals("$label: father's name only from format 6's last weeks",
            if (name == "format6Last") "Karim Bakhsh" else null, parties.getValue(1).fatherName)

        val entries = d.allEntriesForBackup().associateBy { it.id }
        assertEquals("$label: entries", (1L..8L).toSet(), entries.keys)
        assertEquals("$label: entries in the bin", setOf(7L, 8L), entries.values.filter { it.isDeleted }.map { it.id }.toSet())
        assertTrue("$label: the loan is a loan", entries.getValue(3).isQarzeHasna)
        assertEquals("$label: entry numbers", "RK-000005", entries.getValue(5).entryNumber)
        assertEquals("$label: every entry is an ordinary one", setOf(EntryKind.NORMAL), entries.values.map { it.entryKind }.toSet())
        assertEquals("$label: way of paying only from format 6's last weeks",
            if (name == "format6Last") PaymentMethod.CASH else null, entries.getValue(2).paymentMethod)
        assertTrue("$label: every entry has a uid", entries.values.all { !it.uid.isNullOrBlank() })
        assertEquals("$label: uids are unique", 8, entries.values.mapNotNull { it.uid }.toSet().size)

        // The balances the old book showed (live entries only): Ali 4,500 - 1,500; Sana 2,000 - 500; Noor 7,000 - 12,000.
        assertEquals("$label: Ali's balance", 3_000.0, d.liveBalance(1), 0.0001)
        assertEquals("$label: Sana's balance", 1_500.0, d.liveBalance(2), 0.0001)
        assertEquals("$label: Noor's balance", -5_000.0, d.liveBalance(3), 0.0001)
        assertEquals("$label: the binned customer's balance", 0.0, d.liveBalance(4), 0.0001)

        // Goods on the entry itself become one goods line each (formats 1 to 6 had no goods lines).
        val lines = d.allEntryItemsForBackup().sortedBy { it.entryId }
        assertEquals("$label: goods lines", listOf(1L, 5L), lines.map { it.entryId })
        assertEquals("$label: Urea's line", listOf("Urea", "DAP"), lines.map { it.itemName })
        assertEquals("$label: quantities", listOf(2.0, 10.0), lines.map { it.quantity })
        assertEquals("$label: units", listOf("bag", "bag"), lines.map { it.unit })
        assertEquals("$label: products on the lines", if (v >= 4) listOf(1L, 2L) else listOf(null, null), lines.map { it.productId })
        // The entry's billItemId came with 6a2ff7f8, under format 4: the format-4 shape here is the last one, which has it.
        assertEquals("$label: the batch on the supplier's line", if (v >= 4) 1L else null, lines[1].billItemId)
        assertTrue("$label: goods lines have uids", lines.all { !it.uid.isNullOrBlank() })

        val cheque = d.allChequesForBackup().single()
        assertEquals("$label: cheque", 1_000.0, cheque.amount, 0.0)
        assertEquals("$label: cheque status", ChequeStatus.PENDING, cheque.status)
        assertEquals("$label: cheque party", 1L, cheque.partyId)

        val cash = d.allCashForBackup().associateBy { it.id }
        assertEquals("$label: cash rows", setOf(1L, 2L, 3L), cash.keys)
        assertEquals("$label: cash in", 500.0, cash.values.filter { it.isIncome && !it.isDeleted }.sumOf { it.amount }, 0.0)
        assertEquals("$label: cash out", 200.0, cash.values.filter { !it.isIncome && !it.isDeleted }.sumOf { it.amount }, 0.0)
        assertTrue("$label: the binned cash row", cash.getValue(3).isDeleted)

        // Plans from format 2, bills from 3, products from 4, invoices from 5, decisions from 6.
        val plans = d.allPlansForBackup()
        val installments = d.allInstallmentsForBackup()
        if (v >= 2) {
            assertEquals("$label: plan", 3_000.0, plans.single().totalAmount, 0.0)
            assertEquals("$label: instalment's entry", 2L, installments.single().ledgerEntryId)
            assertEquals("$label: instalment", 1_500.0, installments.single().amount, 0.0)
        } else {
            assertTrue("$label: no plans in format 1", plans.isEmpty() && installments.isEmpty())
        }
        val bills = d.allBillsForBackup()
        val billItems = d.allBillItemsForBackup()
        if (v >= 3) {
            assertEquals("$label: bill", "B-101", bills.single().billNumber)
            assertEquals("$label: bill's entry", 5L, bills.single().ledgerEntryId)
            assertEquals("$label: bill total", 12_000.0, bills.single().totalAmount, 0.0)
            val line = billItems.single()
            assertEquals("$label: batch", "LOT-7", line.batchNumber)
            assertEquals("$label: batch quantity", 10.0, line.quantity, 0.0)
            assertEquals("$label: batch product", if (v >= 4) 2L else null, line.productId)
        } else {
            assertTrue("$label: no bills before format 3", bills.isEmpty() && billItems.isEmpty())
        }
        val products = d.allProductsForBackup().associateBy { it.id }
        if (v >= 4) {
            assertEquals("$label: products", setOf(1L, 2L), products.keys)
            assertEquals("$label: product name", "Urea", products.getValue(1).name)
            assertEquals("$label: product key made again", ProductName.key("Urea"), products.getValue(1).nameKey)
            assertEquals("$label: a product's price only from format 6's last weeks",
                if (name == "format6Last") 2_250.0 else null, products.getValue(1).salePrice)
            assertEquals("$label: a product's company only from format 6's last weeks",
                if (name == "format6Last") "Made-up Fertilisers" else null, products.getValue(1).company)
        } else {
            assertTrue("$label: no products before format 4", products.isEmpty())
        }
        val invoices = d.allInvoicesForBackup()
        val invoiceItems = d.allInvoiceItemsForBackup()
        if (v >= 5) {
            val inv = invoices.single()
            assertEquals("$label: invoice", "INV-0001", inv.invoiceNumber)
            assertEquals("$label: invoice kind", DocKind.INVOICE, inv.kind)
            assertEquals("$label: invoice status", DocStatus.OPEN, inv.status)
            assertEquals("$label: invoice received", 1_500.0, inv.receivedAmount!!, 0.0)
            assertEquals("$label: invoice line", 2_250.0, invoiceItems.single().rate, 0.0)
        } else {
            assertTrue("$label: no invoices before format 5", invoices.isEmpty() && invoiceItems.isEmpty())
        }
        val decisions = d.allDismissedDuplicatesForBackup()
        if (v >= 6) assertEquals("$label: not a duplicate", listOf("1,2"), decisions.map { it.partyIdsKey })
        else assertTrue("$label: no decisions before format 6", decisions.isEmpty())

        // Nothing of later formats appears from nowhere.
        assertTrue("$label: no tickets", d.allSalesForBackup().isEmpty())
        assertTrue("$label: no staff", d.allStaffForBackup().isEmpty())
    }
}
