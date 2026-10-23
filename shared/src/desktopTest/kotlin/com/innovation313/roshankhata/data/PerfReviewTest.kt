package com.innovation313.roshankhata.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The 23 Oct 2026 review report's counter performance and safety points
 * (R22-R28, R74, R76, R84), on a real book: every faster path gives the
 * figures the old one gave — FEFO lots with the shelf read once, the bounded
 * cashbook, invoice, ticket and return lists, one customer's balance — Hold is
 * one transaction taken from a snapshot, a line has a ceiling, and two staff
 * may not share a name.
 */
class PerfReviewTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-perf-review").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private suspend fun product(name: String, price: Double = 1000.0) = dao.insertProduct(
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = "bottle", salePrice = price)
    )

    private suspend fun sellTo(partyId: Long?, productId: Long, qty: Double, at: Long = now): Long =
        dao.postSale(
            Sale(partyId = partyId, customerName = partyId?.let { dao.getParty(it)?.name }, timestamp = at, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, productId = productId, itemName = dao.productById(productId)!!.name, quantity = qty, unit = "bottle", rate = 1000.0)),
            listOfNotNull(if (partyId == null) SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = qty * 1000.0) else null)
        )

    // ---------- R22 / R23: the shelf read once ----------

    @Test
    fun `batches capped by a shelf read once are the batches capped by their own read`() = runBlocking {
        val engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
        val karate = product("Karate")
        val urea = product("Urea")
        dao.insertSupplierBill(
            null, SupplierBill(partyId = engro, billDate = now - 30 * day, totalAmount = 0.0),
            listOf(
                BillItem(billId = 0, productName = "Karate", batchNumber = "A", expiryDate = now + 50 * day, quantity = 10.0, unit = "bottle", rate = 500.0, productId = karate),
                BillItem(billId = 0, productName = "Karate", batchNumber = "B", expiryDate = now + 90 * day, quantity = 10.0, unit = "bottle", rate = 500.0, productId = karate),
                BillItem(billId = 0, productName = "Urea", batchNumber = "U", expiryDate = now + 90 * day, quantity = 8.0, unit = "bottle", rate = 500.0, productId = urea)
            )
        )
        // A khata sale with no batch takes stock but no lot: the shelf caps the lots.
        val bilal = dao.insertParty(Party(name = "Bilal"))
        sellTo(bilal, karate, 6.0)
        val shelf = Counter.onHand(dao)
        for (id in listOf(karate, urea)) {
            val p = dao.productById(id)!!
            assertEquals(Fefo.lots(dao, p), Fefo.lots(dao, p, shelf))
        }
        assertEquals(14.0, Fefo.lots(dao, dao.productById(karate)!!, shelf).sumOf { it.left }, 0.001)
        val a = dao.batchesOfProduct(karate).first { it.batchNumber == "A" }.id
        assertEquals(Fefo.lotOf(dao, a), Fefo.lotOf(dao, a, shelf))
        // A ticket over two products takes the soonest batch (A has 4 left once the shelf caps it).
        val draft = CounterDraft()
        draft.add(dao.productById(karate)!!, qty = 3.0)
        draft.add(dao.productById(urea)!!, qty = 2.0)
        draft.payRestInCash()
        val sale = Counter.post(dao, draft)
        val lines = dao.saleItems(sale)
        assertEquals(setOf(a), lines.filter { it.productId == karate }.map { it.billItemId }.toSet())
    }

    // ---------- R24: the cashbook a page at a time ----------

    @Test
    fun `a cashbook page is the newest lines of the whole list, and the count is the whole table's`() = runBlocking {
        repeat(12) { i -> dao.insertCashEntry(CashEntry(amount = 10.0 + i, isIncome = i % 2 == 0, category = "Line $i", timestamp = now - i * 60_000L)) }
        val gone = dao.insertCashEntry(CashEntry(amount = 99.0, isIncome = true, category = "Gone", timestamp = now + 1))
        assertTrue(dao.softDeleteOwnCashEntry(gone))
        val all = dao.observeCashEntries().first()
        assertEquals(all.take(5), dao.observeCashPage(5).first())
        assertEquals(all, dao.observeCashPage(ListWindow.PAGE).first())
        assertEquals(12, dao.observeCashCount().first())
    }

    @Test
    fun `the window grows only when it is full and the end is near`() {
        assertEquals(ListWindow.PAGE, ListWindow.grown(ListWindow.PAGE, 150, 149))
        assertEquals(ListWindow.PAGE, ListWindow.grown(ListWindow.PAGE, ListWindow.PAGE, 10))
        assertEquals(2 * ListWindow.PAGE, ListWindow.grown(ListWindow.PAGE, ListWindow.PAGE, ListWindow.PAGE - 5))
    }

    // ---------- R76: invoices a page at a time, summed once ----------

    @Test
    fun `an invoice page totals every invoice exactly as the full list does`() = runBlocking {
        val shapes = listOf<(Invoice) -> Invoice>(
            { it },
            { it.copy(discountPercent = 10.0, taxPercent = 17.0) },
            { it.copy(discountAmount = 250.0, additionalChargeAmount = 120.0) },
            { it.copy(discountAmount = 99_999.0) },
            { it.copy(taxPercent = 5.0, additionalChargeAmount = 40.5) }
        )
        shapes.forEachIndexed { i, shape ->
            dao.saveInvoiceWithItems(
                shape(Invoice(customerName = "Customer $i", invoiceDate = now - i * day)),
                listOf(
                    InvoiceItem(invoiceId = 0, itemName = "Karate", quantity = 2.0 + i, rate = 1250.5),
                    InvoiceItem(invoiceId = 0, itemName = "Urea", quantity = 1.5, rate = 3300.0)
                )
            )
        }
        val gone = dao.saveInvoiceWithItems(Invoice(customerName = "Gone", invoiceDate = now), listOf(InvoiceItem(invoiceId = 0, itemName = "X", quantity = 1.0, rate = 1.0)))
        dao.softDeleteInvoice(gone)
        val all = dao.observeInvoices().first()
        assertEquals(5, all.size)
        assertEquals(all, dao.observeInvoicePage(ListWindow.PAGE).first())
        assertEquals(all.take(3), dao.observeInvoicePage(3).first())
        val from = now - 2 * day
        assertEquals(all.filter { it.invoiceDate >= from }, dao.observeInvoicesFrom(from).first())
        assertEquals(5, dao.observeInvoiceCount().first())
    }

    // ---------- R25: the counter's recent tickets and returns ----------

    @Test
    fun `the recent tickets are the newest of all tickets, with their line counts`() = runBlocking {
        val urea = product("Urea")
        repeat(10) { i -> sellTo(null, urea, 1.0 + i, at = now - i * 60_000L) }
        val all = dao.observeSales().first()
        assertEquals(all.take(8), dao.observeRecentSales(8).first())
        assertEquals(dao.observeReturns().first().take(6), dao.observeRecentReturns(6).first())
    }

    // ---------- R74: one customer's balance ----------

    @Test
    fun `a receipt's balance read alone is the balance the whole list gives`() = runBlocking {
        val urea = product("Urea")
        val bilal = dao.insertParty(Party(name = "Bilal"))
        val other = dao.insertParty(Party(name = "Other"))
        sellTo(bilal, urea, 3.0)
        sellTo(other, urea, 1.0)
        val listed = dao.partiesWithBalanceOnce().first { it.id == bilal }.balance
        assertEquals(listed, dao.liveBalance(bilal), 0.0001)
        assertEquals(3000.0, dao.liveBalance(bilal), 0.0001)
    }

    // ---------- R27: Hold ----------

    @Test
    fun `a ticket resumed and parked again leaves one held ticket, and a line rung up meanwhile stays`() = runBlocking {
        val p = dao.productById(product("Urea"))!!
        val q = dao.productById(product("DAP"))!!
        val draft = CounterDraft()
        draft.add(p, qty = 2.0)
        val first = Counter.hold(dao, draft)
        assertEquals(1, dao.heldSalesOnce().size)
        // Resumed, then parked again from a snapshot; a line scanned while it is written is the next ticket's.
        val again = CounterDraft()
        again.add(p, qty = 2.0)
        again.heldId = first
        val ticket = Counter.holdTicket(again)
        again.add(q, qty = 1.0)
        val second = dao.holdSale(ticket.sale, ticket.items, ticket.heldId)
        again.clearAfter(ticket)
        val held = dao.heldSalesOnce()
        assertEquals(listOf(second), held.map { it.id })
        assertEquals(1, dao.saleItems(second).size)
        assertEquals(listOf("DAP"), again.lines.map { it.toItem().itemName })
        assertNull(again.heldId)
    }

    // ---------- R28: a line's ceiling ----------

    @Test
    fun `a scanned barcode as a quantity or an endless figure is refused`() {
        fun line(qty: Double, rate: Double = 100.0) = SaleItem(saleId = 0, itemName = "Urea", quantity = qty, rate = rate)
        val scanned = SaleMath.check(listOf(line(2_100_012_345_678.0)), emptyList(), 0.0, hasParty = true)
        assertEquals(SaleMath.Problem.BAD_QUANTITY, scanned)
        assertEquals(SaleMath.Problem.BAD_QUANTITY, SaleMath.check(listOf(line(Double.POSITIVE_INFINITY)), emptyList(), 0.0, hasParty = true))
        assertEquals(SaleMath.Problem.BAD_RATE, SaleMath.check(listOf(line(1.0, SaleMath.MAX_RATE * 2)), emptyList(), 0.0, hasParty = true))
        assertNull(SaleMath.check(listOf(line(SaleMath.MAX_QTY, 1.0)), emptyList(), SaleMath.MAX_QTY, hasParty = true))
    }

    // ---------- R84: one name, one staff member ----------

    @Test
    fun `a staff name already used, removed staff included, is taken`() = runBlocking {
        val ali = dao.insertStaff(Staff(name = "Muhammad Ali", monthlySalary = 30000.0))
        val gone = dao.insertStaff(Staff(name = "Aslam", monthlySalary = 20000.0, isActive = false))
        assertTrue(dao.staffNameTaken(" muhammad ali "))
        assertTrue(dao.staffNameTaken("Aslam"))
        assertFalse(dao.staffNameTaken("Muhammad Ali", exceptId = ali))
        assertFalse(dao.staffNameTaken("Bilal"))
        assertFalse(dao.staffNameTaken("Aslam", exceptId = gone))
    }
}
