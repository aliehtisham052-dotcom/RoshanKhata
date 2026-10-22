package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.Calc
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * The counter's figures after the 22 Oct 2026 review, part 4: a count where
 * the book cannot say what it holds is not written; profit takes a return off
 * as its sale was counted; revenue takes inside tax off after the discount; a
 * cost is never a rate in another unit; Indonesian figures read as written;
 * walk-in tickets are in the register; a hand-typed price label is a price
 * change; audit notes go with their sale; a void never leaves points below
 * nothing; round-to-whole rounds half up; a transfer arrives in the receiving
 * product's unit; a restore says what else it would undo; a ticket is taken
 * once, and what is scanned meanwhile stays for the next.
 */
class PosReviewTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-pos-review").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private fun product(name: String, unit: String = "bag", price: Double? = null, cost: Double? = null, packUnit: String? = null, packSize: Double? = null, barcode: String? = null) =
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = unit,
            salePrice = price, costPrice = cost, packUnit = packUnit, packSize = packSize, barcode = barcode)

    private suspend fun walkIn(lines: List<SaleItem>, discount: Double = 0.0, paid: Double? = null): Long {
        val total = lines.sumOf { SaleMath.lineTotal(it.quantity, it.rate, it.discount) } - discount
        return dao.postSale(
            Sale(timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, discountAmount = discount),
            lines, listOf(SalePayment(saleId = 0, method = PaymentMethod.CASH, amount = paid ?: total))
        )
    }

    private fun line(productId: Long, name: String, qty: Double, rate: Double, unit: String = "bag", cost: Double? = null) =
        SaleItem(saleId = 0, productId = productId, itemName = name, quantity = qty, unit = unit, rate = rate, costRate = cost)

    @Test
    fun `a count where the book cannot say what it holds is not written`() = runBlocking {
        val engro = dao.insertParty(Party(name = "Engro", isCustomer = false))
        val urea = dao.insertProduct(product("Urea"))
        dao.insertSupplierBill(null, SupplierBill(partyId = engro, totalAmount = 0.0),
            listOf(BillItem(billId = 0, productName = "Urea", quantity = 40.0, unit = "bag", rate = 4500.0, productId = urea)))
        // Sold by the kilo with no pack set: the shelf cannot be said.
        walkIn(listOf(line(urea, "Urea", 300.0, 95.0, unit = "kg")))
        assertNull(Stock.of(dao).first { it.productId == urea }.onHand)
        assertEquals(0, dao.recordCount(mapOf(urea to 30.0), "count"))
        assertTrue(dao.allAdjustmentsForBackup().isEmpty())
    }

    @Test
    fun `profit takes a return off as its sale was counted, and only where its cost is known`() = runBlocking {
        val a = dao.insertProduct(product("A"))
        val b = dao.insertProduct(product("B"))
        val sale = walkIn(listOf(line(a, "A", 1.0, 1000.0, cost = 800.0), line(b, "B", 1.0, 5000.0)))
        val bLine = dao.saleItems(sale).first { it.productId == b }
        dao.postReturn(
            SaleReturn(saleId = sale, total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 5000.0, reason = "damaged", timestamp = now),
            listOf(SaleReturnItem(returnId = 0, saleItemId = bLine.id, productId = b, itemName = "B", quantity = 1.0, unit = "bag", rate = 5000.0))
        )
        val r = SalesReport.compute(dao, now - 60_000, now + 60_000)
        // Before: 1,000 - 800 - (5,000 returned - 0) = -4,800. The returned goods' cost was never known.
        assertEquals(200.0, r.grossProfit, 0.001)
    }

    @Test
    fun `tax inside a price comes off after the ticket's discount`() {
        val l = SoldLine(
            saleId = 1, timestamp = 0, customerName = null, createdBy = null, taxPercent = null, productId = null, itemName = "X",
            category = null, quantity = 1.0, unit = null, rate = 1180.0, discount = 0.0, costRate = null,
            taxRate = 18.0, taxAmount = 162.0, taxIncluded = true, saleSubtotal = 1180.0, saleDiscount = 118.0
        )
        assertEquals(900.0, l.revenue, 0.001)
        // Half of it back: half of what it brought in.
        val back = ReturnedLine(quantity = 0.5, soldQuantity = 1.0, rate = 1180.0, discount = 0.0, costRate = 600.0,
            taxAmount = 162.0, taxIncluded = true, saleSubtotal = 1180.0, saleDiscount = 118.0)
        assertEquals(450.0, back.revenue, 0.001)
        assertEquals(300.0, back.cost!!, 0.001)
    }

    @Test
    fun `a cost is never a rate written in another unit`() {
        val kg = product("Urea", unit = "kg")
        assertNull(Cost.lastInOwnUnit(kg, listOf(Cost.Purchase(1.0, "bag", 4500.0))))
        val packed = product("Urea", unit = "kg", packUnit = "bag", packSize = 50.0)
        assertEquals(90.0, Cost.lastInOwnUnit(packed, listOf(Cost.Purchase(1.0, "bag", 4500.0)))!!, 1e-9)
        assertEquals(95.0, Cost.lastInOwnUnit(kg, listOf(Cost.Purchase(0.0, "bag", 4500.0), Cost.Purchase(2.0, "kg", 95.0)))!!, 1e-9)
    }

    @Test
    fun `Indonesian figures read as they are written, and no box takes a figure that is not one`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale("id", "ID"))
            assertEquals(12.5, Calc.evalPad("12,5")!!, 1e-9)
            assertEquals(1250.5, Calc.evalPad("1.250,50")!!, 1e-9)
            assertEquals(1_250_000.0, Calc.evalPad("1.250.000")!!, 1e-9)
            assertEquals(1.125, Calc.evalPad("1.125")!!, 1e-9)
            assertEquals(0.75, Calc.evalPad("0,5+0,25")!!, 1e-9)
            assertEquals(12.5, Calc.evalPercent("12,5%")!!, 1e-9)
            Locale.setDefault(Locale.ENGLISH)
            assertEquals(1500.0, Calc.evalPad("1,500")!!, 1e-9)
            assertEquals(1.5, Calc.evalPad("1.5")!!, 1e-9)
        } finally {
            Locale.setDefault(before)
        }
        assertNull(Calc.eval("NaN"))
        assertNull(Calc.eval("Infinity"))
        assertNull(Calc.eval("1e400"))
    }

    @Test
    fun `walk-in tickets are in the sales register, with the ticket's difference`() = runBlocking {
        val a = dao.insertProduct(product("Karate", unit = "bottle"))
        val b = dao.insertProduct(product("DAP"))
        val sale = walkIn(listOf(line(a, "Karate", 2.0, 100.0, unit = "bottle"), line(b, "DAP", 1.0, 300.0)), discount = 50.0)
        val number = dao.getSale(sale)!!.saleNumber
        val rows = dao.registerSales(0, Long.MAX_VALUE, "ADJ", "WALK").filter { it.refNumber == number }
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.partyName == "WALK" })
        assertEquals(450.0, rows.sumOf { it.amount }, 0.001)
        assertEquals(-50.0, rows.single { it.itemName == "ADJ" }.amount, 0.001)
    }

    @Test
    fun `a price label whose money is not the shelf's is a price change`() {
        val tomato = product("Tomato", unit = "kg", price = 300.0, barcode = "00042").copy(id = 7)
        fun label(price: String) = "21" + "00042" + price + Barcodes.checkDigit("21" + "00042" + price)
        val rule = ScaleCode.Rule(price = true)
        val cheap = ScaleCode.line(label("00010"), rule, listOf(tomato)) { 300.0 }!!
        val draft = CounterDraft()
        draft.addWeighed(cheap)
        assertEquals(1, draft.repriced.size)
        val fair = ScaleCode.line(label("12500"), rule, listOf(tomato)) { 300.0 }!!
        val honest = CounterDraft()
        honest.addWeighed(fair)
        assertTrue(honest.repriced.isEmpty())
    }

    @Test
    fun `audit notes go with their sale, and stay out when the ticket is refused`() = runBlocking {
        val p = dao.productById(dao.insertProduct(product("Urea", price = 1000.0)))!!
        val draft = CounterDraft()
        draft.add(p, qty = 1.0).rate = "900"
        draft.payRestInCash()
        val note = AuditLog(user = "Manager", action = AuditAction.PRICE_CHANGED, detail = "Urea: 1000 > 900")
        val sale = Counter.post(dao, draft.snapshot(notes = listOf(note)))
        val logged = dao.allAuditForBackup().single { it.action == AuditAction.PRICE_CHANGED }
        assertEquals("sales", logged.refTable)
        assertEquals(sale, logged.refId)
        // A ticket that fails its checks writes no note.
        val bad = CounterDraft()
        bad.add(p, qty = 1.0)
        try { Counter.post(dao, bad.snapshot(notes = listOf(note))); throw AssertionError("an unpaid walk-in") } catch (e: IllegalArgumentException) {}
        assertEquals(1, dao.allAuditForBackup().count { it.action == AuditAction.PRICE_CHANGED })
    }

    @Test
    fun `a void never leaves a customer's points below nothing`() = runBlocking {
        dao.setSetting(BookSettings.LOYALTY_POINTS_PER_100, "10")
        dao.setSetting(BookSettings.LOYALTY_POINT_VALUE, "1")
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val dap = dao.insertProduct(product("DAP"))
        fun sale(rate: Double, discount: Double = 0.0) = Sale(partyId = aslam, timestamp = now, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0, discountAmount = discount)
        val first = dao.postSale(sale(1000.0), listOf(line(dap, "DAP", 1.0, 1000.0)), emptyList())
        assertEquals(100.0, dao.loyaltyBalance(aslam), 0.001)
        dao.postSale(sale(200.0, discount = 100.0), listOf(line(dap, "DAP", 1.0, 200.0)), emptyList(), pointsUsed = 100.0)
        assertEquals(10.0, dao.loyaltyBalance(aslam), 0.001)
        assertTrue(dao.voidSale(first, "wrong customer"))
        assertEquals(0.0, dao.loyaltyBalance(aslam), 0.001)
    }

    @Test
    fun `round to whole rounds half up`() {
        val items = listOf(SaleItem(saleId = 0, itemName = "X", quantity = 1.0, rate = 100.5))
        assertEquals(101.0, SaleMath.totals(items, roundToWhole = true).grandTotal, 0.0)
        assertEquals(102.0, SaleMath.totals(listOf(items[0].copy(rate = 101.5)), roundToWhole = true).grandTotal, 0.0)
    }

    @Test
    fun `a transfer arrives in the receiving product's own unit`() {
        val cartons = product("Soap", unit = "carton")
        val bottles = product("Soap", unit = "bottle", packUnit = "carton", packSize = 12.0)
        assertEquals(24.0, StockTransfer.inUnitOf(cartons, 2.0, bottles)!!, 1e-9)
        assertEquals(2.0, StockTransfer.inUnitOf(bottles, 24.0, cartons)!!, 1e-9)
        assertEquals(5.0, StockTransfer.inUnitOf(cartons, 5.0, cartons)!!, 1e-9)
        assertNull(StockTransfer.inUnitOf(product("Urea", unit = "kg"), 1.0, product("Urea", unit = "bag")))
    }

    @Test
    fun `a restore says the voids, counts and closes it would undo`() = runBlocking {
        val p = dao.insertProduct(product("DAP"))
        val sale = walkIn(listOf(line(p, "DAP", 1.0, 1000.0)))
        val out = java.io.StringWriter()
        BackupCodec.exportTo(dao, "T", org.json.JSONObject(), out)
        val copy = BackupCodec.parseText(out.toString()).second!!
        assertTrue(RestoreCheck.of(dao, copy).none)
        // After the copy: the ticket voided, a stock correction, the day closed.
        assertTrue(dao.voidSale(sale, "test"))
        dao.adjustStock(p, -1.0, AdjustReason.DAMAGE, "torn")
        dao.saveDayClose(DayClose(day = DayCloseMath.dayOf(now), opening = 0.0, cashIn = 0.0, cashOut = 0.0, counted = 0.0, closedAt = now))
        val loss = RestoreCheck.of(dao, copy)
        // The ticket the copy holds posted, the correction and the close: each would be undone, and is said.
        assertEquals(3, loss.others)
        assertTrue(!loss.none)
    }

    @Test
    fun `a ticket is taken once, and what is scanned while it posts stays for the next`() {
        val urea = product("Urea", price = 1000.0).copy(id = 1)
        val dap = product("DAP", price = 3000.0).copy(id = 2)
        val draft = CounterDraft()
        draft.add(urea, qty = 2.0)
        draft.payRestInCash()
        val ticket = draft.snapshot()
        // The next customer's item, rung up while the first is being written.
        draft.add(dap, qty = 1.0)
        assertEquals(1, ticket.items.size)
        assertEquals(2000.0, ticket.payments.sumOf { it.amount }, 0.001)
        draft.clearAfter(ticket)
        assertEquals(listOf("DAP"), draft.lines.map { it.name })
        assertTrue(draft.payments.isEmpty())
    }
}
