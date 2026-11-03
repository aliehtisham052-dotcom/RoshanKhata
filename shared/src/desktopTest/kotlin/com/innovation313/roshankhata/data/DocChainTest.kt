package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The chain a convert shows (3 Nov 2026): the real steps a document came through, itself, and the steps still to
 * come — never a step it did not take.
 */
class DocChainTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()

    private val lines = listOf(InvoiceItem(invoiceId = 0, itemName = "Item A", quantity = 2.0, unit = "pcs", rate = 150.0))

    @Before
    fun open() {
        NumberMark.mark = null
        dir = Files.createTempDirectory("rk-docchain").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `a quotation stepped on twice shows its two real steps and what is left`() = runBlocking {
        val q = dao.saveInvoiceWithItems(Invoice(customerName = "Test Customer", kind = DocKind.QUOTATION), lines)
        val so = dao.convertDocument(q, DocKind.SALES_ORDER)
        val dc = dao.convertDocument(so, DocKind.CHALLAN)
        assertEquals(
            listOf(
                DocChain.Node(DocKind.QUOTATION, "Q-000001"),
                DocChain.Node(DocKind.SALES_ORDER, "SO-000001"),
                DocChain.Node(DocKind.CHALLAN, "DC-000001"),
                DocChain.Node(DocKind.INVOICE, null)
            ),
            DocChain.of(dao, dc)
        )
    }

    @Test
    fun `a sales order written fresh starts the chain at itself`() = runBlocking {
        val so = dao.saveInvoiceWithItems(Invoice(customerName = "Test Customer", kind = DocKind.SALES_ORDER), lines)
        val dc = dao.convertDocument(so, DocKind.CHALLAN)
        assertEquals(
            listOf(
                DocChain.Node(DocKind.SALES_ORDER, "SO-000001"),
                DocChain.Node(DocKind.CHALLAN, "DC-000001"),
                DocChain.Node(DocKind.INVOICE, null)
            ),
            DocChain.of(dao, dc)
        )
    }

    @Test
    fun `a converted quotation shows the sales order made from it as done and then what is left`() = runBlocking {
        val q = dao.saveInvoiceWithItems(Invoice(customerName = "Test Customer", kind = DocKind.QUOTATION), lines)
        dao.convertDocument(q, DocKind.SALES_ORDER)
        assertEquals(
            listOf(
                DocChain.Node(DocKind.QUOTATION, "Q-000001"),
                DocChain.Node(DocKind.SALES_ORDER, "SO-000001"),
                DocChain.Node(DocKind.CHALLAN, null),
                DocChain.Node(DocKind.INVOICE, null)
            ),
            DocChain.of(dao, q)
        )
    }

    @Test
    fun `a cancelled document has no step after it`() = runBlocking {
        val q = dao.saveInvoiceWithItems(Invoice(customerName = "Test Customer", kind = DocKind.QUOTATION), lines)
        val so = dao.convertDocument(q, DocKind.SALES_ORDER)
        dao.cancelDocument(so)
        val chain = listOf(DocChain.Node(DocKind.QUOTATION, "Q-000001"), DocChain.Node(DocKind.SALES_ORDER, "SO-000001"))
        assertEquals(chain, DocChain.of(dao, so))
        // From the quotation it was made from too: the chain ends at the cancelled order.
        assertEquals(chain, DocChain.of(dao, q))
    }

    @Test
    fun `a proforma's chain is the proforma and the invoice`() = runBlocking {
        val pi = dao.saveInvoiceWithItems(Invoice(customerName = "Test Customer", kind = DocKind.PROFORMA), lines)
        val inv = dao.convertDocument(pi, DocKind.INVOICE)
        assertEquals(
            listOf(DocChain.Node(DocKind.PROFORMA, "PI-000001"), DocChain.Node(DocKind.INVOICE, "INV-000001")),
            DocChain.of(dao, inv)
        )
    }
}
