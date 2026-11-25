package com.innovation313.roshankhata.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * D3 (BC-293, 25 Nov 2026, money): a sold line returned in parts gives back, over all its parts, exactly what the sales
 * report counted it at. The book's own query says how much of the line came back before each return
 * ([KhataDao.returnedLinesBetween]'s backBefore); [ReturnedLine.revenue] then rounds the running share once. A line of
 * 3 at 100 with 0.01 off (299.99) returned one bag at a time gave back 100.00 three times (300.00). CI is the first run
 * of this class (Room); the arithmetic is held in FormulaDisagreementsTest. Made-up names and figures.
 */
class ReturnedPartsDesktopTest {

    private lateinit var dir: File
    private lateinit var db: KhataDatabase
    private val dao get() = db.khataDao()
    private val now = System.currentTimeMillis()

    @Before
    fun open() {
        dir = Files.createTempDirectory("rk-returned-parts").toFile()
        db = DesktopDatabase.open(dir.resolve("khata.db"))
    }

    @After
    fun close() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun `a line returned one bag at a time gives back what it was counted at, no paisa more`() = runBlocking {
        val customer = dao.insertParty(Party(name = "Customer A"))
        val sale = dao.postSale(
            Sale(partyId = customer, timestamp = now - 10_000, subtotal = 0.0, grandTotal = 0.0, paidTotal = 0.0),
            listOf(SaleItem(saleId = 0, itemName = "Bag", quantity = 3.0, unit = "bag", rate = 100.0, discount = 0.01)),
            emptyList()
        )
        val line = dao.saleItems(sale).single()
        repeat(3) { k ->
            dao.postReturn(
                SaleReturn(saleId = sale, partyId = customer, total = 0.0, refundMethod = SaleReturn.ACCOUNT, refundAmount = 0.0,
                    reason = "damaged", timestamp = now + k),
                listOf(SaleReturnItem(returnId = 0, saleItemId = line.id, itemName = "Bag", quantity = 1.0, unit = "bag", rate = 100.0))
            )
        }
        val back = dao.returnedLinesBetween(now - 60_000, now + 60_000).sortedBy { it.backBefore }
        assertEquals(listOf(0.0, 1.0, 2.0), back.map { it.backBefore })
        val sold = SoldLines.spread(dao.soldLinesBetween(now - 60_000, now + 60_000)).single { it.saleId == sale }.revenue
        assertTrue("sold $sold, back ${back.map { it.revenue }}", Money.same(sold, back.sumOf { it.revenue }))
        // The walk-in reader counts the same way (none here: this ticket is a customer's).
        assertTrue(dao.walkInReturnedLinesBetween(now - 60_000, now + 60_000).isEmpty())
    }
}
