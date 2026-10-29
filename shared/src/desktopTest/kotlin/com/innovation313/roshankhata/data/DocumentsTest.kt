package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The documents (schema 40, 29 Oct 2026): a quotation, a sales order, a
 * challan and the invoice are one table told apart by kind — each numbered
 * in its own series, stepped on by copying, immutable once stepped on, and
 * the notes drawn from the return that already holds the money.
 */
class DocumentsTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    private val lines = listOf(
        InvoiceItem(invoiceId = 0, itemName = "Urea", quantity = 10.0, unit = "bag", rate = 4650.0),
        InvoiceItem(invoiceId = 0, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 12800.0)
    )

    @Before
    fun open() {
        NumberMark.mark = null
        dir = Files.createTempDirectory("rk-documents").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
        NumberMark.mark = null
    }

    private suspend fun quotation(name: String = "Ali") =
        dao.saveInvoiceWithItems(Invoice(customerName = name, kind = DocKind.QUOTATION, discountPercent = 5.0), lines)

    private inline fun refused(why: DocRefused.Why, block: () -> Unit) {
        try {
            block()
            fail("expected a refusal: $why")
        } catch (e: DocRefused) {
            assertEquals(e.message, why, e.why)
        }
    }

    @Test
    fun `every kind runs its own series and the invoices keep the old rule`() = runBlocking {
        assertEquals("INV-000001", dao.getInvoice(dao.saveInvoiceWithItems(Invoice(customerName = "Ali"), lines))!!.invoiceNumber)
        assertEquals("Q-000001", dao.getInvoice(quotation())!!.invoiceNumber)
        assertEquals("Q-000002", dao.getInvoice(quotation())!!.invoiceNumber)
        assertEquals("PI-000001", dao.getInvoice(dao.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.PROFORMA), lines))!!.invoiceNumber)
        // The INV- series counts invoices alone: four rows are in the table but only one is an invoice, so the
        // next is INV-000002, not INV-000005. A book from before 40 (every row an invoice) numbers exactly as
        // it did: max(count, highest INV-) + 1.
        assertEquals("INV-000002", dao.getInvoice(dao.saveInvoiceWithItems(Invoice(customerName = "Bashir"), lines))!!.invoiceNumber)
        assertEquals(2, dao.highestInvoiceNumber())
        assertEquals(2, dao.highestDocNumber(DocKind.INVOICE, "INV-"))
        // A quotation's series is untouched by the invoices', and a deleted one still holds its number.
        val q3 = quotation()
        assertEquals("Q-000003", dao.getInvoice(q3)!!.invoiceNumber)
        dao.softDeleteInvoice(q3)
        assertEquals("Q-000004", dao.getInvoice(quotation())!!.invoiceNumber)
        // The device's letter rides after the number and the series still counts it (NumberMark).
        NumberMark.mark = "B"
        assertEquals("Q-000005-B", dao.getInvoice(quotation())!!.invoiceNumber)
        NumberMark.mark = null
        assertEquals("Q-000006", dao.getInvoice(quotation())!!.invoiceNumber)
        assertEquals(6, dao.highestDocNumber(DocKind.QUOTATION, "Q-"))
    }

    @Test
    fun `the chain runs quotation to sales order to challan to invoice, copying the lines`() = runBlocking {
        val q = quotation()
        val so = dao.convertDocument(q, DocKind.SALES_ORDER, by = "Owner")
        val dc = dao.convertDocument(so, DocKind.CHALLAN, by = "Owner")
        val inv = dao.convertDocument(dc, DocKind.INVOICE, by = "Owner")

        val order = dao.getInvoice(so)!!
        assertEquals(DocKind.SALES_ORDER, order.kind)
        assertEquals("SO-000001", order.invoiceNumber)
        assertEquals(q, order.sourceDocId)
        assertEquals(DocStatus.OPEN, order.status)
        assertEquals("the quotation's pricing rides along", 5.0, order.discountPercent!!, 0.0)
        assertEquals("DC-000001", dao.getInvoice(dc)!!.invoiceNumber)
        assertEquals(so, dao.getInvoice(dc)!!.sourceDocId)
        assertEquals("INV-000001", dao.getInvoice(inv)!!.invoiceNumber)
        assertEquals(dc, dao.getInvoice(inv)!!.sourceDocId)

        // Every step keeps the lines — same names, quantities and rates — as rows of its own with new uids.
        val qLines = dao.invoiceItems(q)
        for (id in listOf(so, dc, inv)) {
            val copied = dao.invoiceItems(id)
            assertEquals(qLines.map { Triple(it.itemName, it.quantity, it.rate) }, copied.map { Triple(it.itemName, it.quantity, it.rate) })
            assertTrue(copied.all { it.invoiceId == id })
            assertTrue("a copied line is its own row", copied.map { it.uid }.intersect(qLines.map { it.uid }.toSet()).isEmpty())
        }
        val uids = listOf(q, so, dc, inv).map { dao.getInvoice(it)!!.uid }
        assertEquals("four documents, four identities", 4, uids.toSet().size)

        // Each source is converted and closed; the invoice at the end is live.
        for (id in listOf(q, so, dc)) assertEquals(DocStatus.CONVERTED, dao.getInvoice(id)!!.status)
        assertEquals(DocStatus.OPEN, dao.getInvoice(inv)!!.status)
        assertEquals(3, dao.latestAudit(10).count { it.action == AuditAction.DOC_CONVERTED })
        assertTrue(dao.latestAudit(10).all { it.action != AuditAction.DOC_CONVERTED || it.user == "Owner" })
    }

    @Test
    fun `a converted document cannot change or step on again, and an illegal step is refused`() = runBlocking {
        val q = quotation()
        dao.convertDocument(q, DocKind.SALES_ORDER)
        val saved = dao.getInvoice(q)!!
        refused(DocRefused.Why.NOT_LIVE) { dao.updateInvoiceWithItems(saved.copy(note = "changed"), lines) }
        refused(DocRefused.Why.NOT_LIVE) { dao.convertDocument(q, DocKind.CHALLAN) }
        refused(DocRefused.Why.NOT_LIVE) { dao.cancelDocument(q) }
        assertNull("nothing was written", dao.getInvoice(q)!!.note)

        val q2 = quotation()
        refused(DocRefused.Why.BAD_STEP) { dao.convertDocument(q2, DocKind.CHALLAN) }
        refused(DocRefused.Why.BAD_STEP) { dao.convertDocument(q2, DocKind.PROFORMA) }
        refused(DocRefused.Why.BAD_STEP) { dao.convertDocument(q2, DocKind.QUOTATION) }
        refused(DocRefused.Why.BAD_KIND) { dao.convertDocument(q2, DocKind.CREDIT_NOTE) }
        val inv = dao.saveInvoiceWithItems(Invoice(customerName = "Ali"), lines)
        refused(DocRefused.Why.BAD_STEP) { dao.convertDocument(inv, DocKind.INVOICE) }
        refused(DocRefused.Why.NOT_FOUND) { dao.convertDocument(9_999L, DocKind.INVOICE) }
        dao.softDeleteInvoice(q2)
        refused(DocRefused.Why.NOT_FOUND) { dao.convertDocument(q2, DocKind.SALES_ORDER) }
        // A quotation may go straight to the invoice, carrying nothing it had received.
        val q3 = dao.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.QUOTATION, receivedAmount = 500.0), lines)
        val so3 = dao.convertDocument(q3, DocKind.SALES_ORDER)
        assertNull("a received amount is an invoice's", dao.getInvoice(so3)!!.receivedAmount)
        val inv3 = dao.convertDocument(dao.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.QUOTATION, receivedAmount = 500.0), lines), DocKind.INVOICE)
        assertEquals(500.0, dao.getInvoice(inv3)!!.receivedAmount!!, 0.0)
        // The print-only notes are never saved.
        refused(DocRefused.Why.BAD_KIND) { dao.saveInvoiceWithItems(Invoice(customerName = "Ali", kind = DocKind.CREDIT_NOTE), lines) }
    }

    @Test
    fun `accept and cancel move only from the state that allows them`() = runBlocking {
        val q = quotation()
        dao.acceptDocument(q, by = "Owner")
        assertEquals(DocStatus.ACCEPTED, dao.getInvoice(q)!!.status)
        refused(DocRefused.Why.NOT_OPEN) { dao.acceptDocument(q) }
        // An accepted quotation still edits and still converts.
        dao.updateInvoiceWithItems(dao.getInvoice(q)!!.copy(note = "agreed on the phone"), lines)
        assertEquals("agreed on the phone", dao.getInvoice(q)!!.note)
        assertEquals(DocStatus.ACCEPTED, dao.getInvoice(q)!!.status)
        val so = dao.convertDocument(q, DocKind.SALES_ORDER)
        assertEquals(DocStatus.CONVERTED, dao.getInvoice(q)!!.status)
        refused(DocRefused.Why.NOT_OPEN) { dao.acceptDocument(q) }

        dao.cancelDocument(so, by = "Owner")
        assertEquals(DocStatus.CANCELLED, dao.getInvoice(so)!!.status)
        refused(DocRefused.Why.NOT_LIVE) { dao.cancelDocument(so) }
        refused(DocRefused.Why.NOT_LIVE) { dao.convertDocument(so, DocKind.CHALLAN) }
        refused(DocRefused.Why.NOT_LIVE) { dao.updateInvoiceWithItems(dao.getInvoice(so)!!.copy(note = "x"), lines) }
        assertFalse("cancelled, not binned: it stays on record under its number", dao.getInvoice(so)!!.isDeleted)
        val actions = dao.latestAudit(10).map { it.action }
        assertTrue(AuditAction.DOC_ACCEPTED in actions && AuditAction.DOC_CANCELLED in actions)
    }

    @Test
    fun `an edit keeps the row's identity and cannot change its kind or status`() = runBlocking {
        val q = quotation()
        val saved = dao.getInvoice(q)!!
        // The phone's editor builds the edit as a fresh Invoice: a new uid, kind INVOICE, status OPEN.
        dao.updateInvoiceWithItems(Invoice(id = q, customerName = "Ali", kind = DocKind.INVOICE, status = DocStatus.CANCELLED, note = "edited"), lines)
        val after = dao.getInvoice(q)!!
        assertEquals("edited", after.note)
        assertEquals(saved.uid, after.uid)
        assertEquals(saved.createdAt, after.createdAt)
        assertEquals(DocKind.QUOTATION, after.kind)
        assertEquals(DocStatus.OPEN, after.status)
        assertNotEquals("the edit did write", saved.note, after.note)
    }

    @Test
    fun `a challan shows no prices and the kinds know their steps`() = runBlocking {
        assertFalse(DocKind.showsPrices(DocKind.CHALLAN))
        for (k in DocKind.ALL - DocKind.CHALLAN) assertTrue(k, DocKind.showsPrices(k))
        assertEquals(DocKind.SALES_ORDER, DocKind.next(DocKind.QUOTATION))
        assertEquals(DocKind.CHALLAN, DocKind.next(DocKind.SALES_ORDER))
        assertEquals(DocKind.INVOICE, DocKind.next(DocKind.CHALLAN))
        assertEquals(DocKind.INVOICE, DocKind.next(DocKind.PROFORMA))
        assertNull(DocKind.next(DocKind.INVOICE))
        assertNull(DocKind.next(DocKind.CREDIT_NOTE))
        assertTrue(DocKind.canConvert(DocKind.QUOTATION, DocKind.INVOICE))
        assertFalse(DocKind.canConvert(DocKind.INVOICE, DocKind.QUOTATION))
        assertFalse(DocKind.canConvert(DocKind.CHALLAN, DocKind.SALES_ORDER))
        assertEquals(listOf("Q-", "PI-", "SO-", "DC-", "INV-"), DocKind.saved.map { DocKind.prefix(it) })
        assertFalse(DocKind.isSaved(DocKind.DEBIT_NOTE))
        // Every kind and status has a word in strings.xml.
        val words = com.innovation313.roshankhata.words.desktopWords(java.util.Locale.ENGLISH)
        assertEquals("Delivery Challan", words.text(DocKind.wordKey(DocKind.CHALLAN)))
        assertEquals("INVOICE", words.text(DocKind.wordKey(DocKind.INVOICE)))
        for (k in DocKind.ALL) assertTrue(words.text(DocKind.wordKey(k)).isNotBlank())
        for (s in DocStatus.ALL) assertTrue(words.text(DocStatus.wordKey(s)).isNotBlank())
    }

    @Test
    fun `a credit note is built from a return and a debit note from goods sent back, nothing saved`() = runBlocking {
        val ali = dao.insertParty(Party(name = "Ali", phone = "0300 0000000"))
        val ret = SaleReturn(id = 7, returnNumber = "R-000012", partyId = ali, customerName = "Ali", total = 9300.0, reason = "Damaged", note = "two bags torn", refundMethod = SaleReturn.ACCOUNT)
        val retLines = listOf(
            SaleReturnItem(id = 1, returnId = 7, lineNo = 1, itemName = "DAP", quantity = 1.0, unit = "bag", rate = 12800.0),
            SaleReturnItem(id = 2, returnId = 7, lineNo = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4650.0),
            SaleReturnItem(id = 3, returnId = 7, lineNo = 2, itemName = "gone", quantity = 1.0, rate = 1.0, isDeleted = true)
        )
        val (credit, creditLines) = NoteDocument.creditNote(ret, retLines, dao.getParty(ali))
        assertEquals(DocKind.CREDIT_NOTE, credit.kind)
        assertEquals("R-000012", credit.invoiceNumber)
        assertEquals("Ali", credit.customerName)
        assertEquals("0300 0000000", credit.customerPhone)
        assertEquals("Damaged · two bags torn", credit.note)
        assertEquals(listOf("Urea", "DAP"), creditLines.map { it.itemName })
        assertEquals(2.0 * 4650.0 + 12800.0, credit.totals(creditLines).grandTotal, 0.0)
        assertNull("how the value went back is the return's own record", credit.receivedAmount)

        val supplier = dao.insertParty(Party(name = "Sayban", isCustomer = false))
        val entryId = dao.insertEntryWithItems(
            LedgerEntry(partyId = supplier, amount = 9300.0, isGiven = true, entryNumber = "", note = "Return: Urea, batch B-7"),
            listOf(EntryItem(entryId = 0, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4650.0))
        )
        val entry = dao.getEntry(entryId)!!
        val (debit, debitLines) = NoteDocument.debitNote(entry, dao.itemsOfEntry(entryId), dao.getParty(supplier))
        assertEquals(DocKind.DEBIT_NOTE, debit.kind)
        assertEquals(entry.entryNumber, debit.invoiceNumber)
        assertEquals("Sayban", debit.customerName)
        assertEquals(entryId, debit.sourceEntryId)
        assertEquals(listOf("Urea"), debitLines.map { it.itemName })
        assertEquals(9300.0, debit.totals(debitLines).grandTotal, 0.0)
        assertEquals("nothing was written to the documents table", 0, dao.totalInvoiceCount())
    }
}
