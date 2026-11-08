package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.Words
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * What is left of a batch, by one rule (Marhala 3, 25 Oct 2026, the audit's "three places count what is
 * left three ways"): the counter, the Expiry page, the stock register, the bell and the khata's batch choice
 * all read Fefo's figure. Before, the register took off only tagged sales in the same unit, so a lot written
 * off still showed its stock and a carton sold by the bottle showed whole; the Expiry list and its badge took
 * off khata sales only; the bell counted every dated batch. Made-up names.
 */
class BatchRuleTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-batch-rule").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private fun product(name: String, packUnit: String? = null, packSize: Double? = null) =
        Product(name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), defaultUnit = "bottle",
            salePrice = 1000.0, packUnit = packUnit, packSize = packSize)

    private class Book(val confidor: Product, val karate: Product, val a: Long, val b: Long, val old: Long, val new: Long, val m: Long)

    /**
     * Confidor in cartons of 40: lot A 2 cartons (10 days), lot B 30 bottles (60 days). Karate: OLD 5 bottles
     * (expired three days ago), NEW 12 bottles (20 days). A line no product is linked to: M, 10 bottles (15 days).
     * Then: 5 bottles of A on the khata; 3 over the counter (FEFO takes A) and 1 of them back; OLD written off;
     * 2 Karate on the khata with no batch; 4 of M on the khata.
     */
    private suspend fun book(): Book {
        val engro = dao.insertParty(Party(name = "Green Fields Agro", isCustomer = false))
        val aslam = dao.insertParty(Party(name = "Aslam"))
        val confidorId = dao.insertProduct(product("Confidor", packUnit = "carton", packSize = 40.0))
        val karateId = dao.insertProduct(product("Karate"))
        dao.insertSupplierBill(
            null, SupplierBill(partyId = engro, billDate = now - 30 * day, totalAmount = 0.0),
            listOf(
                BillItem(billId = 0, productName = "Confidor", batchNumber = "A", expiryDate = now + 10 * day, quantity = 2.0, unit = "carton", rate = 38_000.0, productId = confidorId),
                BillItem(billId = 0, productName = "Confidor", batchNumber = "B", expiryDate = now + 90 * day, quantity = 30.0, unit = "bottle", rate = 950.0, productId = confidorId),
                BillItem(billId = 0, productName = "Karate", batchNumber = "OLD", expiryDate = now - 3 * day, quantity = 5.0, unit = "bottle", rate = 700.0, productId = karateId),
                BillItem(billId = 0, productName = "Karate", batchNumber = "NEW", expiryDate = now + 20 * day, quantity = 12.0, unit = "bottle", rate = 700.0, productId = karateId),
                BillItem(billId = 0, productName = "Mystery spray", batchNumber = "M", expiryDate = now + 15 * day, quantity = 10.0, unit = "bottle", rate = 500.0)
            )
        )
        val confidor = dao.productById(confidorId)!!
        val karate = dao.productById(karateId)!!
        val rows = dao.batchRowsOnce()
        fun id(batch: String) = rows.single { it.batchNumber == batch }.itemId
        val a = id("A"); val b = id("B"); val old = id("OLD"); val new = id("NEW"); val m = id("M")

        dao.insertEntryWithItems(
            LedgerEntry(partyId = aslam, amount = 5_000.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Confidor", quantity = 5.0, unit = "bottle", rate = 1000.0, productId = confidorId, billItemId = a))
        )
        val draft = CounterDraft()
        draft.add(confidor, qty = 3.0)
        draft.payRestInCash()
        val sale = Counter.post(dao, draft)
        val sold = dao.saleItems(sale).single()
        assertEquals("FEFO takes the soonest lot", a, sold.billItemId)
        dao.postReturn(
            SaleReturn(saleId = sale, total = 0.0, refundMethod = PaymentMethod.CASH, refundAmount = 1000.0, reason = "leaking", timestamp = now),
            listOf(SaleReturnItem(returnId = 0, saleItemId = sold.id, productId = confidorId, itemName = "Confidor", quantity = 1.0, unit = "bottle", rate = 1000.0))
        )
        assertEquals(5.0, Fefo.writeOff(dao, old, "OLD")!!, 0.001)
        dao.insertEntryWithItems(
            LedgerEntry(partyId = aslam, amount = 2_000.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Karate", quantity = 2.0, unit = "bottle", rate = 1000.0, productId = karateId))
        )
        dao.insertEntryWithItems(
            LedgerEntry(partyId = aslam, amount = 2_000.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Mystery spray", quantity = 4.0, unit = "bottle", rate = 500.0, billItemId = m))
        )
        return Book(confidor, karate, a, b, old, new, m)
    }

    @Test
    fun `every page reads one figure for what is left of a batch`() = runBlocking {
        val k = book()
        // The rule itself: A 80 - 5 - 3 + 1, B whole, OLD written off, NEW 12 held to the shelf's 10 (the
        // 2 sold with no batch came from the lot sold first), M 10 - 4 with no product to say more.
        val all = Fefo.allLots(dao)
        assertEquals(73.0, all.getValue(k.a).left, 0.001)
        assertEquals("a carton is counted in bottles", "bottle", all.getValue(k.a).unit)
        assertEquals(30.0, all.getValue(k.b).left, 0.001)
        assertEquals(0.0, all.getValue(k.old).left, 0.001)
        assertEquals(10.0, all.getValue(k.new).left, 0.001)
        assertEquals(6.0, all.getValue(k.m).left, 0.001)

        // The counter's own reading of each product is the same.
        for (p in listOf(k.confidor, k.karate)) {
            for (lot in Fefo.lots(dao, p)) {
                assertEquals("lot ${lot.batchNumber}", lot.left, all.getValue(lot.billItemId).left, 0.0001)
                assertEquals(lot.unit, all.getValue(lot.billItemId).unit)
            }
        }

        // The register: what is left, nothing for a lot written off.
        val register = Fefo.register(dao).associateBy { it.itemId }
        assertEquals(setOf(k.a, k.b, k.new, k.m), register.keys)
        assertEquals(73.0, register.getValue(k.a).remaining, 0.001)
        assertEquals("bottle", register.getValue(k.a).unit)
        assertEquals(10.0, register.getValue(k.new).remaining, 0.001)
        assertEquals(6.0, register.getValue(k.m).remaining, 0.001)
        // What the bill wrote is still there for the trace: 2 cartons came.
        assertEquals(2.0, dao.batchRowsOnce().single { it.itemId == k.a }.quantity, 0.001)

        // Expiry within 30 days: A, M, NEW soonest first, with the same figures; OLD has nothing left, B is later.
        val soon = Fefo.expiring(dao, now + 30 * day)
        assertEquals(listOf(k.a, k.m, k.new), soon.map { it.itemId })
        assertEquals(listOf(73.0, 6.0, 10.0), soon.map { it.quantity })
        assertEquals(3, Fefo.observeExpiringCount(dao, now + 30 * day).first())

        // The khata's batch choice says what the counter says.
        val options = Fefo.options(dao, k.confidor)
        assertEquals(listOf(k.a, k.b), options.map { it.id })
        assertEquals(73.0, options.first().remaining, 0.001)
        assertEquals("bottle", options.first().unit)

        // A return to the supplier is written in the bill's own unit: 73 bottles are 1.825 cartons.
        assertEquals(73.0 / 40.0, Fefo.leftInBillUnit(dao, k.a)!!, 0.0001)
        assertEquals(6.0, Fefo.leftInBillUnit(dao, k.m)!!, 0.0001)
    }

    @Test
    fun `the bell counts the batches that still hold something`() = runBlocking {
        val k = book()
        val words = object : Words {
            override fun text(name: String, vararg args: Any) = name + ":" + args.joinToString(",")
            override fun plural(name: String, count: Int, vararg args: Any) = name
            override fun list(name: String) = emptyList<String>()
        }
        suspend fun expiringRow() = LedgerAlerts.compute(words, dao, { it.toString() }).firstOrNull { it.kind == LedgerAlerts.Kind.EXPIRING }
        // A, M and NEW expire within the 60-day window (ExpiryWindow) and hold something; B is later; OLD was
        // written off (it counted before).
        assertEquals("na_expiring:3", expiringRow()!!.title)
        // Write off what is left of M, sell the rest of A over the counter: the bell follows.
        dao.insertEntryWithItems(
            LedgerEntry(partyId = dao.insertParty(Party(name = "Bilal")), amount = 3_000.0, isGiven = true, entryNumber = ""),
            listOf(EntryItem(entryId = 0, itemName = "Mystery spray", quantity = 6.0, unit = "bottle", rate = 500.0, billItemId = k.m))
        )
        val draft = CounterDraft()
        draft.add(k.confidor, qty = 73.0)
        draft.payRestInCash()
        Counter.post(dao, draft)
        assertEquals(0.0, Fefo.allLots(dao).getValue(k.a).left, 0.001)
        assertEquals("na_expiring:1", expiringRow()!!.title)
        assertFalse(Fefo.register(dao).any { it.itemId == k.a || it.itemId == k.m })
        assertNotNull(Fefo.register(dao).firstOrNull { it.itemId == k.new })
        assertTrue(Fefo.expiring(dao, now + 30 * day).map { it.itemId } == listOf(k.new))
    }
}
